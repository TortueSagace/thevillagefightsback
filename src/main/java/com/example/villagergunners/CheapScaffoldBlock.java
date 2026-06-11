package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A throwaway "cheap scaffold": identical to vanilla scaffolding to stand on / climb, but every cheap
 * scaffold auto-breaks ~30s after it is placed. Drop rules:
 * <ul>
 *   <li>auto-break after ~30s -> drops nothing (handled by {@link CheapScaffoldManager});</li>
 *   <li>broken early (mined / loses support) -> drops itself (loot table);</li>
 *   <li>destroyed by fire or explosion -> drops nothing.</li>
 * </ul>
 * Placements are registered with {@link CheapScaffoldManager}, which batches scaffolds placed close
 * together onto one shared timer for efficiency.
 */
public class CheapScaffoldBlock extends ScaffoldingBlock {
    public CheapScaffoldBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && !oldState.is(this)) {
            CheapScaffoldManager.track((ServerLevel) level, pos);
        }
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return false; // explosions destroy cheap scaffolds without dropping anything
    }

    // Flammable like bamboo scaffolding -- fire destroys it, dropping nothing.
    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 60;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 60;
    }
}
