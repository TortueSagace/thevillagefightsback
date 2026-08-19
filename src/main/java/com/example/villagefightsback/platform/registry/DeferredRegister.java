package com.example.villagefightsback.platform.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.example.villagefightsback.platform.ModRegistry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Stand-in for Forge's {@code DeferredRegister}, keeping the twelve {@code Mod*} registration hubs written the way
 * they are on the Forge branch.
 *
 * <p>The one real difference is in the name: nothing is deferred. Forge holds the suppliers until it fires its
 * registry events; Fabric has no such phase, so {@link #register(String, Supplier)} builds and registers the value
 * on the spot. That makes registration order the order the hubs are touched, which is why
 * {@code TheVillageFightsBack.onInitialize} calls them blocks-first: items hold BlockItems, and the Limbo Sage POI
 * reads the brain node's block states.
 *
 * <p>{@link #register()} is therefore a no-op, kept only so the hubs can go on announcing themselves.
 */
public final class DeferredRegister<T> {
    private final Registry<T> registry;
    private final String namespace;
    private final List<RegistryObject<? extends T>> entries = new ArrayList<>();

    private DeferredRegister(Registry<T> registry, String namespace) {
        this.registry = registry;
        this.namespace = namespace;
    }

    /** Mirrors {@code DeferredRegister.create(ForgeRegistries.X, MODID)}. */
    public static <T> DeferredRegister<T> create(ModRegistry<T> registry, String namespace) {
        return new DeferredRegister<>(registry.vanilla(), namespace);
    }

    /** Mirrors {@code DeferredRegister.create(Registries.X, MODID)} for the vanilla registry keys. */
    @SuppressWarnings("unchecked")
    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key, String namespace) {
        Registry<T> registry = (Registry<T>) net.minecraft.core.registries.BuiltInRegistries.REGISTRY
                .get(key.location());
        if (registry == null) {
            throw new IllegalArgumentException("No built-in registry for " + key.location());
        }
        return new DeferredRegister<>(registry, namespace);
    }

    /** Builds and registers the value immediately, returning a handle on it. */
    public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> supplier) {
        ResourceLocation id = new ResourceLocation(this.namespace, name);
        ResourceKey<T> key = ResourceKey.create(this.registry.key(), id);
        I value = supplier.get();
        Registry.register(this.registry, id, value);
        RegistryObject<I> handle = new RegistryObject<>(cast(this.registry), cast(key), value);
        this.entries.add(handle);
        return handle;
    }

    /** Everything is already registered by the time this is called; kept for symmetry with the Forge branch. */
    public void register() {
        // no-op -- see the class javadoc
    }

    /** Every handle this register has handed out, in registration order. */
    public List<RegistryObject<? extends T>> getEntries() {
        return Collections.unmodifiableList(this.entries);
    }

    @SuppressWarnings("unchecked")
    private static <A, B> B cast(A a) {
        return (B) a;
    }
}
