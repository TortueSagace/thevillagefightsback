package com.example.villagefightsback.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;

/**
 * Reaches vanilla's private {@code PotionBrewing.addMix}, which is how brewing recipes are declared without
 * Forge's BrewingRecipeRegistry.
 */
@Mixin(PotionBrewing.class)
public interface PotionBrewingInvoker {
    @Invoker("addMix")
    static void invokeAddMix(Potion from, Item ingredient, Potion to) {
        throw new AssertionError("mixin did not apply");
    }
}
