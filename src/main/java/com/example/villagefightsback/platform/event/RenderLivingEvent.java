package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Wraps the rendering of one living entity. Client-only; fired either side of LivingEntityRenderer.render, which
 * is what lets the mod tint an infected mob and put the shader colour back afterwards.
 */
public class RenderLivingEvent<T extends LivingEntity, M extends EntityModel<T>> extends Event {
    private final T entity;

    public RenderLivingEvent(T entity) {
        this.entity = entity;
    }

    public T getEntity() {
        return this.entity;
    }

    /** Before the entity is drawn. */
    public static class Pre<T extends LivingEntity, M extends EntityModel<T>> extends RenderLivingEvent<T, M> {
        public Pre(T entity) {
            super(entity);
        }
    }

    /** After the entity is drawn. Whatever Pre changed has to be undone here. */
    public static class Post<T extends LivingEntity, M extends EntityModel<T>> extends RenderLivingEvent<T, M> {
        public Post(T entity) {
            super(entity);
        }
    }
}
