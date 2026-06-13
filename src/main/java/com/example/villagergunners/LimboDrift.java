package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import net.minecraft.resources.ResourceKey;

/**
 * "Dimensional drift": a small fraction of Limbo chunks mirror their twin chunk in another dimension, so
 * terrain, blocks and containers are shared between the two worlds. Each Limbo chunk deterministically rolls
 * (from the world seed + coords) a 2% chance to mirror the overworld and a 1% chance to mirror the nether.
 *
 * This class holds the pure helpers: the deterministic roll, the twin mapping, and the section-by-section copy
 * (blocks + biomes) used to fill a drift chunk from its twin. Living entities never cross; only the world does.
 */
public final class LimboDrift {
    private LimboDrift() {}

    public enum Type { NONE, OVERWORLD, NETHER }

    // Defaults; overridden by Config at load.
    public static double overworldChance = 0.02;
    public static double netherChance = 0.01;

    /** The drift type of a Limbo chunk (deterministic). Central chunks never drift. */
    public static Type type(long seed, int cx, int cz) {
        if (Math.abs(cx) <= 1 && Math.abs(cz) <= 1) return Type.NONE;
        long h = seed ^ 0xD1FF5EED12345678L ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h ^= (h >>> 30); h *= 0xBF58476D1CE4E5B9L; h ^= (h >>> 27); h *= 0x94D049BB133111EBL; h ^= (h >>> 31);
        double r = (h >>> 11) * 0x1.0p-53; // [0,1)
        if (r < overworldChance) return Type.OVERWORLD;
        if (r < overworldChance + netherChance) return Type.NETHER;
        return Type.NONE;
    }

    public static ResourceKey<Level> twinDimension(Type t) {
        return t == Type.NETHER ? Level.NETHER : Level.OVERWORLD;
    }

    /** The twin chunk: overworld is 1:1; nether applies the vanilla 8:1 overworld->nether compression. */
    public static ChunkPos twinChunk(Type t, int cx, int cz) {
        return t == Type.NETHER ? new ChunkPos(cx >> 3, cz >> 3) : new ChunkPos(cx, cz);
    }

    /** Copy one 16x16x16 section (blocks + biomes) from the twin chunk into the Limbo chunk. */
    public static void copySection(ServerLevel limbo, LevelChunk limboChunk, LevelChunk twinChunk,
                                   Type type, int sectionIndex) {
        int baseY = limbo.getMinBuildHeight() + sectionIndex * 16;
        boolean nether = type == Type.NETHER;
        int minX = limboChunk.getPos().getMinBlockX();
        int minZ = limboChunk.getPos().getMinBlockZ();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos lp = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos tp = new BlockPos.MutableBlockPos();

        for (int y = 0; y < 16; y++) {
            int wy = baseY + y;
            boolean inTwin = !nether || (wy >= 0 && wy < 256); // nether only spans 0..255
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int wx = minX + x, wz = minZ + z;
                    BlockState st = inTwin ? twinChunk.getBlockState(tp.set(wx, wy, wz)) : air;
                    limboChunk.setBlockState(lp.set(wx, wy, wz), st, false); // no packets; light handled locally
                }
            }
        }

        // Biomes (4x4x4 per section). getBiomes() is typed read-only but is a mutable PalettedContainer.
        LevelChunkSection lsec = limboChunk.getSection(sectionIndex);
        @SuppressWarnings("unchecked")
        net.minecraft.world.level.chunk.PalettedContainer<Holder<net.minecraft.world.level.biome.Biome>> bio =
                (net.minecraft.world.level.chunk.PalettedContainer<Holder<net.minecraft.world.level.biome.Biome>>)
                        lsec.getBiomes();
        int qx0 = limboChunk.getPos().x << 2;
        int qz0 = limboChunk.getPos().z << 2;
        int qy0 = baseY >> 2;
        for (int by = 0; by < 4; by++) {
            int qy = nether ? Math.max(0, Math.min(63, qy0 + by)) : (qy0 + by);
            for (int bx = 0; bx < 4; bx++) {
                for (int bz = 0; bz < 4; bz++) {
                    Holder<net.minecraft.world.level.biome.Biome> biome =
                            twinChunk.getNoiseBiome(qx0 + bx, qy, qz0 + bz);
                    bio.getAndSet(bx, by, bz, biome);
                }
            }
        }
    }

    /** Copy container/sign/etc. contents (block-entity NBT) from the twin into the Limbo chunk. */
    public static void copyBlockEntities(ServerLevel limbo, LevelChunk limboChunk, LevelChunk twinChunk) {
        ChunkPos lp = limboChunk.getPos();
        for (var e : twinChunk.getBlockEntities().entrySet()) {
            BlockPos tpos = e.getKey();
            BlockEntity tbe = e.getValue();
            BlockPos lpos = new BlockPos(lp.getMinBlockX() + (tpos.getX() & 15), tpos.getY(),
                    lp.getMinBlockZ() + (tpos.getZ() & 15));
            BlockEntity lbe = limboChunk.getBlockEntity(lpos);
            if (lbe != null && lbe.getType() == tbe.getType()) {
                lbe.load(tbe.saveWithoutMetadata());
            }
        }
    }

    /** The twin block position for a Limbo block (same local x/z within the twin chunk; same Y). */
    public static BlockPos toTwinBlock(ChunkPos twinChunkPos, BlockPos limboPos) {
        return new BlockPos(twinChunkPos.getMinBlockX() + (limboPos.getX() & 15), limboPos.getY(),
                twinChunkPos.getMinBlockZ() + (limboPos.getZ() & 15));
    }

    /** The Limbo block position for a twin block (same local x/z within the Limbo chunk; same Y). */
    public static BlockPos toLimboBlock(ChunkPos limboChunkPos, BlockPos twinPos) {
        return new BlockPos(limboChunkPos.getMinBlockX() + (twinPos.getX() & 15), twinPos.getY(),
                limboChunkPos.getMinBlockZ() + (twinPos.getZ() & 15));
    }

    /** Resend the whole Limbo chunk to nearby players (one packet) after a bulk copy. */
    public static void resend(ServerLevel limbo, LevelChunk chunk) {
        chunk.setUnsaved(true);
        ClientboundLevelChunkWithLightPacket pkt =
                new ClientboundLevelChunkWithLightPacket(chunk, limbo.getLightEngine(), null, null);
        int viewDist = limbo.getServer().getPlayerList().getViewDistance() + 1;
        ChunkPos cp = chunk.getPos();
        for (ServerPlayer p : limbo.players()) {
            ChunkPos pp = p.chunkPosition();
            if (Math.max(Math.abs(pp.x - cp.x), Math.abs(pp.z - cp.z)) <= viewDist) {
                p.connection.send(pkt);
            }
        }
    }

    /** Map a Limbo block position to its twin position (overworld 1:1; nether is the same Y, within 0..255). */
    public static BlockPos twinBlock(Type t, BlockPos limboPos) {
        return limboPos; // x,z identical; Y identical (nether edits only occur within its own 0..255 range)
    }
}
