package com.example.villagefightsback.platform;

import java.util.Collection;
import java.util.Iterator;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/**
 * One vanilla registry, wrapped in the handful of lookups the mod does through Forge's {@code IForgeRegistry}.
 *
 * <p>Semantics match: on a defaulted registry (blocks, items, entity types) a miss returns the registry's default
 * value rather than null, exactly as Forge's {@code getValue} does, so the call sites keep behaving the same.
 */
public final class ModRegistry<T> implements Iterable<T> {
    private final Registry<T> registry;

    ModRegistry(Registry<T> registry) {
        this.registry = registry;
    }

    /** The wrapped vanilla registry, for the places that want it directly. */
    public Registry<T> vanilla() {
        return this.registry;
    }

    public T getValue(ResourceLocation id) {
        return this.registry.get(id);
    }

    public ResourceLocation getKey(T value) {
        return this.registry.getKey(value);
    }

    public boolean containsKey(ResourceLocation id) {
        return this.registry.containsKey(id);
    }

    public Collection<T> getValues() {
        return this.registry.stream().toList();
    }

    /** Iterating the wrapper iterates the registry, which is what Forge's IForgeRegistry allowed. */
    @Override
    public Iterator<T> iterator() {
        return this.registry.iterator();
    }
}
