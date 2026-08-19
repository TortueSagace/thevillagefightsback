package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.PlayerEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;

/** Forge's PlayerEvent.ItemCraftedEvent: a player taking the result out of a crafting grid. */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
    @Inject(method = "onTake", at = @At("HEAD"))
    private void tvfb$crafted(Player player, ItemStack stack, CallbackInfo ci) {
        EventBus.post(new PlayerEvent.ItemCraftedEvent(player, stack.copy()));
    }
}
