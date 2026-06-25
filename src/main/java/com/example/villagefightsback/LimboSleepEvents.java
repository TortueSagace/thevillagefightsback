package com.example.villagefightsback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * In the Limbo, trying to sleep in ANY ordinary bed acts exactly like using a Limbo Bed: it sends the sleeper
 * back to where they were pulled in from (see {@link LimboManager#requestReturn}). The Limbo Bed keeps its own
 * handling (see {@link LimboBedBlock}); this covers every other bed, and -- by cancelling the click before the
 * vanilla bed logic runs -- also stops a normal bed from exploding in a dimension where beds don't work.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LimboSleepEvents {
    private LimboSleepEvents() {}

    @SubscribeEvent
    public static void onRightClickBed(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return; // once per click, not twice
        Level level = event.getLevel();
        if (level.isClientSide()) return;
        if (!LimboDimension.LIMBO.equals(level.dimension())) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        var block = level.getBlockState(event.getPos()).getBlock();
        if (!(block instanceof BedBlock) || block instanceof LimboBedBlock) return; // Limbo Bed handles itself
        event.setCanceled(true);                              // stop the vanilla sleep / bed explosion
        event.setCancellationResult(InteractionResult.SUCCESS);
        LimboManager.requestReturn(sp);
    }
}
