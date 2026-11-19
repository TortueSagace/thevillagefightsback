package com.example.villagefightsback.modules;

import java.util.function.Supplier;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A {@link DeferredRegister} that belongs to the module system: entries whose owner module is disabled are never
 * registered, so a turned-off module leaves no items, blocks, creatures or effects behind -- not merely inert
 * ones. The returned holder is still non-null (just unbound), so field initialisers that merely hold a reference
 * keep compiling and loading.
 *
 * <p>Ownership is derived from the entry name by {@link Modules#ownerOf(String)}, which keeps the registration
 * classes untouched: swapping {@code DeferredRegister.create(...)} for {@code ModuleRegistry.create(...)} is the
 * whole change at a registration site.
 */
public final class ModuleRegistry<T> {
    private final DeferredRegister<T> delegate;
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String registryPath;

    private ModuleRegistry(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        this.registryKey = registryKey;
        this.delegate = DeferredRegister.create(registryKey, namespace);
        this.registryPath = registryKey.location().getPath();
    }

    public static <T> ModuleRegistry<T> create(Registry<T> registry, String namespace) {
        return new ModuleRegistry<>(registry.key(), namespace);
    }

    public static <T> ModuleRegistry<T> create(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        return new ModuleRegistry<>(registryKey, namespace);
    }

    public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> supplier) {
        if (Modules.contentEnabled(registryPath, name)) {
            return delegate.register(name, supplier);
        }
        return unbound(name);
    }

    public void register(IEventBus bus) {
        delegate.register(bus);
    }

    /** A holder for an entry that was deliberately not registered: a placeholder, not a value source. */
    @SuppressWarnings("unchecked")
    private <I extends T> DeferredHolder<T, I> unbound(String name) {
        ResourceLocation id = TheVillageFightsBack.id(name);
        return (DeferredHolder<T, I>) DeferredHolder.create(registryKey, id);
    }
}
