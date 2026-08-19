package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** The four points in a status effect's life the mod listens at. Mirrors Forge's nesting. */
public class MobEffectEvent extends LivingEvent {
    private final MobEffectInstance effectInstance;

    public MobEffectEvent(LivingEntity entity, MobEffectInstance effectInstance) {
        super(entity);
        this.effectInstance = effectInstance;
    }

    public MobEffectInstance getEffectInstance() {
        return this.effectInstance;
    }

    /**
     * Asked before an effect is allowed to apply. A handler answers with {@link Event.Result#DENY} to refuse it,
     * which is how the mod grants immunity; {@link Event.Result#DEFAULT} leaves vanilla to decide.
     */
    public static class Applicable extends MobEffectEvent {
        public Applicable(LivingEntity entity, MobEffectInstance effectInstance) {
            super(entity, effectInstance);
        }
    }

    /** An effect has just been applied. */
    public static class Added extends MobEffectEvent {
        private final MobEffectInstance oldEffectInstance;
        private final Entity effectSource;

        public Added(LivingEntity entity, MobEffectInstance oldEffectInstance,
                     MobEffectInstance newEffectInstance, Entity effectSource) {
            super(entity, newEffectInstance);
            this.oldEffectInstance = oldEffectInstance;
            this.effectSource = effectSource;
        }

        public MobEffectInstance getOldEffectInstance() {
            return this.oldEffectInstance;
        }

        /** Whatever applied the effect (a thrown potion, a mob), or null when nothing is on record. */
        public Entity getEffectSource() {
            return this.effectSource;
        }
    }

    /** An effect is about to be taken away deliberately (milk, a command). Cancelling keeps it. */
    public static class Remove extends MobEffectEvent {
        private final MobEffect effect;

        public Remove(LivingEntity entity, MobEffect effect) {
            super(entity, entity.getEffect(effect));
            this.effect = effect;
        }

        public Remove(LivingEntity entity, MobEffectInstance effectInstance) {
            super(entity, effectInstance);
            this.effect = effectInstance.getEffect();
        }

        public MobEffect getEffect() {
            return this.effect;
        }
    }

    /** An effect ran out on its own. */
    public static class Expired extends MobEffectEvent {
        public Expired(LivingEntity entity, MobEffectInstance effectInstance) {
            super(entity, effectInstance);
        }
    }
}
