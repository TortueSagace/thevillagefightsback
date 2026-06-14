package com.example.villagefightsback;

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
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
        long _p = ModProfiler.now();

        GravemindData data = GravemindData.get(server);

        for (GlobalPos gp : data.blocks()) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            if (data.econ(gp).getLevel() < REQUIRED_LEVEL) continue; // only this village's defenders, at level 3+
            AABB box = new AABB(gp.pos()).inflate(RADIUS);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, UpgradeEvents::needsProcessing)) {
                if (e instanceof IronGolem golem) {
                    VillageEvents.convertToGigachad(golem); // a village golem becomes its own gigachad creature type
                } else {
                    UpgradeState.setUpgraded(e);
                    VillageEvents.applyUpgrade(e);
                }
            }
        }
        ModProfiler.add("upgrades", _p);
    }

    /** What the upgrade scan acts on: any plain village golem (-> converted to a gigachad creature type), or a
     *  not-yet-upgraded villager/guard (-> armed in place). */
    private static boolean needsProcessing(LivingEntity e) {
        if (!e.isAlive()) return false;
        if (e instanceof IronGolem golem) {
            return !golem.isPlayerCreated() && !(golem instanceof GigachadGolemEntity);
        }
        return !UpgradeState.isUpgraded(e) && isUpgradable(e);
    }

    private static boolean isUpgradable(LivingEntity e) {
        if (e instanceof GigachadGolemEntity) return false; // gigachad (+ vindictive) are already fully armed
        if (e instanceof IronGolem golem) return !golem.isPlayerCreated();
        if (e instanceof Villager) return true;
        return GUARD_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
    }
}
