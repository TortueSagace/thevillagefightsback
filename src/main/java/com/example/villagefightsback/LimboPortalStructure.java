package com.example.villagefightsback;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * A cold Limbo portal, standing in the Overworld waiting for somebody to light it.
 *
 * <p>The frame only -- a nether-portal shape in {@link ModBlocks#FAKE_OBSIDIAN}, complete and correct and
 * completely inert. {@link LimboPortalShape#isFrame} already accepts fake obsidian, so a player who finds one
 * and thinks to strike a flint gets the whole dimension; a player who does not gets an odd black doorway in a
 * field. That is the intended shape of the discovery: the Limbo stops being something you have to be told
 * about and becomes something you can walk into.
 *
 * <p>Built in code rather than from an {@code .nbt} template, unlike the mod's seventeen other structures.
 * A rectangle of one block type is less code than the loader for a file describing a rectangle of one block
 * type, and it means the frame follows the ground rather than needing a flat spot cut for it.
 *
 * <p>Rarity is set by {@code worldgen/structure_set/limbo_portals.json}, at the plains village's spacing.
 */
public class LimboPortalStructure extends Structure {
    public static final Codec<LimboPortalStructure> CODEC =
            RecordCodecBuilder.<LimboPortalStructure>mapCodec(inst ->
                    inst.group(settingsCodec(inst)).apply(inst, LimboPortalStructure::new)).codec();

    /** The vanilla nether portal's minimum: a 2x3 opening inside a 4x5 frame. */
    private static final int INNER_W = 2;
    private static final int INNER_H = 3;

    public LimboPortalStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        int x = ctx.chunkPos().getMiddleBlockX();
        int z = ctx.chunkPos().getMiddleBlockZ();
        int y = ctx.chunkGenerator().getFirstOccupiedHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        BlockPos base = new BlockPos(x, y, z);
        // Ask for the site here rather than in the piece: refusing at the stub costs nothing, while a piece
        // that has already been placed cannot be taken back.
        if (y <= ctx.heightAccessor().getMinBuildHeight() + 2) return Optional.empty();
        return Optional.of(new GenerationStub(base, builder -> generatePieces(builder, base, ctx)));
    }

    private void generatePieces(StructurePiecesBuilder builder, BlockPos base, GenerationContext ctx) {
        boolean alongX = ctx.random().nextBoolean();
        builder.addPiece(new Piece(base, alongX));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.LIMBO_PORTAL.get();
    }

    /**
     * Lay the ring, once, wherever it is asked for.
     *
     * <p>Shared so the superflat fallback ({@link LimboPortalFlatgen}) puts down exactly the same portal as
     * worldgen does, rather than a second implementation that drifts from it.
     *
     * <p>Only the frame is written -- the opening is left exactly as it was found, so a portal that
     * generated across a hillside has the hillside still in it and the player has to clear it before it
     * will light. That is deliberate: an inert doorway that needs a little work is more interesting than a
     * clean one, and it costs nothing to do less.
     */
    static void layFrame(net.minecraft.world.level.LevelAccessor level, BlockPos base, boolean alongX,
                         BoundingBox box) {
        var frame = ModBlocks.FAKE_OBSIDIAN.get().defaultBlockState();
        for (int u = 0; u < INNER_W + 2; u++) {
            for (int v = 0; v < INNER_H + 2; v++) {
                boolean edge = u == 0 || u == INNER_W + 1 || v == 0 || v == INNER_H + 1;
                if (!edge) continue;                       // the opening stays open
                BlockPos at = alongX ? base.offset(u - 1, v, 0) : base.offset(0, v, u - 1);
                // During worldgen the box confines the write to this chunk; the rest of the frame arrives
                // with its own. Placed at runtime there is no such limit and none is wanted.
                if (box != null && !box.isInside(at)) continue;
                level.setBlock(at, frame, 2);
            }
        }
    }

    /** As above, with no chunk to stay inside. */
    static void layFrame(net.minecraft.world.level.LevelAccessor level, BlockPos base, boolean alongX) {
        layFrame(level, base, alongX, null);
    }

    /** The frame itself. */
    public static class Piece extends net.minecraft.world.level.levelgen.structure.StructurePiece {
        private final BlockPos base;
        private final boolean alongX;

        Piece(BlockPos base, boolean alongX) {
            super(ModStructures.LIMBO_PORTAL_PIECE.get(), 0, boxFor(base, alongX));
            this.base = base;
            this.alongX = alongX;
        }

        public Piece(net.minecraft.nbt.CompoundTag tag) {
            super(ModStructures.LIMBO_PORTAL_PIECE.get(), tag);
            this.base = BlockPos.of(tag.getLong("Base"));
            this.alongX = tag.getBoolean("AlongX");
        }

        private static BoundingBox boxFor(BlockPos base, boolean alongX) {
            int w = INNER_W + 2;
            return alongX
                    ? new BoundingBox(base.getX() - 1, base.getY(), base.getZ(),
                            base.getX() + w - 2, base.getY() + INNER_H + 1, base.getZ())
                    : new BoundingBox(base.getX(), base.getY(), base.getZ() - 1,
                            base.getX(), base.getY() + INNER_H + 1, base.getZ() + w - 2);
        }

        @Override
        protected void addAdditionalSaveData(
                net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext ctx,
                net.minecraft.nbt.CompoundTag tag) {
            tag.putLong("Base", base.asLong());
            tag.putBoolean("AlongX", alongX);
        }

        /**
         * Lay the ring.
         *
         * <p>Only the frame is written -- the opening is left exactly as it was found, so a portal that
         * generated across a hillside has the hillside still in it and the player has to clear it before it
         * will light. That is deliberate: an inert doorway that needs a little work is more interesting than
         * a clean one, and it costs nothing to do less.
         */
        @Override
        public void postProcess(WorldGenLevel level, net.minecraft.world.level.StructureManager structures,
                                net.minecraft.world.level.chunk.ChunkGenerator generator,
                                net.minecraft.util.RandomSource random, BoundingBox box,
                                net.minecraft.world.level.ChunkPos chunk, BlockPos pos) {
            layFrame(level, base, alongX, box);
        }
    }
}
