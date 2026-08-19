package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.PersistentDataHolder;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * Gives every entity the persistent scratch tag Forge provides, saved and loaded with the entity.
 *
 * <p>The tag is stored under "ForgeData", which is not an accident: it is the key the Forge build writes, so a
 * world can move between the two builds without a villager forgetting that it is a builder or a golem forgetting
 * its stage.
 */
@Mixin(Entity.class)
public abstract class EntityPersistentDataMixin implements PersistentDataHolder {
    @Unique
    private CompoundTag tvfb$persistentData;

    @Override
    public CompoundTag getPersistentData() {
        if (this.tvfb$persistentData == null) {
            this.tvfb$persistentData = new CompoundTag();
        }
        return this.tvfb$persistentData;
    }

    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void tvfb$save(CompoundTag tag, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<CompoundTag> cir) {
        if (this.tvfb$persistentData != null && !this.tvfb$persistentData.isEmpty()) {
            cir.getReturnValue().put("ForgeData", this.tvfb$persistentData);
        }
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void tvfb$load(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("ForgeData", 10)) {
            this.tvfb$persistentData = tag.getCompound("ForgeData");
        }
    }
}
