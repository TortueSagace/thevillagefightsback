package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * A tiny built-in profiler for THIS mod's own work, so we can see which of our subsystems costs the most
 * server-tick time and decide what to optimize. Instrumented code wraps a section with:
 * <pre>
 *   long t = ModProfiler.now();
 *   ... work ...
 *   ModProfiler.add("section", t);          // or in a finally{} when the body has early returns
 * </pre>
 * Each section accumulates total time + call count; a tick counter lets us report average µs/tick.
 * In-game: {@code /gbtyw profile} prints a sorted breakdown, {@code /gbtyw profile reset} clears it,
 * {@code /gbtyw profile on|off} toggles measuring (overhead is a couple of nanoTime calls per section).
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ModProfiler {
    private ModProfiler() {}

    private static volatile boolean enabled = true;
    private static final Map<String, long[]> SECTIONS = new ConcurrentHashMap<>(); // name -> [totalNanos, calls]
    private static volatile long ticks = 0L;

    /** Start timing a section (returns 0 when disabled, which makes {@link #add} a no-op). */
    public static long now() {
        return enabled ? System.nanoTime() : 0L;
    }

    /** Accumulate elapsed time into {@code section} since the {@link #now()} that returned {@code start}. */
    public static void add(String section, long start) {
        if (start == 0L) return;
        long dt = System.nanoTime() - start;
        long[] v = SECTIONS.computeIfAbsent(section, k -> new long[2]);
        v[0] += dt;
        v[1]++;
    }

    public static void reset() {
        SECTIONS.clear();
        ticks = 0L;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        if (enabled) ticks++;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gbtyw").requires(s -> s.hasPermission(2))
                .then(Commands.literal("profile")
                        .executes(c -> { printReport(c.getSource()); return 1; })
                        .then(Commands.literal("reset").executes(c -> {
                            reset();
                            c.getSource().sendSuccess(() -> Component.literal("§6[GBTYW] profiler reset"), false);
                            return 1;
                        }))
                        .then(Commands.literal("on").executes(c -> {
                            enabled = true; reset();
                            c.getSource().sendSuccess(() -> Component.literal("§a[GBTYW] profiler ON (reset)"), false);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(c -> {
                            enabled = false;
                            c.getSource().sendSuccess(() -> Component.literal("§c[GBTYW] profiler OFF"), false);
                            return 1;
                        }))));
    }

    private static void printReport(CommandSourceStack src) {
        final long t = Math.max(1L, ticks);
        List<Map.Entry<String, long[]>> list = new ArrayList<>(SECTIONS.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));

        long total = 0L;
        for (Map.Entry<String, long[]> e : list) total += e.getValue()[0];
        final long tot = Math.max(1L, total);

        src.sendSuccess(() -> Component.literal(
                "§6=== The Village Fights Back profiler -- " + ticks + " ticks "
                        + (enabled ? "" : "§c(OFF) ") + "§6==="), false);
        if (list.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7(no samples yet. Play a bit, then run /gbtyw profile)"), false);
            return;
        }
        for (Map.Entry<String, long[]> e : list) {
            long nanos = e.getValue()[0];
            long calls = e.getValue()[1];
            String line = String.format("§e%-18s §f%7.1f µs/tick §7(%4.1f%%)  total %dms  calls %d",
                    e.getKey(), nanos / 1000.0 / t, nanos * 100.0 / tot, nanos / 1_000_000L, calls);
            src.sendSuccess(() -> Component.literal(line), false);
        }
        String totalLine = String.format("§6TOTAL measured: %.1f µs/tick  (%.1f ms total over %d ticks)",
                total / 1000.0 / t, total / 1_000_000.0, ticks);
        src.sendSuccess(() -> Component.literal(totalLine), false);
    }
}
