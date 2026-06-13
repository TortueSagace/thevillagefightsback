package com.example.villagergunners;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Helpers for the Limbo armour set: which pieces are Limbo gear, how many a creature wears, and whether it
 * wears the full set. The full-set bonuses (immunity, fire/fall immunity, phantom neutrality, the banish beam)
 * live in {@link LimboGearEvents}; the worn green glow lives there too.
 */
public final class LimboGear {
    private LimboGear() {}

    public static boolean isLimboPiece(ItemStack stack) {
        return stack.getItem() == ModArmor.LIMBO_HELMET.get()
                || stack.getItem() == ModArmor.LIMBO_CHESTPLATE.get()
                || stack.getItem() == ModArmor.LIMBO_LEGGINGS.get()
                || stack.getItem() == ModArmor.LIMBO_BOOTS.get();
    }

    /** How many Limbo armour pieces the entity wears (0..4). */
    public static int wornCount(LivingEntity e) {
        int n = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (isLimboPiece(e.getItemBySlot(slot))) n++;
        }
        return n;
    }

    /** True only when all four slots hold the matching Limbo piece. */
    public static boolean hasFullSet(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.HEAD).getItem() == ModArmor.LIMBO_HELMET.get()
                && e.getItemBySlot(EquipmentSlot.CHEST).getItem() == ModArmor.LIMBO_CHESTPLATE.get()
                && e.getItemBySlot(EquipmentSlot.LEGS).getItem() == ModArmor.LIMBO_LEGGINGS.get()
                && e.getItemBySlot(EquipmentSlot.FEET).getItem() == ModArmor.LIMBO_BOOTS.get();
    }

    /** Convenience: is this stack a Limbo armour item of the given armour slot? */
    public static boolean isPieceForSlot(ItemStack stack, EquipmentSlot slot) {
        return stack.getItem() instanceof ArmorItem ai && ai.getMaterial() == LimboArmorMaterial.INSTANCE
                && ai.getEquipmentSlot() == slot;
    }
}
