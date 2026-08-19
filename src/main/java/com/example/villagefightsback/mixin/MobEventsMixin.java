package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.LivingChangeTargetEvent;
import com.example.villagefightsback.platform.event.MobSpawnEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;

/** Fires the two mob events: taking a new target, and a spawn being finalised. */
@Mixin(Mob.class)
public abstract class MobEventsMixin {
    @Shadow
    public abstract void setTarget(LivingEntity target);

    /** Up while a handler's replacement target is being applied, so the event does not fire on its own change. */
    @Unique
    private boolean tvfb$reenteringSetTarget;

    /**
     * Forge's LivingChangeTargetEvent. Cancelling leaves the old target alone, which is what stops the village's
     * own units turning on each other; a handler may also swap the target outright, which means cancelling
     * vanilla's pass and setting the replacement instead.
     */
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void tvfb$changeTarget(LivingEntity target, CallbackInfo ci) {
        if (this.tvfb$reenteringSetTarget) return;
        LivingChangeTargetEvent event =
                EventBus.post(new LivingChangeTargetEvent((Mob) (Object) this, target));
        if (event.isCanceled()) {
            ci.cancel();
            return;
        }
        if (event.getNewTarget() != target) {
            ci.cancel();
            this.tvfb$reenteringSetTarget = true;
            try {
                this.setTarget(event.getNewTarget());
            } finally {
                this.tvfb$reenteringSetTarget = false;
            }
        }
    }

    /**
     * Forge's MobSpawnEvent.FinalizeSpawn. A handler that calls the spawn off gets the mob discarded, so it
     * never reaches the world -- the closest thing to Forge refusing the spawn outright.
     */
    @Inject(method = "finalizeSpawn", at = @At("HEAD"), cancellable = true)
    private void tvfb$finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                    SpawnGroupData data, CompoundTag tag,
                                    CallbackInfoReturnable<SpawnGroupData> cir) {
        Mob self = (Mob) (Object) this;
        MobSpawnEvent.FinalizeSpawn event =
                EventBus.post(new MobSpawnEvent.FinalizeSpawn(self, level, reason));
        if (event.isSpawnCancelled() || event.isCanceled()) {
            self.discard();
            cir.setReturnValue(data);
        }
    }
}
