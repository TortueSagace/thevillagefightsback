package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Builds a Defender Golem from blocks, mirroring vanilla iron-golem assembly: place a Wall Defender where the
 * carved pumpkin would go -- on top of the T of iron blocks (a vertical body + leg with two arms on one
 * horizontal axis) -- and the four iron blocks + the wall defender turn into a {@link DefenderGolemEntity}.
 *
 * Triggered from both build orders: when the wall defender appears last (it joins onto a completed T) and when
 * an iron block is placed last (a wall defender is already hovering above the now-complete T). The
 * village-spawned wall defenders sit on protecting glass / nodes, never on an iron T, so they never trip this.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DefenderGolemForming {
    private DefenderGolemForming() {}

    /** A wall defender just appeared -> if it stands at the head of a completed iron T, form a Defender Golem
     *  and cancel its join (it is consumed into the golem). */
    @SubscribeEvent
    public static void onWallDefenderJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof WallDefenderEntity wd && wd.level() instanceof ServerLevel sl) {
            if (tryForm(sl, wd)) event.setCanceled(true);
        }
    }

    /** An iron block was placed -> a wall defender already hovering above a now-complete T forms a golem. */
    @SubscribeEvent
    public static void onIronBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!event.getPlacedBlock().is(Blocks.IRON_BLOCK)) return;
        BlockPos at = event.getPos();
        for (WallDefenderEntity wd : sl.getEntitiesOfClass(WallDefenderEntity.class, new AABB(at).inflate(3.0))) {
            if (tryForm(sl, wd)) break;
        }
    }

    private static boolean isIron(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.IRON_BLOCK);
    }

    /**
     * If {@code wd} sits at the head position of a vanilla iron-golem T of iron blocks, consume the four iron
     * blocks + the wall defender and spawn a Defender Golem standing where the structure was. Returns true if a
     * golem was formed.
     */
    private static boolean tryForm(ServerLevel level, WallDefenderEntity wd) {
        if (!wd.isAlive()) return false;
        BlockPos headPos = wd.blockPosition();   // where the carved pumpkin would sit
        BlockPos body = headPos.below();
        if (!isIron(level, body)) return false;
        BlockPos leg = body.below();
        if (!isIron(level, leg)) return false;
        BlockPos armA, armB;
        if (isIron(level, body.east()) && isIron(level, body.west())) {
            armA = body.east(); armB = body.west();
        } else if (isIron(level, body.north()) && isIron(level, body.south())) {
            armA = body.north(); armB = body.south();
        } else {
            return false;
        }

        // Clear the four iron blocks (no drops), with break particles -- like vanilla golem assembly.
        clear(level, body);
        clear(level, leg);
        clear(level, armA);
        clear(level, armB);

        // Spawn the Defender Golem standing on the former leg block.
        DefenderGolemEntity golem = ModEntities.DEFENDER_GOLEM.get().create(level);
        if (golem != null) {
            golem.moveTo(leg.getX() + 0.5, leg.getY() + 0.05, leg.getZ() + 0.5, 0.0F, 0.0F);
            golem.finalizeSpawn(level, level.getCurrentDifficultyAt(leg), MobSpawnType.TRIGGERED, null, null);
            level.addFreshEntity(golem);
        }
        wd.discard();

        // Neighbour updates for the now-empty columns.
        level.blockUpdated(body, Blocks.AIR);
        level.blockUpdated(leg, Blocks.AIR);
        level.blockUpdated(armA, Blocks.AIR);
        level.blockUpdated(armB, Blocks.AIR);
        return true;
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        BlockState old = level.getBlockState(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        level.levelEvent(2001, pos, Block.getId(old)); // block-break particles + sound
    }
}
