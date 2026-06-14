package com.example.villagefightsback;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The stage-3 guard's gear: an enchanted mace_port mace, a crossbow, the Alien Slasher melee fallback, and
 * the emerald armour set (each piece force-enchanted with Fire + Blast Protection). mace_port is a soft
 * dependency (resolved by registry id), so a missing mace falls back to the Alien Slasher.
 */
public final class GuardWeapons {
    private GuardWeapons() {}

    private static final ResourceLocation MACE_ID = new ResourceLocation("mace_port", "mace");
    private static final ResourceLocation DENSITY_ID = new ResourceLocation("mace_port", "density");
    private static final ResourceLocation WIND_BURST_ID = new ResourceLocation("mace_port", "wind_burst");

    private static ItemStack mace, crossbow, slasher; // built lazily (registries ready after construction)

    /** mace_port mace with Density 3 + Wind Burst 3 + Fire Aspect 2 (Alien Slasher if mace_port is absent). */
    public static ItemStack mace() {
        if (mace == null) {
            Item maceItem = ForgeRegistries.ITEMS.getValue(MACE_ID);
            if (maceItem == null) { mace = slasher(); return mace; }
            ItemStack s = new ItemStack(maceItem);
            enchant(s, DENSITY_ID, 3);
            enchant(s, WIND_BURST_ID, 3);
            s.enchant(Enchantments.FIRE_ASPECT, 2);
            mace = s;
        }
        return mace;
    }

    /** Vanilla crossbow with Quick Charge III + Piercing IV. */
    public static ItemStack crossbow() {
        if (crossbow == null) {
            ItemStack s = new ItemStack(Items.CROSSBOW);
            s.enchant(Enchantments.QUICK_CHARGE, 3);
            s.enchant(Enchantments.PIERCING, 4);
            crossbow = s;
        }
        return crossbow;
    }

    /** The Alien Slasher (its on-hit Emerald-Virus payload is applied by EmeraldVirusEvents). */
    public static ItemStack slasher() {
        if (slasher == null) slasher = new ItemStack(ModItems.ALIEN_SLASHER.get());
        return slasher;
    }

    /** True if the mace was actually built from a real mace_port mace (so bouncing/smashing makes sense). */
    public static boolean hasMace() {
        return ForgeRegistries.ITEMS.containsKey(MACE_ID);
    }

    /** Dress the guard in emerald armour, each piece force-enchanted with Fire + Blast Protection. */
    public static void equipEmeraldArmor(Mob guard) {
        equip(guard, EquipmentSlot.HEAD, ModArmor.EMERALD_HELMET.get());
        equip(guard, EquipmentSlot.CHEST, ModArmor.EMERALD_CHESTPLATE.get());
        equip(guard, EquipmentSlot.LEGS, ModArmor.EMERALD_LEGGINGS.get());
        equip(guard, EquipmentSlot.FEET, ModArmor.EMERALD_BOOTS.get());
    }

    private static void equip(Mob guard, EquipmentSlot slot, Item piece) {
        ItemStack s = new ItemStack(piece);
        // forced together (the two protection types are normally mutually exclusive): both still function.
        s.enchant(Enchantments.FIRE_PROTECTION, 4);
        s.enchant(Enchantments.BLAST_PROTECTION, 4);
        guard.setItemSlot(slot, s);
        guard.setDropChance(slot, 0.0F);
    }

    private static void enchant(ItemStack s, ResourceLocation id, int level) {
        Enchantment e = ForgeRegistries.ENCHANTMENTS.getValue(id);
        if (e != null) s.enchant(e, level);
    }
}
