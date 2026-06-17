package com.example.villagefightsback;

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
                || stack.getItem() == ModArmor.LIMBO_ELYTRA_CHESTPLATE.get()
                || stack.getItem() == ModArmor.LIMBO_LEGGINGS.get()
                || stack.getItem() == ModArmor.LIMBO_BOOTS.get();
    }

    /** True for either chest piece of the set: the plain Limbo chestplate or the elytra variant. */
    private static boolean isLimboChest(ItemStack stack) {
        return stack.getItem() == ModArmor.LIMBO_CHESTPLATE.get()
                || stack.getItem() == ModArmor.LIMBO_ELYTRA_CHESTPLATE.get();
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
                && isLimboChest(e.getItemBySlot(EquipmentSlot.CHEST))
                && e.getItemBySlot(EquipmentSlot.LEGS).getItem() == ModArmor.LIMBO_LEGGINGS.get()
                && e.getItemBySlot(EquipmentSlot.FEET).getItem() == ModArmor.LIMBO_BOOTS.get();
    }

    /** Convenience: is this stack a Limbo armour item of the given armour slot? */
    public static boolean isPieceForSlot(ItemStack stack, EquipmentSlot slot) {
        return stack.getItem() instanceof ArmorItem ai && ai.getMaterial() == LimboArmorMaterial.INSTANCE
                && ai.getEquipmentSlot() == slot;
    }

    /** Does the entity's bounding box currently overlap any bedrock block? Used to let a full-set wearer (and
     *  the Limbo Phantom) phase through bedrock: BlockPos.containing includes the boundary block, so this is
     *  true when flush against a bedrock wall but false when merely standing on a bedrock floor. */
    public static boolean touchesBedrock(net.minecraft.world.entity.Entity e) {
        net.minecraft.world.phys.AABB box = e.getBoundingBox();
        net.minecraft.core.BlockPos min = net.minecraft.core.BlockPos.containing(box.minX, box.minY, box.minZ);
        net.minecraft.core.BlockPos max = net.minecraft.core.BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(min, max)) {
            if (e.level().getBlockState(p).is(net.minecraft.world.level.block.Blocks.BEDROCK)) return true;
        }
        return false;
    }
}
