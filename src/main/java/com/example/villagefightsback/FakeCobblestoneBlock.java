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
 * "Fake cobblestone" -- looks/breaks like mossy cobblestone (and emits light), but is a smart door:
 * players, villagers, iron golems, and Guard Villagers guards walk THROUGH it as if it were air (and
 * their AI paths through it), while every other entity treats it as solid cobblestone.
 *
 * Two mechanisms, kept in sync via {@link #isPhaser}:
 *  - Collision: {@link #getCollisionShape} returns an empty shape for allied entities, solid otherwise
 *    (and for non-entity contexts: placement/redstone/the per-state cache). isSuffocating is false so an
 *    ally standing inside takes no damage, while isViewBlocking is TRUE so they still get the ordinary
 *    in-wall overlay and cannot use the gate as a free window (see {@link ModBlocks#FAKE_COBBLESTONE}).
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
        // MCA Reborn outlaws / cultists are hostile Villagers -- they must NOT be handed a key to the village's
        // smart doors, or a raid would simply walk through the wall.
        if (com.example.villagefightsback.compat.MCACompat.isHostileVillager(e)) return false;
        if (e instanceof Player || e instanceof Villager || e instanceof IronGolem) return true;
        if (Factions.isEmerald(e)) return true; // emerald faction units phase the village's smart doors too
        return GUARD_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ecc) {
            Entity e = ecc.getEntity();
            if (e != null && isPhaser(e)) {
                // Allies phase through the wall, BUT a fake-cobble resting on a REAL solid block with open space
                // above acts as a floor they can stand on (so they don't fall through a fake-cobble platform).
                // When it sits on ANOTHER fake-cobble, it's the upper half of a vertical door/gate column, so it
                // stays passable -- a plain 2-high fake-cobble stack (foot + head) is a walk-through doorway, and
                // a 3-high gate (capped or not) works too. Only the BOTTOM block of a stack can be a floor.
                BlockState below = level.getBlockState(pos.below());
                BlockState above = level.getBlockState(pos.above());
                boolean openAbove = above.isAir() || above.canBeReplaced();
                boolean realFloor = openAbove && !below.is(this) && !below.isAir() && !below.canBeReplaced();
                return realFloor ? Shapes.block() : Shapes.empty();
            }
        }
        return Shapes.block(); // everyone else + non-entity contexts: solid
    }

    // NOTE: isSuffocating / isViewBlocking are Properties predicates (not overridable methods), and they are
    // set opposite ways in ModBlocks: suffocating false so an ally inside takes no damage, view-blocking TRUE
    // so they still get the "inside a block" overlay. Standing in the gate should cost you your view, just not
    // your health -- otherwise it is a free one-way window into the battle. See ModBlocks#FAKE_COBBLESTONE.

    @Override
    @Nullable
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        // Mob-blind in 1.20.1 (WalkNodeEvaluator passes null); ally/enemy split is via per-mob malus.
        return ModPathTypes.ALLY_PASSABLE;
    }
}
