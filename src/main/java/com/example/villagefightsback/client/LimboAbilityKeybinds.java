package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Registers the Limbo banish-beam keybind (default: G). Only meaningful while the full Limbo set is worn. */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LimboAbilityKeybinds {
    private LimboAbilityKeybinds() {}

    public static final KeyMapping LIMBO_BEAM = new KeyMapping(
            "key.gobacktoyourworld.limbo_beam",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.gobacktoyourworld");

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(LIMBO_BEAM);
    }
}
