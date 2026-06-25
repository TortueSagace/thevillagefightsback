package com.example.villagefightsback;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * The Rocket Launcher: the gigachad golems' weapon, also usable by players. Hold right-click to AIM (the
 * launcher is raised like drawing a bow), then release to fire a {@link RocketEntity} along your look,
 * consuming one Rocket ({@link ModItems#ROCKET}). Golems fire it directly via their goal (not this use path).
 */
public class RocketLauncherItem extends Item {
    private static final int COOLDOWN = 40; // 2s between shots

    public RocketLauncherItem(Properties props) {
        super(props);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW; // raised, aiming pose while held
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000; // effectively "hold until released"
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack launcher = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(launcher);
        }
        if (!player.getAbilities().instabuild && !hasRocket(player)) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.7F, 1.0F);
            return InteractionResultHolder.fail(launcher);
        }
        return ItemUtils.startUsingInstantly(level, player, hand); // begin aiming
    }

    /** Fire when the player releases the aim. */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player) || player.getCooldowns().isOnCooldown(this)) return;
        if (!level.isClientSide) {
            if (!player.getAbilities().instabuild && !consumeRocket(player)) return;
            RocketEntity.fireFromLook(player);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    private static boolean hasRocket(Player player) {
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.ROCKET.get())) return true;
        }
        return false;
    }

    /** Remove one Rocket from the player's inventory; returns false if they have none. */
    private static boolean consumeRocket(Player player) {
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.ROCKET.get())) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }
}
