package com.example.villagefightsback;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

/** A Limbo armour piece: an {@link ArmorItem} that always shows the enchantment shimmer, so the gear itself
 *  looks shiny on the icon, in hand, and worn (on top of the animated green-glint texture). */
public class LimboArmorItem extends ArmorItem {
    public LimboArmorItem(ArmorMaterial material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
