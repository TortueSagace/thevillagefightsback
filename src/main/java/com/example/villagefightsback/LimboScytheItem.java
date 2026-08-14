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
 * <p>Crafted from a Limbo hoe: amethyst blocks at the four corners, a golden apple above it and soul sand to
 * either side and below. The Limbo hoe itself is smithed from netherite, so a scythe still costs a trip to the
 * Limbo for the membrane behind it.
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
    public static final int MAX_SOULS = 8;

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

    /** Burn one soul. Public because dying spends them too, from every scythe carried (see LimboScytheEvents). */
    public static void spendSoul(ItemStack stack) {
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

        boolean mcaGrave = com.example.villagefightsback.compat.MCATombstones.isOccupiedGrave(sl, pos);
        boolean ourGrave = sl.getBlockEntity(pos) instanceof MemorialBlockEntity m && m.hasOccupant();
        if (!mcaGrave && !ourGrave) return InteractionResult.PASS; // not a grave: normal hoe behaviour

        if (souls(stack) <= 0) {
            tell(ctx, "The scythe holds no soul. Reap something worthy first.", ChatFormatting.GRAY);
            return InteractionResult.CONSUME;
        }
        boolean raised = mcaGrave
                ? com.example.villagefightsback.compat.MCATombstones.raise(sl, pos)
                : raiseFromMemorial(sl, pos);
        if (!raised) return InteractionResult.PASS;

        spendSoul(stack);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.02);
        sl.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);
        tell(ctx, "A soul returns to the village.", ChatFormatting.GREEN);
        // Success branch only: a swing that failed for want of a soul earns nothing.
        if (ctx.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp) {
            ModTriggers.RESURRECTION.trigger(sp);
        }
        return InteractionResult.CONSUME;
    }

    /** Empty this mod's own memorial and release whoever lies in it. */
    private static boolean raiseFromMemorial(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof MemorialBlockEntity grave)) return false;
        net.minecraft.nbt.CompoundTag occupant = grave.takeOccupant();
        if (occupant == null) return false;
        if (!Graves.spawnFromNbt(level, pos, occupant)) return false;
        com.example.villagefightsback.compat.MCATombstones.unregisterGrave(level, pos);
        level.destroyBlock(pos, false);   // the grave has given up its occupant
        return true;
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
