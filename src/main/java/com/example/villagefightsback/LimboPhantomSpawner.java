package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Naturally populates the Limbo with Limbo Phantoms: every so often, each player in the Limbo has a chance to
 * have one or two phantoms spawn in the air around them, capped per player so it never swarms. These spawns
 * are NOT persistence-required, so they despawn once the player moves away (egg-hatched phantoms, which ARE
 * persistent, stay in their cubes).
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LimboPhantomSpawner {
    private LimboPhantomSpawner() {}

    private static final int INTERVAL = 200;     // try ~every 10s
    private static final double SCAN = 64.0;     // count phantoms this close to the player
    private static final int MAX_NEAR = 5;       // cap per player
    private static final float CHANCE = 0.6F;    // per interval

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % INTERVAL != 0) return;
        ServerLevel limbo = server.getLevel(LimboDimension.LIMBO);
        if (limbo == null || limbo.players().isEmpty()) return;

        long _p = ModProfiler.now();
        RandomSource r = limbo.random;
        for (ServerPlayer player : limbo.players()) {
            if (r.nextFloat() > CHANCE) continue;
            AABB near = player.getBoundingBox().inflate(SCAN);
            if (limbo.getEntitiesOfClass(LimboPhantomEntity.class, near).size() >= MAX_NEAR) continue;
            int toSpawn = 1 + r.nextInt(2);
            for (int i = 0; i < toSpawn; i++) trySpawn(limbo, player, r);
        }
        ModProfiler.add("limbo.phantomspawn", _p);
    }

    private static void trySpawn(ServerLevel limbo, ServerPlayer player, RandomSource r) {
        double ang = r.nextDouble() * Math.PI * 2.0;
        double dist = 20.0 + r.nextDouble() * 20.0;
        int x = (int) (player.getX() + Math.cos(ang) * dist);
        int z = (int) (player.getZ() + Math.sin(ang) * dist);
        int y = (int) (player.getY() + 6.0 + r.nextInt(18));
        BlockPos pos = new BlockPos(x, y, z);
        if (!limbo.isLoaded(pos)) return;
        if (!limbo.getBlockState(pos).isAir() || !limbo.getBlockState(pos.above()).isAir()) return; // need open air

        LimboPhantomEntity ph = ModEntities.LIMBO_PHANTOM.get().create(limbo);
        if (ph == null) return;
        ph.moveTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360.0F, 0.0F);
        ph.finalizeSpawn(limbo, limbo.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
        limbo.addFreshEntity(ph);
    }
}
