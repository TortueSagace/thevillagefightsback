package com.example.villagefightsback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Limbo Scythe: the reaper's tool, in the shape of a Limbo hoe.
 *
 * <p>It cannot be crafted. Each Limbo creature (a Limbo Phantom or any of the Limbo raiders) has a small chance
 * of dropping one when it falls, so the only way to hold a scythe is to go into the Limbo and take it from the
 * things that live there.
 *
 * <p>It works by SOULS. Reap a creature of {@value #SOUL_HEALTH_MIN} maximum health or more with it and the
 * scythe keeps that soul; spend a soul on a grave and the villager buried there walks out of it. Small creatures
 * carry nothing worth taking, so a soul always costs a real fight.
 *
 * <p>It raises the village's dead from either kind of grave: MCA Reborn's tombstones when that mod is installed,
 * and this mod's memorial otherwise. Only defenders that fought are ever buried, so the scythe can only ever
 * bring back a villager that earned its headstone.
 */
public class LimboScytheItem extends HoeItem {
    /** A creature must have at least this much max health for its soul to be worth taking. */
    public static final float SOUL_HEALTH_MIN = 20.0F;

    /** How many souls one scythe can hold at once. */
    public static final int MAX_SOULS = 16;

    private static final String NBT_SOULS = "vgun_souls";

    public LimboScytheItem(Properties properties) {
        // Reaper's reach: slow and heavy, in line with the rest of the Limbo set rather than a farming tool.
        super(LimboToolTier.INSTANCE, 3, -3.0F, properties);
    }

    /** Souls currently held. */
    public static int souls(ItemStack stack) {
        return stack.getOrCreateTag().getInt(NBT_SOULS);
    }

    /** Store one more soul, up to {@link #MAX_SOULS}. Returns false when the scythe is already full. */
    public static boolean addSoul(ItemStack stack) {
        int held = souls(stack);
        if (held >= MAX_SOULS) return false;
        stack.getOrCreateTag().putInt(NBT_SOULS, held + 1);
        return true;
    }

    private static void spendSoul(ItemStack stack) {
        stack.getOrCreateTag().putInt(NBT_SOULS, Math.max(0, souls(stack) - 1));
    }

    /** The Limbo gear all shimmers; the scythe is no exception, and it is never actually enchanted. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /** Right-click a grave to spend a soul and raise whoever lies in it. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS; // client: let the server decide
        BlockPos pos = ctx.getClickedPos();
        ItemStack stack = ctx.getItemInHand();

        boolean grave = com.example.villagefightsback.compat.MCATombstones.isOccupiedGrave(sl, pos);
        if (!grave) return InteractionResult.PASS;   // not a grave: fall through to normal hoe behaviour

        if (souls(stack) <= 0) {
            tell(ctx, "The scythe holds no soul. Reap something worthy first.", ChatFormatting.GRAY);
            return InteractionResult.CONSUME;
        }
        if (!com.example.villagefightsback.compat.MCATombstones.raise(sl, pos)) {
            return InteractionResult.PASS;
        }

        spendSoul(stack);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.02);
        sl.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);
        tell(ctx, "A soul returns to the village.", ChatFormatting.GREEN);
        return InteractionResult.CONSUME;
    }

    private static void tell(UseOnContext ctx, String msg, ChatFormatting colour) {
        if (ctx.getPlayer() != null) {
            ctx.getPlayer().displayClientMessage(Component.literal(msg).withStyle(colour), true);
        }
    }

    /** Show the stored souls on the tooltip, so the scythe's charge is readable at a glance. */
    @Override
    public void appendHoverText(ItemStack stack, Level level, java.util.List<Component> lines,
                                net.minecraft.world.item.TooltipFlag flag) {
        lines.add(Component.literal("Souls: " + souls(stack) + " / " + MAX_SOULS)
                .withStyle(ChatFormatting.AQUA));
    }
}
