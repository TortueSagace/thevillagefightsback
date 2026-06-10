package com.example.villagergunners;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Drives the permanent upgrade: once the (global) Village Gravemind reaches level {@value #REQUIRED_LEVEL},
 * any defender within {@value #RADIUS} blocks of a gravemind is upgraded to the full gun loadout, instantly
 * and forever (the flag never clears). Cheap: gated by the level check, throttled, only loaded graveminds,
 * and already-upgraded defenders are filtered out of the query.
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UpgradeEvents {
    private UpgradeEvents() {}

    private static final int REQUIRED_LEVEL = 3;
    private static final double RADIUS = 100.0;
    private static final int INTERVAL = 100; // check every ~5s (upgrade is permanent; no need to be fast)
    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % INTERVAL != 0) return;

        GravemindData data = GravemindData.get(server);
        if (data.getLevel() < REQUIRED_LEVEL) return; // cheap gate: nothing upgrades below level 3

        for (GlobalPos gp : data.blocks()) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            AABB box = new AABB(gp.pos()).inflate(RADIUS);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                    x -> x.isAlive() && !UpgradeState.isUpgraded(x) && isUpgradable(x))) {
                UpgradeState.setUpgraded(e);
                GunnerEvents.applyUpgrade(e);
            }
        }
    }

    private static boolean isUpgradable(LivingEntity e) {
        if (e instanceof IronGolem golem) return !golem.isPlayerCreated();
        if (e instanceof Villager) return true;
        return GUARD_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
    }
}
