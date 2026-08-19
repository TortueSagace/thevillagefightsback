package com.example.villagefightsback.platform.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.example.villagefightsback.TheVillageFightsBack;

/**
 * Stand-in for Forge's {@code ForgeConfigSpec}, over a hand-written commented-TOML reader and writer.
 *
 * <p>Fabric ships no config system, and the alternatives all want their own annotations or file layout. The mod
 * already has a well-documented config file that players edit and that the {@code /thevillagefightsback config}
 * commands read and write live, so the file format is the thing worth preserving: a config written by the Forge
 * build loads here unchanged, and one written here loads there.
 *
 * <p>What is supported is exactly what {@link com.example.villagefightsback.Config} uses: sections, booleans, ints
 * and doubles, each with a multi-line comment and (for the numbers) a valid range. Values outside their range are
 * clamped back and the file is rewritten, which is what Forge's "correcting" pass does.
 */
public final class ModConfigSpec {
    private final List<ConfigValue<?>> values;
    private Path file;

    private ModConfigSpec(List<ConfigValue<?>> values) {
        this.values = values;
    }

    /** Every value in declaration order. */
    public List<ConfigValue<?>> values() {
        return this.values;
    }

    /**
     * Reads {@code path} into the spec (a missing file, or a missing key, simply keeps its default), then writes
     * it back so new keys, comments and corrections land on disk.
     */
    public void loadFrom(Path path) {
        this.file = path;
        Map<String, String> raw = new LinkedHashMap<>();
        if (Files.exists(path)) {
            try {
                parse(Files.readAllLines(path, StandardCharsets.UTF_8), raw);
            } catch (IOException e) {
                TheVillageFightsBack.LOGGER.error("[The Village Fights Back] Could not read {}; using defaults",
                        path, e);
            }
        }
        for (ConfigValue<?> v : this.values) {
            String text = raw.get(v.pathString());
            if (text != null) {
                v.parseAndSet(text);
            }
        }
        save();
    }

    /** Writes the current values back to the file the spec was loaded from. A no-op before {@link #loadFrom}. */
    public void save() {
        if (this.file == null) return;
        try {
            Files.createDirectories(this.file.getParent());
            Files.writeString(this.file, render(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TheVillageFightsBack.LOGGER.error("[The Village Fights Back] Could not write {}", this.file, e);
        }
    }

    private String render() {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        String section = null;
        boolean first = true;
        for (ConfigValue<?> v : this.values) {
            if (!Objects.equals(section, v.section())) {
                section = v.section();
                if (!first) sb.append(nl);
                if (section != null) {
                    sb.append("[").append(section).append("]").append(nl);
                }
            }
            first = false;
            for (String line : v.comment()) {
                sb.append("    #").append(line).append(nl);
            }
            if (v.rangeComment() != null) {
                sb.append("    #").append(v.rangeComment()).append(nl);
            }
            sb.append("    ").append(v.name()).append(" = ").append(v.render()).append(nl);
        }
        return sb.toString();
    }

    /** Fills {@code out} with dotted-path to raw value text. Comments and blank lines are skipped. */
    private static void parse(List<String> lines, Map<String, String> out) {
        String section = null;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            out.put(section == null ? key : section + "." + key, value);
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Values
    // ---------------------------------------------------------------------------------------------------------

    /** One config entry. Mirrors Forge's {@code ConfigValue}: {@link #get}, {@link #set}, {@link #getDefault}. */
    public abstract static class ConfigValue<T> {
        private final String section;
        private final String name;
        private final List<String> comment;
        private final T defaultValue;
        private T value;

        ConfigValue(String section, String name, List<String> comment, T defaultValue) {
            this.section = section;
            this.name = name;
            this.comment = comment;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }

        public T get() {
            return this.value;
        }

        public void set(T newValue) {
            this.value = clamp(newValue);
        }

        public T getDefault() {
            return this.defaultValue;
        }

        /** The dotted path, e.g. {@code features.mega_brutal_mode}. */
        public String getPath() {
            return pathString();
        }

        String section() {
            return this.section;
        }

        String name() {
            return this.name;
        }

        List<String> comment() {
            return this.comment;
        }

        String pathString() {
            return this.section == null ? this.name : this.section + "." + this.name;
        }

        /** An extra generated comment line, e.g. the allowed range. Null when there is nothing to add. */
        String rangeComment() {
            return null;
        }

        /** How the value appears in the file. */
        String render() {
            return String.valueOf(this.value);
        }

        /** Values outside a valid range come back inside it, as Forge's correcting pass would do. */
        T clamp(T candidate) {
            return candidate;
        }

        abstract void parseAndSet(String text);
    }

    public static final class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(String section, String name, List<String> comment, boolean defaultValue) {
            super(section, name, comment, defaultValue);
        }

        @Override
        void parseAndSet(String text) {
            set(Boolean.parseBoolean(text.trim()));
        }
    }

    public static final class IntValue extends ConfigValue<Integer> {
        private final int min;
        private final int max;

        IntValue(String section, String name, List<String> comment, int defaultValue, int min, int max) {
            super(section, name, comment, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        Integer clamp(Integer candidate) {
            return Math.max(this.min, Math.min(this.max, candidate));
        }

        @Override
        String rangeComment() {
            return "Range: " + this.min + " ~ " + this.max;
        }

        @Override
        void parseAndSet(String text) {
            try {
                set(Integer.parseInt(text.trim()));
            } catch (NumberFormatException e) {
                TheVillageFightsBack.LOGGER.warn(
                        "[The Village Fights Back] {} is not a whole number ({}); keeping {}",
                        getPath(), text, get());
            }
        }
    }

    public static final class DoubleValue extends ConfigValue<Double> {
        private final double min;
        private final double max;

        DoubleValue(String section, String name, List<String> comment, double defaultValue,
                    double min, double max) {
            super(section, name, comment, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        Double clamp(Double candidate) {
            return Math.max(this.min, Math.min(this.max, candidate));
        }

        @Override
        String rangeComment() {
            return "Range: " + this.min + " ~ " + this.max;
        }

        @Override
        void parseAndSet(String text) {
            try {
                set(Double.parseDouble(text.trim()));
            } catch (NumberFormatException e) {
                TheVillageFightsBack.LOGGER.warn("[The Village Fights Back] {} is not a number ({}); keeping {}",
                        getPath(), text, get());
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------------------------------------------------

    /** Mirrors Forge's builder, down to the comment-then-define shape the mod's Config is written in. */
    public static final class Builder {
        private final List<ConfigValue<?>> values = new ArrayList<>();
        private final Deque<String> path = new ArrayDeque<>();
        private List<String> pending = new ArrayList<>();

        public Builder comment(String... lines) {
            this.pending = new ArrayList<>(Arrays.asList(lines));
            return this;
        }

        public Builder push(String section) {
            this.path.addLast(section);
            return this;
        }

        public Builder pop() {
            this.path.pollLast();
            return this;
        }

        public BooleanValue define(String name, boolean defaultValue) {
            return add(new BooleanValue(section(), name, takeComment(), defaultValue));
        }

        public IntValue defineInRange(String name, int defaultValue, int min, int max) {
            return add(new IntValue(section(), name, takeComment(), defaultValue, min, max));
        }

        public DoubleValue defineInRange(String name, double defaultValue, double min, double max) {
            return add(new DoubleValue(section(), name, takeComment(), defaultValue, min, max));
        }

        public ModConfigSpec build() {
            return new ModConfigSpec(List.copyOf(this.values));
        }

        private <V extends ConfigValue<?>> V add(V value) {
            this.values.add(value);
            return value;
        }

        private String section() {
            return this.path.isEmpty() ? null : String.join(".", this.path);
        }

        private List<String> takeComment() {
            List<String> c = this.pending;
            this.pending = new ArrayList<>();
            return c;
        }
    }
}
