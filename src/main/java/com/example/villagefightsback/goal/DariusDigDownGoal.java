package com.example.villagefightsback.goal;

import java.util.EnumSet;

import com.example.villagefightsback.GeneralDariusEntity;
import com.example.villagefightsback.LimboGear;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

/**
 * Lets General Darius DIG STRAIGHT DOWN to reach a target below him. His contact-plowing
 * ({@link LimboGear#breakSoftBlocksOnContact}) never touches the floor he stands on, so a target dug into a pit
 * or bunker was unreachable. When his locked target is clearly below him and he cannot path to it, he smashes the
 * block(s) directly under his feet -- the exact same set he plows through on contact (anything breakable below
 * the ancient-debris hardness, never bedrock or a phase-door he is walking through) -- and drops toward it.
 *
 * <p>No goal flags, so it runs ALONGSIDE his combat brain. Darius is fall-immune, so the descent is safe. The
 * reach test is staggered to a few times a second to stay cheap, mirroring {@link EmeraldDigGoal}.
 */
public class DariusDigDownGoal extends Goal {
    private static final int RECHECK = 10; // staggered reach-test cadence (ticks)

    private final GeneralDariusEntity darius;

    public DariusDigDownGoal(GeneralDariusEntity darius) {
        this.darius = darius;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class)); // runs concurrently with movement/combat goals
    }

    @Override
    public boolean canUse() {
        LivingEntity t = darius.getTarget();
        if (t == null || !t.isAlive() || !darius.isOnGround()) return false;
        if (t.getY() >= darius.getY() - 1.5) return false;   // only when the target is clearly BELOW
        if (!stuckCheckDue()) return false;                  // throttle the costly pathfind
        if (canReach(t)) return false;                       // a walkable path exists -> walk it, don't dig
        return hasDiggableBelow();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = darius.getTarget();
        if (t == null || !t.isAlive() || !darius.isOnGround()) return false;
        if (t.getY() >= darius.getY() - 1.0) return false;   // reached the target's level -> stop digging
        if (stuckCheckDue() && canReach(t)) return false;    // a path opened up -> walk it
        return hasDiggableBelow();
    }

    private boolean stuckCheckDue() {
        return (darius.tickCount + darius.getId()) % RECHECK == 0;
    }

    private boolean canReach(LivingEntity t) {
        Path p = darius.getNavigation().createPath(t, 1);
        return p != null && p.canReach();
    }

    @Override
    public void tick() {
        if (!(darius.level instanceof ServerLevel sl)) return;
        AABB box = darius.getBoundingBox();
        int y = Mth.floor(box.minY) - 1;                     // the layer directly beneath his feet
        int minX = Mth.floor(box.minX + 0.01), maxX = Mth.floor(box.maxX - 0.01);
        int minZ = Mth.floor(box.minZ + 0.01), maxZ = Mth.floor(box.maxZ - 0.01);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean broke = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                p.set(x, y, z);
                if (isDiggable(sl, p)) { sl.destroyBlock(p.immutable(), true, darius); broke = true; }
            }
        }
        if (broke) darius.swing(InteractionHand.MAIN_HAND);   // gravity then drops him into the new gap
    }

    /** True if any block in the footprint directly below his feet can be dug. */
    private boolean hasDiggableBelow() {
        if (!(darius.level instanceof ServerLevel sl)) return false;
        AABB box = darius.getBoundingBox();
        int y = Mth.floor(box.minY) - 1;
        int minX = Mth.floor(box.minX + 0.01), maxX = Mth.floor(box.maxX - 0.01);
        int minZ = Mth.floor(box.minZ + 0.01), maxZ = Mth.floor(box.maxZ - 0.01);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                p.set(x, y, z);
                if (isDiggable(sl, p)) return true;
            }
        }
        return false;
    }

    /** The same block set Darius plows through on contact: breakable, below the ancient-debris hardness, never
     *  bedrock or a phase-door (the ally-passable gate he might be standing on). */
    private static boolean isDiggable(ServerLevel sl, BlockPos pos) {
        BlockState s = sl.getBlockState(pos);
        if (s.isAir()) return false;
        if (LimboGear.isPhaseDoor(s)) return false;
        if (s.is(Blocks.BEDROCK)) return false;
        float h = s.getDestroySpeed(sl, pos);
        return h >= 0.0F && h < LimboGear.ANCIENT_DEBRIS_HARDNESS;
    }
}
