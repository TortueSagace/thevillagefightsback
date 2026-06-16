package com.example.villagefightsback;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The stage-3 guard's gear: an enchanted (native) Mace, a crossbow, the Alien Slasher melee fallback, and the
 * emerald armour set (each piece force-enchanted with Fire + Blast Protection). Everything here is native to
 * this mod now -- the mace, the wind charge, and the mace enchantments are all our own ({@link ModItems},
 * {@link ModEnchantments}); there is no longer any third-party soft dependency for the mace gear.
 */
public final class GuardWeapons {
    private GuardWeapons() {}

    private static ItemStack mace, crossbow, slasher, eradicator; // built lazily (registries ready after construction)

    /** Our native Mace with Density 3 + Wind Burst 3 + Fire Aspect 2 (the stage-3/4 guard loadout). */
    public static ItemStack mace() {
        if (mace == null) {
            ItemStack s = new ItemStack(ModItems.MACE.get());
            s.enchant(ModEnchantments.DENSITY.get(), 3);
            s.enchant(ModEnchantments.WIND_BURST.get(), 3);
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

    /** The Alien Slasher (its on-hit Emerald-Virus payload -- Virus I -- is applied by EmeraldVirusEvents). */
    public static ItemStack slasher() {
        if (slasher == null) slasher = new ItemStack(ModItems.ALIEN_SLASHER.get());
        return slasher;
    }

    /** The Alien Eradicator (the stage-4 melee weapon: same as the slasher but its on-hit payload is Virus II). */
    public static ItemStack eradicator() {
        if (eradicator == null) eradicator = new ItemStack(ModItems.ALIEN_ERADICATOR.get());
        return eradicator;
    }

    /** Our mace always exists (it is a native item), so the guard's bounce/smash behaviour is always available. */
    public static boolean hasMace() {
        return true;
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
}
