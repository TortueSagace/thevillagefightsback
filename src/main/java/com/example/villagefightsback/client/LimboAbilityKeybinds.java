package com.example.villagefightsback.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

import org.lwjgl.glfw.GLFW;

/**
 * Registers the Limbo banish-beam keybind (default: G). Only meaningful while the full Limbo set is worn.
 *
 * <p>Forge scopes the binding to in-game with a KeyConflictContext. Fabric has no equivalent, so the guard is
 * where it has to be anyway: {@code LimboAbilityClient} ignores the key while a screen is open.
 */
public final class LimboAbilityKeybinds {
    private LimboAbilityKeybinds() {}

    public static final KeyMapping LIMBO_BEAM = new KeyMapping(
            "key.thevillagefightsback.limbo_beam",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.thevillagefightsback");

    public static void register() {
        KeyBindingHelper.registerKeyBinding(LIMBO_BEAM);
    }
}
