package com.example.villagefightsback.mixin;

import java.util.ArrayList;
import java.util.List;

import com.example.villagefightsback.platform.UncurableEffects;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Keeps the effects that are not supposed to be curable through a milk bucket or a totem.
 *
 * <p>Both go through {@code removeAllEffects}, so the marked ones are set aside on the way in and put back on the
 * way out. See {@link UncurableEffects} for which ones and why.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityRemoveAllEffectsMixin {
    @Unique
    private final List<MobEffectInstance> tvfb$kept = new ArrayList<>();

    @Inject(method = "removeAllEffects", at = @At("HEAD"))
    private void tvfb$keepUncurable(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        this.tvfb$kept.clear();
        for (MobEffectInstance instance : self.getActiveEffects()) {
            if (UncurableEffects.isUncurable(instance.getEffect())) {
                this.tvfb$kept.add(new MobEffectInstance(instance));
            }
        }
    }

    @Inject(method = "removeAllEffects", at = @At("RETURN"))
    private void tvfb$restoreUncurable(CallbackInfoReturnable<Boolean> cir) {
        if (this.tvfb$kept.isEmpty()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        for (MobEffectInstance instance : this.tvfb$kept) {
            self.addEffect(instance);
        }
        this.tvfb$kept.clear();
    }
}
