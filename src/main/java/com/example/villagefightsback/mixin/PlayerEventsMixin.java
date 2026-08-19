package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.TickEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;

/** Forge's PlayerTickEvent, fired either side of the player's own tick. */
@Mixin(Player.class)
public abstract class PlayerEventsMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void tvfb$tickStart(CallbackInfo ci) {
        if (EventBus.hasListeners(TickEvent.PlayerTickEvent.class)) {
            EventBus.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, (Player) (Object) this));
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void tvfb$tickEnd(CallbackInfo ci) {
        if (EventBus.hasListeners(TickEvent.PlayerTickEvent.class)) {
            EventBus.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, (Player) (Object) this));
        }
    }
}
