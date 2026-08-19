package com.example.villagefightsback;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * The three extra chest drops the mod sprinkles into village loot.
 *
 * <p>On Forge these are global loot modifiers: three classes plus three json files declaring a chance, rolled
 * against every table as it generates. Fabric has no such registry, so the same thing is expressed the way
 * vanilla would -- an extra one-entry pool, guarded by a random-chance condition, grafted onto each matching
 * table as it loads. The odds and the tables they apply to are unchanged.
 *
 * <p>The one thing lost in the move is that the chances used to sit in a datapack-overridable json. They are
 * constants here; a datapack that wants different odds has to override the loot table itself.
 */
public final class ModLoot {
    private ModLoot() {}

    /** A Limbo Potion in a village chest: vanilla villages and this mod's own structures. */
    private static final float LIMBO_POTION_CHANCE = 0.04F;

    /**
     * A Limbo Smithing Template, vanilla villages only. It is a progression key, so it stays on the vanilla loot
     * curve rather than turning up in the village's own buildings.
     */
    private static final float LIMBO_TEMPLATE_CHANCE = 0.01F;

    /** The Rickroll painting. A joke, so it gets the wider net -- our chests count as village chests too. */
    private static final float RICKROLL_CHANCE = 0.005F;

    public static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (isVillageChest(id)) {
                tableBuilder.withPool(chancePool(ModItems.LIMBO_POTION.get(), LIMBO_POTION_CHANCE));
                tableBuilder.withPool(chancePool(ModItems.RICKROLL_PAINTING.get(), RICKROLL_CHANCE));
            }
            if (isVanillaVillageChest(id)) {
                tableBuilder.withPool(chancePool(ModItems.LIMBO_SMITHING_TEMPLATE.get(), LIMBO_TEMPLATE_CHANCE));
            }
        });
    }

    /** One roll, one item, gated on {@code chance} -- the same shot the loot modifier used to take. */
    private static LootPool.Builder chancePool(Item item, float chance) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(item))
                .when(LootItemRandomChanceCondition.randomChance(chance));
    }

    /** Vanilla village chests, plus every chest this mod's own village structures generate. */
    private static boolean isVillageChest(ResourceLocation id) {
        if (isVanillaVillageChest(id)) return true;
        return TheVillageFightsBack.MODID.equals(id.getNamespace()) && id.getPath().startsWith("chests/");
    }

    private static boolean isVanillaVillageChest(ResourceLocation id) {
        return "minecraft".equals(id.getNamespace()) && id.getPath().startsWith("chests/village/");
    }
}
