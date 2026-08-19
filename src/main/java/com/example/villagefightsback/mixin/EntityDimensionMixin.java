package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.EntityTravelToDimensionEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Forge's EntityTravelToDimensionEvent. The Limbo containment leash hangs off this, so it has to fire for every
 * crossing -- vanilla portals and the mod's own banishments alike, which both end up here.
 */
@Mixin(Entity.class)
public abstract class EntityDimensionMixin {
    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void tvfb$changingDimension(ServerLevel destination, CallbackInfoReturnable<Entity> cir) {
        EntityTravelToDimensionEvent event = new EntityTravelToDimensionEvent(
                (Entity) (Object) this, destination.dimension());
        if (EventBus.post(event).isCanceled()) {
            cir.setReturnValue(null);
        }
    }
}
