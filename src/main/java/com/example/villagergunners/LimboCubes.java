package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The floating empty bedrock cubes that dot the Limbo. Placement is deterministic from the world seed + chunk
 * coords, so a given chunk always yields the same cube (or none). Cubes are hollow bedrock shells of varying
 * size, sparsely encrusted with Forgotten Glowstone (like moss on stone). The big ones get a nest
 * ({@link LimboNest}); the central cube ({@link LimboDimension}) is built separately and excluded here.
 */
public final class LimboCubes {
    private LimboCubes() {}

    /** ~1 in this many chunks originates a cube. */
    private static final int RARITY = 16;
    private static final int MIN_SIZE = 5, MAX_SIZE = 17;
    private static final int MIN_Y = 24, MAX_Y = 232;
    /** Fraction of shell blocks swapped for Forgotten Glowstone. */
    private static final float GLOWSTONE_CHANCE = 0.12F;

    /** A cube: its minimum corner + edge length (blocks). */
    public record Cube(int x0, int y0, int z0, int size) {
        public int center(int a) { return a + size / 2; }
        public boolean hasNest() { return size >= 7; } // "at least 7x7"
    }

    /** A stable per-chunk RNG seeded by (worldSeed, chunkX, chunkZ). */
    private static RandomSource chunkRandom(long seed, int cx, int cz) {
        long h = seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h ^= (h >>> 33); h *= 0xFF51AFD7ED558CCDL; h ^= (h >>> 33);
        return RandomSource.create(h);
    }

    /** The cube originating in chunk (cx,cz), or null if none. Excludes the central cube's chunks. */
    public static Cube cubeForChunk(long seed, int cx, int cz) {
        if (Math.abs(cx) <= 1 && Math.abs(cz) <= 1) return null; // keep clear of the central cube/spawn
        if (LimboDrift.type(seed, cx, cz) != LimboDrift.Type.NONE) return null; // never overlap a drift chunk
        RandomSource r = chunkRandom(seed, cx, cz);
        if (r.nextInt(RARITY) != 0) return null;
        int size = MIN_SIZE + r.nextInt(MAX_SIZE - MIN_SIZE + 1);
        int ox = r.nextInt(Math.max(1, 16 - size));    // keep the cube mostly within its origin chunk
        int oz = r.nextInt(Math.max(1, 16 - size));
        int x0 = (cx << 4) + ox;
        int z0 = (cz << 4) + oz;
        int y0 = MIN_Y + r.nextInt(MAX_Y - MIN_Y - size);
        return new Cube(x0, y0, z0, size);
    }

    /** Build a hollow bedrock cube with sparse Forgotten Glowstone, + a nest if it is big enough. */
    public static void build(ServerLevel level, Cube c) {
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState glow = ModBlocks.FORGOTTEN_GLOWSTONE.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        RandomSource r = RandomSource.create((long) c.x0 * 132871 ^ (long) c.y0 * 7919 ^ (long) c.z0 * 40503);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int x1 = c.x0 + c.size - 1, y1 = c.y0 + c.size - 1, z1 = c.z0 + c.size - 1;
        for (int x = c.x0; x <= x1; x++) {
            for (int y = c.y0; y <= y1; y++) {
                for (int z = c.z0; z <= z1; z++) {
                    boolean shell = x == c.x0 || x == x1 || y == c.y0 || y == y1 || z == c.z0 || z == z1;
                    m.set(x, y, z);
                    if (shell) {
                        level.setBlock(m, r.nextFloat() < GLOWSTONE_CHANCE ? glow : bedrock, 2);
                    } else {
                        level.setBlock(m, air, 2);
                    }
                }
            }
        }
        if (c.hasNest()) {
            LimboNest.placeInCubeCenter(level, c); // ancient-debris bowl + limbo eggs (cubes >= 7)
        }
    }
}
