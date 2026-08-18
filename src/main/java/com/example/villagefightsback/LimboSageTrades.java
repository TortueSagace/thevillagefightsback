package com.example.villagefightsback;

import java.util.Optional;

import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The Limbo Sage's wares. EVERYTHING is expensive in emeralds -- the sage deals in Limbo curios and luxury gear,
 * and the prices escalate hard across the five tiers. The master tiers sell RANDOMLY ENCHANTED Limbo TOOLS;
 * Limbo ARMOR is NEVER offered.
 *
 * <p>Trades are plain {@link VillagerTrades.ItemListing} lambdas (vanilla's ItemsForEmeralds is package-private),
 * each producing a {@link MerchantOffer} of "N emeralds -> item". They are added to the per-tier lists from the
 * {@link VillagerTradesEvent} only when the traded profession is the Limbo Sage.</p>
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LimboSageTrades {
    private LimboSageTrades() {}

    /** Level the sage's master tiers enchant their tools to (random book-style enchant via EnchantmentHelper). */
    private static final int ENCHANT_LEVEL = 28;

    @SubscribeEvent
    public static void onTrades(VillagerTradesEvent event) {
        if (event.getType() != ModVillagers.LIMBO_SAGE.get()) return;
        var trades = event.getTrades();

        // --- Tier 1 (novice): useless Limbo curio blocks -- cheap by sage standards. ---
        trades.get(1).add(emeraldsFor(new ItemStack(ModBlocks.FAKE_BEDROCK_ITEM.get()), 6, 8, 2));
        // The Limbo Book, at the price the debris block used to go for. A manual about the place is a far
        // better novice offer than a decorative block, and the sage is the one native who would sell one.
        trades.get(1).add(emeraldsFor(new ItemStack(ModItems.LIMBO_BOOK.get()), 8, 8, 2));
        trades.get(1).add(emeraldsFor(new ItemStack(ModItems.FORGOTTEN_POWDER.get(), 2), 12, 6, 3));

        // --- Tier 2 (apprentice): glowstone + the drinkable Limbo potion -- pricier. ---
        trades.get(2).add(emeraldsFor(new ItemStack(ModBlocks.FORGOTTEN_GLOWSTONE_ITEM.get()), 18, 6, 5));
        trades.get(2).add(emeraldsFor(new ItemStack(ModItems.LIMBO_POTION.get()), 22, 4, 5));

        // --- Tier 3 (journeyman): obsidian gate block, the thrown volatile potion, an actual Limbo Egg. ---
        trades.get(3).add(emeraldsFor(new ItemStack(ModBlocks.FAKE_OBSIDIAN_ITEM.get()), 26, 5, 8));
        trades.get(3).add(emeraldsFor(new ItemStack(ModItems.VOLATILE_LIMBO_POTION.get()), 32, 3, 10));
        // The literal Limbo Egg (the dragon-egg-like block that hatches a phantom), NOT a phantom spawn egg.
        trades.get(3).add(emeraldsFor(new ItemStack(ModBlocks.LIMBO_EGG_ITEM.get()), 40, 2, 12));

        // --- Tier 4 (expert): a randomly enchanted Limbo tool, VERY expensive. ---
        trades.get(4).add(enchantedTool(ModItems.LIMBO_PICKAXE, 44, 2, 15));
        trades.get(4).add(enchantedTool(ModItems.LIMBO_SWORD, 48, 2, 15));

        // --- Tier 5 (master): a randomly enchanted Limbo tool, the priciest of all. ---
        trades.get(5).add(enchantedTool(ModItems.LIMBO_AXE, 56, 2, 30));
        trades.get(5).add(enchantedTool(ModItems.LIMBO_SHOVEL, 60, 2, 30));
        trades.get(5).add(enchantedTool(ModItems.LIMBO_HOE, 64, 2, 30));
    }

    /** A plain "N emeralds -> {@code result}" listing (no crafting recipe involved). */
    private static VillagerTrades.ItemListing emeraldsFor(ItemStack result, int emeralds, int maxUses, int xp) {
        return (trader, rand) -> new MerchantOffer(
                new ItemCost(Items.EMERALD, emeralds), result.copy(), maxUses, xp, 0.05F);
    }

    /**
     * A "N emeralds -> randomly enchanted Limbo TOOL" listing. The tool comes out enchanted via
     * {@link EnchantmentHelper#enchantItem} (treasure = false), so each offer is a fresh roll. NEVER used for
     * armor -- only the five Limbo tools are passed in here.
     */
    private static VillagerTrades.ItemListing enchantedTool(DeferredHolder<net.minecraft.world.item.Item, net.minecraft.world.item.Item> tool,
            int emeralds, int maxUses, int xp) {
        return (trader, rand) -> {
            ItemStack stack = new ItemStack(tool.get());
            EnchantmentHelper.enchantItem(rand, stack, ENCHANT_LEVEL, trader.level().registryAccess(), Optional.empty());
            return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), stack, maxUses, xp, 0.05F);
        };
    }
}
