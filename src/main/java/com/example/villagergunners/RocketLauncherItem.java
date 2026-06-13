package com.example.villagergunners;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Rocket Launcher: the gigachad golems' weapon, also usable by players. Right-click to fire a {@link
 * RocketEntity} along your look direction, consuming one Rocket ({@link ModItems#ROCKET}) from your inventory.
 * Dropped by gigachad golems + vindictive gigachads.
 */
public class RocketLauncherItem extends Item {
    private static final int COOLDOWN = 40; // 2s between shots

    public RocketLauncherItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack launcher = player.getItemInHand(hand);
        boolean creative = player.getAbilities().instabuild;
        if (!creative && !consumeRocket(player)) {
            // out of ammo
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.7F, 1.0F);
            return InteractionResultHolder.fail(launcher);
        }
        if (!level.isClientSide) {
            RocketEntity.fireFromLook(player);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(launcher, level.isClientSide());
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
