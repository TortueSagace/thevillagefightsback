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
    private static final int BLOCKS_PER_TICK = 3; // ~5x slower build: a ~190-block house finishes in ~63 ticks (~3s)

    private static final List<BuildJob> JOBS = new ArrayList<>();

    // Pending TNT detonations: the build is captured as a closure at PLACEMENT time and run on the next
    // server tick. This is reliable where a scheduled block tick is not -- it fires even in a non-ticking
    // chunk, and even if a neighbouring TNT's footprint already carved this TNT's block away (which silently
    // dropped its scheduled tick before, so some TNTs "did nothing").
    private static final List<PendingTnt> DETONATIONS = new ArrayList<>();
    private record PendingTnt(ServerLevel level, BlockPos pos, Runnable detonate) {}

    /** Queue a TNT's detonation (sound/particles + build) to run next server tick. */
    public static void detonateNextTick(ServerLevel level, BlockPos pos, Runnable detonate) {
        DETONATIONS.add(new PendingTnt(level, pos.immutable(), detonate));
    }

    /** One planned block placement. {@code deferred} = an attachment/sensitive block placed in the last pass. */
    public record Placement(BlockPos pos, BlockState state, CompoundTag nbt, boolean deferred) {}

    /** Queue an ordered list of placements (+ decorative entity NBTs placed last) to animate into {@code level}. */
    public static void submit(ServerLevel level, List<Placement> order, ResourceLocation loot, List<CompoundTag> entities) {
        if (!order.isEmpty() || !entities.isEmpty()) JOBS.add(new BuildJob(level, order, loot, entities));
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
        if (event.phase != TickEvent.Phase.END) return;
        // Fire any pending TNT detonations first (each carves a footprint + queues a build job below).
        if (!DETONATIONS.isEmpty()) {
            List<PendingTnt> due = new ArrayList<>(DETONATIONS);
            DETONATIONS.clear();
            for (PendingTnt d : due) {
                if (!d.level().hasChunkAt(d.pos())) continue; // chunk unloaded in the meantime -> skip
                try {
                    d.detonate().run();
                } catch (Throwable t) {
                    VillagerGunners.LOGGER.error("[Go Back To Your World] TNT detonation failed at {}", d.pos(), t);
                }
            }
        }
        if (JOBS.isEmpty()) return;
        Iterator<BuildJob> it = JOBS.iterator();
        while (it.hasNext()) {
            if (it.next().step()) it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
        DETONATIONS.clear();
    }

    private static final class BuildJob {
        private final ServerLevel level;
        private final List<Placement> order;
        private final ResourceLocation loot;
        private final List<CompoundTag> entities;
        private int cursor;

        BuildJob(ServerLevel level, List<Placement> order, ResourceLocation loot, List<CompoundTag> entities) {
            this.level = level;
            this.order = order;
            this.loot = loot;
            this.entities = entities;
        }

        /** Place up to BLOCKS_PER_TICK blocks; returns true when the job is finished (or failed). */
        boolean step() {
            try {
                if (cursor < order.size()) {
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
                    return false; // still placing blocks
                }
                placeEntities(); // all blocks up -> add the decorative entities (paintings, ...) last
                return true;
            } catch (Throwable t) {
                VillagerGunners.LOGGER.error("[Go Back To Your World] house build animation aborted", t);
                return true;
            }
        }

        /** Spawn the structure's decorative entities once the building is fully up (walls support paintings). */
        private void placeEntities() {
            for (CompoundTag nbt : entities) {
                try {
                    net.minecraft.world.entity.Entity e =
                            net.minecraft.world.entity.EntityType.loadEntityRecursive(nbt, level, x -> x);
                    if (e != null) level.addFreshEntity(e);
                } catch (Throwable t) {
                    VillagerGunners.LOGGER.error("[Go Back To Your World] failed to place a structure entity", t);
                }
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
