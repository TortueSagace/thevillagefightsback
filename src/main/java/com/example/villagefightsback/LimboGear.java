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

    /** A block is "soft" to the Limbo set if it is breakable and strictly LESS tough than ancient debris
     *  (hardness 30): stone, cobble, logs, etc. break; obsidian, ancient debris, reinforced deepslate and
     *  bedrock do not. */
    public static final float ANCIENT_DEBRIS_HARDNESS = 30.0F;

    /** Our fake/phase-through "door" blocks: allies (and full-set wearers) walk through them, so the contact
     *  break must NEVER destroy them or it would smash the door you are simply passing through. */
    public static boolean isPhaseDoor(net.minecraft.world.level.block.state.BlockState s) {
        return s.getBlock() instanceof FakeCobblestoneBlock
                || s.getBlock() instanceof FakeObsidianBlock
                || s.getBlock() instanceof FakeBedrockBlock;
    }

    /**
     * Full-set power: break every block the wearer is in CONTACT with that is softer than ancient debris, in
     * any direction EXCEPT the floor it is standing/walking on (the block below the feet is preserved). The
     * ceiling is included only when the wearer's head actually presses into it (e.g. jumping). The mod's
     * fake-cobblestone / fake-obsidian / fake-bedrock "doors" are never broken (you phase through them).
     * Server-side.
     */
    public static void breakSoftBlocksOnContact(LivingEntity e) {
        if (!(e.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        net.minecraft.world.phys.AABB box = e.getBoundingBox();
        final double m = 0.02; // small contact margin: only blocks actually touched
        int minX = net.minecraft.util.Mth.floor(box.minX - m), maxX = net.minecraft.util.Mth.floor(box.maxX + m);
        int minZ = net.minecraft.util.Mth.floor(box.minZ - m), maxZ = net.minecraft.util.Mth.floor(box.maxZ + m);
        int feetY = net.minecraft.util.Mth.floor(box.minY + m); // floor (below this) is never broken
        int topY = net.minecraft.util.Mth.floor(box.maxY + m);  // includes a block the head presses into
        net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int y = feetY; y <= topY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    p.set(x, y, z);
                    net.minecraft.world.level.block.state.BlockState s = sl.getBlockState(p);
                    if (s.isAir()) continue;
                    if (isPhaseDoor(s)) continue; // never smash a phase-through door you are walking through
                    float h = s.getDestroySpeed(sl, p);
                    if (h < 0.0F || h >= ANCIENT_DEBRIS_HARDNESS) continue; // unbreakable or too tough
                    if (s.is(net.minecraft.world.level.block.Blocks.BEDROCK)) continue; // safety
                    sl.destroyBlock(p.immutable(), true, e); // break + drop, crediting the wearer
                }
            }
        }
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
