package com.example.villagefightsback.mixin.client;

import com.example.villagefightsback.LimboArmorItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;

/**
 * Gives worn Limbo armour its animated texture.
 *
 * <p>The icon and held textures animate from an .mcmeta sprite, but vanilla armour-layer textures are static and
 * their path is derived from the armour material. Forge lets the item name its own; here the layer is asked to
 * hand back the frame for the current game time instead.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Inject(method = "getArmorLocation", at = @At("HEAD"), cancellable = true)
    private void tvfb$animateLimboArmor(ArmorItem item, boolean innerModel, String type,
                                        CallbackInfoReturnable<ResourceLocation> cir) {
        if (type != null) return;                 // no "overlay" layer for our gear
        if (!(item instanceof LimboArmorItem)) return;
        long gameTime = Minecraft.getInstance().level == null ? 0L
                : Minecraft.getInstance().level.getGameTime();
        cir.setReturnValue(LimboArmorItem.armorTexture(innerModel, gameTime));
    }
}
