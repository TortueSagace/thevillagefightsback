package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Optionally moves the overworld spawn point onto the nearest village when the server starts, so players
 * spawn in a village instead of a random place. OFF by default -- enable it via the
 * {@code spawnAtNearestVillage} config option. If no village is within range we leave the vanilla spawn alone.
 *
 * Setting the world spawn (rather than teleporting each player) is the simplest, most robust option:
 * new players and bed-less respawns use it automatically, and players who already have a bed are
 * unaffected (a bed overrides the world spawn per-player). It re-runs each start, but the village
 * position is deterministic, so once spawn sits on the village this is a no-op.
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpawnInVillage {
    private SpawnInVillage() {}

    /** Search radius for the village, in CHUNKS (~16 blocks each); 100 chunks ≈ 1600 blocks. */
    private static final int SEARCH_CHUNKS = 100;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Config.SPAWN_AT_NEAREST_VILLAGE.get()) return; // opt-in: off by default (config/gobacktoyourworld-common.toml)
        ServerLevel overworld = event.getServer().overworld();
        BlockPos from = overworld.getSharedSpawnPos();

        // Locate the nearest village structure. skipExisting=false so it also finds not-yet-generated ones.
        BlockPos village = overworld.findNearestMapStructure(StructureTags.VILLAGE, from, SEARCH_CHUNKS, false);
        if (village == null) {
            VillagerGunners.LOGGER.info("[Go Back To Your World] no village within {} chunks of spawn; leaving spawn as-is.", SEARCH_CHUNKS);
            return;
        }

        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, village.getX(), village.getZ());
        BlockPos spawn = new BlockPos(village.getX(), y, village.getZ());
        overworld.setDefaultSpawnPos(spawn, 0.0F);
        VillagerGunners.LOGGER.info("[Go Back To Your World] world spawn set to village at {}.", spawn);
    }
}
