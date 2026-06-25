package com.example.villagefightsback.client;

import com.example.villagefightsback.LimboAbility;
import com.example.villagefightsback.LimboGear;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

/**
 * Draws the banish-ability cooldown indicator (a small green square that fills as it recharges, with the
 * seconds remaining) just left of the hotbar -- but only while the player wears the full Limbo set, i.e. only
 * once they actually have the ability.
 */
public final class LimboAbilityHud {
    private LimboAbilityHud() {}

    private static final int GREEN = 0xFF3BF07A;

    /** Registered from client setup (1.19.2 has no RegisterGuiOverlaysEvent). */
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "limbo_ability_cooldown",
                (gui, ps, partialTick, w, h) -> draw(ps, w, h));
    }

    private static void draw(PoseStack ps, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !LimboGear.hasFullSet(mc.player)) return;

        int size = 18;
        int x = w / 2 - 91 - size - 6; // just left of the 182-wide hotbar
        int y = h - 22;
        int cd = LimboAbilityClient.getCooldownTicks();
        boolean ready = cd <= 0;

        GuiComponent.fill(ps, x, y, x + size, y + size, 0xAA000000); // backdrop
        float frac = ready ? 1.0F : 1.0F - cd / (float) LimboAbility.COOLDOWN_TICKS;
        int fillH = Math.max(0, Math.min(size, Math.round(size * frac)));
        GuiComponent.fill(ps, x, y + (size - fillH), x + size, y + size, ready ? GREEN : (0x80000000 | (GREEN & 0xFFFFFF)));
        // border
        GuiComponent.fill(ps, x, y, x + size, y + 1, 0xFFFFFFFF);
        GuiComponent.fill(ps, x, y + size - 1, x + size, y + size, 0xFFFFFFFF);
        GuiComponent.fill(ps, x, y, x + 1, y + size, 0xFFFFFFFF);
        GuiComponent.fill(ps, x + size - 1, y, x + size, y + size, 0xFFFFFFFF);

        if (!ready) {
            String s = String.valueOf(cd / 20 + 1); // seconds remaining (rounded up)
            GuiComponent.drawString(ps, mc.font, s, x + (size - mc.font.width(s)) / 2 + 1, y + 5, 0xFFFFFFFF);
        } else {
            // Charged: show the fire key ("G") so the player knows how to unleash the beam.
            String key = "G";
            GuiComponent.drawString(ps, mc.font, key, x + (size - mc.font.width(key)) / 2 + 1, y + 5, 0xFF0A2A12);
        }
    }
}
