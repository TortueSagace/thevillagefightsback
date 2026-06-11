package com.example.villagergunners.goal;

import java.util.EnumSet;

import com.example.villagergunners.GravemindData;
import com.example.villagergunners.infection.BlockMatcher;
import com.example.villagergunners.infection.InfectionData;
import com.example.villagergunners.infection.MinerRole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Miner villager (gravemind level 2). Roams for rich (emerald-bearing) infection blocks tracked in
 * {@link InfectionData}, digs the nearest with the correct IRON tool, then carries the emerald in hand and
 * runs it to the gravemind (fed within 3 blocks -> +1 emerald). Drops the emerald if it dies mid-run. Can
 * dive underwater to mine (water path malus cleared + a swim nudge).
 */
public class MinerGoal extends Goal {
    private static final int SELECT_THROTTLE = 10;
    private static final double SEARCH_RANGE = 48.0;     // look for rich blocks within this (roam across the zone)
    private static final double REACH_SQR = 3.2 * 3.2;   // close enough to dig
    private static final double FEED_RANGE_SQR = 3.0 * 3.0;
    private static final double GRAVEMIND_RANGE = 160.0; // search this far for a gravemind to feed
    private static final double SPEED = 0.95;            // miners hustle
    private static final int REPATH = 10;

    private static final int CARRY_TIMEOUT = 1200; // 60s to deliver; else drop the emerald and resume hunting

    // Anti-stuck: a rich block the miner can't reach within SEEK_TIMEOUT ticks is blacklisted for
    // BLACKLIST_TICKS so it picks a DIFFERENT reachable block instead of pathing at it forever.
    private static final int SEEK_TIMEOUT = 160;   // ~8s of trying to reach a target before giving up on it
    private static final long BLACKLIST_TICKS = 600L; // ~30s before that block is reconsidered

    private final Villager villager;
    private boolean carrying;          // holding an emerald to deliver
    private int carryTicks;            // how long we've carried (anti-deadlock)
    private BlockPos target;           // rich block being sought
    private int seekTicks;             // how long we've been trying to reach the current target
    private final java.util.HashMap<Long, Long> avoid = new java.util.HashMap<>(); // unreachable rich -> expiry
    private BlockPos gravemind;        // delivery target
    private int repathTimer;

    // progressive dig (mirrors WallBuildGoal)
    private BlockPos digPos;
    private int digTicks, digTotal;

    public MinerGoal(Villager villager) {
        this.villager = villager;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private Level level() { return villager.level(); }
    private boolean fighting() { return villager.getTarget() != null && villager.getTarget().isAlive(); }

    @Override
    public boolean canUse() {
        if (fighting() || !MinerRole.isMiner(villager)) return false;
        if (!(level() instanceof ServerLevel sl)) return false;
        if (carrying) return true;
        if (target != null) return true;
        if (villager.tickCount % SELECT_THROTTLE != 0) return false;
        pruneAvoid(sl.getGameTime());
        BlockPos r = InfectionData.get(sl).nearestRich(villager.blockPosition(), SEARCH_RANGE, avoid.keySet());
        if (r != null) { target = r; seekTicks = 0; return true; }
        return false;
    }

    /** Forget blacklisted rich blocks whose cool-down has elapsed so they can be tried again. */
    private void pruneAvoid(long now) {
        avoid.values().removeIf(expiry -> expiry <= now);
    }

    @Override
    public boolean canContinueToUse() {
        if (fighting() || !MinerRole.isMiner(villager)) return false;
        return carrying || target != null || digPos != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        villager.setPathfindingMalus(BlockPathTypes.WATER, 0.0F); // let miners path through / dive into water
        if (!carrying) equipTool(Items.IRON_PICKAXE);
    }

    @Override
    public void stop() {
        villager.getNavigation().stop();
        clearDig();
        // keep the emerald if carrying; it must reach the gravemind or drop on death.
    }

    @Override
    public void tick() {
        if (villager.isSleeping()) villager.stopSleeping();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (!(level() instanceof ServerLevel sl)) return;

        if (digPos != null) { tickDig(sl); return; }
        if (carrying) { tickDeliver(sl); return; }

        InfectionData inf = InfectionData.get(sl);
        avoid.values().removeIf(exp -> exp <= sl.getGameTime()); // let blacklisted blocks become eligible again

        // 1. Mine any rich block right next to us FIRST -- never walk past one we could already dig.
        BlockPos here = villager.blockPosition();
        for (BlockPos n : BlockPos.betweenClosed(here.offset(-1, -2, -1), here.offset(1, 2, 1))) {
            if (BlockMatcher.isRich(sl.getBlockState(n))) {
                avoid.remove(n.asLong());          // clearly reachable -> un-blacklist
                target = n.immutable();
                seekTicks = 0;
                startDig(target);
                return;
            }
        }

        // 2. Make sure we have a still-valid target; otherwise pick the nearest non-blacklisted rich block.
        if (target != null && !BlockMatcher.isRich(sl.getBlockState(target))) {
            inf.removeRich(target.asLong());
            target = null;
        }
        if (target == null || avoid.containsKey(target.asLong())) {
            target = inf.nearestRich(here, SEARCH_RANGE, avoid.keySet());
            seekTicks = 0;
            if (target == null) return;            // nothing minable right now -> let normal AI roam; canUse retries
        }

        // 3. Pursue. Dig when in reach; if we can't make progress, blacklist it and re-pick NEXT tick (never idle).
        Vec3 c = Vec3.atCenterOf(target);
        villager.getLookControl().setLookAt(c.x, c.y, c.z);
        if (villager.position().distanceToSqr(c) <= REACH_SQR) { seekTicks = 0; startDig(target); return; }
        navigate(target);
        boolean unreachable = villager.getNavigation().isDone() && seekTicks > 10; // navigator finished but not in reach
        if (++seekTicks > SEEK_TIMEOUT || unreachable) {
            avoid.put(target.asLong(), sl.getGameTime() + BLACKLIST_TICKS);
            villager.getNavigation().stop();
            target = null;
            seekTicks = 0;
        }
    }

    private void tickDeliver(ServerLevel sl) {
        if (++carryTicks > CARRY_TIMEOUT) { dropEmerald(sl); return; } // can't deliver -> drop it, resume hunting
        if (gravemind == null || !isGravemind(sl, gravemind)) gravemind = findGravemind(sl);
        if (gravemind == null) return; // nowhere to deliver yet; keep carrying (until the timeout above)
        Vec3 c = Vec3.atCenterOf(gravemind);
        villager.getLookControl().setLookAt(c.x, c.y, c.z);
        if (villager.position().distanceToSqr(c) <= FEED_RANGE_SQR) {
            // Feed: +1 emerald to this village's economy, consume the held emerald.
            GravemindData data = GravemindData.get(sl.getServer());
            data.econ(GlobalPos.of(sl.dimension(), gravemind.immutable())).addEmeralds(1);
            sl.playSound(null, villager.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.6F, 1.4F);
            carrying = false;
            carryTicks = 0;
            gravemind = null;
            equipTool(Items.IRON_PICKAXE);
        } else {
            navigate(gravemind);
        }
    }

    /** Couldn't reach any gravemind in time: drop the emerald in the world (not lost) and go back to mining. */
    private void dropEmerald(ServerLevel sl) {
        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                sl, villager.getX(), villager.getY() + 0.5, villager.getZ(), new ItemStack(Items.EMERALD));
        sl.addFreshEntity(drop);
        carrying = false;
        carryTicks = 0;
        gravemind = null;
        equipTool(Items.IRON_PICKAXE);
    }

    // --- navigation (with an underwater swim nudge) ---
    private void navigate(BlockPos to) {
        if (--repathTimer <= 0) {
            villager.getNavigation().moveTo(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5, SPEED);
            repathTimer = REPATH;
        }
        if (villager.isInWater() || villager.isUnderWater()) {
            Vec3 dir = Vec3.atCenterOf(to).subtract(villager.position());
            if (dir.lengthSqr() > 1.0E-4) {
                dir = dir.normalize().scale(0.05);
                villager.setDeltaMovement(villager.getDeltaMovement().add(dir.x, dir.y + 0.01, dir.z));
            }
        }
    }

    // --- digging ---
    private void startDig(BlockPos pos) {
        digPos = pos.immutable();
        digTicks = 0;
        BlockState s = level().getBlockState(pos);
        equipTool(toolFor(s));
        float hardness = s.getDestroySpeed(level(), pos);
        if (hardness <= 0) { digTotal = 1; return; }
        ItemStack tool = villager.getMainHandItem();
        boolean canHarvest = !s.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(s);
        float perTick = tool.getDestroySpeed(s) / hardness / (canHarvest ? 30.0F : 100.0F);
        digTotal = Math.max(1, (int) Math.ceil(1.0F / perTick));
    }

    private void tickDig(ServerLevel sl) {
        BlockState s = sl.getBlockState(digPos);
        if (!BlockMatcher.isRich(s)) { // gone / no longer rich
            InfectionData.get(sl).removeRich(digPos.asLong());
            clearDig();
            target = null;
            return;
        }
        digTicks++;
        int stage = (int) Math.min(9, (digTicks * 10L) / Math.max(1, digTotal));
        sl.destroyBlockProgress(villager.getId(), digPos, stage);
        villager.swing(InteractionHand.MAIN_HAND);
        villager.getLookControl().setLookAt(digPos.getX() + 0.5, digPos.getY() + 0.5, digPos.getZ() + 0.5);
        if (digTicks >= digTotal) {
            InfectionData id = InfectionData.get(sl);
            id.sanction(digPos.asLong(), sl.getGameTime()); // our own dig is NOT an attack
            id.removeRich(digPos.asLong());
            id.decConverted();                              // frees the footprint cap so infection can regrow
            sl.destroyBlock(digPos, false);                 // remove without vanilla drops; we hand over the emerald
            villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.EMERALD));
            villager.setDropChance(EquipmentSlot.MAINHAND, 1.0F); // drop the emerald if killed mid-run
            carrying = true;
            carryTicks = 0;
            target = null;
            clearDig();
        }
    }

    private void clearDig() {
        if (digPos != null && level() instanceof ServerLevel sl) sl.destroyBlockProgress(villager.getId(), digPos, -1);
        digPos = null;
    }

    private void equipTool(net.minecraft.world.item.Item item) {
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
        villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private static net.minecraft.world.item.Item toolFor(BlockState s) {
        if (s.is(BlockTags.MINEABLE_WITH_AXE)) return Items.IRON_AXE;
        if (s.is(BlockTags.MINEABLE_WITH_SHOVEL)) return Items.IRON_SHOVEL;
        return Items.IRON_PICKAXE;
    }

    // --- gravemind lookup ---
    private boolean isGravemind(ServerLevel sl, BlockPos pos) {
        return GravemindData.get(sl.getServer()).blocks()
                .contains(GlobalPos.of(sl.dimension(), pos.immutable()));
    }

    private BlockPos findGravemind(ServerLevel sl) {
        BlockPos from = villager.blockPosition();
        BlockPos best = null;
        double bestD = GRAVEMIND_RANGE * GRAVEMIND_RANGE;
        for (GlobalPos gp : GravemindData.get(sl.getServer()).blocks()) {
            if (!gp.dimension().equals(sl.dimension())) continue;
            double d = gp.pos().distSqr(from);
            if (d <= bestD) { bestD = d; best = gp.pos(); }
        }
        return best;
    }
}
