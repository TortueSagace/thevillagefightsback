package com.example.villagefightsback.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stand-in for Forge's {@code @SubscribeEvent}. Marks a static, single-argument method as an event handler; the
 * argument type is the event it listens for.
 *
 * <p>Unlike Forge, nothing scans the classpath for these: {@link ModEventHandlers} lists the handler classes and
 * {@link EventBus#register} reflects over each one at startup.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribeEvent {
    /** Handlers fire highest-first; equal priorities fire in registration order. */
    EventPriority priority() default EventPriority.NORMAL;

    /** When false (the default), a handler is skipped once an earlier one has cancelled the event. */
    boolean receiveCanceled() default false;
}
