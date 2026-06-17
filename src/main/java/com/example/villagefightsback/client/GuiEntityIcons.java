package com.example.villagefightsback.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Renders a live, animated entity inside a small GUI icon slot -- used by {@link BrainNodeScreen} to show the
 * spinning Wall Defender (and the gold stage-4 one) in the upgrade tree instead of a spawn egg. Caches one
 * client-side dummy entity per type and advances its age each frame so it animates. Fully guarded: if entity
 * rendering ever throws, it disables itself and the caller falls back to a static item icon.
 */
public final class GuiEntityIcons {
    private GuiEntityIcons() {}

    private static final Map<EntityType<?>, LivingEntity> CACHE = new HashMap<>();
    private static Level cacheLevel;
    private static int age;
    private static boolean disabled;

    /**
     * Render {@code type}'s dummy entity centred in the 16x16 icon at (iconX, iconY) at the given display
     * scale. Returns false (so the caller draws a fallback icon) when there is no client level yet or entity
     * rendering has been disabled by a prior failure.
     */
    public static boolean render(GuiGraphics g, int iconX, int iconY, EntityType<?> type, int scale) {
        if (disabled) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        try {
            if (cacheLevel != mc.level) { CACHE.clear(); cacheLevel = mc.level; }
            LivingEntity e = CACHE.get(type);
            if (e == null) {
                if (!(type.create(mc.level) instanceof LivingEntity created)) return false;
                e = created;
                CACHE.put(type, e);
            }
            e.tickCount = ++age; // advance the model animation (the wall defender's spin/bob reads ageInTicks)
            // Centre the entity horizontally in the icon; its feet sit near the bottom of the 16px slot.
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, iconX + 8, iconY + 15, scale, 0.0F, 0.0F, e);
            return true;
        } catch (Throwable t) {
            disabled = true;
            return false;
        }
    }
}
