package com.example.villagefightsback;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.example.villagefightsback.goal.GeneralDariusGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * General Darius: a boss-grade upgrade of the stage-4 guard, summoned only by a stage-4 Village Brain Node's
 * emerald-funded reflex ({@link BrainReflexes}). Modelled on {@link VindictiveGuardEntity} (a custom
 * ZombieVillager-bodied fighter), but he wields the {@link GuardWeapons#alienSmasher() Alien Smasher} (no
 * crossbow), wears the FULL Limbo set including the Limbo Elytra Chestplate, and gets every Limbo full-set
 * advantage (fire/fall immunity, harmful-effect clearing, contact soft-block breaking, and -- below 50% HP --
 * the Limbo banish beam). He is immune to Limbo banishment (see {@link LimboDimension#sendToLimbo}), has a boss
 * bar, and a persistent "Darius" name tag. His combat brain is {@link GeneralDariusGoal}; his navigation is the
 * {@link DariusNavigation} (routes around village walls, plows through generic terrain).
 *
 * On the locked-target queue dying out, Darius walks back to his home brain node and despawns, REFUNDING the
 * 64 gems he cost to that brain's economy ({@link #refundAndDespawn()}).
 */
public class GeneralDariusEntity extends Monster {
    private static final String NBT_KILLS = "darius_kills";
    private static final String NBT_RIGHT_ARM = "darius_right_arm";
    private static final String NBT_LEFT_ARM = "darius_left_arm";
    private static final String NBT_HOME_DIM = "darius_home_dim";
    private static final String NBT_HOME_POS = "darius_home_pos";

    /** The brain node's influence radius -- the zone Darius hunts within (set from Config.apply(); base 100). */
    public static double BRAIN_RADIUS = 100.0;
    /** Targets weaker than this max-HP are ignored once the first lock dies (a "big threat" floor). */
    private static final double NEXT_TARGET_MIN_MAXHP = 50.0;
    /** A player is "dangerous" if they have recently killed several villagers/guards (see BrainReflexes). */
    public static final int DANGEROUS_PLAYER_KILLS = 3;

    private static final int BEAM_COOLDOWN = 600; // 30s, only fires below 50% HP

    /**
     * The jump-and-smash is now a SPECIAL, once every {@value #SMASH_COOLDOWN} ticks.
     *
     * <p>It used to be his default move. The launch recharged in 35 ticks and the smash itself had no
     * cooldown at all -- it was gated only on being airborne and descending -- so a Darius in the open
     * simply never stopped leaping, and a player never got a window in which to fight him. He now does it
     * once, then fights with the Obliterator like anything else until it comes back.
     */
    private static final int SMASH_COOLDOWN = 200;              // 10s
    /** ...and it is handed back ONCE, the first time he drops this low, so the last stand still bites. */
    private static final float SMASH_RESET_HP = 0.25F;
    /** How often he can put an Arm back on the field. */
    private static final int ARMS_COOLDOWN = 400;               // 20s
    /**
     * How often he may drink his own Special Sauce.
     *
     * <p>The below-50% grant used to re-arm the moment he healed back above half, and since the Sauce
     * reflects everything thrown at him he could heal past half on the reflection alone -- drop, drink,
     * reflect, heal, repeat. Ten minutes between doses makes it a comeback rather than a state of being.
     */
    private static final int SAUCE_COOLDOWN = 12000;            // 10 minutes

    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.literal("General Darius"),
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);

    /** Hired-guard squad (UUIDs); transient -- released on death/despawn. */
    private final Set<UUID> squad = new LinkedHashSet<>();

    @Nullable private GlobalPos homeBrain;
    private int beamCooldown;
    private int smashCooldown;
    private int armsCooldown;
    private int sauceCooldown;
    /** True while the special is in the air: he is holding the Smasher and owes the world one landing. */
    private boolean smashing;
    /**
     * True once this activation's mace blow has landed.
     *
     * <p>The Mace bounces its wielder off whatever it hits, and the dive test is only "airborne, falling,
     * near the target" -- so the bounce fed straight back into another smash and he pogoed on anything
     * that survived the first hit. One activation is one blow; the bounce is now just a landing.
     */
    private boolean smashSpent;
    /** The one below-{@value #SMASH_RESET_HP} refund, so it cannot be farmed by healing back up. */
    private boolean smashRefunded;
    /**
     * A target written off as unreachable, and for how much longer.
     *
     * <p>It lives on the ENTITY rather than in the goal because {@link #pickBiggestThreat} is what chooses
     * the next lock, and a goal-side ignore list the picker cannot see produces a loop rather than a fix:
     * the goal drops the target, the picker immediately hands the same one straight back, the goal drops it
     * again -- and since every re-lock announced itself, the whole server got "General Darius hunts X!"
     * once per tick.
     */
    private LivingEntity ignoredTarget;
    private int ignoreTicks;

    /** Lives taken, for {@link DariusStatue}. Counted here because VillagerKills only counts defenders. */
    private int kills;
    /** His two escorts, so exactly one of each stands at a time. */
    private java.util.UUID rightArm;
    private java.util.UUID leftArm;
    private boolean despawning;     // guards refund-once
    private boolean lowHpBuffed; // true once the below-50%-HP buffs (Regen II + Darius' Special Sauce) fired (re-armed above 50%)

    public GeneralDariusEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal("Darius"));
        setCustomNameVisible(true);
        this.xpReward = 100;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)        // stronger than a stage-4 guard
                .add(Attributes.MOVEMENT_SPEED, 0.32)       // a bit faster than a stage-4 guard (0.28)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0); // full KB immunity (matches the Limbo material)
    }

    @Override
    protected void registerGoals() {
        // No vanilla zombie goals (no super call): Darius's combat brain only, plus float + idle look.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new GeneralDariusGoal(this));
        // Dig straight down to a target buried below him (his contact-plowing never breaks the floor he stands on).
        this.goalSelector.addGoal(2, new com.example.villagefightsback.goal.DariusDigDownGoal(this));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // NOTE: no target selector -- the goal locks/retaliates by itself (HurtByTarget is handled in-goal).
    }

    /** Custom navigation: route around village walls, plow through generic terrain (broken on contact). */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new DariusNavigation(this, level);
    }

    /** Equip the FULL Limbo set (incl. the elytra chestplate) + the Alien Smasher; never drop the gear. */
    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor lvl,
            net.minecraft.world.DifficultyInstance diff, net.minecraft.world.entity.MobSpawnType reason,
            net.minecraft.world.entity.SpawnGroupData data) {
        net.minecraft.world.entity.SpawnGroupData r = super.finalizeSpawn(lvl, diff, reason, data);
        equipLimbo(EquipmentSlot.HEAD, ModArmor.LIMBO_HELMET.get());
        equipLimbo(EquipmentSlot.CHEST, ModArmor.LIMBO_ELYTRA_CHESTPLATE.get());
        equipLimbo(EquipmentSlot.LEGS, ModArmor.LIMBO_LEGGINGS.get());
        equipLimbo(EquipmentSlot.FEET, ModArmor.LIMBO_BOOTS.get());
        // The Obliterator is what he FIGHTS with now, and the Paladin Shield is what he fights behind.
        // The Alien Smasher is no longer his sidearm: it is the special, swapped in for the leap and swapped
        // straight back out on landing (see beginSmash/endSmash), so the craters stay tied to the smash.
        setItemSlot(EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(ModItems.OBLITERATOR.get()));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        setItemSlot(EquipmentSlot.OFFHAND, new net.minecraft.world.item.ItemStack(ModItems.PALADIN_SHIELD.get()));
        setDropChance(EquipmentSlot.OFFHAND, 0.0F);

        // Every cooldown starts FULL, so he cannot open the fight with all three specials at once. The first
        // thing a player meets is a swordsman; the rest arrives while they are already committed.
        beamCooldown = BEAM_COOLDOWN;
        smashCooldown = SMASH_COOLDOWN;
        armsCooldown = ARMS_COOLDOWN;
        sauceCooldown = SAUCE_COOLDOWN;
        return r;
    }

    // --- the jump-smash special ------------------------------------------------------------------

    /** One more life taken; the running total. */
    public int recordKill() {
        return ++kills;
    }

    /**
     * Hand back the statue trigger.
     *
     * <p>Called when the twentieth kill found nowhere to put it, so the statue is owed rather than lost.
     */
    public void rearmStatue() {
        kills--;
    }

    /** Write {@code e} off for {@code ticks}: it will not be hunted or re-picked until that runs out. */
    public void ignoreTarget(LivingEntity e, int ticks) {
        ignoredTarget = e;
        ignoreTicks = ticks;
    }

    public boolean isIgnoredTarget(LivingEntity e) {
        return e != null && e == ignoredTarget && ignoreTicks > 0;
    }

    /** Is the leap available? Read by {@code GeneralDariusGoal} before it commits to one. */
    public boolean smashReady() {
        return smashCooldown <= 0 && !smashing;
    }

    /**
     * Commit to the special: the Smasher comes out and the clock starts.
     *
     * <p>The clock starts HERE rather than on the landing, so a leap that misses still costs the full
     * {@value #SMASH_COOLDOWN} ticks. Otherwise a Darius who kept missing would keep leaping.
     */
    public void beginSmash() {
        smashing = true;
        smashSpent = false;
        smashCooldown = SMASH_COOLDOWN;
        setItemSlot(EquipmentSlot.MAINHAND, GuardWeapons.alienSmasher().copy());
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    /** Back to the Obliterator, whether the smash landed or he simply came down. */
    public void endSmash() {
        if (!smashing) return;
        smashing = false;
        setItemSlot(EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(ModItems.OBLITERATOR.get()));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public boolean isSmashing() {
        return smashing;
    }

    /** Has this activation's one mace blow already landed? */
    public boolean smashSpent() {
        return smashSpent;
    }

    /** The blow landed: the Smasher goes away immediately, so the bounce cannot become a second smash. */
    public void spendSmash() {
        smashSpent = true;
        endSmash();
    }

    // --- the Right Arm and the Left Arm -----------------------------------------------------------

    /**
     * Put an Arm back on the field, at most one of each, at most one every {@value #ARMS_COOLDOWN} ticks.
     *
     * <p>Two checks, because either alone leaks. The tracked UUID is the cheap one and survives a reload;
     * the name sweep is the one that stops a SECOND Darius standing nearby from raising a third Right Arm,
     * since the user's rule is one of each on the field rather than one of each per champion. The sweep is a
     * 64-block box on a 20-second cooldown, so it is nothing.
     */
    private void maybeSummonArms(ServerLevel sl) {
        if (armsCooldown > 0) return;
        boolean spawned = false;
        if (armFor(sl, rightArm, ARM_RIGHT) == null) {
            LivingEntity m = spawnArm(sl, ARM_RIGHT, -1.0);
            if (m != null) { rightArm = m.getUUID(); spawned = true; }
        }
        if (armFor(sl, leftArm, ARM_LEFT) == null) {
            LivingEntity m = spawnArm(sl, ARM_LEFT, 1.0);
            if (m != null) { leftArm = m.getUUID(); spawned = true; }
        }
        if (spawned) armsCooldown = ARMS_COOLDOWN;
    }

    /** The living Arm with this id, or any other bearing the name nearby; null when the post is vacant. */
    private LivingEntity armFor(ServerLevel sl, java.util.UUID id, String name) {
        if (id != null) {
            net.minecraft.world.entity.Entity e = sl.getEntity(id);
            if (e instanceof LivingEntity le && le.isAlive()) return le;
        }
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(64.0), x -> x.isAlive() && x.hasCustomName())) {
            if (name.equals(e.getCustomName().getString())) return e;
        }
        return null;
    }

    /** A stage-4 guard at his shoulder. {@code side} is -1 for his right, +1 for his left. */
    private LivingEntity spawnArm(ServerLevel sl, String name, double side) {
        net.minecraft.world.entity.EntityType<?> type =
                com.example.villagefightsback.compat.MCACompat.guardType(sl.getRandom());
        if (type == null) return null;                     // neither MCA nor Guard Villagers present
        double yaw = Math.toRadians(getYRot());
        // The vector to his right is the facing turned 90 degrees, which in Minecraft's yaw is (-cos, -sin).
        double rx = getX() + side * -Math.cos(yaw) * 1.5;
        double rz = getZ() + side * -Math.sin(yaw) * 1.5;
        net.minecraft.core.BlockPos spot = BrainNodeEvents.nearestFit(
                sl, net.minecraft.core.BlockPos.containing(rx, getY(), rz), type, 3);
        if (spot == null) return null;

        net.minecraft.world.entity.Entity e = type.create(sl);
        if (!(e instanceof net.minecraft.world.entity.Mob mob)) return null;
        mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, getYRot(), 0.0F);
        // 1.21.1 dropped finalizeSpawn's trailing CompoundTag argument.
        mob.finalizeSpawn(sl, sl.getCurrentDifficultyAt(spot),
                net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null);
        com.example.villagefightsback.compat.MCACompat.makeGuard(mob);
        // Stage 4 must be stamped BEFORE the join handler reads it, exactly as StagedDefenderEggItem does.
        UpgradeState.setStage4(mob);
        GuardWeapons.equipShield(mob);      // the escort keeps the shield ordinary guards gave up
        mob.setCustomName(net.minecraft.network.chat.Component.literal(name));
        mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();
        sl.addFreshEntity(mob);
        squad.add(mob.getUUID());          // they follow him like the rest of the escort
        return mob;
    }

    /**
     * The Arms fight whatever he is fighting.
     *
     * <p>They are guards, and a guard left to its own AI picks the nearest thing it dislikes -- which is
     * how an escort ends up scattered across a field while the champion fights alone. Pushing his lock onto
     * them every ten ticks keeps the three of them as one unit.
     */
    private void commandArms(ServerLevel sl) {
        LivingEntity t = getTarget();
        for (java.util.UUID id : new java.util.UUID[]{rightArm, leftArm}) {
            if (id == null) continue;
            if (!(sl.getEntity(id) instanceof net.minecraft.world.entity.Mob arm) || !arm.isAlive()) continue;
            // Left behind -> put them back at his shoulder. A guard's own navigation gives up long
            // before {@value #ARM_LEASH} blocks, so an Arm that lost a step during a chase over broken
            // ground never caught up again and the escort quietly stopped existing.
            if (arm.distanceToSqr(this) > ARM_LEASH * ARM_LEASH) {
                arm.teleportTo(getX(), getY(), getZ());
                arm.getNavigation().stop();
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                        arm.getX(), arm.getY() + 1.0, arm.getZ(), 16, 0.4, 0.6, 0.4, 0.1);
            }

            // His fight is their fight. Pushed on EVERY call rather than only when it differs: a guard's
            // own target goals re-pick constantly, so a one-shot assignment is overwritten within the tick
            // and the escort wanders off after whatever happens to be nearest to itself.
            if (arm.getTarget() != t) arm.setTarget(t);
        }
    }

    /** How far an Arm may drift before it is put back at his shoulder. */
    private static final double ARM_LEASH = 50.0;

    public static final String ARM_RIGHT = "Right Arm";
    public static final String ARM_LEFT = "Left Arm";

    /** How long Darius's mark lasts. The same minute the Obliterator gives, so one rule covers both. */
    private static final int OBLITERATION_TICKS = 20 * 60;

    /**
     * Every blow marks its victim with Obliteration, so what Darius kills near a burning furnace is struck off
     * the world's spawn tables.
     *
     * <p>On DARIUS, deliberately, and not on the Alien Smasher he carries. He drops that weapon when he dies
     * and the player picks it up, so putting the mark on the item would hand the village's own power of
     * erasure to whoever beat him -- and it would spread further than that, since {@code
     * GuardWeapons.alienSmasher()} arms every guard in the village once mega-brutal mode is on. The mark
     * belongs to the general, not the sword.
     *
     * <p>NEVER a player. {@link ObliterationEvents} already refuses to obliterate one, so the mark could not
     * erase anybody -- but a purple HARMFUL icon that does nothing reads as a threat the game never carries
     * out. See {@code Config.dariusInflictsObliteration}, which switches the whole mechanism off.
     */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && Config.dariusInflictsObliteration()
                && target instanceof LivingEntity victim
                && !(target instanceof net.minecraft.world.entity.player.Player)) {
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    ModEffects.OBLITERATION, OBLITERATION_TICKS, 0, false, true, true));
        }
        return hit;
    }

    private void equipLimbo(EquipmentSlot slot, net.minecraft.world.item.Item piece) {
        setItemSlot(slot, new net.minecraft.world.item.ItemStack(piece));
        setDropChance(slot, 0.0F);
    }

    /** On death Darius always drops the actual (used) Alien Smasher he wielded -- enchants + durability intact.
     *  The limbo armour never drops (drop chances are 0). Only fires on a real death, not the refund-despawn. */
    @Override
    protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        // Always the Obliterator, even if he died mid-leap with the Smasher in hand: the weapon he is
        // known by should not depend on which frame of which animation killed him.
        endSmash();
        net.minecraft.world.item.ItemStack weapon = getMainHandItem();
        if (!weapon.isEmpty()) {
            spawnAtLocation(weapon.copy());
            setItemSlot(EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY); // no duplicate
        }
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random,
            net.minecraft.world.DifficultyInstance difficulty) {
        // intentionally empty: gear is set in finalizeSpawn
    }

    /** Spawn {@code count} village guards next to Darius and immediately enroll them in his squad
     *  (guards_spawning_with_general_darius). No-op if count &lt;= 0 or Guard Villagers isn't installed. */
    public void enrollEscort(net.minecraft.server.level.ServerLevel level, int count) {
        if (count <= 0) return;
        // entityTypeOrNull, not ENTITY_TYPE.get: the entity registry is DEFAULTED, so get() would return a PIG
        // rather than null when Guard Villagers is absent (see MCACompat#present) and Darius would march out
        // with an escort of pigs in stage-3 guard kit.
        // MCA first: with MCA installed its humans are what a village is made of, so an escort of blank-faced
        // Guard Villagers guards stood out as the only non-humans in town. guardType falls back to the Guard
        // Villagers entity on its own when MCA is absent, so this keeps the old behaviour without MCA.
        net.minecraft.world.entity.EntityType<?> guardType =
                com.example.villagefightsback.compat.MCACompat.guardType(level.getRandom());
        if (guardType == null) return; // neither MCA nor Guard Villagers installed
        for (int i = 0; i < count; i++) {
            net.minecraft.world.entity.Entity e = guardType.create(level);
            if (!(e instanceof net.minecraft.world.entity.Mob m)) { if (e != null) e.discard(); continue; }
            double ang = level.random.nextDouble() * Math.PI * 2.0;
            double r = 1.5 + level.random.nextDouble() * 2.0;
            m.moveTo(getX() + Math.cos(ang) * r, getY(), getZ() + Math.sin(ang) * r,
                    level.random.nextFloat() * 360.0F, 0.0F);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()),
                    net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null);
            com.example.villagefightsback.compat.MCACompat.makeGuard(m); // pin mca:guard (no-op off MCA)
            level.addFreshEntity(m);
            squad.add(m.getUUID());
        }
    }

    // --- the home brain node (for refund + walk-home) -------------------------------------------------
    public void setHomeBrain(@Nullable GlobalPos gp) { this.homeBrain = gp; }

    @Nullable
    public GlobalPos getHomeBrain() { return homeBrain; }

    /** The home brain block position if it is in THIS level, else null (used by the walk-home goal). */
    @Nullable
    public BlockPos getHomeBrainPos() {
        if (homeBrain == null) return null;
        if (level().dimension() != homeBrain.dimension()) return null;
        return homeBrain.pos();
    }

    // --- per-tick: Limbo full-set advantages + boss bar + squad + beam cooldown ----------------------
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl)) return;

        // Full-set Limbo advantages (LimboGearEvents only handles players, so apply them here to Darius).
        clearFire();
        fallDistance = 0.0F;
        if ((tickCount & 7) == 0) clearHarmfulEffects();
        // Gated HERE rather than inside LimboGear, which players in a full Limbo set also call.
        if (Config.dariusCanBreakBlocks()) {
            LimboGear.breakSoftBlocksOnContact(this); // breaks soft blocks (incl. cobblestone) on contact
        }

        if (beamCooldown > 0) beamCooldown--;
        if (smashCooldown > 0) smashCooldown--;
        if (armsCooldown > 0) armsCooldown--;
        if (sauceCooldown > 0) sauceCooldown--;
        if (ignoreTicks > 0 && --ignoreTicks == 0) ignoredTarget = null;

        // Below 50% HP: spontaneously gain Regeneration II (30s) AND Darius' Special Sauce (10s). Granted once on
        // dropping below half, then re-armed if he heals back above half (so a second collapse triggers again).
        if (getHealth() < getMaxHealth() * 0.5F) {
            if (!lowHpBuffed && sauceCooldown <= 0) {
                addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.REGENERATION, 600, 1, false, true, true));
                addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        ModEffects.DARIUS_SPECIAL_EFFECT, 200, 0, false, true, true));
                lowHpBuffed = true;
                sauceCooldown = SAUCE_COOLDOWN;
            }
            // The one smash refund, at a quarter health. Separate from the Sauce on purpose: this is the
            // moment the fight should get harder again, and it should happen even when the Sauce is spent.
            if (!smashRefunded && getHealth() < getMaxHealth() * SMASH_RESET_HP) {
                smashCooldown = 0;
                smashRefunded = true;
            }
        } else {
            lowHpBuffed = false;
        }

        maybeSummonArms(sl);
        commandArms(sl);

        bossBar.setProgress(getHealth() / getMaxHealth());
        // While hunting (a locked target) Darius glows so players can always spot him; off when walking home.
        setGlowingTag(getTarget() != null);

        if ((tickCount % 10) == 0) DariusSquad.tick(sl, this, squad); // hire + command the guard squad (count hidden)
    }

    private void clearHarmfulEffects() {
        var harmful = getActiveEffects().stream()
                .map(net.minecraft.world.effect.MobEffectInstance::getEffect)
                .filter(eff -> eff.value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL)
                .toList();
        for (var eff : harmful) removeEffect(eff);
    }

    // --- full-set damage immunity (fire + fall), mirroring LimboGearEvents.onHurt for the player --------
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            return false; // fire + fall immunity
        }
        return super.hurt(source, amount);
    }

    @Override public boolean fireImmune() { return true; }
    @Override public void igniteForTicks(int ticks) { /* fire-immune */ }
    @Override public boolean causeFallDamage(float distance, float multiplier,
            net.minecraft.world.damagesource.DamageSource source) { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }

    // --- targeting: pick the biggest threat in the brain's range ------------------------------------
    /**
     * The enemy with the MOST max-health within {@link #BRAIN_RADIUS}, OR a "dangerous" enemy player (one who
     * has recently killed >= {@value #DANGEROUS_PLAYER_KILLS} villagers/guards near the home brain). When the
     * first lock has died, callers want the NEXT biggest -- so we only consider enemies with maxHealth
     * >= {@value #NEXT_TARGET_MIN_MAXHP}, plus dangerous players. Returns null when nothing qualifies.
     *
     * <p>That floor is dropped for a Darius with NO HOME BRAIN, i.e. one placed by hand with the spawn egg or a
     * command rather than summoned by a village. A summoned champion is deliberately a boss-hunter who ignores
     * small fry and marches home, but a hand-placed one has no home to march to and no treasury to refund, so
     * with the floor in place he simply stood inert in any ordinary village, every mob in it being under 50 HP.
     */
    /**
     * The biggest thing worth fighting near {@code center}: highest max health, at least {@code minMaxHp}.
     *
     * <p>The same reach and the same faction filter Darius uses to pick his next target, lifted out so the
     * brain node's sunset bounty can ask the identical question without a second, drifting copy of the rules
     * for what counts as a threat. Players are never candidates: {@code Factions.isVillageThreat} tests
     * {@code Enemy}, which a player is not.
     *
     * <p>Testing {@code Enemy} rather than a list of names is what makes this work with an infection mod the
     * village has never heard of. An arbitrary third-party horror qualifies for a bounty the day it is
     * installed, with no compatibility code at all.
     */
    @Nullable
    public static LivingEntity biggestThreatNear(ServerLevel level, BlockPos center, double radius,
                                                 double minMaxHp) {
        LivingEntity best = null;
        double bestHp = minMaxHp - 1.0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(center).inflate(radius), GeneralDariusEntity::isThreat)) {
            if (e.getMaxHealth() > bestHp) { bestHp = e.getMaxHealth(); best = e; }
        }
        return best;
    }

    /** The faction half of {@link #isHuntable}, without the "not me" test an instance needs. */
    private static boolean isThreat(LivingEntity e) {
        if (!e.isAlive() || Factions.isInfected(e) || Factions.isEmerald(e)) return false;
        return Factions.isVillageThreat(e);
    }

    public LivingEntity pickBiggestThreat() {
        if (!(level() instanceof ServerLevel sl)) return null;
        LivingEntity best = null;
        double bestHp = -1.0;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(BRAIN_RADIUS), this::isHuntable)) {
            boolean dangerous = e instanceof Player p && BrainReflexes.isDangerousPlayer(sl, p, homeBrain);
            boolean freelance = homeBrain == null;   // hand-placed: no village summoned him, so he hunts anything
            if (!dangerous && !freelance && e.getMaxHealth() < NEXT_TARGET_MIN_MAXHP) continue;
            double hp = dangerous ? Math.max(e.getMaxHealth(), 1000.0) : e.getMaxHealth(); // prioritise a dangerous player
            if (hp > bestHp) { bestHp = hp; best = e; }
        }
        return best;
    }

    private boolean isHuntable(LivingEntity e) {
        // Emerald units are a separate faction the village never fights (see EmeraldConversion faction rules), so
        // Darius -- a village champion -- never hunts or chases them.
        // ...and neither is another champion. GeneralDariusEntity extends Monster, so it IS an Enemy, so
        // Factions.isVillageThreat answers true for one Darius looking at another -- which is why two of them
        // placed by a player went straight for each other. VillageEvents cancels the damage and the target
        // change, so the fight looked harmless, but the banish beam, the Alien Smasher craters and the
        // "General Darius hunts Darius!" broadcast all still landed. isSparedByVillage is the predicate that
        // already knows the answer; the two huntability tests were simply never asking it.
        if (e == this || !e.isAlive() || Factions.isInfected(e) || Factions.isSparedByVillage(e)) return false;
        if (isIgnoredTarget(e)) return false;   // written off as unreachable; see ignoreTarget
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return com.example.villagefightsback.Factions.isVillageThreat(e);
    }

    // --- the banish beam (below 50% HP, 30s cooldown) ------------------------------------------------
    /** Fire the Limbo banish beam at {@code target} if off cooldown. Below-50%-HP gate is enforced by caller. */
    public void tryFireBeamAt(LivingEntity target) {
        if (beamCooldown > 0) return;
        net.minecraft.world.phys.Vec3 origin = getEyePosition();
        net.minecraft.world.phys.Vec3 dir = target.getEyePosition().subtract(origin).normalize();
        LimboBeam.fireFrom(this, origin, dir, net.minecraft.sounds.SoundSource.HOSTILE);
        beamCooldown = BEAM_COOLDOWN;
    }

    // --- refund + despawn at home --------------------------------------------------------------------
    /** Refund the 64 gems Darius cost to the home brain's economy, release the squad, and discard. Once-only. */
    public void refundAndDespawn() {
        if (despawning) return;
        despawning = true;
        if (level() instanceof ServerLevel sl) {
            DariusSquad.release(sl, squad);
            if (homeBrain != null) {
                BrainNodeData data = BrainNodeData.get(sl.getServer());
                if (data.blocks().contains(homeBrain)) {
                    data.econ(homeBrain).addEmeralds(64);
                    BrainReflexes.logExternal(sl, homeBrain.pos(), "General Darius returns (+64)");
                }
            }
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                    getX(), getY() + 1.0, getZ(), 24, 0.4, 0.6, 0.4, 0.02);
        }
        discard();
    }

    // --- boss bar player tracking --------------------------------------------------------------------
    @Override
    public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossBar.addPlayer(player); }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        if (level() instanceof ServerLevel sl) DariusSquad.release(sl, squad);
        super.remove(reason);
    }

    // --- persistence ---------------------------------------------------------------------------------
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(NBT_KILLS, kills);
        if (rightArm != null) tag.putUUID(NBT_RIGHT_ARM, rightArm);
        if (leftArm != null) tag.putUUID(NBT_LEFT_ARM, leftArm);
        if (homeBrain != null) {
            tag.putString(NBT_HOME_DIM, homeBrain.dimension().location().toString());
            tag.putLong(NBT_HOME_POS, homeBrain.pos().asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        kills = tag.getInt(NBT_KILLS);
        rightArm = tag.hasUUID(NBT_RIGHT_ARM) ? tag.getUUID(NBT_RIGHT_ARM) : null;
        leftArm = tag.hasUUID(NBT_LEFT_ARM) ? tag.getUUID(NBT_LEFT_ARM) : null;
        if (tag.contains(NBT_HOME_DIM) && tag.contains(NBT_HOME_POS)) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(tag.getString(NBT_HOME_DIM)));
            homeBrain = GlobalPos.of(dim, BlockPos.of(tag.getLong(NBT_HOME_POS)));
        }
        if (getCustomName() == null) { setCustomName(Component.literal("Darius")); setCustomNameVisible(true); }
    }

}
