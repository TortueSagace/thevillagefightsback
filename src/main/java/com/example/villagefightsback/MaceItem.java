package com.example.villagefightsback;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * A NATIVE clone of the vanilla 1.21 Mace (so the mod no longer needs any third-party weapon mod). It is a
 * heavy one-handed weapon: +5 attack damage / -3.4 attack speed (so 6 damage and a slow swing), 500 durability,
 * EPIC rarity, and an enchantment value of 15. Its signature SMASH attack (heavy bonus damage + area knockback
 * on a downward strike from a fall) lives entirely in the game-bus {@link MaceCombatEvents} so we never touch a
 * mixin; the mace enchantments (Density, Breach, Wind Burst from {@link ModEnchantments}) feed that same code.
 */
public class MaceItem extends Item {
    // Vanilla mace attribute additions (TieredItem/DiggerItem-style modifiers). In 1.21 the modifier id is a
    // ResourceLocation (no UUID/name); we use the vanilla base ids so the mainhand tooltip merges correctly.
    public static final float ATTACK_DAMAGE = 5.0F;   // +5 (-> 6 shown)
    public static final float ATTACK_SPEED = -3.4F;   // slow, heavy swing

    public MaceItem(Properties properties) {
        super(properties.durability(500).rarity(Rarity.EPIC).attributes(createAttributes()));
    }

    /** The mainhand attack-damage / attack-speed modifiers (mirrors the old getAttributeModifiers override). */
    private static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, ATTACK_DAMAGE,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, ATTACK_SPEED,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /** True if {@code stack} is one of our maces (used by the smash handler). */
    public static boolean isMace(ItemStack stack) {
        return stack != null && stack.getItem() instanceof MaceItem;
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }

    /** Mining a block costs 2 durability (vanilla mace behaviour). */
    @Override
    public boolean mineBlock(ItemStack stack, net.minecraft.world.level.Level level,
                             net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos,
                             net.minecraft.world.entity.LivingEntity miner) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F) {
            stack.hurtAndBreak(2, miner, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    /** True if {@code ench} can be applied to this mace. Enchantability is governed by item tags in 1.21. */
    public static boolean canApply(Enchantment ench, ItemStack stack) {
        return stack.getItem() instanceof MaceItem;
    }

    // The inventory tooltip (.desc) is added centrally by ModTooltips; no per-item override (it would double).
}
