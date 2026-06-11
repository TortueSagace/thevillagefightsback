package com.example.villagergunners;

import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * A throwaway "cheap scaffold": identical to vanilla scaffolding to stand on / climb, but every cheap
 * scaffold auto-breaks ~30s after it is placed. Drop rule: it drops itself ONLY when a player mines it;
 * every other cause (the ~30s auto-break, loss of support, a builder clearing it, fire, or an explosion)
 * drops nothing -- see {@link #getDrops}.
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

    /**
     * Drop the scaffold item if and ONLY if a player broke it. Any other cause -- the ~30s auto-break, loss
     * of support, a builder clearing it, fire, or an explosion -- yields nothing (the loot context has no
     * THIS_ENTITY, or it isn't a player). This overrides the loot table entirely.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Entity breaker = params.getOptionalParameter(LootContextParams.THIS_ENTITY);
        return breaker instanceof Player ? List.of(new ItemStack(this)) : Collections.emptyList();
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
