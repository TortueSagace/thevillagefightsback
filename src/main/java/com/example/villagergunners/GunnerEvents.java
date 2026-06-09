package com.example.villagergunners;

import com.example.villagergunners.goal.GolemRocketGoal;
import com.example.villagergunners.goal.VillagerGunCombatGoal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Arms villagers / iron golems and wires the combat behaviour to them. All handlers are server-side.
 *
 * Note: we deliberately do NOT put a JEG gun in a mob's hand -- JEG's GunnerMobSpawner attaches its
 * own gunner AI to any mob holding a GunItem (causing the ammo bubble, point-blank strafing, and
 * firing that ignores our ally rule). Our goals fire via their own cached ItemStacks. The guns are
 * shown purely client-side by a render layer (see {@code VillagerGunnersClient}).
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GunnerEvents {
    private GunnerEvents() {}

    /** Persistent retaliation state: which player angered this villager, and until when. */
    public static final String NBT_AGGRO_UUID = "vgun_aggro_uuid";
    public static final String NBT_AGGRO_UNTIL = "vgun_aggro_until";
    /** How long (ticks) a player-struck villager stays hostile to that player. ~5 seconds. */
    public static final int AGGRO_TICKS = 100;

    /** Detection radius (blocks). Must be >= the 30-block engagement radius used by the goals. */
    private static final double FOLLOW_RANGE = 32.0;

    /** Arm villagers / iron golems whenever they join a level (including world reload). */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof PathfinderMob mob)) return;

        if (mob instanceof IronGolem golem) {
            if (golem.isPlayerCreated()) return;           // village golems only, not player-built ones
            armGolem(golem);
        } else if (mob instanceof Villager villager) {
            armVillager(villager);                          // EVERY villager, however it spawned
        }
    }

    private static void armVillager(Villager villager) {
        // Iron boots on every villager (functional armour; survives saves like any worn item).
        if (!(villager.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof net.minecraft.world.item.ArmorItem)) {
            villager.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            villager.setDropChance(EquipmentSlot.FEET, 0.0F);
        }
        if (hasGoal(villager, VillagerGunCombatGoal.class)) return; // goals are re-added each load
        GunUtil.extendFollowRange(villager, FOLLOW_RANGE);
        villager.goalSelector.addGoal(1, new VillagerGunCombatGoal(villager));
    }

    private static void armGolem(IronGolem golem) {
        if (hasGoal(golem, GolemRocketGoal.class)) return;
        GunUtil.extendFollowRange(golem, FOLLOW_RANGE);
        // Priority 0 so rocketing-at-range takes precedence over the golem's melee goal (priority 1).
        golem.goalSelector.addGoal(0, new GolemRocketGoal(golem));
    }

    /** Player hits a villager -> the villager turns hostile toward that player for a few moments. */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Villager villager)) return;
        DamageSource src = event.getSource();
        if (src.getEntity() instanceof Player player) {
            CompoundTag data = villager.getPersistentData();
            data.putUUID(NBT_AGGRO_UUID, player.getUUID());
            data.putLong(NBT_AGGRO_UNTIL, villager.level().getGameTime() + AGGRO_TICKS);
        }
    }

    /** The current valid retaliation target for a villager, or null if none / expired / gone. */
    public static Player getAggroPlayer(Villager villager) {
        CompoundTag data = villager.getPersistentData();
        if (!data.hasUUID(NBT_AGGRO_UUID)) return null;
        if (villager.level().getGameTime() > data.getLong(NBT_AGGRO_UNTIL)) return null;
        Player p = villager.level().getPlayerByUUID(data.getUUID(NBT_AGGRO_UUID));
        return (p != null && p.isAlive()) ? p : null;
    }

    private static boolean hasGoal(Mob mob, Class<?> goalClass) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (goalClass.isInstance(wrapped.getGoal())) return true;
        }
        return false;
    }
}
