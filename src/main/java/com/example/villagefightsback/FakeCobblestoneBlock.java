package com.example.villagefightsback;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * "Fake cobblestone" — looks/breaks like mossy cobblestone (and emits light), but is a smart door:
 * players, villagers, iron golems, and Guard Villagers guards walk THROUGH it as if it were air (and
 * their AI paths through it), while every other entity treats it as solid cobblestone.
 *
 * Two mechanisms, kept in sync via {@link #isPhaser}:
 *  - Collision: {@link #getCollisionShape} returns an empty shape for allied entities, solid otherwise
 *    (and for non-entity contexts: placement/redstone/the per-state cache). {@link #isSuffocating} /
 *    {@link #isViewBlocking} are false so an ally standing inside doesn't take damage or black-screen.
 *  - Pathfinding: {@link #getBlockPathType} returns {@link ModPathTypes#ALLY_PASSABLE} (mob-blind by
 *    necessity in 1.20.1); allied mobs set that type's malus to 0 so only they route through.
 */
public class FakeCobblestoneBlock extends Block {
    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");

    public FakeCobblestoneBlock(Properties properties) {
        super(properties);
    }

    /** Entities allowed to phase through (kept consistent with the pathfinding malus opt-in). */
    public static boolean isPhaser(Entity e) {
        if (e instanceof Player || e instanceof Villager || e instanceof IronGolem) return true;
        return GUARD_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ecc) {
            Entity e = ecc.getEntity();
            if (e != null && isPhaser(e)) {
                // Allies phase through the wall, BUT a supported fake-cobble that has open space above acts as
                // a floor they can stand on (so they don't fall through a platform / the top of a stack). A
                // gate stack (fake cobble or the cobble cap directly above) has no open space above, so it
                // stays fully passable -- the 3-high doorway still works.
                BlockState below = level.getBlockState(pos.below());
                BlockState above = level.getBlockState(pos.above());
                boolean solidBelow = below.is(this) || (!below.isAir() && !below.canBeReplaced());
                boolean openAbove = above.isAir() || above.canBeReplaced();
                return (solidBelow && openAbove) ? Shapes.block() : Shapes.empty();
            }
        }
        return Shapes.block(); // everyone else + non-entity contexts: solid
    }

    // NOTE: isSuffocating / isViewBlocking are Properties predicates (not overridable methods); they
    // are set to "always false" on the block's Properties in ModBlocks so an ally standing inside the
    // block doesn't take suffocation damage or get the black "inside a block" overlay.

    @Override
    @Nullable
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        // Mob-blind in 1.20.1 (WalkNodeEvaluator passes null); ally/enemy split is via per-mob malus.
        return ModPathTypes.ALLY_PASSABLE;
    }
}
