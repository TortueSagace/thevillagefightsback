package com.example.villagergunners.goal;

import java.util.EnumSet;

import com.example.villagergunners.wall.WallBlock;
import com.example.villagergunners.wall.WallBuild;
import com.example.villagergunners.wall.WallPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundSource;

/**
 * Makes a non-fighting villager build its village's Gravemind-planned wall, block by block, layer by layer.
 * The villager claims the nearest unbuilt block of the lowest incomplete course (reserved via
 * {@link WallBuild} so builders don't collide), walks to it, and places it. To reach high courses it relies
 * on the navigator climbing the wall's own ladders; where that fails it pillars straight up with temporary
 * scaffolding like a player would. Priority 3 (below fight/flee/rally), so any of those preempt building.
 */
public class WallBuildGoal extends Goal {
    private static final int SELECT_THROTTLE = 10;     // ticks between target re-selection scans
    private static final double BUILD_SPEED = 1.0;
    private static final int REPATH = 10;              // ticks between re-paths while approaching
    private static final double REACH_SQR = 4.5 * 4.5; // close enough (to the target centre) to place
    private static final int STUCK_TICKS = 50;         // no approach progress for this long => stuck
    private static final int MAX_PILLAR = 12;          // scaffold-tower height cap before giving up
    private static final int PLACE_DELAY = 8;          // brief "working" pause before a block appears
    private static final float TOUGH_HARDNESS = 25.0F; // blocks at/above this hardness can't be dug (obsidian=50)

    private enum Phase { APPROACH, PILLAR, PLACE }

    private final Villager villager;

    private WallPlan plan;
    private WallBlock target;
    private BlockPos stand;       // interior-adjacent column the villager works from
    private Phase phase;

    private int repathTimer;
    private int stuckTimer;
    private double lastDistSqr;
    private int pillarSteps;
    private int placeTimer;

    public WallBuildGoal(Villager villager) {
        this.villager = villager;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean fighting() {
        return villager.getTarget() != null && villager.getTarget().isAlive();
    }

    private Level level() { return villager.level(); }

    @Override
    public boolean canUse() {
        if (fighting()) return false;
        if (villager.tickCount % SELECT_THROTTLE != 0) return false;
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return false;
        long now = sl.getGameTime();
        WallPlan p = WallBuild.planFor(sl, villager.blockPosition());
        if (p == null) return false;
        WallBlock t = p.nextUnbuiltFor(sl, villager.blockPosition(),
                pos -> WallBuild.isReserved(p.gravemind(), pos, now));
        if (t == null) return false;
        if (!WallBuild.reserve(p.gravemind(), t.pos(), now)) return false;
        this.plan = p;
        this.target = t;
        this.stand = interiorColumn(t.pos());
        this.phase = Phase.APPROACH;
        this.repathTimer = 0;
        this.stuckTimer = 0;
        this.lastDistSqr = Double.MAX_VALUE;
        this.pillarSteps = 0;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (fighting() || plan == null || target == null || plan.isComplete()) return false;
        return !plan.satisfied(level(), target); // finished or blocked -> re-seek
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        villager.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, BUILD_SPEED);
    }

    @Override
    public void stop() {
        if (plan != null && target != null) WallBuild.release(plan.gravemind(), target.pos());
        villager.getNavigation().stop();
        plan = null;
        target = null;
        stand = null;
    }

    @Override
    public void tick() {
        if (target == null) return;
        // Villagers are brain-driven: keep them awake and stop the brain from overriding our navigation.
        if (villager.isSleeping()) villager.stopSleeping();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        long now = level().getGameTime();
        WallBuild.refresh(plan.gravemind(), target.pos(), now);
        villager.getLookControl().setLookAt(
                target.pos().getX() + 0.5, target.pos().getY() + 0.5, target.pos().getZ() + 0.5);

        switch (phase) {
            case APPROACH -> tickApproach();
            case PILLAR -> tickPillar();
            case PLACE -> tickPlace();
        }
    }

    private void tickApproach() {
        double d2 = villager.position().distanceToSqr(
                target.pos().getX() + 0.5, target.pos().getY() + 0.5, target.pos().getZ() + 0.5);
        if (d2 <= REACH_SQR && placeable(target.pos())) {
            phase = Phase.PLACE;
            placeTimer = PLACE_DELAY;
            return;
        }
        if (--repathTimer <= 0) {
            villager.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, BUILD_SPEED);
            repathTimer = REPATH;
        }
        boolean elevationNeeded = target.pos().getY() > villager.getBlockY() + 1;
        if (villager.getNavigation().isDone()) {              // path ended but we're not in reach
            if (elevationNeeded) phase = Phase.PILLAR; else abandon();
            return;
        }
        if (d2 < lastDistSqr - 0.05) { lastDistSqr = d2; stuckTimer = 0; }   // making progress
        else if (++stuckTimer > STUCK_TICKS) {
            if (elevationNeeded) phase = Phase.PILLAR; else abandon();
        }
    }

    private void tickPillar() {
        // Make sure we are standing over the interior column before stacking up.
        double hx = villager.getX() - (stand.getX() + 0.5);
        double hz = villager.getZ() - (stand.getZ() + 0.5);
        if (hx * hx + hz * hz > 1.2) {
            villager.getNavigation().moveTo(stand.getX() + 0.5, villager.getY(), stand.getZ() + 0.5, BUILD_SPEED);
            return;
        }
        villager.getNavigation().stop();

        int feetY = villager.getBlockY();
        if (feetY >= target.pos().getY() - 1) {   // high enough to reach the target by hand
            phase = Phase.PLACE;
            placeTimer = PLACE_DELAY;
            return;
        }
        if (pillarSteps++ >= MAX_PILLAR) { abandon(); return; }

        BlockPos feet = new BlockPos(stand.getX(), feetY, stand.getZ());
        BlockPos head = feet.above();      // the villager's head now / where the next scaffold goes
        BlockPos newHead = feet.above(2);  // the villager's head AFTER stepping up -- must be clear

        // A block above can stop the tower from growing. Dig it out -- unless it's very tough (obsidian,
        // bedrock, ...), in which case there's no way up here, so give up and pick another target.
        if (tooToughToDig(head) || tooToughToDig(newHead)) { abandon(); return; }
        digIfNeeded(head);
        digIfNeeded(newHead);

        if (level().getBlockState(feet).canBeReplaced()) {
            level().setBlock(feet, Blocks.SCAFFOLDING.defaultBlockState(), 3);
            plan.addScaffold(feet);
        }
        // Step up onto the scaffold we just placed (a reliable, player-like climb).
        villager.setPos(stand.getX() + 0.5, feetY + 1, stand.getZ() + 0.5);
    }

    /** Break a solid block that blocks the pillar climb (no drops). No-op if already passable. */
    private void digIfNeeded(BlockPos pos) {
        if (!level().getBlockState(pos).canBeReplaced()) level().destroyBlock(pos, false);
    }

    /** True if a solid block here is too hard to dig through (obsidian, bedrock, reinforced deepslate, ...). */
    private boolean tooToughToDig(BlockPos pos) {
        BlockState s = level().getBlockState(pos);
        if (s.canBeReplaced()) return false; // passable -> not a blocker at all
        float hardness = s.getDestroySpeed(level(), pos);
        return hardness < 0 || hardness >= TOUGH_HARDNESS;
    }

    private void tickPlace() {
        if (placeTimer-- > 0) return;
        if (!plan.isBuilt(level(), target) && placeable(target.pos())) {
            level().setBlock(target.pos(), target.toState(), 3);
            SoundType s = SoundType.STONE;
            level().playSound(null, target.pos(), s.getPlaceSound(), SoundSource.BLOCKS,
                    (s.getVolume() + 1.0F) / 2.0F, s.getPitch() * 0.9F);
        }
        WallBuild.release(plan.gravemind(), target.pos());
        target = null; // -> canContinueToUse() false -> re-seek next canUse()
    }

    /** A cell we may place a wall block into: empty/replaceable (never bulldoze houses, bedrock, ...). */
    private boolean placeable(BlockPos pos) {
        BlockState s = level().getBlockState(pos);
        return s.canBeReplaced();
    }

    private void abandon() {
        if (plan != null && target != null) WallBuild.release(plan.gravemind(), target.pos());
        target = null; // canContinueToUse() will end the goal; canUse() picks a fresh target later
    }

    /** The block one step toward the village interior (toward the gravemind) from a wall position. */
    private BlockPos interiorColumn(BlockPos wall) {
        BlockPos gm = plan.gravemind().pos();
        int dx = gm.getX() - wall.getX();
        int dz = gm.getZ() - wall.getZ();
        Direction in = Math.abs(dx) >= Math.abs(dz)
                ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        return wall.relative(in);
    }
}
