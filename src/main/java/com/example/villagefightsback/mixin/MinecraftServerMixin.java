package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.LevelEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;

/**
 * Forge's LevelEvent.CreateSpawnPosition, which fires exactly once in a world's life while its initial spawn
 * point is being chosen. Cancelling means the mod has set the spawn itself and vanilla should not.
 *
 * <p>The method lives on MinecraftServer, not on ServerLevel where Forge's event name suggests.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void tvfb$initialSpawn(ServerLevel level, ServerLevelData data, boolean generateBonusChest,
                                          boolean debug, CallbackInfo ci) {
        if (EventBus.post(new LevelEvent.CreateSpawnPosition(level)).isCanceled()) {
            ci.cancel();
        }
    }
}
