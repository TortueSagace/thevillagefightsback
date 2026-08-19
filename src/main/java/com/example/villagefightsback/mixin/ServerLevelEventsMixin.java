package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.EntityJoinLevelEvent;
import com.example.villagefightsback.platform.event.ModEventSources;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Fires EntityJoinLevelEvent for an entity being added fresh, where cancelling can still keep it out. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelEventsMixin {
    /**
     * Forge's EntityJoinLevelEvent for a fresh spawn, where cancelling keeps the entity out. Entities coming
     * back off disk are announced separately, from {@link ModEventSources}, and cannot be refused.
     */
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void tvfb$entityJoining(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (EventBus.post(new EntityJoinLevelEvent(entity, self, false)).isCanceled()) {
            cir.setReturnValue(false);
            return;
        }
        // Tells the ENTITY_LOAD callback that this one has already been announced.
        ModEventSources.freshlyAdded = entity;
    }

    @Inject(method = "addEntity", at = @At("RETURN"))
    private void tvfb$entityJoined(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ModEventSources.freshlyAdded = null;
    }
}
