package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.BlockEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Forge's BlockEvent.EntityPlaceEvent, for a block placed from an item -- by a player or a dispenser, which is
 * every case the mod cares about.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemPlaceMixin {
    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void tvfb$placed(BlockPlaceContext context, BlockState state,
                             CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        EventBus.post(new BlockEvent.EntityPlaceEvent(context.getLevel(), context.getClickedPos(),
                state, context.getLevel().getBlockState(context.getClickedPos().below()),
                context.getPlayer()));
    }
}
