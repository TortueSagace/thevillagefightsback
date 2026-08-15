package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Keeps an Obliteration Furnace LOOKING like what {@link ObliterationData} says it is.
 *
 * <p>It holds no state of its own, on purpose. The fuel and the obliteration list live in the world-level
 * record so they keep working while this chunk is unloaded, which is the whole design (see
 * {@link ObliterationData}). Duplicating either here would create a second source of truth that drifts the
 * moment the chunk is out of sight, and the first symptom would be a furnace claiming to burn while the ban it
 * is supposed to enforce has already lapsed.
 *
 * <p>So its entire job is the resync: when the chunk comes back, read the record and set {@link
 * ObliterationFurnaceBlock#LIT} to match. That is what makes a furnace that ran dry while nobody was watching
 * go dark the instant you walk up to it, instead of sitting there glowing at a ban that expired days ago.
 */
public class ObliterationFurnaceBlockEntity extends BlockEntity {
    /** How often to re-check the record. Once a second is plenty for a light that changes twice an hour. */
    private static final int RESYNC_INTERVAL = 20;

    public ObliterationFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.OBLITERATION_FURNACE_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ObliterationFurnaceBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (level.getGameTime() % RESYNC_INTERVAL != 0) return;
        boolean burning = ObliterationData.get(sl.getServer())
                .isBurning(GlobalPos.of(sl.dimension(), pos.immutable()));
        if (state.getValue(ObliterationFurnaceBlock.LIT) != burning) {
            syncLit(sl, pos, burning);
        }
    }

    /** Set the blockstate's lit flag, which also drives the light level and the animated fire texture. */
    public static void syncLit(ServerLevel level, BlockPos pos, boolean lit) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ObliterationFurnaceBlock)) return;
        if (state.getValue(ObliterationFurnaceBlock.LIT) == lit) return;
        level.setBlock(pos, state.setValue(ObliterationFurnaceBlock.LIT, lit), 3);
    }
}
