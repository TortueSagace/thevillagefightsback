package com.example.villagergunners;

import java.util.ArrayList;
import java.util.List;

import com.example.villagergunners.goal.FlareRallyGoal;
import com.example.villagergunners.goal.GolemRocketGoal;
import com.example.villagergunners.goal.GunCombatGoal;
import com.example.villagergunners.goal.StoneAxeDefenseGoal;
import com.example.villagergunners.goal.TntFleeGoal;
import com.example.villagergunners.goal.WallBuildGoal;

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
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import ttv.migami.jeg.item.GunItem;

/**
 * Arms villagers, iron golems, and Guard Villagers guards, and wires the combat behaviour to them.
 *
 * Villagers/golems do NOT hold a JEG gun (that makes JEG hijack their AI); their guns are decorative
 * client-side render layers. Guards DO hold the gun (their humanoid renderer shows it natively) -- so
 * for guards we equip it and then strip JEG's auto-added gunner goal so only our AI drives them.
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GunnerEvents {
    private GunnerEvents() {}

    /** Persistent retaliation state: which player angered this villager, and until when. */
    public static final String NBT_AGGRO_UUID = "vgun_aggro_uuid";
    public static final String NBT_AGGRO_UNTIL = "vgun_aggro_until";
    public static final int AGGRO_TICKS = 100; // ~5 seconds

    private static final double FOLLOW_RANGE = 32.0;

    // Bonus damage injected via NBT so the guns actually hurt (minigun base dmg is low). The villager
    // revolver is handled separately by the nerf below, so it has no bonus constant.
    private static final float MINIGUN_BONUS = 9.0F;    // 4  -> 13 (minigun also fades over range)
    private static final float FLAME_BONUS = 3.0F;      // 3  -> 6 per flame (x3 flames)

    // Villager gun NERF (villagers only; guards/golems keep the values above): fire once per second and
    // each projectile does a flat 8 damage, regardless of the guns' data-driven rate/base damage.
    private static final int VILLAGER_FIRE_RATE = 20;   // ticks between shots = 1/sec
    private static final float VILLAGER_GUN_DAMAGE = 8.0F;

    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");

    // JEG's "fire grenade" -- the molotov cocktail; guards have a chance to drop one on death.
    private static final ResourceLocation MOLOTOV = new ResourceLocation("jeg", "molotov_cocktail");
    private static final float GUARD_MOLOTOV_DROP_CHANCE = 0.5F;

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

        // A villager far from every gravemind can found a new home with a Blue TNT (independent of the
        // combat upgrade). Idempotent: goals are re-added on every load; the goal settles at most once.
        if (mob instanceof Villager settler && !hasGoal(settler, com.example.villagergunners.goal.SettlerGoal.class)) {
            settler.goalSelector.addGoal(6, new com.example.villagergunners.goal.SettlerGoal(settler));
        }

        // Defenders start NERFED and permanently upgrade to the full gun loadout once they have been near a
        // level-3 Village Gravemind (UpgradeEvents flips the flag). Until then: villagers fight only with a
        // stone axe (and only when badly hurt), guards and golems are vanilla.
        boolean upgraded = UpgradeState.isUpgraded(mob);
        if (mob instanceof IronGolem golem) {
            if (golem.isPlayerCreated()) return;       // village golems only
            if (upgraded) armGolemFull(golem);          // else: normal iron golem
        } else if (mob instanceof Villager villager) {
            if (upgraded) armVillagerFull(villager);
            else armVillagerNerfed(villager);
        } else if (isGuard(mob)) {
            if (upgraded) armGuardFull(mob);            // else: normal guard
        }
    }

    /** Apply the full upgrade to an already-spawned defender (called when it first reaches a lvl-3 gravemind). */
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
        // (WallBuildGoal is added separately by GravemindEvents.ensureWallBuildGoal when a gravemind is near.)
    }

    private static void armVillagerFull(Villager villager) {
        // Hold the revolver so the vanilla CrossedArmsItemLayer renders it -- a villager only shows a gun
        // when it actually HOLDS one, which happens solely here on upgrade (nerfed villagers hold a stone
        // axe). Firing still uses the goal's cached stacks; JEG's auto gunner-goal is stripped on equip.
        if (!(villager.getMainHandItem().getItem() instanceof GunItem)) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, GunUtil.makeGun(GunUtil.REVOLVER));
            villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        // Iron boots on upgraded villagers (functional armour; villagers have no armour render layer).
        if (!(villager.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof net.minecraft.world.item.ArmorItem)) {
            villager.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            villager.setDropChance(EquipmentSlot.FEET, 0.0F);
        }
        if (hasGoal(villager, GunCombatGoal.class)) return; // goals are re-added each load
        GunUtil.extendFollowRange(villager, FOLLOW_RANGE);
        // Priority 0 (above combat): drop TNT and flee when standing on From Another World infection.
        villager.goalSelector.addGoal(0, new TntFleeGoal(villager));
        villager.goalSelector.addGoal(1, new GunCombatGoal(
                villager, GunUtil.REVOLVER, GunUtil.FLAMETHROWER, VILLAGER_FIRE_RATE, VILLAGER_GUN_DAMAGE));
        villager.goalSelector.addGoal(2, new FlareRallyGoal(villager)); // run to reinforcement flares
        villager.goalSelector.addGoal(3, new WallBuildGoal(villager)); // build the gravemind's wall when idle
    }

    /** Add the wall-build goal to a villager if it lacks it (idempotent) -- retrofits already-loaded villagers. */
    public static void ensureWallBuildGoal(Villager villager) {
        if (!hasGoal(villager, WallBuildGoal.class)) {
            villager.goalSelector.addGoal(3, new WallBuildGoal(villager));
        }
    }

    private static void armGuardFull(PathfinderMob guard) {
        // Guards are humanoid and render their main-hand item, so equip the minigun for the visual.
        // Firing still uses the goal's own cached stacks. JEG's auto gunner goal is stripped below.
        if (!(guard.getMainHandItem().getItem() instanceof GunItem)) {
            guard.setItemSlot(EquipmentSlot.MAINHAND, GunUtil.makeGun(GunUtil.MINIGUN));
            guard.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        if (hasGoal(guard, GunCombatGoal.class)) return;
        GunUtil.extendFollowRange(guard, FOLLOW_RANGE);
        guard.goalSelector.addGoal(1, new GunCombatGoal(
                guard, GunUtil.MINIGUN, MINIGUN_BONUS, GunUtil.FLAMETHROWER, FLAME_BONUS));
        guard.goalSelector.addGoal(2, new FlareRallyGoal(guard));
    }

    private static void armGolemFull(IronGolem golem) {
        // Equip the rocket launcher so the render layer can show it (and only on upgraded golems). JEG's
        // auto gunner goal is stripped in onEquipmentChange; our GolemRocketGoal fires from a cached stack.
        if (!(golem.getMainHandItem().getItem() instanceof GunItem)) {
            golem.setItemSlot(EquipmentSlot.MAINHAND, GunUtil.makeGun(GunUtil.ROCKET_LAUNCHER));
            golem.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        if (hasGoal(golem, GolemRocketGoal.class)) return;
        GunUtil.extendFollowRange(golem, FOLLOW_RANGE);
        golem.goalSelector.addGoal(0, new GolemRocketGoal(golem));
        golem.goalSelector.addGoal(2, new FlareRallyGoal(golem));
    }

    /** Remove every goal of {@code goalClass} from the mob's goal selector (used when upgrading). */
    private static void removeGoal(Mob mob, Class<?> goalClass) {
        List<Goal> remove = new ArrayList<>();
        for (WrappedGoal w : mob.goalSelector.getAvailableGoals()) {
            if (goalClass.isInstance(w.getGoal())) remove.add(w.getGoal());
        }
        for (Goal g : remove) mob.goalSelector.removeGoal(g);
    }

    /**
     * After a guard's hand becomes a JEG gun, JEG's GunnerMobSpawner adds its own GunAttackGoal.
     * We run at LOWEST priority (after JEG) and strip it so only our GunCombatGoal drives the guard.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof PathfinderMob mob)) return;
        if (!isAlly(mob)) return; // any armed defender (guard/golem) that now holds a gun
        List<Goal> remove = new ArrayList<>();
        for (WrappedGoal w : mob.goalSelector.getAvailableGoals()) {
            if (w.getGoal() instanceof ttv.migami.jeg.entity.ai.GunAttackGoal) {
                remove.add(w.getGoal());
            }
        }
        for (Goal g : remove) {
            mob.goalSelector.removeGoal(g);
        }
    }

    /**
     * Hard ally immunity: villagers, iron golems, and guards must NEVER target one another. Their
     * native AI (e.g. HurtByTargetGoal after rocket splash) tries to; we cancel any such target change.
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (isAlly(event.getEntity()) && event.getNewTarget() != null && isAlly(event.getNewTarget())) {
            event.setCanceled(true);
        }
    }

    /** A killed guard has a 50% chance to drop a fire grenade (JEG molotov cocktail). */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!isGuard(entity)) return;
        if (!UpgradeState.isUpgraded(entity)) return; // molotov drop is a level-3 unlock
        if (entity.getRandom().nextFloat() >= GUARD_MOLOTOV_DROP_CHANCE) return;
        Item molotov = ForgeRegistries.ITEMS.getValue(MOLOTOV);
        if (molotov == null) return; // JEG not present / item missing -> no drop
        event.getDrops().add(new ItemEntity(entity.level(),
                entity.getX(), entity.getY(), entity.getZ(), new ItemStack(molotov)));
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

        if (!(event.getEntity() instanceof Villager villager)) return;
        DamageSource src = event.getSource();
        if (src.getEntity() instanceof Player player) {
            CompoundTag data = villager.getPersistentData();
            data.putUUID(NBT_AGGRO_UUID, player.getUUID());
            data.putLong(NBT_AGGRO_UNTIL, villager.level().getGameTime() + AGGRO_TICKS);
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
     * the player); golems get the target set directly (their melee goal punches it). Used when the gravemind
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

    /** Defenders that must never fight each other: villagers, iron golems, and guards. */
    private static boolean isAlly(Entity e) {
        return e instanceof Villager || e instanceof IronGolem || isGuard(e);
    }

    private static boolean hasGoal(Mob mob, Class<?> goalClass) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (goalClass.isInstance(wrapped.getGoal())) return true;
        }
        return false;
    }
}
