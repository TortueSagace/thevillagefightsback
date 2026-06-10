package com.example.villagergunners;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives the "rising house" build animation: instead of pasting a house at once, the blocks are placed
 * a handful per server tick. Blocks are placed structure-first (sorted bottom-up) and attachment /
 * gravity-sensitive blocks (torches, lanterns, ladders, beds, doors, ...) LAST, so their support always
 * exists before they appear. All placements use no-neighbour-update flags, so nothing ever pops off.
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HouseBuildManager {
    private HouseBuildManager() {}

    /** UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE: visible to clients, but no neighbour updates -> no pops. */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int BLOCKS_PER_TICK = 14; // "quick" build: a ~190-block house finishes in ~14 ticks

    private static final List<BuildJob> JOBS = new ArrayList<>();

    /** One planned block placement. {@code deferred} = an attachment/sensitive block placed in the last pass. */
    public record Placement(BlockPos pos, BlockState state, CompoundTag nbt, boolean deferred) {}

    /** Queue an ordered list of placements to animate into {@code level}. */
    public static void submit(ServerLevel level, List<Placement> order, ResourceLocation loot) {
        if (!order.isEmpty()) JOBS.add(new BuildJob(level, order, loot));
    }

    /** Blocks that hang on / sit atop a neighbour (or fall) -> place them after the structure is up. */
    public static boolean isDeferred(BlockState s) {
        Block b = s.getBlock();
        return b instanceof TorchBlock || b instanceof LanternBlock || b instanceof LadderBlock
                || b instanceof BellBlock
                || b instanceof BedBlock || b instanceof DoorBlock || b instanceof BaseRailBlock
                || b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof BasePressurePlateBlock
                || b instanceof SignBlock || b instanceof AbstractBannerBlock || b instanceof VineBlock
                || b instanceof BushBlock || b instanceof CarpetBlock || b instanceof SnowLayerBlock
                || b instanceof FallingBlock || b instanceof ScaffoldingBlock || b instanceof ChainBlock
                || b instanceof RedStoneWireBlock || b instanceof FlowerPotBlock;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) return;
        Iterator<BuildJob> it = JOBS.iterator();
        while (it.hasNext()) {
            if (it.next().step()) it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
    }

    private static final class BuildJob {
        private final ServerLevel level;
        private final List<Placement> order;
        private final ResourceLocation loot;
        private int cursor;

        BuildJob(ServerLevel level, List<Placement> order, ResourceLocation loot) {
            this.level = level;
            this.order = order;
            this.loot = loot;
        }

        /** Place up to BLOCKS_PER_TICK blocks; returns true when the job is finished (or failed). */
        boolean step() {
            try {
                int end = Math.min(cursor + BLOCKS_PER_TICK, order.size());
                for (; cursor < end; cursor++) {
                    Placement p = order.get(cursor);
                    level.setBlock(p.pos(), p.state(), FLAGS);
                    if (p.nbt() != null) loadBlockEntity(p);
                    if (loot != null && level.getBlockEntity(p.pos()) instanceof RandomizableContainerBlockEntity c) {
                        c.setLootTable(loot, level.getRandom().nextLong());
                        c.setChanged();
                    }
                    if ((cursor & 3) == 0) { // a soft building tick every 4th block
                        level.playSound(null, p.pos(), p.state().getSoundType().getPlaceSound(),
                                SoundSource.BLOCKS, 0.4F, 0.9F + (cursor % 5) * 0.05F);
                    }
                }
                return cursor >= order.size();
            } catch (Throwable t) {
                VillagerGunners.LOGGER.error("[Go Back To Your World] house build animation aborted", t);
                return true;
            }
        }

        private void loadBlockEntity(Placement p) {
            BlockEntity be = level.getBlockEntity(p.pos());
            if (be == null) return;
            CompoundTag tag = p.nbt().copy();
            tag.putInt("x", p.pos().getX());
            tag.putInt("y", p.pos().getY());
            tag.putInt("z", p.pos().getZ());
            be.load(tag);
            be.setChanged();
        }
    }
}
