package com.example.villagergunners;

import java.util.ArrayList;
import java.util.List;

import com.example.villagergunners.goal.GolemRocketGoal;
import com.example.villagergunners.goal.GunCombatGoal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
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

    // Bonus damage injected via NBT so the guns actually hurt (revolver/minigun base dmg are low).
    private static final float REVOLVER_BONUS = 10.0F;  // 5  -> 15
    private static final float MINIGUN_BONUS = 9.0F;    // 4  -> 13 (minigun also fades over range)
    private static final float FLAME_BONUS = 3.0F;      // 3  -> 6 per flame (x3 flames)

    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");

    /** Arm villagers / iron golems / guards whenever they join a level (including world reload). */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof PathfinderMob mob)) return;

        if (mob instanceof IronGolem golem) {
            if (golem.isPlayerCreated()) return;       // village golems only
            armGolem(golem);
        } else if (mob instanceof Villager villager) {
            armVillager(villager);                      // every villager
        } else if (isGuard(mob)) {
            armGuard(mob);                              // Guard Villagers guards (soft dependency)
        }
    }

    private static void armVillager(Villager villager) {
        // Iron boots on every villager (functional armour; rendered? no -- villagers have no armour layer).
        if (!(villager.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof net.minecraft.world.item.ArmorItem)) {
            villager.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            villager.setDropChance(EquipmentSlot.FEET, 0.0F);
        }
        if (hasGoal(villager, GunCombatGoal.class)) return; // goals are re-added each load
        GunUtil.extendFollowRange(villager, FOLLOW_RANGE);
        villager.goalSelector.addGoal(1, new GunCombatGoal(
                villager, GunUtil.REVOLVER, REVOLVER_BONUS, GunUtil.FLAMETHROWER, FLAME_BONUS));
    }

    private static void armGuard(PathfinderMob guard) {
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
    }

    private static void armGolem(IronGolem golem) {
        if (hasGoal(golem, GolemRocketGoal.class)) return;
        GunUtil.extendFollowRange(golem, FOLLOW_RANGE);
        golem.goalSelector.addGoal(0, new GolemRocketGoal(golem));
    }

    /**
     * After a guard's hand becomes a JEG gun, JEG's GunnerMobSpawner adds its own GunAttackGoal.
     * We run at LOWEST priority (after JEG) and strip it so only our GunCombatGoal drives the guard.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof PathfinderMob mob)) return;
        if (!isGuard(mob)) return;
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
