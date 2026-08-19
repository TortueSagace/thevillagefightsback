package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.LivingDestroyBlockEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Forge's LivingDestroyBlockEvent: a mob about to break a block of its own accord -- a ravager through leaves, a
 * wither through anything. Cancelling saves the block.
 */
@Mixin(Level.class)
public abstract class LevelDestroyBlockMixin {
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"), cancellable = true)
    private void tvfb$mobBreakingBlock(BlockPos pos, boolean dropBlock, Entity breaker, int recursion,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!(breaker instanceof LivingEntity living)) return;
        if (!EventBus.hasListeners(LivingDestroyBlockEvent.class)) return;
        Level self = (Level) (Object) this;
        LivingDestroyBlockEvent event =
                new LivingDestroyBlockEvent(living, pos, self.getBlockState(pos));
        if (EventBus.post(event).isCanceled()) {
            cir.setReturnValue(false);
        }
    }
}
