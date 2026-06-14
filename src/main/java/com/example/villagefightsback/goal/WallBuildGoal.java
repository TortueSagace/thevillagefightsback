package com.example.villagefightsback.goal;

import java.util.EnumSet;

import com.example.villagefightsback.BuilderRole;
import com.example.villagefightsback.ModBlocks;
import com.example.villagefightsback.wall.WallBlock;
import com.example.villagefightsback.wall.WallBuild;
import com.example.villagefightsback.wall.WallPlan;

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
 * Makes a builder villager build its village's BrainNode-planned wall, block by block, layer by layer.
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
    private static final int SKIP_TICKS = 200;         // skip an unreachable cell for ~10s, then retry
                                                       // (short, so the brain node's fallback seal kicks in ~3x faster)
    private static final int ABANDON_LIMIT = 3;        // give up the builder role after this many fails in a row
    private static final int IDLE_LIMIT = 100;         // ~5s in WAIT with no reachable cell => step down (never idle-tagged)
    private static final float TOUGH_HARDNESS = 25.0F; // at/above this hardness a block can't be dug (obsidian=50)

    // WAIT = tagged but between targets: we HOLD the villager (no vanilla wandering) and re-seek; APPROACH/
    // PILLAR/PLACE/DESCEND are the active build states.
    private enum Phase { WAIT, APPROACH, PILLAR, PLACE, DESCEND }

    private final Villager villager;
    private int consecutiveFails; // abandons in a row; at ABANDON_LIMIT the villager steps down

    private WallPlan plan;
    private WallBlock target;
    private BlockPos stand;       // exterior-adjacent column the villager works from (outside the wall ring)
    private int baseY;            // the wall's foundation Y at this column -- we never dig below it (no breach)
    private Phase phase;

    private int idleTicks;        // consecutive ticks spent in WAIT without acquiring a target
    private int scanTimer;        // throttle between target-acquisition scans while in WAIT
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
        if (fighting()) return false;                       // a threat -> BuilderDefenseGoal (prio 2) takes over
        if (!BuilderRole.isBuilder(villager)) return false; // only the tagged builders build
        if (!(level() instanceof ServerLevel sl)) return false;
        WallPlan p = WallBuild.planFor(sl, villager.blockPosition());
        if (p == null) { BuilderRole.demote(villager); return false; }      // not at any wall -> drop the tag
        if (p.isComplete()) { BuilderRole.demote(villager); return false; } // wall done -> drop the tag (any range)
        // Claim the villager for the WHOLE time it's a builder, so it never falls back to vanilla wandering
        // while tagged. The actual build target is (re-)acquired inside tick()'s WAIT handling.
        this.plan = p;
        this.target = null;
        this.phase = Phase.WAIT;
        this.idleTicks = 0;
        this.scanTimer = 0;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (fighting()) return false;
        if (!BuilderRole.isBuilder(villager)) return false;  // self-demoted (idle/complete) -> release the villager
        if (plan == null || plan.isComplete()) return false;
        if (phase == Phase.DESCEND) return descendTimer > 0; // keep climbing down
        if (phase == Phase.WAIT) return true;                // holding/seeking a target -- stay claimed, don't wander
        if (target == null) return false;
        if (digPos != null) return true;                     // finish the current dig before re-evaluating
        return !plan.satisfied(level(), target);             // built or blocked -> re-seek (handled via WAIT)
    }

    /** Try to claim the next reachable unbuilt cell. Returns true and enters APPROACH on success. */
    private boolean acquireTarget(ServerLevel sl) {
        long now = sl.getGameTime();
        // A cell is unavailable to builders if it's parked (briefly hidden), another builder owns it (live
        // reservation), OR it has been abandoned more than 3 times -- past that, builders give up on it for
        // good and the brain node's hole-sealer places it, so the whole crew never piles on one hard cell.
        java.util.function.Predicate<BlockPos> taken = pos ->
                WallBuild.isSkipped(plan.brainNode(), pos, now)
                || WallBuild.isReserved(plan.brainNode(), pos, now)
                || WallBuild.abandonCount(plan.brainNode(), pos) > 3;
        // Build MY section: from my door rightward to the next door, raising each column as a full pillar.
        int[] doors = plan.doorColumns();
        int from = 0;
        WallBlock t = null;
        if (doors.length > 0) {
            int d = Math.floorMod(BuilderRole.door(villager), doors.length);
            from = doors[d];
            t = plan.nextInSection(sl, from, doors[(d + 1) % doors.length], taken);
        }
        // My section is done (or there are no gates) -> help finish the rest of the wall.
        if (t == null) t = plan.nextAnyUnbuilt(sl, from, taken);
        if (t == null) return false;                                       // nothing buildable right now
        if (!WallBuild.reserve(plan.brainNode(), t.pos(), now)) return false; // lost the race; retry next scan
        this.target = t;
        this.stand = exteriorColumn(t.pos());
        this.baseY = t.pos().getY() - t.course(); // course 0 sits on the foundation; never dig below it
        this.phase = Phase.APPROACH;
        this.repathTimer = 0;
        this.stuckTimer = 0;
        this.lastDistSqr = Double.MAX_VALUE;
        this.pillarSteps = 0;
        this.bridgeSteps = 0;
        clearDig();
        com.example.villagefightsback.NavThrottle.goTo(villager, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, BUILD_SPEED);
        return true;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        villager.getNavigation().stop(); // begin in WAIT, planted; acquireTarget() will drive movement
    }

    @Override
    public void stop() {
        if (plan != null && target != null) WallBuild.release(plan.brainNode(), target.pos());
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
            WallBuild.refresh(plan.brainNode(), target.pos(), rsl.getGameTime());

        if (phase == Phase.WAIT) { tickWait(); return; }
        if (phase == Phase.DESCEND) { tickDescend(); return; }
        if (target == null) { phase = Phase.WAIT; return; }

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

    /**
     * Tagged, between targets: stand PUT (never wander) and periodically try to claim the next reachable cell.
     * If none can be found for {@link #IDLE_LIMIT} ticks, step down to a normal villager rather than linger
     * tagged-but-idle -- the village then refills the freed door from another villager.
     */
    private void tickWait() {
        villager.getNavigation().stop(); // hold position; the brain's WALK_TARGET is wiped in tick()
        idleTicks++;
        if (--scanTimer <= 0) {
            scanTimer = SELECT_THROTTLE;
            if (level() instanceof ServerLevel sl && acquireTarget(sl)) { idleTicks = 0; return; }
        }
        if (idleTicks >= IDLE_LIMIT) BuilderRole.demote(villager); // no reachable work -> drop the builder role
    }

    private void tickApproach() {
        BlockPos tp = target.pos();
        double d2 = villager.position().distanceToSqr(tp.getX() + 0.5, tp.getY() + 0.5, tp.getZ() + 0.5);
        if (d2 <= REACH_SQR) { // in reach -> PLACE (which digs the cell clear first if something's there)
            phase = Phase.PLACE;
            placeTimer = PLACE_DELAY;
            return;
        }
        double hx = villager.getX() - (stand.getX() + 0.5), hz = villager.getZ() - (stand.getZ() + 0.5);
        double horiz2 = hx * hx + hz * hz;
        boolean above = tp.getY() > villager.getBlockY() + 1;
        // Near the work column but the block is too high to reach -> scaffold UP to it right away. Don't wait
        // for the navigator to "finish" (it can never path to a mid-air node) and don't give up -- stacking a
        // couple of cheap scaffolds is exactly what's needed here.
        if (above && horiz2 <= 4.0) { phase = Phase.PILLAR; return; }

        if (--repathTimer <= 0) {
            // Navigate to the work column at our OWN height (a reachable ground node), not the block's height.
            com.example.villagefightsback.NavThrottle.goTo(villager, stand.getX() + 0.5, villager.getY(), stand.getZ() + 0.5, BUILD_SPEED);
            repathTimer = REPATH;
        }
        boolean stuck = villager.getNavigation().isDone();
        if (!stuck) {
            if (d2 < lastDistSqr - 0.05) { lastDistSqr = d2; stuckTimer = 0; return; } // progress
            if (++stuckTimer <= STUCK_TICKS) return;
            stuck = true;
        }
        // Stuck: scaffold UP to an above target; bridge a lava/void gap sideways; otherwise still try to
        // scaffold our way toward it (PILLAR builds stepping stones) before finally giving up -- so we
        // exhaust the cheap-scaffold options first instead of abandoning a block we just can't quite reach.
        if (above) {
            phase = Phase.PILLAR;
        } else if (bridgeSteps < MAX_BRIDGE && tryBridge()) {
            stuckTimer = 0;
            repathTimer = 0;
        } else if (pillarSteps < MAX_PILLAR) {
            phase = Phase.PILLAR;
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

        // Climb straight up when we're over (within 1 of) the target column: stack a cheap scaffold under our
        // feet and step onto it. We PREFER scaffolding over digging; only when the space above is solid (so we
        // can't rise any other way) do we dig it out as a last resort -- and never below the foundation.
        if (tp.getY() > fy && Math.abs(hx) <= 1 && Math.abs(hz) <= 1) {
            if (isSolid(fx, fy + 1, fz)) { blocked(new BlockPos(fx, fy + 1, fz)); return; }
            if (isSolid(fx, fy + 2, fz)) { blocked(new BlockPos(fx, fy + 2, fz)); return; }
            placeScaffold(new BlockPos(fx, fy, fz));
            villager.setPos(fx + 0.5, fy + 1, fz + 0.5);
            return;
        }

        // Otherwise move one block horizontally toward the target.
        Direction d = Math.abs(hx) >= Math.abs(hz)
                ? (hx >= 0 ? Direction.EAST : Direction.WEST)
                : (hz >= 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos step = new BlockPos(fx, fy, fz).relative(d);
        if (isSolid(step.getX(), step.getY(), step.getZ())) {
            // a one-block rise ahead -> STEP UP onto it (like jumping) if there's head room; else dig the
            // overhang out (last resort) rather than stay stuck.
            if (isSolid(step.getX(), fy + 1, step.getZ())) { blocked(new BlockPos(step.getX(), fy + 1, step.getZ())); return; }
            if (isSolid(step.getX(), fy + 2, step.getZ())) { blocked(new BlockPos(step.getX(), fy + 2, step.getZ())); return; }
            villager.setPos(step.getX() + 0.5, fy + 1, step.getZ() + 0.5);
            return;
        }
        // open cell ahead -> walk in; lay a scaffold floor if needed. Dig the head room only if it's blocked.
        if (isSolid(step.getX(), fy + 1, step.getZ())) { blocked(new BlockPos(step.getX(), fy + 1, step.getZ())); return; }
        if (!isStandable(step.below())) placeScaffold(step.below());
        villager.setPos(step.getX() + 0.5, fy, step.getZ() + 0.5);
    }

    private boolean isSolid(int x, int y, int z) {
        return level().getBlockState(new BlockPos(x, y, z)).blocksMotion();
    }

    /**
     * Blocked by a solid block we can't scaffold past: builders no longer dig anything (it tore the village
     * apart), so we simply give the cell up. The brainNode seals cells the builders keep failing to reach.
     */
    private void blocked(BlockPos blocker) {
        abandon();
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
            // A block is occupying the wall cell. Builders no longer dig, so we give it up rather than tear
            // anything down; the brain node seals cells the builders can't fill.
            abandon();
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
        if (plan != null && target != null) WallBuild.release(plan.brainNode(), target.pos());
        consecutiveFails = 0; // made progress -> not stuck
        descendY = groundBelow(stand);
        target = null;
        // If we built up high, prefer climbing back down our own scaffolds before seeking the next block.
        if (villager.getBlockY() - descendY > 3) {
            phase = Phase.DESCEND;
            descendTimer = DESCEND_TIMEOUT;
            repathTimer = 0;
        } else {
            phase = Phase.WAIT; // re-seek while staying claimed (no wander gap)
            idleTicks = 0;
            scanTimer = 0;
        }
    }

    private void tickDescend() {
        if (--descendTimer <= 0 || villager.getBlockY() <= descendY + 1) {
            phase = Phase.WAIT; // reached the bottom / timed out -> re-seek (stay claimed)
            idleTicks = 0;
            scanTimer = 0;
            return;
        }
        if (--repathTimer <= 0) {
            // Navigate to the column base; the cheap scaffolds we placed are climbable, so the path goes
            // down them rather than off the wall.
            com.example.villagefightsback.NavThrottle.goTo(villager, stand.getX() + 0.5, descendY, stand.getZ() + 0.5, BUILD_SPEED);
            repathTimer = REPATH;
        }
    }

    // --- tool breaking ---

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

    // --- bridging sideways over a lava / void gap ---

    /**
     * Cross a gap directly ahead toward the work column. Over a void we lay a cheap scaffold to walk on (with
     * a short support column below so it doesn't pop), so it cleans itself up afterwards; over LAVA we use
     * dirt instead (a cheap scaffold is flammable and would burn out from under us). Returns true if it did.
     */
    private boolean tryBridge() {
        Direction d = horizontalToward(stand);
        BlockPos ahead = villager.blockPosition().relative(d);
        BlockPos floor = ahead.below();
        BlockState aheadS = level().getBlockState(ahead);
        BlockState floorS = level().getBlockState(floor);
        if (aheadS.getFluidState().is(FluidTags.LAVA)) {           // lava at body level ahead -> dirt
            level().setBlock(ahead, Blocks.DIRT.defaultBlockState(), 3);
            bridgeSteps++;
            return true;
        }
        if (floorS.getFluidState().is(FluidTags.LAVA)) {           // lava to stand over -> dirt (fireproof)
            level().setBlock(floor, Blocks.DIRT.defaultBlockState(), 3);
            bridgeSteps++;
            return true;
        }
        if (floorS.canBeReplaced() && floorS.getFluidState().isEmpty()) { // empty gap -> disposable scaffold
            bridgeWithScaffold(floor);
            bridgeSteps++;
            return true;
        }
        return false; // a real (non-lava/void) obstacle -> can't bridge
    }

    /** Place a cheap scaffold to stand on, plus a short support column down to solid ground so scaffolding
     *  (which needs a base) doesn't immediately pop over the gap. */
    private void bridgeWithScaffold(BlockPos floor) {
        placeScaffold(floor);
        BlockPos p = floor.below();
        for (int i = 0; i < 8; i++) {
            BlockState s = level().getBlockState(p);
            if (s.blocksMotion()) break;        // reached a base -> the column above is now supported
            if (!s.canBeReplaced()) break;       // fluid / something else -> stop
            placeScaffold(p);
            p = p.below();
        }
    }

    private boolean placeable(BlockPos pos) {
        BlockState s = level().getBlockState(pos);
        return s.canBeReplaced()                          // air / water / short grass / ferns / snow layer / ...
                || s.getBlock() instanceof net.minecraft.world.level.block.BushBlock // flowers, saplings, crops, mushrooms, ...
                || s.is(net.minecraft.tags.BlockTags.LEAVES)   // build straight through overgrowth
                || s.is(ModBlocks.CHEAP_SCAFFOLD.get());  // our own disposable scaffold -> overwrite it
        // (still never bulldozes houses, terrain, bedrock -- those aren't replaceable/plants/leaves/scaffold)
    }

    private void abandon() {
        // We couldn't reach/finish this cell. Park it at the plan level so NO builder churns on it for a
        // while, and release our claim.
        if (target != null && plan != null) {
            WallBuild.skip(plan.brainNode(), target.pos(), level().getGameTime() + SKIP_TICKS);
            WallBuild.recordAbandon(plan.brainNode(), target.pos()); // count failures so the brain node can seal it
            WallBuild.release(plan.brainNode(), target.pos());
        }
        target = null;
        phase = Phase.WAIT; // re-seek a different cell while staying claimed (no wander gap)
        idleTicks = 0;
        scanTimer = 0;
        // Persistent failure (can't finish several cells in a row) -> drop the builder role so a villager
        // that CAN build takes over. A one-off (e.g. a momentary jostle while builders fan out at the start)
        // is tolerated; a successful placement resets the counter.
        if (++consecutiveFails >= ABANDON_LIMIT) BuilderRole.demote(villager);
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

    /**
     * The block one step toward the village EXTERIOR (away from the brain node) from a wall position. Builders
     * stand and work from OUTSIDE the wall ring, so they never path into / pillar through / dig up the
     * protected area (e.g. a house the brain node sits inside). All their destructive ops -- digging head room,
     * clearing the wall cell, dirt-bridging over gaps -- then happen on the outside field, not indoors.
     */
    private BlockPos exteriorColumn(BlockPos wall) {
        BlockPos gm = plan.brainNode().pos();
        int dx = gm.getX() - wall.getX();
        int dz = gm.getZ() - wall.getZ();
        Direction toward = Math.abs(dx) >= Math.abs(dz)
                ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        return wall.relative(toward.getOpposite()); // step OUTWARD, away from the brain node
    }
}
