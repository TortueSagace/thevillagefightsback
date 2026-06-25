package com.example.villagefightsback;

import com.example.villagefightsback.goal.SageVolatileThrowGoal;

import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Attaches the {@link SageVolatileThrowGoal} to every Limbo Sage villager at runtime (goals aren't saved to NBT,
 * so this re-adds it on every join / world reload). Idempotent: the goal is only added if not already present
 * (mirroring {@code VillageEvents.hasGoal}). The throw goal also self-scans for a target, so the sage acquires
 * one without a separate target goal -- and it never targets village allies (see the goal's filter).
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LimboSageEvents {
    private LimboSageEvents() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (villager.getVillagerData().getProfession() != ModVillagers.LIMBO_SAGE.get()) return;
        if (hasGoal(villager)) return;
        // Priority 1: above the villager's idle/wander goals so the sage actually throws when a big threat appears.
        villager.goalSelector.addGoal(1, new SageVolatileThrowGoal(villager));
    }

    private static boolean hasGoal(Villager villager) {
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof SageVolatileThrowGoal) return true;
        }
        return false;
    }
}
