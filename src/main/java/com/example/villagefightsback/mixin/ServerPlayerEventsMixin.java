package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.PlayerContainerEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;

/** Forge's PlayerContainerEvent.Close. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEventsMixin {
    @Inject(method = "doCloseContainer", at = @At("HEAD"))
    private void tvfb$containerClosed(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        EventBus.post(new PlayerContainerEvent.Close(self, self.containerMenu));
    }
}
