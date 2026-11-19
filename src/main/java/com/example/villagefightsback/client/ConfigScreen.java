package com.example.villagefightsback.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.villagefightsback.Config;
import com.example.villagefightsback.TheVillageFightsBack;
import com.example.villagefightsback.modules.Module;
import com.example.villagefightsback.modules.Modules;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.lwjgl.glfw.GLFW;

/**
 * The mod's configuration GUI, in the shape of Quark's: a column of categories on the left, the selected
 * category's settings on the right, each as a toggle button (booleans) or a text field (numbers and lists).
 *
 * <p>What it edits is {@link Config#INDEX}, the same key -> value table {@code /thevillagefightsback config}
 * uses, so the two can never drift apart, and a category is simply the entry's first config path element
 * ({@code [features]}, {@code [multipliers]}, ...). The {@code modules} section is special: it is rendered as
 * one row per module from {@link Modules}, with the module's display name rather than its key.
 *
 * <p>Edits are applied to the live values as they are made -- that is what the game reads -- and written to the
 * .toml once, when the screen closes ({@link Config#saveSettings()}). Module switches are the exception: they
 * only take effect on the next start, so a row whose file value differs from the running state is marked with
 * a {@code *} and its tooltip says a restart is needed.
 */
public class ConfigScreen extends Screen {
    private static final int ROW_H = 22;
    private static final int WIDGET_H = 20;
    private static final int COLUMN_W = 104;
    private static final int BOOL_W = 46;
    private static final int MODULE_W = 62;
    private static final int NUM_W = 68;
    private static final int LIST_W = 132;
    private static final int MARGIN = 8;

    private final Screen parent;
    private final List<Group> groups = buildGroups();

    private int groupIndex;
    private double scroll;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private final List<Row> rows = new ArrayList<>();

    public ConfigScreen(Screen parent) {
        super(Component.literal("The Village Fights Back"));
        this.parent = parent;
    }

    // --- categories -------------------------------------------------------------------------------------

    /** Groups the index by its first path element, with the modules section first. */
    private static List<Group> buildGroups() {
        Map<String, Group> bySection = new LinkedHashMap<>();
        for (Map.Entry<String, ModConfigSpec.ConfigValue<?>> entry : Config.INDEX.entrySet()) {
            ModConfigSpec.ConfigValue<?> value = entry.getValue();
            List<String> path = value.getPath();
            // Module switches are rebuilt from Modules below, from their display names.
            if (path.size() > 1 && path.get(0).equals("modules")) continue;
            String section = path.isEmpty() ? "misc" : path.get(0);
            bySection.computeIfAbsent(section, s -> new Group(prettify(s)))
                    .items.add(new Item(prettify(entry.getKey()), String.join(".", path), value, null));
        }

        List<Group> out = new ArrayList<>();
        Group modules = new Group("Modules");
        for (Module module : Modules.ALL) {
            String key = module.toggle() == null ? "always on" : "modules." + module.id + ".enabled";
            modules.items.add(new Item(module.name, key, null, module));
        }
        out.add(modules);
        out.addAll(bySection.values());
        return out;
    }

    /** {@code player_is_hostile} -> {@code Player is hostile}. */
    private static String prettify(String key) {
        String spaced = key.replace('_', ' ').trim();
        if (spaced.isEmpty()) return spaced;
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    // --- layout -----------------------------------------------------------------------------------------

    @Override
    protected void init() {
        rows.clear();
        listTop = 40;
        listBottom = this.height - 32;
        listLeft = MARGIN + COLUMN_W + 12;
        listRight = this.width - MARGIN;

        int y = listTop;
        for (int i = 0; i < groups.size(); i++) {
            final int index = i;
            Button tab = Button.builder(Component.literal(groups.get(i).label), b -> selectGroup(index))
                    .bounds(MARGIN, y, COLUMN_W, WIDGET_H)
                    .build();
            tab.active = i != groupIndex; // the current category reads as "pressed"
            addRenderableWidget(tab);
            y += ROW_H;
        }

        for (Item item : groups.get(groupIndex).items) {
            Row row = new Row(item, listLeft + 2);
            row.widget = createWidget(row);
            addRenderableWidget(row.widget);
            rows.add(row);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 27, 200, WIDGET_H)
                .build());
    }

    private void selectGroup(int index) {
        if (index == groupIndex) return;
        commitEditors();
        groupIndex = index;
        scroll = 0;
        rebuildWidgets();
    }

    /** Places the current category's widgets, hiding the ones scrolled out of the list. */
    private void layoutRows() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            row.y = listTop + i * ROW_H - (int) scroll;
            row.widget.setX(listRight - 6 - row.widget.getWidth());
            row.widget.setY(row.y);
            row.widget.visible = row.y >= listTop && row.y + WIDGET_H <= listBottom;
            if (!row.widget.visible && row.widget.isFocused()) row.widget.setFocused(false);
        }
    }

    private double maxScroll() {
        return Math.max(0, rows.size() * ROW_H - (listBottom - listTop));
    }

    // --- widgets ----------------------------------------------------------------------------------------

    private AbstractWidget createWidget(Row row) {
        Item item = row.item;

        if (item.module != null) {
            Module module = item.module;
            ModConfigSpec.BooleanValue toggle = module.toggle();
            if (toggle == null) {
                Button locked = Button.builder(Component.literal("ON"), b -> { })
                        .bounds(0, 0, BOOL_W, WIDGET_H).build();
                locked.active = false;
                locked.setTooltip(Tooltip.create(Component.literal("This module is always on.")));
                return locked;
            }
            Button button = Button.builder(moduleLabel(module), b -> {
                toggle.set(!Boolean.TRUE.equals(toggle.get()));
                b.setMessage(moduleLabel(module));
            }).bounds(0, 0, MODULE_W, WIDGET_H).build();
            button.setTooltip(Tooltip.create(Component.literal(
                    "Module switches apply the next time the game starts.")));
            return button;
        }

        ModConfigSpec.ConfigValue<?> value = item.value;
        if (value instanceof ModConfigSpec.BooleanValue bool) {
            return Button.builder(boolLabel(bool), b -> {
                bool.set(!bool.get());
                b.setMessage(boolLabel(bool));
            }).bounds(0, 0, BOOL_W, WIDGET_H).build();
        }
        if (value instanceof ModConfigSpec.DoubleValue || value instanceof ModConfigSpec.IntValue) {
            EditBox box = new EditBox(this.font, 0, 0, NUM_W, WIDGET_H, Component.literal(item.label));
            box.setMaxLength(24);
            box.setFilter(text -> text.isEmpty() || text.matches("-?\\d*(\\.\\d*)?"));
            box.setValue(displayText(value));
            row.editor = box;
            return box;
        }

        EditBox box = new EditBox(this.font, 0, 0, LIST_W, WIDGET_H, Component.literal(item.label));
        box.setMaxLength(1024);
        box.setValue(displayText(value));
        box.setTooltip(Tooltip.create(Component.literal("Comma-separated entries.")));
        row.editor = box;
        return box;
    }

    private static Component boolLabel(ModConfigSpec.BooleanValue value) {
        return Component.literal(value.get() ? "ON" : "OFF");
    }

    /** {@code ON}/{@code OFF}, starred while the file and the running game disagree (a restart is needed). */
    private static Component moduleLabel(Module module) {
        boolean on = Boolean.TRUE.equals(module.toggle().get());
        boolean pending = on != module.isEnabled();
        return Component.literal((on ? "ON" : "OFF") + (pending ? " *" : ""));
    }

    // --- values -----------------------------------------------------------------------------------------

    private static String displayText(ModConfigSpec.ConfigValue<?> value) {
        Object current = value.get();
        if (current instanceof List<?> list) {
            return String.join(", ", list.stream().map(String::valueOf).toList());
        }
        if (current instanceof Double d && !d.isInfinite() && d == Math.rint(d)) {
            return String.valueOf(d.longValue());
        }
        return String.valueOf(current);
    }

    /** Applies a text field's contents to the config value, then shows what the config actually holds. */
    private void commitEditor(Row row) {
        ModConfigSpec.ConfigValue<?> value = row.item.value;
        String text = row.editor.getValue().trim();
        try {
            if (value instanceof ModConfigSpec.DoubleValue d) {
                if (!text.isEmpty()) d.set(Double.parseDouble(text));
            } else if (value instanceof ModConfigSpec.IntValue i) {
                if (!text.isEmpty()) i.set(Integer.parseInt(text));
            } else {
                setList(value, text);
            }
        } catch (NumberFormatException ignored) {
            // Not a number: leave the value as it was and let the field snap back below.
        }
        row.editor.setValue(displayText(value));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setList(ModConfigSpec.ConfigValue<?> value, String text) {
        List<String> parts = new ArrayList<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) parts.add(trimmed);
        }
        ((ModConfigSpec.ConfigValue) value).set(List.copyOf(parts));
    }

    private void commitEditors() {
        for (Row row : rows) {
            if (row.editor != null) commitEditor(row);
        }
    }

    // --- input ------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Clicking away from a field commits it; clicking inside it must not, so the caret stays put.
        for (Row row : rows) {
            if (row.editor != null && !row.editor.isMouseOver(mouseX, mouseY)) commitEditor(row);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < listLeft - 6 || mouseX > listRight || mouseY < listTop || mouseY > listBottom) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scroll = Mth.clamp(scroll - scrollY * ROW_H, 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitEditors();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        commitEditors();
        try {
            Config.saveSettings();
        } catch (RuntimeException e) {
            TheVillageFightsBack.LOGGER.warn("[config] could not write the config file: {}", e.toString());
        }
        this.minecraft.setScreen(this.parent);
    }

    // --- rendering --------------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(4, 4, this.width - 4, this.height - 4, 0x90000000);
        graphics.fill(MARGIN - 2, listTop - 2, MARGIN + COLUMN_W + 2, listBottom + 2, 0x40000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layoutRows();
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        graphics.enableScissor(0, listTop, this.width, listBottom);
        for (Row row : rows) {
            if (!row.widget.visible) continue;
            int labelWidth = row.widget.getX() - 6 - row.labelX;
            boolean pending = row.item.module != null && row.item.module.toggle() != null
                    && Boolean.TRUE.equals(row.item.module.toggle().get()) != row.item.module.isEnabled();
            int colour = pending ? 0xFFD75F : 0xE0E0E0;
            graphics.drawString(this.font, fit(row.item.label, labelWidth), row.labelX, row.y + 6, colour, false);
        }
        graphics.disableScissor();

        drawScrollbar(graphics);
        drawRowTooltip(graphics, mouseX, mouseY);
    }

    /** Truncates with an ellipsis rather than letting a long key run under its widget. */
    private String fit(String text, int width) {
        if (width <= 0 || this.font.width(text) <= width) return text;
        String ellipsis = "...";
        return this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width(ellipsis))) + ellipsis;
    }

    private void drawScrollbar(GuiGraphics graphics) {
        double max = maxScroll();
        if (max <= 0) return;
        int x = listRight - 3;
        int height = listBottom - listTop;
        int barHeight = Math.max(16, (int) (height * height / (rows.size() * (double) ROW_H)));
        int barY = listTop + (int) ((height - barHeight) * (scroll / max));
        graphics.fill(x, listTop, x + 2, listBottom, 0x40FFFFFF);
        graphics.fill(x, barY, x + 2, barY + barHeight, 0xFFFFFFFF);
    }

    /** Hovering a label names the exact config key, since long ones are cut short. */
    private void drawRowTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseX < listLeft || mouseX > listRight) return;
        for (Row row : rows) {
            if (!row.widget.visible || mouseY < row.y || mouseY >= row.y + WIDGET_H) continue;
            if (mouseX >= row.widget.getX() - 4) return;
            graphics.renderTooltip(this.font, Component.literal(row.item.hint), mouseX, mouseY);
            return;
        }
    }

    // --- data -------------------------------------------------------------------------------------------

    /** A row's backing data: a config entry, or (value == null) a module switch. */
    private static final class Item {
        private final String label;
        /** Shown on hover: the config key, or for modules the switch it toggles. */
        private final String hint;
        private final ModConfigSpec.ConfigValue<?> value;
        private final Module module;

        private Item(String label, String hint, ModConfigSpec.ConfigValue<?> value, Module module) {
            this.label = label;
            this.hint = hint;
            this.value = value;
            this.module = module;
        }
    }

    private static final class Group {
        private final String label;
        private final List<Item> items = new ArrayList<>();

        private Group(String label) {
            this.label = label;
        }
    }

    private static final class Row {
        private final Item item;
        private final int labelX;
        private AbstractWidget widget;
        private EditBox editor;
        private int y;

        private Row(Item item, int labelX) {
            this.item = item;
            this.labelX = labelX;
        }
    }
}
