package com.example.villagergunners.goal;

import java.util.EnumSet;

import com.example.villagergunners.BuilderRole;
import com.example.villagergunners.ModBlocks;
import com.example.villagergunners.wall.WallBlock;
import com.example.villagergunners.wall.WallBuild;
import com.example.villagergunners.wall.WallPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Makes a builder villager build its village's Gravemind-planned wall, block by block, layer by layer.
 * The villager claims the nearest unbuilt block of the lowest incomplete course (reserved via
 * {@link WallBuild} so builders don't collide), walks to it, and places it. To reach high courses it relies
 * on the navigator climbing the wall's own ladders; where that fails it pillars up with temporary
 * {@link ModBlocks#CHEAP_SCAFFOLD cheap scaffolds} and then climbs back down them. When a block is in the
 * way it equips the right stone tool and breaks it at that tool's speed (like a player); when lava or a void
 * blocks its path sideways it bridges across with dirt. Priority 3 (below fight/flee/rally), and only the
 * tagged builders run it.
 */
public class WallBuildGoal extends Goal {
    private static final int SELECT_THROTTLE = 10;     // ticks between target re-selection scans
    private static final double BUILD_SPEED = 1.0;
    private static final int REPATH = 10;              // ticks between re-paths
    private static final double REACH_SQR = 4.5 * 4.5; // close enough (to the target centre) to place
    private static final int STUCK_TICKS = 50;         // no approach progress for this long => stuck
    private static final int MAX_PILLAR = 16;          // scaffold-tower height cap before giving up
    private static final int MAX_BRIDGE = 8;           // dirt-bridge length cap before giving up
    private static final int PLACE_DELAY = 8;          // brief "working" pause before a block appears
    private static final int DESCEND_TIMEOUT = 80;     // ticks to spend climbing down before moving on
    private static final int SKIP_TICKS = 600;         // skip an unreachable cell for ~30s, then retry
    private static final float TOUGH_HARDNESS = 25.0F; // at/above this hardness a block can't be dug (obsidian=50)

    private enum Phase { APPROACH, PILLAR, PLACE, DESCEND }

    private final Villager villager;
    private final java.util.Map<Long, Long> skipUntil = new java.util.HashMap<>(); // cells we couldn't reach

    private WallPlan plan;
    private WallBlock target;
    private BlockPos stand;       // interior-adjacent column the villager works from
    private Phase phase;

    private int repathTimer;
    private int stuckTimer;
    private double lastDistSqr;
    private int pillarSteps;
    private int bridgeSteps;
    private int placeTimer;
    private int descendTimer;
    private int descendY;

    // progressive tool breaking
    private BlockPos digPos;
    private int digTicks;
    private int digTotal;

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
        if (!BuilderRole.isBuilder(villager)) return false; // only the tagged builders build
        if (villager.tickCount % SELECT_THROTTLE != 0) return false;
        if (!(level() instanceof ServerLevel sl)) return false;
        WallPlan p = WallBuild.planFor(sl, villager.blockPosition());
        if (p == null || p.isComplete()) return false;
        int course = BuilderRole.course(villager);
        long now = sl.getGameTime();
        // A cell is unavailable if we recently gave up on it (our own skip) OR another builder owns it
        // (live reservation) -- so the six builders never all pile on the same block and collide.
        java.util.function.Predicate<BlockPos> taken = pos -> {
            Long u = skipUntil.get(pos.asLong());
            if (u != null && now < u) return true;
            return WallBuild.isReserved(p.gravemind(), pos, now);
        };
        // Build my own layer where it already has support (the course below is built); support-gating makes
        // each layer follow the one beneath. If my layer has no ready cell yet, help the lowest unfinished
        // layer instead of idling, so no builder ever stands idle while the wall is incomplete.
        WallBlock t = p.nextForCourse(sl, course, taken);
        if (t == null) t = p.nextAnyCourse(sl, taken);
        if (t == null) return false; // nothing free for me right now
        if (!WallBuild.reserve(p.gravemind(), t.pos(), now)) return false; // lost the race this tick; retry soon
        this.plan = p;
        this.target = t;
        this.stand = interiorColumn(t.pos());
        this.phase = Phase.APPROACH;
        this.repathTimer = 0;
        this.stuckTimer = 0;
        this.lastDistSqr = Double.MAX_VALUE;
        this.pillarSteps = 0;
        this.bridgeSteps = 0;
        clearDig();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (fighting() || plan == null) return false;
        if (phase == Phase.DESCEND) return descendTimer > 0; // keep climbing down
        if (target == null || plan.isComplete()) return false;
        if (digPos != null) return true;                     // finish the current dig before re-evaluating
        return !plan.satisfied(level(), target);             // built or blocked -> re-seek
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
        clearDig();
        unequipTool();
        plan = null;
        target = null;
        stand = null;
    }

    @Override
    public void tick() {
        // Villagers are brain-driven: keep them awake and stop the brain from overriding our navigation.
        if (villager.isSleeping()) villager.stopSleeping();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        // Hold our claim on the target so no other builder grabs it mid-build.
        if (target != null && plan != null && level() instanceof ServerLevel rsl)
            WallBuild.refresh(plan.gravemind(), target.pos(), rsl.getGameTime());

        if (phase == Phase.DESCEND) { tickDescend(); return; }
        if (target == null) return;

        if (digPos != null) { tickDig(); return; } // a progressive break takes precedence

        villager.getLookControl().setLookAt(
                target.pos().getX() + 0.5, target.pos().getY() + 0.5, target.pos().getZ() + 0.5);

        switch (phase) {
            case APPROACH -> tickApproach();
            case PILLAR -> tickPillar();
            case PLACE -> tickPlace();
            default -> { }
        }
    }

    private void tickApproach() {
        BlockPos tp = target.pos();
        double d2 = villager.position().distanceToSqr(tp.getX() + 0.5, tp.getY() + 0.5, tp.getZ() + 0.5);
        if (d2 <= REACH_SQR) { // in reach -> PLACE (which digs the cell clear first if something's there)
            phase = Phase.PLACE;
            placeTimer = PLACE_DELAY;
            return;
        }
        // As soon as we're standing at the work column but the block is too high to reach, scaffold up --
        // don't wait around for the navigator to "finish" (it never can reach a node up in the air).
        double hx = villager.getX() - (stand.getX() + 0.5), hz = villager.getZ() - (stand.getZ() + 0.5);
        boolean atColumn = hx * hx + hz * hz <= 2.0;
        boolean above = tp.getY() > villager.getBlockY() + 1;
        if (atColumn && above) { phase = Phase.PILLAR; return; }

        if (--repathTimer <= 0) {
            // Navigate to the work column at our OWN height (a reachable ground node), not the block's height.
            villager.getNavigation().moveTo(stand.getX() + 0.5, villager.getY(), stand.getZ() + 0.5, BUILD_SPEED);
            repathTimer = REPATH;
        }
        boolean stuck = villager.getNavigation().isDone();
        if (!stuck) {
            if (d2 < lastDistSqr - 0.05) { lastDistSqr = d2; stuckTimer = 0; return; } // progress
            if (++stuckTimer <= STUCK_TICKS) return;
            stuck = true;
        }
        // Stuck: climb (pillar) if the target is above; bridge with dirt over lava/void sideways; else give up.
        if (above) {
            phase = Phase.PILLAR;
        } else if (bridgeSteps < MAX_BRIDGE && tryBridge()) {
            stuckTimer = 0;
            repathTimer = 0;
        } else {
            abandon();
        }
    }

    private void tickPillar() {
        villager.getNavigation().stop();
        BlockPos tp = target.pos();
        double d2 = villager.position().distanceToSqr(tp.getX() + 0.5, tp.getY() + 0.5, tp.getZ() + 0.5);
        if (d2 <= REACH_SQR && placeable(tp)) { // close enough now -> place
            phase = Phase.PLACE;
            placeTimer = PLACE_DELAY;
            return;
        }
        if (pillarSteps++ >= MAX_PILLAR) { abandon(); return; }

        int fx = villager.getBlockX(), fy = villager.getBlockY(), fz = villager.getBlockZ();
        int hx = tp.getX() - fx, hz = tp.getZ() - fz;

        // Climb straight up only when we're already over (within 1 of) the target column; otherwise we'd
        // pillar in the wrong place. Scaffolds are always placed AT/NEXT TO us so we can actually reach them.
        if (tp.getY() > fy && Math.abs(hx) <= 1 && Math.abs(hz) <= 1) {
            BlockPos feet = new BlockPos(fx, fy, fz);
            if (clearBlocker(feet.above()) || clearBlocker(feet.above(2))) return; // dig head room first
            placeScaffold(feet);
            villager.setPos(fx + 0.5, fy + 1, fz + 0.5); // step up onto it
            return;
        }

        // Step one block horizontally toward the target, laying a stepping-stone scaffold beside us.
        Direction d = Math.abs(hx) >= Math.abs(hz)
                ? (hx >= 0 ? Direction.EAST : Direction.WEST)
                : (hz >= 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos step = new BlockPos(fx, fy, fz).relative(d);
        if (clearBlocker(step) || clearBlocker(step.above())) return; // clear the cell + head room
        if (!isStandable(step.below())) placeScaffold(step.below()); // floor to walk onto
        villager.setPos(step.getX() + 0.5, fy, step.getZ() + 0.5);
    }

    private void placeScaffold(BlockPos pos) {
        if (level().getBlockState(pos).canBeReplaced()) {
            level().setBlock(pos, ModBlocks.CHEAP_SCAFFOLD.get().defaultBlockState(), 3);
            plan.addScaffold(pos);
        }
    }

    private boolean isStandable(BlockPos pos) {
        BlockState s = level().getBlockState(pos);
        return s.blocksMotion() || s.is(ModBlocks.CHEAP_SCAFFOLD.get());
    }

    private void tickPlace() {
        if (placeTimer-- > 0) return;
        BlockPos tp = target.pos();
        if (plan.isBuilt(level(), target)) { finishTarget(); return; } // already built (us or another builder)
        if (!placeable(tp)) {
            // A block is occupying the wall cell -> break it with the right stone tool (or hand) first,
            // unless it's too tough to manage; then we'll re-enter and place once it's clear.
            if (tooToughToDig(tp)) { abandon(); return; }
            startDig(tp);
            return;
        }
        level().setBlock(tp, target.toState(), 3);
        SoundType s = SoundType.STONE;
        level().playSound(null, tp, s.getPlaceSound(), SoundSource.BLOCKS,
                (s.getVolume() + 1.0F) / 2.0F, s.getPitch() * 0.9F);
        finishTarget();
    }

    /** Done with the current target (built or given up): then descend our scaffolds or re-seek. */
    private void finishTarget() {
        if (plan != null && target != null) WallBuild.release(plan.gravemind(), target.pos());
        descendY = groundBelow(stand);
        target = null;
        // If we built up high, prefer climbing back down our own scaffolds before seeking the next block.
        if (villager.getBlockY() - descendY > 3) {
            phase = Phase.DESCEND;
            descendTimer = DESCEND_TIMEOUT;
            repathTimer = 0;
        } else {
            phase = Phase.APPROACH; // target null -> goal ends -> re-seek
        }
    }

    private void tickDescend() {
        if (--descendTimer <= 0 || villager.getBlockY() <= descendY + 1) {
            phase = Phase.APPROACH; // reached the bottom / timed out -> goal ends -> re-seek
            return;
        }
        if (--repathTimer <= 0) {
            // Navigate to the column base; the cheap scaffolds we placed are climbable, so the path goes
            // down them rather than off the wall.
            villager.getNavigation().moveTo(stand.getX() + 0.5, descendY, stand.getZ() + 0.5, BUILD_SPEED);
            repathTimer = REPATH;
        }
    }

    // --- tool breaking ---

    /** If a blocker is here, start breaking it (or give up if it's too tough). Returns true to pause pillaring. */
    private boolean clearBlocker(BlockPos p) {
        BlockState s = level().getBlockState(p);
        if (s.canBeReplaced()) return false;
        if (tooToughToDig(p)) { abandon(); return true; }
        startDig(p);
        return true;
    }

    private void startDig(BlockPos pos) {
        digPos = pos.immutable();
        digTicks = 0;
        BlockState s = level().getBlockState(pos);
        villager.setItemSlot(EquipmentSlot.MAINHAND, toolFor(s));
        villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        float hardness = s.getDestroySpeed(level(), pos);
        if (hardness <= 0) { digTotal = 1; return; }
        ItemStack tool = villager.getMainHandItem();
        boolean canHarvest = !s.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(s);
        float perTick = tool.getDestroySpeed(s) / hardness / (canHarvest ? 30.0F : 100.0F);
        digTotal = Math.max(1, (int) Math.ceil(1.0F / perTick)); // player-like dig time
    }

    private void tickDig() {
        BlockState s = level().getBlockState(digPos);
        if (s.canBeReplaced()) { finishDig(); return; } // already gone
        digTicks++;
        int stage = (int) Math.min(9, (digTicks * 10L) / Math.max(1, digTotal));
        level().destroyBlockProgress(villager.getId(), digPos, stage);
        villager.swing(InteractionHand.MAIN_HAND);
        villager.getLookControl().setLookAt(digPos.getX() + 0.5, digPos.getY() + 0.5, digPos.getZ() + 0.5);
        if (digTicks >= digTotal) {
            level().destroyBlock(digPos, true); // break it, dropping as a player would
            finishDig();
        }
    }

    private void finishDig() {
        clearDig();
        unequipTool();
    }

    private void clearDig() {
        if (digPos != null) level().destroyBlockProgress(villager.getId(), digPos, -1);
        digPos = null;
    }

    private void unequipTool() {
        if (!villager.getMainHandItem().isEmpty()) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    private ItemStack toolFor(BlockState s) {
        if (s.is(BlockTags.MINEABLE_WITH_AXE)) return new ItemStack(Items.STONE_AXE);
        if (s.is(BlockTags.MINEABLE_WITH_SHOVEL)) return new ItemStack(Items.STONE_SHOVEL);
        return new ItemStack(Items.STONE_PICKAXE); // pickaxe-mineable and everything else
    }

    /** True if a solid block here is too hard to dig through (obsidian, bedrock, reinforced deepslate, ...). */
    private boolean tooToughToDig(BlockPos pos) {
        BlockState s = level().getBlockState(pos);
        if (s.canBeReplaced()) return false;
        float hardness = s.getDestroySpeed(level(), pos);
        return hardness < 0 || hardness >= TOUGH_HARDNESS;
    }

    // --- dirt bridging (sideways, over lava / void) ---

    /** Lay a dirt block to cross lava or a void gap directly ahead toward the work column. Returns true if it did. */
    private boolean tryBridge() {
        Direction d = horizontalToward(stand);
        BlockPos ahead = villager.blockPosition().relative(d);
        BlockPos floor = ahead.below();
        BlockState aheadS = level().getBlockState(ahead);
        BlockState floorS = level().getBlockState(floor);
        if (aheadS.getFluidState().is(FluidTags.LAVA)) {           // lava at body level ahead
            level().setBlock(ahead, Blocks.DIRT.defaultBlockState(), 3);
            bridgeSteps++;
            return true;
        }
        boolean voidFloor = floorS.canBeReplaced() && floorS.getFluidState().isEmpty(); // nothing to stand on
        if (voidFloor || floorS.getFluidState().is(FluidTags.LAVA)) {
            level().setBlock(floor, Blocks.DIRT.defaultBlockState(), 3);
            bridgeSteps++;
            return true;
        }
        return false; // a real (non-lava/void) obstacle -> can't bridge
    }

    private boolean placeable(BlockPos pos) {
        return level().getBlockState(pos).canBeReplaced(); // never bulldoze houses, bedrock, ...
    }

    private void abandon() {
        // Skip an unreachable/too-tough cell for a while so we don't spin on it; canUse() then picks the next
        // cell of our course, and this one is retried once the skip lapses. Release the claim so another
        // builder may try it.
        if (target != null) {
            skipUntil.put(target.pos().asLong(), level().getGameTime() + SKIP_TICKS);
            if (plan != null) WallBuild.release(plan.gravemind(), target.pos());
        }
        target = null;
    }

    /** Standable Y at a column: the first block (below the villager) that blocks motion and isn't a scaffold. */
    private int groundBelow(BlockPos col) {
        Level lvl = level();
        int y = villager.getBlockY();
        for (int i = 0; i < 64 && y > lvl.getMinBuildHeight(); i++, y--) {
            BlockState s = lvl.getBlockState(new BlockPos(col.getX(), y - 1, col.getZ()));
            if (s.blocksMotion() && !s.is(ModBlocks.CHEAP_SCAFFOLD.get())) return y;
        }
        return y;
    }

    private Direction horizontalToward(BlockPos goal) {
        int dx = goal.getX() - villager.getBlockX();
        int dz = goal.getZ() - villager.getBlockZ();
        return Math.abs(dx) >= Math.abs(dz)
                ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
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
