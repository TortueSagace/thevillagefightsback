package com.example.villagefightsback;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps the Limbo a Phantom-only dimension: cancels every WILD (natural / chunk-gen / structure) mob spawn in
 * the Limbo except our own Limbo Phantoms. This blocks vanilla mobs (e.g. the basalt-delta magma cubes that
 * still spawn in chunks generated before the custom biome was set) regardless of chunk age. Intentional spawns
 * (spawn eggs, commands, our phantom spawner / egg hatches) are left alone.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboMobControl {
    private LimboMobControl() {}

    @SubscribeEvent
    public static void onFinalizeSpawn(LivingSpawnEvent.SpecialSpawn event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!LimboDimension.LIMBO.equals(level.dimension())) return;
        if (event.getEntity() instanceof LimboPhantomEntity) return; // our natives are welcome
        MobSpawnType reason = event.getSpawnReason();
        if (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION
                || reason == MobSpawnType.STRUCTURE || reason == MobSpawnType.PATROL
                || reason == MobSpawnType.JOCKEY || reason == MobSpawnType.REINFORCEMENT) {
            event.setCanceled(true);
        }
    }
}
