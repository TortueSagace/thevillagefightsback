package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.List;

import com.example.villagefightsback.goal.AxeCombatGoal;
import com.example.villagefightsback.wall.WallBuild;
import com.example.villagefightsback.wall.WallPlan;
import com.example.villagefightsback.goal.GolemRocketGoal;
import com.example.villagefightsback.goal.StoneAxeDefenseGoal;
import com.example.villagefightsback.goal.TntFleeGoal;
import com.example.villagefightsback.goal.WallBuildGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Arms villagers, iron golems, and Guard Villagers guards, and wires the combat behaviour to them. As the
 * village evolves, defenders are upgraded (stage-3 villagers get the Alien Slasher, guards become emerald
 * knights, golems become gigachad golems) and the mod's ally-immunity keeps them from harming one another.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillageEvents {
    private VillageEvents() {}

    /** Persistent retaliation state: which player angered this villager, and until when. */
    public static final String NBT_AGGRO_UUID = "vgun_aggro_uuid";
    public static final String NBT_AGGRO_UNTIL = "vgun_aggro_until";
    public static final int AGGRO_TICKS = 100; // ~5 seconds

    private static final double FOLLOW_RANGE = 32.0;

    // Bonus damage injected via NBT so the guns actually hurt (minigun base dmg is low). The villager
    // revolver is handled separately by the nerf below, so it has no bonus constant.
    private static final float MINIGUN_BONUS = 9.0F;    // 4  -> 13 (minigun also fades over range)
    private static final float FLAME_BONUS = 3.0F;      // 3  -> 6 per flame (x3 flames)

    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");

    // Guards have a chance to drop our native Fire Grenade on death.
    private static final float GUARD_FIRE_GRENADE_DROP_CHANCE = 0.5F;

    /** Arm villagers / iron golems / guards whenever they join a level (including world reload). */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof PathfinderMob mob)) return;

        // Let village defenders (and all iron golems) PATH through fake-cobblestone "doors": flip our
        // custom passable type's malus to 0 for them only (others keep the -1 default = blocked). This
        // mirrors FakeCobblestoneBlock.isPhaser, which lets the same set physically walk through.
        if (mob instanceof IronGolem || mob instanceof Villager || isGuard(mob)) {
            mob.setPathfindingMalus(ModPathTypes.ALLY_PASSABLE, 0.0F);
        }

        // Emeraldgers are stage-4 virus spawns: combat-only villagers that can NEVER take a role (miner/
        // settler/builder) or profession. So skip every role goal for them -- they still get the stage-3
        // combat loadout below (they spawn pre-upgraded).
        boolean emeraldger = mob instanceof EmeraldgerEntity;

        // A villager far from every brainNode can found a new home with a Blue TNT (independent of the
        // combat upgrade). Idempotent: goals are re-added on every load; the goal settles at most once.
        if (!emeraldger && mob instanceof Villager settler
                && !hasGoal(settler, com.example.villagefightsback.goal.SettlerGoal.class)) {
            settler.goalSelector.addGoal(6, new com.example.villagefightsback.goal.SettlerGoal(settler));
        }

        // A wall builder must not ignore a point-blank threat: every villager carries this (dormant) goal;
        // it only fires when the villager is a builder and a hostile is within ~3 blocks, interrupting the
        // build (priority 2, above WallBuildGoal's 3) until the threat is dead or leaves. Idempotent.
        if (!emeraldger && mob instanceof Villager defender
                && !hasGoal(defender, com.example.villagefightsback.goal.BuilderDefenseGoal.class)) {
            defender.goalSelector.addGoal(2, new com.example.villagefightsback.goal.BuilderDefenseGoal(defender));
        }

        // Every villager carries the (dormant) miner goal; it only runs once the brain node tags it a miner
        // (level-2 infection). Idempotent. (Emeraldgers can't be miners, so they don't need it.)
        if (!emeraldger && mob instanceof Villager v) {
            com.example.villagefightsback.infection.InfectionManager.ensureMinerGoal(v);
        }

        // Defenders start NERFED and permanently upgrade to the full gun loadout once they have been near a
        // level-3 Village Brain Node (UpgradeEvents flips the flag). Until then: villagers fight only with a
        // stone axe (and only when badly hurt), guards and golems are vanilla.
        boolean upgraded = UpgradeState.isUpgraded(mob);
        if (mob instanceof IronGolem golem) {
            if (golem.isPlayerCreated()) return;       // village golems only
            // A gigachad is its own type: re-apply its kit on every load (goals aren't saved to NBT). Plain
            // village golems are converted into GigachadGolemEntity by UpgradeEvents near a lvl-3 brainNode.
            if (golem instanceof GigachadGolemEntity) armGolemFull(golem);
        } else if (mob instanceof Villager villager) {
            if (upgraded) armVillagerFull(villager);
            else armVillagerNerfed(villager);
        } else if (isGuard(mob)) {
            if (upgraded) armGuardFull(mob);            // else: normal guard
        }
    }

    /** Replace a plain village iron golem with a {@link GigachadGolemEntity} at the same spot; the new type
     *  arms itself in finalizeSpawn. Used when a village golem first reaches a level-3 brainNode. */
    public static void convertToGigachad(IronGolem old) {
        if (old instanceof GigachadGolemEntity) return;
        if (!(old.level() instanceof ServerLevel sl)) return;
        GigachadGolemEntity g = ModEntities.GIGACHAD_GOLEM.get().create(sl);
        if (g == null) return;
        g.moveTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), old.getXRot());
        g.yBodyRot = old.yBodyRot;
        g.yHeadRot = old.yHeadRot;
        g.finalizeSpawn(sl, sl.getCurrentDifficultyAt(old.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.CONVERSION, null, null);
        UpgradeState.setUpgraded(g);
        sl.addFreshEntity(g);
        old.discard();
    }

    /** Apply the full upgrade to an already-spawned defender (called when it first reaches a lvl-3 brainNode). */
    public static void applyUpgrade(LivingEntity e) {
        if (e instanceof Villager villager) {
            removeGoal(villager, StoneAxeDefenseGoal.class);
            armVillagerFull(villager); // replaces the stone axe with the revolver
        } else if (e instanceof IronGolem golem) {
            if (!golem.isPlayerCreated()) armGolemFull(golem);
        } else if (e instanceof PathfinderMob pm && isGuard(pm)) {
            armGuardFull(pm);
        }
    }

    /** NERFED villager: a stone axe (shown via the vanilla crossed-arms layer), defensive melee only. */
    private static void armVillagerNerfed(Villager villager) {
        if (hasGoal(villager, StoneAxeDefenseGoal.class)) return;
        if (!(villager.getMainHandItem().getItem() instanceof AxeItem)) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
            villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        villager.goalSelector.addGoal(2, new StoneAxeDefenseGoal(villager));
        // (WallBuildGoal is added separately by BrainNodeEvents.ensureWallBuildGoal when a brainNode is near.)
    }

    private static void armVillagerFull(Villager villager) {
        // Stage-3 villagers wield the Alien Slasher (a netherite axe) instead of the revolver. They hold it
        // for real -- the vanilla CrossedArmsItemLayer renders the held item -- and melee with it; the axe's
        // on-hit payload (Emerald Virus, Slowness IV, fire, huge knockback) is applied in EmeraldVirusEvents.
        // No guns, flamethrower, flare or molotov: the slasher is the villager's only stage-3 weapon.
        if (villager.getMainHandItem().getItem() != ModItems.ALIEN_SLASHER.get()) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.ALIEN_SLASHER.get()));
            villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        // Iron boots on upgraded villagers (functional armour; villagers have no armour render layer).
        if (!(villager.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof net.minecraft.world.item.ArmorItem)) {
            villager.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            villager.setDropChance(EquipmentSlot.FEET, 0.0F);
        }
        applyLeatherArmor(villager); // protection of a full leather set (idempotent; survives reload)
        if (hasGoal(villager, AxeCombatGoal.class)) return; // goals are re-added each load
        CombatUtil.extendFollowRange(villager, FOLLOW_RANGE);
        // Priority 0 (above combat): drop TNT and flee when standing on From Another World infection.
        villager.goalSelector.addGoal(0, new TntFleeGoal(villager));
        villager.goalSelector.addGoal(1, new AxeCombatGoal(villager)); // hunt and slash hostiles with the axe
        villager.goalSelector.addGoal(3, new WallBuildGoal(villager)); // build the brain node's wall when idle
    }

    // Fixed UUID so re-applying the armour buff on world reload is idempotent.
    private static final java.util.UUID VILLAGER_ARMOR_UUID =
            java.util.UUID.fromString("c0ffee00-0000-4000-8000-000000000002");
    private static final double LEATHER_ARMOR = 7.0; // full leather set: helmet 1 + chest 3 + legs 2 + boots 1

    /**
     * Grant an upgraded villager the protection of a full leather set (+7 armour) as a permanent attribute
     * modifier. (Leather's armour TOUGHNESS is 0, so the meaningful "leather equivalent" is its armour value.)
     * Idempotent: the fixed UUID means a reload won't stack a second copy.
     */
    private static void applyLeatherArmor(Villager villager) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance inst =
                villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (inst == null) return;
        net.minecraft.world.entity.ai.attributes.AttributeModifier mod =
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(VILLAGER_ARMOR_UUID,
                        "alien slasher leather armour", LEATHER_ARMOR,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        if (!inst.hasModifier(mod)) inst.addPermanentModifier(mod);
    }

    /** Add the wall-build goal to a villager if it lacks it (idempotent) -- retrofits already-loaded villagers. */
    public static void ensureWallBuildGoal(Villager villager) {
        if (!hasGoal(villager, WallBuildGoal.class)) {
            villager.goalSelector.addGoal(3, new WallBuildGoal(villager));
        }
    }

    /**
     * STAGE 3: guards drop JEG entirely and become emerald knights -- an enchanted mace + crossbow + emerald
     * armour (Fire/Blast Protection), driven by {@link com.example.villagefightsback.goal.StageThreeGuardGoal}.
     */
    private static void armGuardFull(PathfinderMob guard) {
        if (hasGoal(guard, com.example.villagefightsback.goal.StageThreeGuardGoal.class)) return; // re-added each load
        CombatUtil.extendFollowRange(guard, FOLLOW_RANGE);
        // The mace replaces any JEG gun (the equipment change makes onEquipmentChange strip JEG's gun goal);
        // the combat goal swaps in the crossbow / Alien Slasher as the situation demands.
        guard.setItemSlot(EquipmentSlot.MAINHAND, GuardWeapons.mace().copy());
        guard.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        GuardWeapons.equipEmeraldArmor(guard);
        guard.goalSelector.addGoal(1, new com.example.villagefightsback.goal.StageThreeGuardGoal(guard));
    }

    static void armGolemFull(IronGolem golem) {
        // Equip our NATIVE rocket launcher so the render layer can show it (and only on upgraded golems).
        // It is a plain item (not a JEG GunItem), so JEG never hijacks the golem; GolemRocketGoal fires our
        // own RocketEntity. The held item also gates the decorative HeldWeaponLayer (it shows only this item).
        if (golem.getMainHandItem().getItem() != ModItems.ROCKET_LAUNCHER.get()) {
            golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.ROCKET_LAUNCHER.get()));
            golem.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        applyGolemArmor(golem);  // gigachad: protection of a full iron set (idempotent; survives reload)
        applyGolemHealth(golem); // gigachad: 200 HP (base 100 + 100)
        // No artificial name tag: a gigachad/vindictive gigachad is its own creature TYPE, so its name comes
        // from the entity type (lang) and shows on hover/death like any mob -- no floating custom name.
        if (hasGoal(golem, GolemRocketGoal.class)) return;
        CombatUtil.extendFollowRange(golem, FOLLOW_RANGE);
        golem.goalSelector.addGoal(0, new GolemRocketGoal(golem));
    }

    // Fixed UUID so re-applying the gigachad armour buff on world reload is idempotent.
    private static final java.util.UUID GOLEM_ARMOR_UUID =
            java.util.UUID.fromString("c0ffee00-0000-4000-8000-000000000003");
    private static final double IRON_ARMOR = 15.0; // full iron set: helmet 2 + chest 6 + legs 5 + boots 2

    /** Give a stage-3 ("gigachad") iron golem the armour of a full iron set (+15 armour) as a permanent
     *  attribute modifier. Idempotent via the fixed UUID, so a reload never stacks a second copy. */
    private static void applyGolemArmor(IronGolem golem) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance inst =
                golem.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (inst == null) return;
        net.minecraft.world.entity.ai.attributes.AttributeModifier mod =
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(GOLEM_ARMOR_UUID,
                        "gigachad iron armour", IRON_ARMOR,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        if (!inst.hasModifier(mod)) inst.addPermanentModifier(mod);
    }

    private static final java.util.UUID GOLEM_HEALTH_UUID =
            java.util.UUID.fromString("c0ffee00-0000-4000-8000-000000000004");
    private static final double GOLEM_HEALTH_BONUS = 100.0; // base iron golem 100 -> gigachad 200

    /** Give a stage-3 ("gigachad") iron golem +100 max health (-> 200 total), as a permanent, idempotent
     *  attribute modifier. Heals to full only the first time it's applied (so reloads don't re-heal). */
    private static void applyGolemHealth(IronGolem golem) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance inst =
                golem.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (inst == null) return;
        net.minecraft.world.entity.ai.attributes.AttributeModifier mod =
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(GOLEM_HEALTH_UUID,
                        "gigachad health", GOLEM_HEALTH_BONUS,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        if (!inst.hasModifier(mod)) {
            inst.addPermanentModifier(mod);
            golem.setHealth(golem.getMaxHealth()); // top up to the new 200 on first upgrade
        }
    }

    /** Remove every goal of {@code goalClass} from the mob's goal selector (used when upgrading). */
    private static void removeGoal(Mob mob, Class<?> goalClass) {
        List<Goal> remove = new ArrayList<>();
        for (WrappedGoal w : mob.goalSelector.getAvailableGoals()) {
            if (goalClass.isInstance(w.getGoal())) remove.add(w.getGoal());
        }
        for (Goal g : remove) mob.goalSelector.removeGoal(g);
    }

    /** Guards have a chance to drop our native Fire Grenade when they die. */
    @SubscribeEvent
    public static void onGuardDeathDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || !isGuard(dead)) return;
        if (dead.getRandom().nextFloat() >= GUARD_FIRE_GRENADE_DROP_CHANCE) return;
        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(dead.level(),
                dead.getX(), dead.getY(), dead.getZ(), new ItemStack(ModItems.FIRE_GRENADE.get())));
    }

    /** Gigachad golems + vindictive gigachads drop their rocket launcher + a few rockets when they die. */
    @SubscribeEvent
    public static void onGigachadDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || !(dead instanceof GigachadGolemEntity)) return; // covers the vindictive subclass
        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(dead.level(),
                dead.getX(), dead.getY(), dead.getZ(), new ItemStack(ModItems.ROCKET_LAUNCHER.get())));
        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(dead.level(),
                dead.getX(), dead.getY(), dead.getZ(), new ItemStack(ModItems.ROCKET.get(), 1 + dead.getRandom().nextInt(3))));
    }

    /**
     * Hard ally immunity: villagers, iron golems, and guards must NEVER target one another. Their
     * native AI (e.g. HurtByTargetGoal after rocket splash) tries to; we cancel any such target change.
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity self = event.getEntity();
        LivingEntity newTarget = event.getNewTarget();
        if (isAlly(self) && newTarget != null && isAlly(newTarget)) {
            event.setCanceled(true);            // allies never fight each other
            return;
        }
        // Villagers/guards must not leave the walled zone to chase a mob: if WE stand inside our village wall
        // but the would-be target is on the far side of it, refuse the target (we engage it only once it
        // breaks IN). Players are exempt -- anti-grief retaliation can still chase the griefer.
        if (newTarget != null && (self instanceof Villager || isGuard(self)) && beyondHomeWall(self, newTarget)) {
            event.setCanceled(true);
        }
    }

    /**
     * True if {@code self} stands inside its village wall but {@code target} is OUTSIDE that wall -- i.e.
     * engaging it would mean leaving the protected zone. Players are never "beyond the wall" (so anti-grief
     * still works). Used by the combat goals and {@link #onChangeTarget} to keep defenders home.
     */
    public static boolean beyondHomeWall(LivingEntity self, LivingEntity target) {
        if (target == null || target instanceof Player) return false;
        if (!(self.level() instanceof ServerLevel sl)) return false;
        WallPlan wall = WallBuild.enclosingPlan(sl, self.blockPosition());
        return wall != null && !wall.contains(target.blockPosition());
    }

    private static final double GUARD_KILL_EMERALD_RANGE = 100.0; // brainNode must be within this of the kill

    /** Every hostile mob a guard kills feeds one emerald to the nearest in-range Village Brain Node. */
    @SubscribeEvent
    public static void onMobKilledByGuard(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Enemy)) return;                 // only hostile mobs count
        if (!(event.getEntity().level() instanceof ServerLevel sl)) return;
        Entity killer = event.getSource().getEntity();
        if (killer == null || !isGuard(killer)) return;                    // only kills credited to a guard
        BrainNodeData data = BrainNodeData.get(sl.getServer());
        BlockPos at = event.getEntity().blockPosition();
        GlobalPos best = null;
        double bestD = GUARD_KILL_EMERALD_RANGE * GUARD_KILL_EMERALD_RANGE;
        for (GlobalPos gp : data.blocks()) {
            if (!gp.dimension().equals(sl.dimension())) continue;
            double d = gp.pos().distSqr(at);
            if (d <= bestD) { bestD = d; best = gp; }
        }
        if (best != null) data.econ(best).addEmeralds(1); // addEmeralds() self-dirties the saved data
    }

    /** Immunity effect: the holder takes NO damage while it is active -- except sources that bypass
     *  invulnerability (the void, /kill), so an immune entity can never get permanently stuck. */
    @SubscribeEvent
    public static void onImmunityAttack(LivingAttackEvent event) {
        if (event.getEntity().hasEffect(ModEffects.IMMUNITY.get())
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    /** Immunity also blocks every HARMFUL status effect (poison, wither, slowness, the emerald virus, ...)
     *  from ever landing on the holder. Beneficial effects (incl. Immunity itself) are unaffected. */
    @SubscribeEvent
    public static void onImmunityEffect(net.minecraftforge.event.entity.living.MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect().getCategory()
                    == net.minecraft.world.effect.MobEffectCategory.HARMFUL
                && event.getEntity().hasEffect(ModEffects.IMMUNITY.get())) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    /** Player hits a villager -> the villager turns hostile toward that player for a few moments. */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        // No friendly fire among defenders (e.g. rocket splash hitting an allied golem/villager).
        if (isAlly(event.getEntity()) && event.getSource().getEntity() != null
                && isAlly(event.getSource().getEntity())) {
            event.setCanceled(true);
            return;
        }

        // A player hurting a villager OR a wall defender makes that defender retaliate against them.
        if (!(event.getEntity() instanceof Villager) && !(event.getEntity() instanceof WallDefenderEntity)) return;
        DamageSource src = event.getSource();
        if (src.getEntity() instanceof Player player) {
            CompoundTag data = event.getEntity().getPersistentData();
            data.putUUID(NBT_AGGRO_UUID, player.getUUID());
            data.putLong(NBT_AGGRO_UNTIL, event.getEntity().level().getGameTime() + AGGRO_TICKS);
        }
    }

    /** The current valid retaliation target for a mob, or null if none / expired / gone. */
    public static Player getAggroPlayer(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (!data.hasUUID(NBT_AGGRO_UUID)) return null;
        if (mob.level().getGameTime() > data.getLong(NBT_AGGRO_UNTIL)) return null;
        Player p = mob.level().getPlayerByUUID(data.getUUID(NBT_AGGRO_UUID));
        return (p != null && p.isAlive()) ? p : null;
    }

    /**
     * Make every defender (villager / guard / iron golem) within {@code radius} of {@code center} turn on
     * {@code attacker} for {@code ticks}. Villagers/guards use the aggro NBT (their combat goal then shoots
     * the player); golems get the target set directly (their melee goal punches it). Used when the brain node
     * is mined. One bounded entity query -- only runs on the (throttled) attack, never per tick.
     */
    public static void aggroDefendersAgainst(ServerLevel level, BlockPos center, double radius,
                                             Player attacker, int ticks) {
        long until = level.getGameTime() + ticks;
        AABB box = new AABB(center).inflate(radius);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, e -> e.isAlive() && isAlly(e))) {
            if (m instanceof IronGolem) {
                m.setTarget(attacker); // golems melee the attacker (the rocket goal ignores non-Enemy targets)
            } else { // villager or guard
                CompoundTag data = m.getPersistentData();
                data.putUUID(NBT_AGGRO_UUID, attacker.getUUID());
                data.putLong(NBT_AGGRO_UNTIL, until);
            }
        }
    }

    private static boolean isGuard(Entity e) {
        return GUARD_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
    }

    /** Defenders that must never fight each other: villagers, iron golems, guards, and wall defenders. */
    private static boolean isAlly(Entity e) {
        return e instanceof Villager || e instanceof IronGolem || e instanceof WallDefenderEntity || isGuard(e);
    }

    private static boolean hasGoal(Mob mob, Class<?> goalClass) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (goalClass.isInstance(wrapped.getGoal())) return true;
        }
        return false;
    }
}
