package com.example.villagefightsback.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.villagefightsback.TheVillageFightsBack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Loads, resolves and registers the {@link Modules}.
 *
 * <p>Order matters: {@link #init()} must run in the mod constructor <em>before</em> any {@link ModuleRegistry} is
 * filled, because NeoForge fires the registry events before it loads configs. Resolution therefore reads the
 * module section of {@code config/thevillagefightsback-common.toml} directly -- see {@link Module} for why.
 *
 * <p>Event handlers are attached only for enabled modules: {@code @EventBusSubscriber} is gone from the mod's
 * handler classes, and each module hands its classes to the bus here instead. A disabled module therefore costs
 * nothing during play, rather than waking up on every event only to return immediately.
 */
public final class ModuleManager {
    private ModuleManager() {}

    private static final String CONFIG_FILE = TheVillageFightsBack.MODID + "-common.toml";

    /** State the game booted with, so a config reload can tell the player it needs a restart. */
    private static final Map<String, Boolean> bootState = new LinkedHashMap<>();
    private static boolean initialised;

    /**
     * Reads {@code [modules.*]} from the config file and fixes each module's state for this session.
     * Safe to call twice; the second call is ignored.
     */
    public static void init() {
        if (initialised) return;
        initialised = true;

        Map<String, Boolean> fromFile = readToggles();
        Set<String> known = new HashSet<>();

        for (Module m : Modules.ALL) {
            known.add(m.id);
            boolean wanted = m.alwaysOn || fromFile.getOrDefault(m.id, m.enabledByDefault);
            m.setEnabled(wanted);
        }

        // Unknown ids in the file are almost always a typo; say so instead of failing silently.
        for (String id : fromFile.keySet()) {
            if (!known.contains(id)) {
                TheVillageFightsBack.LOGGER.warn("[modules] unknown module '{}' in {} -- ignored", id, CONFIG_FILE);
            }
        }

        // Dependency pass: a module whose requirement is off cannot run.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Module m : Modules.ALL) {
                if (!m.isEnabled()) continue;
                for (String required : m.requires) {
                    Module dep = Modules.byId(required);
                    if (dep == null) {
                        TheVillageFightsBack.LOGGER.warn("[modules] '{}' requires unknown module '{}'", m.id, required);
                    } else if (!dep.isEnabled()) {
                        m.setEnabled(false);
                        TheVillageFightsBack.LOGGER.info(
                                "[modules] '{}' disabled because it requires '{}', which is off", m.id, dep.id);
                        changed = true;
                    }
                }
            }
        }

        for (Module m : Modules.ALL) {
            m.markResolved();
            bootState.put(m.id, m.isEnabled());
        }
        logTable();
    }

    /** Adds the {@code [modules]} section to the shared spec. Called from {@code Config}'s static block. */
    public static void buildConfig(ModConfigSpec.Builder b) {
        b.comment("Module switches: the mod's features are split into independently enableable modules, each",
                        "registering its own content and its own event handlers. Disabling one removes it entirely.",
                        "Every module needs a game restart; a server and its clients must agree.")
                .push("modules");
        for (Module m : Modules.ALL) {
            m.defineConfig(b);
        }
        b.pop();
    }

    /** Attaches the enabled modules' handler classes to the game bus (and mod bus where declared). */
    public static void registerHandlers(IEventBus modBus) {
        boolean client = FMLEnvironment.dist == Dist.CLIENT;
        Set<Class<?>> registered = new HashSet<>();
        int count = 0;

        for (Module m : Modules.ALL) {
            if (!m.isEnabled()) continue;
            for (Class<?> handler : m.gameHandlers()) {
                register(NeoForge.EVENT_BUS, handler, registered, m);
                count++;
            }
            for (Class<?> handler : m.modHandlers()) {
                register(modBus, handler, registered, m);
                count++;
            }
            if (client) {
                for (Class<?> handler : m.clientGameHandlers()) {
                    register(NeoForge.EVENT_BUS, handler, registered, m);
                    count++;
                }
                for (Class<?> handler : m.clientModHandlers()) {
                    register(modBus, handler, registered, m);
                    count++;
                }
            }
        }
        TheVillageFightsBack.LOGGER.info("[modules] {} event handler classes attached", count);
    }

    private static void register(IEventBus bus, Class<?> handler, Set<Class<?>> seen, Module m) {
        if (!seen.add(handler)) {
            throw new IllegalStateException("Handler " + handler.getName() + " is claimed by more than one module ("
                    + m.id + "): a class may only be registered once.");
        }
        try {
            bus.register(handler);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Module '" + m.id + "' could not register " + handler.getName(), e);
        }
    }

    /**
     * A config file reload cannot add or remove content that is already registered, so the only sane response is
     * to say so. Called from the mod's {@code ModConfigEvent} listener.
     *
     * <p>The reloaded value is deliberately left in place rather than reverted to the booted one: the file is
     * what the next start will use, and the config GUI (and {@code /thevillagefightsback config module_*}) has
     * to be able to show and save it. Behaviour is unaffected either way -- {@link Module#isEnabled()} is fixed
     * at load time and is what everything asks -- and a mismatch between the two is what the warning reports.
     */
    public static void onConfigReload() {
        List<String> changed = new ArrayList<>();
        for (Module m : Modules.ALL) {
            Boolean booted = bootState.get(m.id);
            if (booted == null || m.toggle() == null) continue;
            if (booted != m.toggle().get()) changed.add(m.id);
        }
        if (!changed.isEmpty()) {
            TheVillageFightsBack.LOGGER.warn(
                    "[modules] {} was changed while the game is running; the running game keeps the state it loaded with. Restart to apply: {}",
                    CONFIG_FILE, changed);
        }
    }

    private static void logTable() {
        StringBuilder sb = new StringBuilder("[modules] module state (");
        long on = Modules.ALL.stream().filter(Module::isEnabled).count();
        sb.append(on).append('/').append(Modules.ALL.size()).append(" enabled)");
        TheVillageFightsBack.LOGGER.info(sb.toString());
        for (ModuleCategory category : ModuleCategory.values()) {
            for (Module m : Modules.ALL) {
                if (m.category != category) continue;
                TheVillageFightsBack.LOGGER.info("[modules]   {} {} -- {}", m.isEnabled() ? "[on ]" : "[off]",
                        String.format("%-14s", m.id), m.name);
            }
        }
    }

    /**
     * Minimal reader for {@code [modules.<id>] enabled = true/false}. A hand-written scanner rather than
     * nightconfig so the module state is available the instant the mod class is constructed, with no dependency
     * on the config system that is not loaded yet.
     */
    private static Map<String, Boolean> readToggles() {
        Map<String, Boolean> out = new HashMap<>();
        Path path = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        if (!Files.isRegularFile(path)) return out;

        String section = null;
        try {
            for (String raw : Files.readAllLines(path)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).trim();
                    continue;
                }
                if (section == null || !section.startsWith("modules.")) continue;

                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                if (!line.substring(0, eq).trim().equals("enabled")) continue;

                String value = line.substring(eq + 1).trim();
                String id = section.substring("modules.".length()).trim();
                if (value.equalsIgnoreCase("true")) {
                    out.put(id, Boolean.TRUE);
                } else if (value.equalsIgnoreCase("false")) {
                    out.put(id, Boolean.FALSE);
                }
            }
        } catch (IOException e) {
            TheVillageFightsBack.LOGGER.warn("[modules] could not read {}: {}", path, e.toString());
        }
        return out;
    }
}
