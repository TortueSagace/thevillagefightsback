package com.example.villagefightsback.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Puts the config screen where Quark's is: a button on the title screen, plus NeoForge's own "Config" button in
 * the Mods list (the {@link IConfigScreenFactory} extension point).
 *
 * <p>Registered from the mod constructor whether or not the {@code client} module is on -- a player who turns
 * the HUD off still has to be able to open the config again.
 */
public final class ClientConfigUi {
    private ClientConfigUi() {}

    public static void register(ModContainer container) {
        IConfigScreenFactory factory = (mod, parent) -> new ConfigScreen(parent);
        container.registerExtensionPoint(IConfigScreenFactory.class, factory);
        NeoForge.EVENT_BUS.addListener(ClientConfigUi::onScreenInit);
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) return;

        // Quark's slot: the 20x20 cell just left of the language button. Find that button instead of
        // recomputing the layout, since NeoForge adds buttons of its own to this screen.
        int x = 6;
        int y = screen.height - 26;
        for (var listener : screen.children()) {
            if (listener instanceof AbstractWidget widget
                    && widget.getWidth() == 20 && widget.getHeight() == 20
                    && widget.getX() == screen.width / 2 - 124) {
                x = widget.getX() - 24;
                y = widget.getY();
                break;
            }
        }

        event.addListener(Button.builder(Component.literal("\u2699"),
                        b -> Minecraft.getInstance().setScreen(new ConfigScreen(screen)))
                .bounds(x, y, 20, 20)
                .tooltip(Tooltip.create(Component.literal("The Village Fights Back -- configuration")))
                .build());
    }
}
