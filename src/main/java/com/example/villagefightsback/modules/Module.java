package com.example.villagefightsback.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.example.villagefightsback.Config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * One slice of the mod, in the shape of Quark's {@code Module}: an id, a category, and the event-handler classes
 * that belong to it. A disabled module registers nothing -- no content (see {@link ModuleRegistry}) and no
 * handlers -- so it costs zero at runtime.
 *
 * <p><b>Every module is always on.</b> The table is a map of what the mod is made of, not a set of switches: no
 * module generates an {@code enabled} line, and {@link #isEnabled()} answers true for all of them. The switch
 * machinery is still here and still runs, so a module could be made optional again by declaring it with
 * {@code alwaysOn = false}.
 *
 * <p><b>Why the enable state is read from the file, not from {@link ModConfigSpec}.</b> NeoForge loads configs
 * <em>after</em> the registry events (see {@code CommonModLoader.begin()}: mod construct, then
 * {@code postRegisterEvents}, then config loading), so a {@code BooleanValue} cannot answer "is this module on?"
 * while {@code DeferredRegister}s are being filled. {@link ModuleManager} therefore parses the module section of
 * the TOML early, the same problem Quark/Zeta solve by reading their own config before registration. The
 * {@link ModConfigSpec} entries built here exist so the file is generated, documented and reloadable.
 *
 * <p>Module state is fixed at load time. Flipping a toggle writes the file and logs that a restart is required;
 * the content and handlers are already in place for this session, exactly like Quark's restart-required features.
 */
public abstract class Module {
    public final String id;
    /** Display name, e.g. "Village Brain Node". */
    public final String name;
    public final ModuleCategory category;
    /** One-line description, used as the config comment and in the command listing. */
    public final String description;
    public final boolean enabledByDefault;
    /** Modules with this set are never written to the config and never turn off. Every module of the mod is. */
    public final boolean alwaysOn;
    /** Modules this one is meaningless without; disabling a requirement disables this module too. */
    public final List<String> requires = new ArrayList<>();

    private final List<Class<?>> gameHandlers = new ArrayList<>();
    private final List<Class<?>> clientGameHandlers = new ArrayList<>();
    private final List<Class<?>> modHandlers = new ArrayList<>();
    private final List<Class<?>> clientModHandlers = new ArrayList<>();

    private boolean enabled;
    /** True once {@link ModuleManager} has applied the file's state (or the default). */
    private boolean resolved;
    private ModConfigSpec.BooleanValue toggle;

    /** The ordinary declaration: on, and with no switch -- which is what every module of this mod is. */
    protected Module(String id, String name, ModuleCategory category, String description) {
        this(id, name, category, description, true, true);
    }

    protected Module(String id, String name, ModuleCategory category, String description,
            boolean enabledByDefault, boolean alwaysOn) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.enabledByDefault = enabledByDefault;
        this.alwaysOn = alwaysOn;
        this.enabled = enabledByDefault || alwaysOn;
    }

    // --- declaration helpers used by Modules' nested module classes ---

    /** {@code @EventBusSubscriber(bus = GAME)} classes: registered on both sides. */
    protected final void handlers(Class<?>... classes) {
        gameHandlers.addAll(Arrays.asList(classes));
    }

    /** Client-only game-bus classes (the old {@code value = Dist.CLIENT}). */
    protected final void clientHandlers(Class<?>... classes) {
        clientGameHandlers.addAll(Arrays.asList(classes));
    }

    /** Mod-bus classes (the old {@code bus = MOD}). */
    protected final void modHandlers(Class<?>... classes) {
        modHandlers.addAll(Arrays.asList(classes));
    }

    /** Client-only mod-bus classes (the old {@code bus = MOD, value = Dist.CLIENT}). */
    protected final void clientModHandlers(Class<?>... classes) {
        clientModHandlers.addAll(Arrays.asList(classes));
    }

    /** Declares that this module cannot work without {@code ids}. */
    protected final void requires(String... ids) {
        requires.addAll(Arrays.asList(ids));
    }

    // --- state ---

    public final boolean isEnabled() {
        return enabled;
    }

    public final boolean isResolved() {
        return resolved;
    }

    /** The config entry backing this module, or null when it has no switch (that is, every module of the mod). */
    public final ModConfigSpec.BooleanValue toggle() {
        return toggle;
    }

    public final List<Class<?>> gameHandlers() {
        return Collections.unmodifiableList(gameHandlers);
    }

    public final List<Class<?>> clientGameHandlers() {
        return Collections.unmodifiableList(clientGameHandlers);
    }

    public final List<Class<?>> modHandlers() {
        return Collections.unmodifiableList(modHandlers);
    }

    public final List<Class<?>> clientModHandlers() {
        return Collections.unmodifiableList(clientModHandlers);
    }

    // --- package-private plumbing for ModuleManager ---

    final void setEnabled(boolean value) {
        this.enabled = value;
    }

    final void markResolved() {
        this.resolved = true;
    }

    /** Writes {@code [modules.<id>] enabled = <default>} into the shared config spec, for a module that has one. */
    final void defineConfig(ModConfigSpec.Builder b) {
        if (alwaysOn) return;
        b.push(id);
        toggle = b.comment(description,
                        "Turn this module off to remove its content (items, blocks, creatures, blocks' recipes) and",
                        "its event handlers entirely. Requires a game restart; a running world keeps the state it",
                        "loaded with. Disabling a module that another one requires disables the dependent too.")
                .define("enabled", enabledByDefault);
        b.pop();
        // Surface it to /thevillagefightsback config as well, so packs can flip it without opening the file.
        Config.INDEX.put("module_" + id, toggle);
    }

    @Override
    public String toString() {
        return id + (enabled ? " (on)" : " (off)");
    }
}
