package com.example.villagefightsback.platform;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.example.villagefightsback.TheVillageFightsBack;

/**
 * The mod's own event bus, standing in for Forge's.
 *
 * <p>Forge finds handlers by scanning the jar for {@code @Mod.EventBusSubscriber}. Fabric has no such scan, so
 * {@link ModEventHandlers} lists the handler classes explicitly and this class reflects over each one at startup,
 * binding every static {@link SubscribeEvent} method to the event type in its signature. After that the bus is
 * read-only and dispatch is a plain array walk.
 *
 * <p>Listeners registered against a supertype also see subtype events, matching Forge -- so a handler taking
 * {@code LivingEvent} hears a {@code LivingDeathEvent}. The resolved listener array per concrete event class is
 * cached, because these fire on hot paths (every living entity, every tick).
 *
 * <p>Call {@link #hasListeners} before building an event object on a hot path: no listeners means the allocation
 * can be skipped entirely.
 */
public final class EventBus {
    private EventBus() {}

    /** Listeners exactly as declared, keyed by the event class in the handler's signature. */
    private static final Map<Class<?>, List<Listener>> DECLARED = new HashMap<>();

    /** Listeners that apply to a concrete event class, supertypes folded in and sorted. Built on first post. */
    private static final Map<Class<?>, Listener[]> RESOLVED = new ConcurrentHashMap<>();

    private static final Set<Class<?>> REGISTERED = new LinkedHashSet<>();

    private record Listener(Method method, EventPriority priority, boolean receiveCanceled, int order) {}

    private static int registrationOrder;

    /**
     * Binds every static {@code @SubscribeEvent} method on {@code handlerClass} and on its nested classes --
     * some of the mod's handlers sit in a nested holder rather than on the outer class. Registering the same
     * class twice is a no-op, so a class in both the common and the client list is still only bound once.
     */
    public static synchronized void register(Class<?> handlerClass) {
        if (!REGISTERED.add(handlerClass)) return;
        int found = bind(handlerClass);
        for (Class<?> nested : handlerClass.getDeclaredClasses()) {
            found += bind(nested);
        }
        if (found == 0) {
            throw new IllegalStateException(
                    "No @SubscribeEvent methods on " + handlerClass.getName() + "; drop it from ModEventHandlers");
        }
        RESOLVED.clear();
    }

    /** Binds one class's handler methods, returning how many it found. */
    private static int bind(Class<?> handlerClass) {
        int found = 0;
        for (Method m : handlerClass.getDeclaredMethods()) {
            SubscribeEvent ann = m.getAnnotation(SubscribeEvent.class);
            if (ann == null) continue;
            if (!Modifier.isStatic(m.getModifiers())) {
                throw new IllegalStateException("@SubscribeEvent method must be static: " + m);
            }
            if (m.getParameterCount() != 1 || !Event.class.isAssignableFrom(m.getParameterTypes()[0])) {
                throw new IllegalStateException(
                        "@SubscribeEvent method must take exactly one Event argument: " + m);
            }
            m.setAccessible(true);
            DECLARED.computeIfAbsent(m.getParameterTypes()[0], k -> new ArrayList<>())
                    .add(new Listener(m, ann.priority(), ann.receiveCanceled(), registrationOrder++));
            found++;
        }
        return found;
    }

    /** True when at least one handler would see an event of this class. */
    public static boolean hasListeners(Class<? extends Event> eventClass) {
        return resolve(eventClass).length > 0;
    }

    /**
     * Fires {@code event} at every listener, highest priority first. Returns the same event so callers can read
     * back whatever the handlers changed, including {@link Event#isCanceled()}.
     */
    public static <T extends Event> T post(T event) {
        for (Listener l : resolve(event.getClass())) {
            if (event.isCanceled() && !l.receiveCanceled()) continue;
            try {
                l.method().invoke(null, event);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Event handler failed: " + l.method(), e);
            } catch (RuntimeException e) {
                TheVillageFightsBack.LOGGER.error("[The Village Fights Back] Handler {} threw on {}",
                        l.method(), event.getClass().getSimpleName(), e);
                throw e;
            }
        }
        return event;
    }

    private static Listener[] resolve(Class<?> eventClass) {
        Listener[] cached = RESOLVED.get(eventClass);
        if (cached != null) return cached;
        List<Listener> out = new ArrayList<>();
        for (Class<?> c = eventClass; c != null && Event.class.isAssignableFrom(c); c = c.getSuperclass()) {
            List<Listener> here = DECLARED.get(c);
            if (here != null) out.addAll(here);
        }
        out.sort(Comparator.<Listener, Integer>comparing(l -> l.priority().ordinal())
                .thenComparing(Listener::order));
        Listener[] arr = out.toArray(new Listener[0]);
        RESOLVED.put(eventClass, arr);
        return arr;
    }
}
