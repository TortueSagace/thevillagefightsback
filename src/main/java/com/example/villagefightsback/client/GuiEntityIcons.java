package com.example.villagefightsback.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
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
    private static double age;          // advanced slowly per render so the icon spins gently
    private static boolean disabled;

    private static final double SPIN_RATE = 0.45; // ticks of model-age added per render (lower = slower spin)
    private static final int ICON_DROP = 22;       // y of the entity's feet within the slot (higher = icon sits lower)

    /**
     * Render {@code type}'s dummy entity centred in the 16x16 icon at (iconX, iconY) at the given display
     * scale. Returns false (so the caller draws a fallback icon) when there is no client level yet or entity
     * rendering has been disabled by a prior failure.
     */
    public static boolean render(PoseStack ps, int iconX, int iconY, EntityType<?> type, int scale) {
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
            age += SPIN_RATE;        // advance the model animation slowly (the wall defender's spin reads ageInTicks)
            e.tickCount = (int) age;
            // Centre the entity horizontally; ICON_DROP lowers the feet anchor so the spinning core sits mid-slot.
            // 1.19.2 InventoryScreen.renderEntityInInventory takes no PoseStack/GuiGraphics arg (uses the global modelview pose).
            InventoryScreen.renderEntityInInventory(iconX + 8, iconY + ICON_DROP, scale, 0.0F, 0.0F, e);
            return true;
        } catch (Throwable t) {
            disabled = true;
            return false;
        }
    }
}
