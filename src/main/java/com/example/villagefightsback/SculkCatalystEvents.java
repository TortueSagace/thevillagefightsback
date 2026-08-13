package com.example.villagefightsback;

import java.util.ArrayDeque;
import java.util.HashSet;

import com.example.villagefightsback.infection.BlockMatcher;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Our fertile / rich sculk catalyst. Like vanilla, when a mob dies within range of a catalyst it "blooms"
 * and spreads sculk over the ground around the death -- except it spreads our fertile (or rich) sculk. The
 * spread reuses {@link BlockMatcher#isCandidate} so it respects the same preserved blocks as the infection
 * (walls, chests, beds, job sites, bookshelves, food, ...).
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SculkCatalystEvents {
    private SculkCatalystEvents() {}

    private static final int ACTIVATE_R = 8;   // a death this far from a catalyst triggers it (vanilla parity)
    private static final int MIN_SPREAD = 4, MAX_SPREAD = 24; // blocks placed per bloom, scaled by mob size
    private static final Direction[] HORIZ = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level instanceof ServerLevel sl)) return;
        BlockPos death = event.getEntity().blockPosition();
        Block fertile = ModBlocks.FERTILE_SCULK_CATALYST.get(), rich = ModBlocks.RICH_SCULK_CATALYST.get();

        // Nearest catalyst within range decides whether (and which variant) to bloom.
        BlockPos best = null;
        boolean bestRich = false;
        double bestD = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -ACTIVATE_R; dx <= ACTIVATE_R; dx++)
            for (int dy = -ACTIVATE_R; dy <= ACTIVATE_R; dy++)
                for (int dz = -ACTIVATE_R; dz <= ACTIVATE_R; dz++) {
                    m.set(death.getX() + dx, death.getY() + dy, death.getZ() + dz);
                    if (!sl.isLoaded(m)) continue;
                    BlockState s = sl.getBlockState(m);
                    boolean isF = s.is(fertile), isR = s.is(rich);
                    if (!isF && !isR) continue;
                    double d = m.distSqr(death);
                    if (d < bestD) { bestD = d; best = m.immutable(); bestRich = isR; }
                }
        if (best == null) return;

        int budget = Math.max(MIN_SPREAD, Math.min(MAX_SPREAD, (int) event.getEntity().getMaxHealth()));
        if (spread(sl, death, bestRich, budget) > 0) {
            sl.playSound(null, best, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /** Creep our sculk outward over the ground from {@code center}, up to {@code budget} blocks. */
    private static int spread(ServerLevel level, BlockPos center, boolean rich, int budget) {
        Block sculk = BlockMatcher.sculk(rich);
        if (sculk == null) return 0;
        BlockState target = sculk.defaultBlockState();

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<Long> seen = new HashSet<>();
        BlockPos start = floorAt(level, center);
        if (start != null) queue.add(start);
        int placed = 0, guard = 0;
        while (!queue.isEmpty() && placed < budget && guard++ < 4096) {
            BlockPos p = queue.poll();
            if (!seen.add(p.asLong())) continue;
            BlockState s = level.getBlockState(p);
            if (BlockMatcher.isWoodenDoor(s)) continue;           // don't leave a half-door behind
            if (!BlockMatcher.isCandidate(level, p, s)) continue; // same preserved-block rules as the infection
            // The bloom has no range gate of its own, so a death on a doorstep could sculk the floor inside.
            if (com.example.villagefightsback.infection.StructureZones.protectedAt(level, p)) continue;
            level.setBlock(p, target, 3);
            placed++;
            for (Direction dir : HORIZ) {
                BlockPos nf = floorAt(level, p.relative(dir));
                if (nf != null && !seen.contains(nf.asLong())) queue.add(nf);
            }
        }
        return placed;
    }

    /** The surface block near {@code p} (within +/-1 in Y) whose top is open, or null -- where sculk can sit. */
    private static BlockPos floorAt(ServerLevel level, BlockPos p) {
        for (int dy = 1; dy >= -1; dy--) {
            BlockPos g = p.above(dy);
            if (!level.isLoaded(g)) continue;
            BlockState s = level.getBlockState(g);
            if (s.isAir() || !s.getFluidState().isEmpty() || !s.getMaterial().blocksMotion()) continue;
            BlockState above = level.getBlockState(g.above());
            if (above.isAir() || above.getMaterial().isReplaceable()) return g;
        }
        return null;
    }
}
