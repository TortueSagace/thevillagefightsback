package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.ExplosionEvent;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

/**
 * Forge's ExplosionEvent.Detonate, fired just before the blast is applied to the world.
 *
 * <p>The block list is the live one, so a handler removing a position really does spare that block -- which is
 * what the mod uses it for. The entity list is empty here: vanilla has already dealt its entity damage by this
 * point, and nothing in the mod reads it.
 */
@Mixin(Explosion.class)
public abstract class ExplosionEventsMixin {
    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private ObjectArrayList<BlockPos> toBlow;

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void tvfb$detonate(boolean spawnParticles, CallbackInfo ci) {
        if (!EventBus.hasListeners(ExplosionEvent.Detonate.class)) return;
        EventBus.post(new ExplosionEvent.Detonate(this.level, (Explosion) (Object) this,
                this.toBlow, new ArrayList<>()));
    }
}
