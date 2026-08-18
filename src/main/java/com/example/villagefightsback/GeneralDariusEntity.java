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
    private static final String NBT_HOME_DIM = "darius_home_dim";
    private static final String NBT_HOME_POS = "darius_home_pos";

    /** The brain node's influence radius -- the zone Darius hunts within (set from Config.apply(); base 100). */
    public static double BRAIN_RADIUS = 100.0;
    /** Targets weaker than this max-HP are ignored once the first lock dies (a "big threat" floor). */
    private static final double NEXT_TARGET_MIN_MAXHP = 50.0;
    /** A player is "dangerous" if they have recently killed several villagers/guards (see BrainReflexes). */
    public static final int DANGEROUS_PLAYER_KILLS = 3;

    private static final int BEAM_COOLDOWN = 600; // 30s, only fires below 50% HP

    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.literal("General Darius"),
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);

    /** Hired-guard squad (UUIDs); transient -- released on death/despawn. */
    private final Set<UUID> squad = new LinkedHashSet<>();

    @Nullable private GlobalPos homeBrain;
    private int beamCooldown;
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
        setItemSlot(EquipmentSlot.MAINHAND, GuardWeapons.alienSmasher().copy());
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        return r;
    }

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
        net.minecraft.world.item.ItemStack smasher = getMainHandItem();
        if (!smasher.isEmpty()) {
            spawnAtLocation(smasher.copy());
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
        net.minecraft.world.entity.EntityType<?> guardType = com.example.villagefightsback.compat.MCACompat
                .entityTypeOrNull(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard"));
        if (guardType == null) return; // soft dep absent
        for (int i = 0; i < count; i++) {
            net.minecraft.world.entity.Entity e = guardType.create(level);
            if (!(e instanceof net.minecraft.world.entity.Mob m)) { if (e != null) e.discard(); continue; }
            double ang = level.random.nextDouble() * Math.PI * 2.0;
            double r = 1.5 + level.random.nextDouble() * 2.0;
            m.moveTo(getX() + Math.cos(ang) * r, getY(), getZ() + Math.sin(ang) * r,
                    level.random.nextFloat() * 360.0F, 0.0F);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()),
                    net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null);
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
        LimboGear.breakSoftBlocksOnContact(this); // breaks soft blocks (incl. cobblestone) on contact

        if (beamCooldown > 0) beamCooldown--;

        // Below 50% HP: spontaneously gain Regeneration II (30s) AND Darius' Special Sauce (10s). Granted once on
        // dropping below half, then re-armed if he heals back above half (so a second collapse triggers again).
        if (getHealth() < getMaxHealth() * 0.5F) {
            if (!lowHpBuffed) {
                addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.REGENERATION, 600, 1, false, true, true));
                addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        ModEffects.DARIUS_SPECIAL_EFFECT, 200, 0, false, true, true));
                lowHpBuffed = true;
            }
        } else {
            lowHpBuffed = false;
        }

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
        if (e == this || !e.isAlive() || Factions.isInfected(e) || Factions.isEmerald(e)) return false;
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
        if (homeBrain != null) {
            tag.putString(NBT_HOME_DIM, homeBrain.dimension().location().toString());
            tag.putLong(NBT_HOME_POS, homeBrain.pos().asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NBT_HOME_DIM) && tag.contains(NBT_HOME_POS)) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(tag.getString(NBT_HOME_DIM)));
            homeBrain = GlobalPos.of(dim, BlockPos.of(tag.getLong(NBT_HOME_POS)));
        }
        if (getCustomName() == null) { setCustomName(Component.literal("Darius")); setCustomNameVisible(true); }
    }

}
