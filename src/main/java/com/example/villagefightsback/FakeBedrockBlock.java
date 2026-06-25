package com.example.villagefightsback;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * "Fake bedrock": indistinguishable from real bedrock (full bedrock texture, bedrock toughness -- unbreakable
 * except by the Limbo Pickaxe), but EVERY creature passes straight through it. Like fake cobblestone, it still
 * acts as a floor/surface when there is open space above, so mobs and players stand on the Limbo cube floors and
 * roofs instead of dropping into the void; a covered block (a wall's interior) is fully passable. The whole
 * Limbo is built from it, so anything can simply walk out of a Limbo cube while it still looks like a sealed
 * bedrock cell. isSuffocating / isViewBlocking are set false on its Properties (in {@link ModBlocks}) so an
 * entity standing inside it takes no damage and gets no black overlay.
 */
public class FakeBedrockBlock extends Block {
    public FakeBedrockBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ecc && ecc.getEntity() != null) {
            // A surface (open space above) is solid so creatures stand on it; a covered block (wall interior) is
            // fully passable, so any creature walks through the walls of a cube but not down through its floor.
            BlockState above = level.getBlockState(pos.above());
            boolean openAbove = above.isAir() || above.getMaterial().isReplaceable();
            return openAbove ? Shapes.block() : Shapes.empty();
        }
        return Shapes.block(); // non-entity contexts (support / redstone / the per-state cache): solid
    }

    @Override
    @Nullable
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        // Match the collision: a covered block is open to pathfinding (mobs route through walls); a surface is
        // left to vanilla (a solid block they stand on top of).
        BlockState above = level.getBlockState(pos.above());
        boolean openAbove = above.isAir() || above.getMaterial().isReplaceable();
        return openAbove ? null : BlockPathTypes.OPEN;
    }
}
