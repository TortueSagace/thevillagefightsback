package com.example.villagefightsback;

import java.util.UUID;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * A NATIVE clone of the vanilla 1.21 Mace (so the mod no longer needs any third-party weapon mod). It is a
 * heavy one-handed weapon: +5 attack damage / -3.4 attack speed (so 6 damage and a slow swing), 500 durability,
 * EPIC rarity, and an enchantment value of 15. Its signature SMASH attack (heavy bonus damage + area knockback
 * on a downward strike from a fall) lives entirely in the Forge-bus {@link MaceCombatEvents} so we never touch a
 * mixin; the mace enchantments (Density, Breach, Wind Burst from {@link ModEnchantments}) feed that same code.
 */
public class MaceItem extends Item {
    // Vanilla mace attribute additions (TieredItem/DiggerItem-style modifiers, fixed UUIDs so they're stable).
    private static final UUID BASE_ATTACK_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID BASE_ATTACK_SPEED_UUID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");
    public static final float ATTACK_DAMAGE = 5.0F;   // +5 (-> 6 shown)
    public static final float ATTACK_SPEED = -3.4F;   // slow, heavy swing

    private final Multimap<Attribute, AttributeModifier> mainhandModifiers;

    public MaceItem(Properties properties) {
        super(properties.durability(500).rarity(Rarity.EPIC));
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", ATTACK_DAMAGE, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                BASE_ATTACK_SPEED_UUID, "Weapon modifier", ATTACK_SPEED, AttributeModifier.Operation.ADDITION));
        this.mainhandModifiers = b.build();
    }

    /** True if {@code stack} is one of our maces (used by the mace EnchantmentCategory and the smash handler). */
    public static boolean isMace(ItemStack stack) {
        return stack != null && stack.getItem() instanceof MaceItem;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.mainhandModifiers : super.getDefaultAttributeModifiers(slot);
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
            stack.hurtAndBreak(2, miner, m -> m.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
        return true;
    }

    /** True if {@code ench} can be applied to this mace (only the mace-category enchants + a few generic ones). */
    public static boolean canApply(Enchantment ench, ItemStack stack) {
        return ench.canEnchant(stack);
    }

    // The inventory tooltip (.desc) is added centrally by ModTooltips; no per-item override (it would double).
}
