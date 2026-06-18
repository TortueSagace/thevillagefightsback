package com.example.villagefightsback;

import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The "soul gravel" carried form of a Limbo leash. Silk-mining a creature's gravel block converts it into ONE
 * of these (non-stackable, named after the creature). While it is carried or stored the creature stays banished;
 * the leash data travels in the item's NBT under {@code LimboHold} (the leash {@code token}, the held creature,
 * its origin dimension, name, type id and max-health, and whether it is a wither-storm part). Placing it back
 * re-anchors the leash ({@link LimboManager#onSoulGravelPlaced}); when it is freed (loose &gt;3 s / destroyed)
 * the creature returns ({@link LimboGravelItemEvents}).
 */
public class LimboSoulGravelItem extends net.minecraft.world.item.BlockItem {
    private static final String HOLD = "LimboHold";

    public LimboSoulGravelItem(Properties properties) {
        super(ModBlocks.LIMBO_GRAVEL.get(), properties);
    }

    /** Build the carried item that represents this leash. */
    public static ItemStack of(LimboData.Entry e) {
        ItemStack stack = new ItemStack(ModBlocks.LIMBO_SOUL_GRAVEL.get());
        CompoundTag t = stack.getOrCreateTagElement(HOLD);
        t.putUUID("token", e.token);
        t.putUUID("creature", e.creature);
        t.putString("dim", e.originDim.location().toString());
        t.putString("name", e.name == null ? "" : e.name);
        t.putString("type", e.typeId);
        t.putFloat("maxhp", e.maxHealth);
        t.putBoolean("storm", e.stormPart);
        return stack;
    }

    public static boolean isSoulGravel(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof LimboSoulGravelItem && stack.getTagElement(HOLD) != null;
    }

    public static CompoundTag hold(ItemStack stack) { return stack.getTagElement(HOLD); }

    public static UUID token(ItemStack stack) {
        CompoundTag t = hold(stack);
        return t != null && t.hasUUID("token") ? t.getUUID("token") : null;
    }

    public static String holdName(ItemStack stack) {
        CompoundTag t = hold(stack);
        return t == null ? "" : t.getString("name");
    }

    public static String typeId(ItemStack stack) {
        CompoundTag t = hold(stack);
        return t == null ? "" : t.getString("type");
    }

    @Override
    public Component getName(ItemStack stack) {
        String n = holdName(stack);
        if (!n.isEmpty()) return Component.literal(n).append(Component.literal("'s Soul"))
                .withStyle(ChatFormatting.DARK_PURPLE);
        return Component.translatable(getDescriptionId(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag t = hold(stack);
        if (t != null && t.getBoolean("storm")) {
            tooltip.add(Component.literal("A piece of a banished Wither Storm.").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Break any piece and the whole storm returns.").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal("A soul held in Limbo. Free it and it returns.").withStyle(ChatFormatting.GRAY));
        }
    }
    // Re-anchoring on placement is handled by LimboGravelBlock.setPlacedBy (a block-level hook).
}
