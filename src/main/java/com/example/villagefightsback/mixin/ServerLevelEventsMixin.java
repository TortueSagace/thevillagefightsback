package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.EntityJoinLevelEvent;
import com.example.villagefightsback.platform.event.EntityJoinTracking;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Fires EntityJoinLevelEvent, and marks which kind of arrival is in progress.
 *
 * <p>A plain spawn is announced here, at a point where a handler can still refuse it. The other fresh arrivals --
 * a teleport, a dimension change, a player joining or respawning, a worldgen mob -- cannot be refused, so they
 * are only marked, and Fabric's ENTITY_LOAD callback fires the event for them. Anything reaching that callback
 * unmarked came off disk. See {@link EntityJoinTracking} for why that distinction is load-bearing.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelEventsMixin {
    /** A plain spawn: the one arrival a handler can still refuse. */
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void tvfb$entityJoining(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (EventBus.post(new EntityJoinLevelEvent(entity, self, false)).isCanceled()) {
            cir.setReturnValue(false);
            return;
        }
        EntityJoinTracking.announced(entity);
    }

    @Inject(method = "addEntity", at = @At("RETURN"))
    private void tvfb$entityJoined(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        EntityJoinTracking.clearAnnounced(entity);
    }

    // --- fresh arrivals that cannot be refused, only marked -----------------------------------------------
    //
    // Each is a real arrival, not a chunk restoring what it already held, so the event must say so. Missing any
    // of these is not cosmetic: the emerald population cap and the obliteration check both skip disk loads, so a
    // mob that teleports in while wrongly flagged walks straight past both of them.

    /** A mob or player arriving from another dimension. This is the Limbo banish and return path. */
    @Inject(method = "addDuringTeleport", at = @At("HEAD"))
    private void tvfb$teleportArrivalStart(Entity entity, CallbackInfo ci) {
        EntityJoinTracking.pushFreshArrival();
    }

    @Inject(method = "addDuringTeleport", at = @At("RETURN"))
    private void tvfb$teleportArrivalEnd(Entity entity, CallbackInfo ci) {
        EntityJoinTracking.popFreshArrival();
    }

    /** Every way a player enters a level: joining, respawning, a command teleport, a portal. */
    @Inject(method = "addPlayer", at = @At("HEAD"))
    private void tvfb$playerArrivalStart(ServerPlayer player, CallbackInfo ci) {
        EntityJoinTracking.pushFreshArrival();
    }

    @Inject(method = "addPlayer", at = @At("RETURN"))
    private void tvfb$playerArrivalEnd(ServerPlayer player, CallbackInfo ci) {
        EntityJoinTracking.popFreshArrival();
    }

    /** Mobs placed by world generation as a chunk is first built. New, not restored. */
    @Inject(method = "addWorldGenChunkEntities", at = @At("HEAD"))
    private void tvfb$worldGenArrivalStart(Stream<Entity> entities, CallbackInfo ci) {
        EntityJoinTracking.pushFreshArrival();
    }

    @Inject(method = "addWorldGenChunkEntities", at = @At("RETURN"))
    private void tvfb$worldGenArrivalEnd(Stream<Entity> entities, CallbackInfo ci) {
        EntityJoinTracking.popFreshArrival();
    }
}
