package com.example.villagefightsback.client;

import com.example.villagefightsback.LimboAbility;
import com.example.villagefightsback.LimboGear;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws the banish-ability cooldown indicator (a small green square that fills as it recharges, with the
 * seconds remaining) just RIGHT of the hotbar -- but only while the player wears the full Limbo set, i.e.
 * only once they actually have the ability.
 *
 * <p>The right rather than the left because the left is where the offhand slot sits, and a shield in the
 * offhand put the two squares side by side arguing over the same corner.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LimboAbilityHud {
    private LimboAbilityHud() {}

    private static final int GREEN = 0xFF3BF07A;

    @SubscribeEvent
    public static void onRegister(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "limbo_ability_cooldown",
                (gui, g, partialTick, w, h) -> draw(g, w, h));
    }

    private static void draw(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !LimboGear.hasFullSet(mc.player)) return;

        int size = 18;
        int x = w / 2 + 91 + 6;        // just right of the 182-wide hotbar
        int y = h - 22;
        int cd = LimboAbilityClient.getCooldownTicks();
        boolean ready = cd <= 0;

        g.fill(x, y, x + size, y + size, 0xAA000000); // backdrop
        float frac = ready ? 1.0F : 1.0F - cd / (float) LimboAbility.COOLDOWN_TICKS;
        int fillH = Math.max(0, Math.min(size, Math.round(size * frac)));
        g.fill(x, y + (size - fillH), x + size, y + size, ready ? GREEN : (0x80000000 | (GREEN & 0xFFFFFF)));
        // border
        g.fill(x, y, x + size, y + 1, 0xFFFFFFFF);
        g.fill(x, y + size - 1, x + size, y + size, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + size, 0xFFFFFFFF);
        g.fill(x + size - 1, y, x + size, y + size, 0xFFFFFFFF);

        if (!ready) {
            String s = String.valueOf(cd / 20 + 1); // seconds remaining (rounded up)
            g.drawString(mc.font, s, x + (size - mc.font.width(s)) / 2 + 1, y + 5, 0xFFFFFFFF, false);
        } else {
            // Charged: show the fire key ("G") so the player knows how to unleash the beam.
            String key = "G";
            g.drawString(mc.font, key, x + (size - mc.font.width(key)) / 2 + 1, y + 5, 0xFF0A2A12, false);
        }
    }
}
