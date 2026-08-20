package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Survival opening: a map instead of a bell.
 *
 * <p>Creative hands the player a bell and lets them found a village wherever they like. Survival makes the
 * village something to go and FIND: the means to build one is buried a few hundred blocks away, and a
 * treasure map is the only thing in the player's hands at spawn. The Reputation Book goes in the chest with
 * it, so the rules and the tool arrive together at the end of the walk rather than one at each end.
 *
 * <p>The chest is generated on demand rather than looked for. Relying on a naturally generated structure
 * being nearby would mean the opening silently failing in the wrong biome, and a mod that hands out a map to
 * nothing is worse than one that hands out no map.
 */
public final class SurvivalStart {
    private SurvivalStart() {}

    /** How far out the cache is buried. A walk, not an expedition -- 100 blocks is the ceiling. */
    private static final int MIN_DIST = 40;
    private static final int MAX_DIST = 100;

    /** How deep below the surface the chest sits, so it reads as buried treasure rather than a gift box. */
    private static final int BURY_DEPTH = 4;

    /**
     * Bury the cache and hand {@code player} the map to it.
     *
     * <p>Returns false if nowhere workable turned up, in which case the caller should fall back to the normal
     * bell so the player is never left with nothing.
     */
    public static boolean give(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos spot = findSpot(level, player.blockPosition());
        if (spot == null) {
            // Loud, not silent. This falls back to the bell, and a silent fallback is exactly how the Survival
            // opening went unnoticed twice: the log said "starter bell given" and nothing said why.
            TheVillageFightsBack.LOGGER.warn(
                    "[The Village Fights Back] found nowhere to bury the Survival starter cache near {}; "
                    + "falling back to the bell.", player.blockPosition());
            return false;
        }

        // PROMISED, not written. The chunk it goes in may well not exist yet;
        // setBlock there would need a synchronous generation that cannot happen inside a packet handler, so
        // the write landed on nothing and the whole opening quietly became a bell. SurvivalCache writes it
        // when the chunk genuinely loads -- which is when the player arrives to dig it up.
        //
        // No map item and no shovel: SurvivalCache shows a live distance on the action bar instead, which
        // needs no inventory slot and cannot be lost, dropped or filled out of existence by another mod.
        SurvivalCache.get(level).promise(level, spot, player.getUUID());

        TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] Survival starter cache promised at {} for {}.",
                spot, player.getGameProfile().getName());
        return true;
    }

    /**
     * Somewhere to bury it: a random bearing, a random distance, and solid ground under the surface.
     *
     * <p>The height comes from the chunk GENERATOR, not from {@code level.getHeight}. That distinction is the
     * whole reason this works: the cache is hundreds of blocks away, those chunks are not loaded, and
     * {@code Level.getHeight} answers {@code getMinBuildHeight()} for an unloaded chunk rather than admitting
     * it does not know. Every candidate was therefore rejected as "no room under it", all eight attempts, and
     * the opening silently fell through to the bell. {@code getBaseHeight} computes the terrain height from
     * the generator's own noise without generating anything, which is how vanilla locates structures and how
     * a real treasure map picks its X.
     */
    private static BlockPos findSpot(ServerLevel level, BlockPos from) {
        net.minecraft.world.level.chunk.ChunkGenerator gen = level.getChunkSource().getGenerator();
        net.minecraft.world.level.levelgen.RandomState rs = level.getChunkSource().randomState();
        int floor = level.getMinBuildHeight();
        BlockPos wetOrShallow = null;

        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            int dist = MIN_DIST + level.random.nextInt(MAX_DIST - MIN_DIST);
            int x = from.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = from.getZ() + (int) Math.round(Math.sin(angle) * dist);

            int surface = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
            if (surface <= floor + 1) continue;                 // genuinely no ground here at all

            // Bury as deep as there is room for, rather than demanding the full BURY_DEPTH. A SUPERFLAT world
            // is three blocks thick: asking for four below the surface put every candidate at or under the
            // world floor, all twelve attempts, so the opening fell back to the bell in every flat world.
            int y = Math.max(floor + 1, surface - BURY_DEPTH);

            BlockPos cand = new BlockPos(x, y, z);
            if (surface > level.getSeaLevel()) return cand;     // dry land: ideal, take it

            // Below sea level -- probably an ocean floor. Worth avoiding, NOT worth failing over: a superflat
            // sits far below y=63 and would never pass this, and an all-ocean start would rather have a wet
            // cache than no map. Remembered, and used only if nothing drier turns up.
            if (wetOrShallow == null) wetOrShallow = cand;
        }
        return wetOrShallow;
    }
}
