package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.DropCapture;
import com.example.villagefightsback.platform.EventBus;
import com.example.villagefightsback.platform.event.LivingAttackEvent;
import com.example.villagefightsback.platform.event.LivingDropsEvent;
import com.example.villagefightsback.platform.event.LivingEquipmentChangeEvent;
import com.example.villagefightsback.platform.event.LivingEvent;
import com.example.villagefightsback.platform.event.LivingExperienceDropEvent;
import com.example.villagefightsback.platform.event.LivingHurtEvent;
import com.example.villagefightsback.platform.event.MobEffectEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Fires the living-entity events Forge has and Fabric does not.
 *
 * <p>The hurt hook is the one that has to both call the damage off AND change how much it is, which is why it
 * wraps the whole method rather than injecting into it: Fabric's own damage callback can only vote yes or no,
 * and half the mod's combat maths is in the amount.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEventsMixin implements DropCapture {
    @Shadow
    protected abstract void actuallyHurt(DamageSource source, float amount);

    @Unique
    private List<ItemEntity> tvfb$capturedDrops;

    /** Up while the hurt hook is re-entering actuallyHurt with a changed amount, so it does not fire twice. */
    @Unique
    private boolean tvfb$reenteringHurt;

    @Unique
    private LivingEntity tvfb$self() {
        return (LivingEntity) (Object) this;
    }

    // --- damage ---------------------------------------------------------------------------------------------

    /**
     * Forge's LivingHurtEvent: damage past the shield and invulnerability checks, still adjustable.
     *
     * <p>This is the one hook that has to be able to both call the damage off and change how much it is, which is
     * why it cannot be Fabric's own damage callback (that one only votes yes or no). Changing the amount means
     * cancelling vanilla's pass and re-entering with the new figure, so a flag keeps the second pass from firing
     * the event again.
     */
    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void tvfb$livingHurt(DamageSource source, float amount, CallbackInfo ci) {
        if (this.tvfb$reenteringHurt) return;
        LivingHurtEvent event = EventBus.post(new LivingHurtEvent(tvfb$self(), source, amount));
        if (event.isCanceled()) {
            ci.cancel();
            return;
        }
        if (event.getAmount() != amount) {
            ci.cancel();
            this.tvfb$reenteringHurt = true;
            try {
                this.actuallyHurt(source, event.getAmount());
            } finally {
                this.tvfb$reenteringHurt = false;
            }
        }
    }

    /** Forge's LivingAttackEvent: the first moment of a hit. Cancelling drops it entirely. */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void tvfb$livingAttack(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (EventBus.post(new LivingAttackEvent(tvfb$self(), source, amount)).isCanceled()) {
            cir.setReturnValue(false);
        }
    }

    // --- tick -----------------------------------------------------------------------------------------------

    @Inject(method = "tick", at = @At("HEAD"))
    private void tvfb$livingTick(CallbackInfo ci) {
        if (EventBus.hasListeners(LivingEvent.LivingTickEvent.class)) {
            EventBus.post(new LivingEvent.LivingTickEvent(tvfb$self()));
        }
    }

    // --- drops ----------------------------------------------------------------------------------------------

    /**
     * Forge's LivingDropsEvent hands handlers the actual list of drop entities before any of them reach the
     * world, so they can add to it and take from it. Nothing in vanilla collects them, so they are caught on the
     * way out: {@code spawnAtLocation} diverts into this list while the flag is up (see {@link EntityDropsMixin}),
     * the event is fired with it, and whatever survives is then spawned for real.
     */
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void tvfb$captureDrops(DamageSource source, CallbackInfo ci) {
        if (EventBus.hasListeners(LivingDropsEvent.class)) {
            this.tvfb$capturedDrops = new ArrayList<>();
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
    private void tvfb$fireDrops(DamageSource source, CallbackInfo ci) {
        List<ItemEntity> captured = this.tvfb$capturedDrops;
        if (captured == null) return;
        this.tvfb$capturedDrops = null;   // stop diverting before anything is spawned for real
        LivingEntity self = tvfb$self();
        LivingDropsEvent event = EventBus.post(new LivingDropsEvent(self, source, captured,
                0, self.getLastHurtByMob() != null));
        if (event.isCanceled()) return;
        for (ItemEntity drop : event.getDrops()) {
            self.level().addFreshEntity(drop);
        }
    }

    @Override
    public List<ItemEntity> tvfbCapturedDrops() {
        return this.tvfb$capturedDrops;
    }

    /** Forge's LivingExperienceDropEvent. The mod only ever cancels it, so no amount is threaded back. */
    @Inject(method = "dropExperience", at = @At("HEAD"), cancellable = true)
    private void tvfb$experience(CallbackInfo ci) {
        LivingEntity self = tvfb$self();
        LivingExperienceDropEvent event = new LivingExperienceDropEvent(self, null, self.getExperienceReward());
        if (EventBus.post(event).isCanceled()) {
            ci.cancel();
        }
    }

    // --- equipment ------------------------------------------------------------------------------------------

    /**
     * Forge's LivingEquipmentChangeEvent. Vanilla hands over what each slot changed TO but not what it held
     * before, so {@code getFrom} is empty here; the mod's one handler reads the slot and the new stack.
     */
    @Inject(method = "handleEquipmentChanges", at = @At("HEAD"))
    private void tvfb$equipmentChanged(Map<EquipmentSlot, ItemStack> changes, CallbackInfo ci) {
        if (!EventBus.hasListeners(LivingEquipmentChangeEvent.class)) return;
        for (Map.Entry<EquipmentSlot, ItemStack> entry : changes.entrySet()) {
            EventBus.post(new LivingEquipmentChangeEvent(
                    tvfb$self(), entry.getKey(), ItemStack.EMPTY, entry.getValue()));
        }
    }

    // --- status effects -------------------------------------------------------------------------------------

    /** Forge's MobEffectEvent.Applicable: a DENY result refuses the effect, which is how immunity works. */
    @Inject(method = "canBeAffected", at = @At("RETURN"), cancellable = true)
    private void tvfb$effectApplicable(MobEffectInstance instance, CallbackInfoReturnable<Boolean> cir) {
        MobEffectEvent.Applicable event =
                EventBus.post(new MobEffectEvent.Applicable(tvfb$self(), instance));
        if (event.getResult() == com.example.villagefightsback.platform.Event.Result.DENY) {
            cir.setReturnValue(false);
        } else if (event.getResult() == com.example.villagefightsback.platform.Event.Result.ALLOW) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"))
    private void tvfb$effectAdded(MobEffectInstance instance, Entity source,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            EventBus.post(new MobEffectEvent.Added(tvfb$self(), null, instance, source));
        }
    }

    /**
     * Forge splits these two: Expired when an effect runs down, Remove when something takes it away. Vanilla
     * funnels both through onEffectRemoved, so they are told apart by whether the duration ran out.
     */
    @Inject(method = "onEffectRemoved", at = @At("HEAD"))
    private void tvfb$effectGone(MobEffectInstance instance, CallbackInfo ci) {
        if (instance.getDuration() <= 0) {
            EventBus.post(new MobEffectEvent.Expired(tvfb$self(), instance));
        } else {
            EventBus.post(new MobEffectEvent.Remove(tvfb$self(), instance));
        }
    }
}
