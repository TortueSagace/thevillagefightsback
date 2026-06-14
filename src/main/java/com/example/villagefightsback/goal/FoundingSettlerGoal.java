package com.example.villagefightsback.goal;

import java.util.EnumSet;
import java.util.List;

import com.example.villagefightsback.FoundingManager;
import com.example.villagefightsback.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A founding settler's behaviour: scatter randomly for ~1s AWAY from the brain node (never toward it, so a
 * house footprint can't carve the brain node block), keeping at least 5 blocks from the other settlers, then
 * drop a Blue TNT (door facing the brain node) that self-detonates to build its house. Runs once; once the
 * TNT is placed the goal ends and the villager is free to become a wall builder. Added by {@link FoundingManager}.
 */
public class FoundingSettlerGoal extends Goal {
    private static final int SCATTER_TICKS = 20;   // ~1s
    private static final double SPACING = 5.0;     // keep this far from other settlers
    private static final double WANDER_SPEED = 1.15;
    private static final int SAFE_RADIUS = 6;      // keep settlers (and their house) at least this far from the brain node

    private final Villager villager;
    private final BlockPos brainNode;
    private int startTick;
    private boolean placed;

    public FoundingSettlerGoal(Villager villager, BlockPos brainNode) {
        this.villager = villager;
        this.brainNode = brainNode;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return !placed
                && villager.level() instanceof ServerLevel
                && villager.getPersistentData().getBoolean(FoundingManager.NBT_FOUNDING);
    }

    @Override
    public boolean canContinueToUse() {
        return !placed;
    }

    @Override
    public void start() {
        startTick = villager.tickCount;
    }

    @Override
    public void tick() {
        if (villager.tickCount - startTick < SCATTER_TICKS) {
            scatter();
        } else {
            placeTnt();
            placed = true;
        }
    }

    /** Random wander AWAY from the brain node (never toward it), pushing apart from nearby settlers. */
    private void scatter() {
        Villager other = nearestOtherSettler();
        if (other != null && villager.distanceToSqr(other) < SPACING * SPACING) {
            Vec3 away = villager.position().subtract(other.position());
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
            away = away.normalize().scale(6.0);
            moveTo(villager.blockPosition().offset((int) Math.round(away.x), 0, (int) Math.round(away.z)));
        } else if (villager.getNavigation().isDone()) {
            // a random direction in the hemisphere pointing AWAY from the brain node, so the house never
            // lands on it (a Blue TNT footprint would otherwise carve the brain node block).
            double awayAng = Math.atan2(villager.getZ() - brainNode.getZ(), villager.getX() - brainNode.getX());
            double ang = awayAng + (villager.getRandom().nextDouble() - 0.5) * Math.PI; // +/- 90 degrees from "away"
            int r = 3 + villager.getRandom().nextInt(4);
            moveTo(villager.blockPosition().offset((int) Math.round(Math.cos(ang) * r), 0, (int) Math.round(Math.sin(ang) * r)));
        }
    }

    private void moveTo(BlockPos t) {
        t = keepClearOfBrainNode(t);
        com.example.villagefightsback.NavThrottle.goTo(villager, t.getX() + 0.5, t.getY(), t.getZ() + 0.5, WANDER_SPEED);
    }

    /** Push a target out so it (and the house built there) stays at least SAFE_RADIUS from the brain node. */
    private BlockPos keepClearOfBrainNode(BlockPos t) {
        double dx = t.getX() - brainNode.getX();
        double dz = t.getZ() - brainNode.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d >= SAFE_RADIUS) return t;
        if (d < 1.0E-4) { dx = 1; dz = 0; d = 1; }
        double s = SAFE_RADIUS / d;
        return new BlockPos((int) Math.round(brainNode.getX() + dx * s), t.getY(), (int) Math.round(brainNode.getZ() + dz * s));
    }

    private Villager nearestOtherSettler() {
        List<Villager> others = villager.level().getEntitiesOfClass(Villager.class,
                villager.getBoundingBox().inflate(8.0),
                v -> v != villager && v.isAlive() && v.getPersistentData().getBoolean(FoundingManager.NBT_FOUNDING));
        Villager best = null;
        double bestSqr = Double.MAX_VALUE;
        for (Villager v : others) {
            double s = villager.distanceToSqr(v);
            if (s < bestSqr) { bestSqr = s; best = v; }
        }
        return best;
    }

    private void placeTnt() {
        ServerLevel level = (ServerLevel) villager.level();
        BlockPos pos = keepClearOfBrainNode(villager.blockPosition());
        // door faces the brain node (village centre)
        Direction face = Direction.getNearest(brainNode.getX() - pos.getX(), 0, brainNode.getZ() - pos.getZ());
        if (face.getAxis().isVertical()) face = villager.getDirection();
        BlockState tnt = ModBlocks.BLUE_TNT.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, face);
        level.setBlock(pos, tnt, 3); // detonates instantly on placement -> builds the house (no fuse, no fleeing)
    }
}
