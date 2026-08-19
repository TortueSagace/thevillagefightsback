package com.example.villagefightsback;

import org.jetbrains.annotations.Nullable;

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
 * bedrock cell. On its Properties (in {@link ModBlocks}) isSuffocating is false so an entity standing inside
 * takes no damage, while isViewBlocking is TRUE so it blinds you like the stone it imitates -- the same pair
 * the other two fake blocks carry. {@code ModBlocks#X_RAY_BEDROCK} is the see-through variant, and it shares
 * this class, so the floor rule below treats the two as one family.
 */
public class FakeBedrockBlock extends Block implements com.example.villagefightsback.platform.PathTypeProvider {
    public FakeBedrockBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ecc && ecc.getEntity() != null) {
            // A surface (open space above) is solid so creatures stand on it; a covered block (wall interior) is
            // fully passable, so any creature walks through the walls of a cube but not down through its floor.
            //
            // ...unless it is standing on another block of this family, in which case it is the upper half of a
            // gate and stays passable. Without that clause a free-standing 2-high doorway was solid at HEAD
            // height (open sky above the top block made it read as a floor), so you could put your feet in it
            // and go no further -- while the fake cobblestone and obsidian gates, which have always had this
            // rule, let you walk straight through. A doorway made of the family's third block should not be the
            // one that refuses to open.
            //
            // Safe for every structure the Limbo builds because all of them are ONE block thick: cube shells,
            // portal floors and the emergency floor under a spawning entity all sit on void, so their "below"
            // is never family and they stay solid. The Limbo's worldgen is a single layer of air, so there is
            // no stacked bedrock floor anywhere to fall through.
            BlockState above = level.getBlockState(pos.above());
            BlockState below = level.getBlockState(pos.below());
            boolean openAbove = above.isAir() || above.canBeReplaced();
            boolean onFamily = below.getBlock() instanceof FakeBedrockBlock;
            return openAbove && !onFamily ? Shapes.block() : Shapes.empty();
        }
        return Shapes.block(); // non-entity contexts (support / redstone / the per-state cache): solid
    }

    @Override
    @Nullable
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        // Match the collision: a covered block is open to pathfinding (mobs route through walls); a surface is
        // left to vanilla (a solid block they stand on top of).
        BlockState above = level.getBlockState(pos.above());
        boolean openAbove = above.isAir() || above.canBeReplaced();
        return openAbove ? null : BlockPathTypes.OPEN;
    }
}
