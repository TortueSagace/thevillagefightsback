package com.example.villagefightsback.platform.registry;

import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Stand-in for Forge's {@code RegistryObject}: a named handle on one registered thing, used 776 times across the
 * mod as {@code ModBlocks.SOMETHING.get()}.
 *
 * <p>The Forge original is a lazy handle, because Forge registers content later than the field is declared. Here
 * registration has already happened by the time this exists ({@link DeferredRegister} registers eagerly), so the
 * value is simply held. The API is kept identical so no call site has to change.
 */
public final class RegistryObject<T> implements Supplier<T> {
    private final ResourceKey<T> key;
    private final T value;
    private final Registry<T> registry;

    RegistryObject(Registry<T> registry, ResourceKey<T> key, T value) {
        this.registry = registry;
        this.key = key;
        this.value = value;
    }

    @Override
    public T get() {
        return this.value;
    }

    /** The registry key. Never null: this handle only exists because the value was registered under it. */
    public ResourceKey<T> getKey() {
        return this.key;
    }

    /** The registry id, e.g. {@code thevillagefightsback:village_brain_node}. */
    public ResourceLocation getId() {
        return this.key.location();
    }

    /** Always true here; kept so the 25 call sites that ask do not have to change. */
    public boolean isPresent() {
        return true;
    }

    public Optional<Holder.Reference<T>> getHolder() {
        return this.registry.getHolder(this.key);
    }

    @Override
    public String toString() {
        return "RegistryObject{" + this.key.location() + "}";
    }
}
