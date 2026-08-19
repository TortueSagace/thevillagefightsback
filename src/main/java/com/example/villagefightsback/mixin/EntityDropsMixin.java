package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.DropCapture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Diverts a dying entity's drops into the list the LivingDropsEvent is fired with.
 *
 * <p>Every death drop -- loot table, equipment, custom -- goes through {@code spawnAtLocation}, so that is where
 * they are intercepted. Outside the death window the flag is down and this does nothing.
 */
@Mixin(Entity.class)
public abstract class EntityDropsMixin {
    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void tvfb$captureDrop(ItemStack stack, float yOffset,
                                  CallbackInfoReturnable<ItemEntity> cir) {
        if (!(((Object) this) instanceof DropCapture capture)) return;
        List<ItemEntity> captured = capture.tvfbCapturedDrops();
        if (captured == null) return;
        Entity self = (Entity) (Object) this;
        if (stack.isEmpty()) {
            cir.setReturnValue(null);
            return;
        }
        ItemEntity item = new ItemEntity(self.level(), self.getX(), self.getY() + yOffset, self.getZ(), stack);
        item.setDefaultPickUpDelay();
        captured.add(item);
        cir.setReturnValue(item);
    }
}
