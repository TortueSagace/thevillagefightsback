package com.example.villagefightsback;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Limbo Potion: a drinkable that banishes the drinker to Limbo. The actual teleport + the automatic
 * Limbo-gravel marker (dropped where the drinker stood) are handled by {@link LimboDimension#sendToLimbo}
 * and {@link LimboManager} -- so drinking returns an empty bottle and sends you in.
 */
public class LimboPotionItem extends Item {
    public LimboPotionItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32; // same drink time as a vanilla potion
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            LimboDimension.sendToLimbo(entity); // LimboManager drops the gravel marker + registers the entry
        }
        boolean creative = entity instanceof Player p && p.getAbilities().instabuild;
        if (!creative) {
            stack.shrink(1);
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (entity instanceof Player p) {
                if (stack.isEmpty()) return bottle;
                if (!p.getInventory().add(bottle)) p.drop(bottle, false);
            }
        }
        return stack;
    }
}
