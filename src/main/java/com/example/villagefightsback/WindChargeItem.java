package com.example.villagefightsback;

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
 * A NATIVE clone of the vanilla 1.21 Wind Charge item. Right-click hurls a {@link WindChargeEntity} along the
 * thrower's look direction; on contact it bursts into a knockback-only gust (no block/explosion damage). A
 * short cooldown gates re-throws. Throwing consumes one (unless in creative). Not craftable -- only sold by
 * the Weaponsmith and dropped by guards (see {@link VillageEvents}).
 */
public class WindChargeItem extends Item {
    private static final int COOLDOWN_TICKS = 10;

    public WindChargeItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // SNOWBALL_THROW: 1.20.1 has no dedicated wind-charge-throw sound.
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            WindChargeEntity charge = new WindChargeEntity(level, player);
            charge.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(charge);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // The inventory tooltip (.desc) is added centrally by ModTooltips; no per-item override (it would double).
}
