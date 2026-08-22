package com.example.villagefightsback;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Global loot modifier: with a small {@code chance}, drops a Limbo Potion into any VILLAGE chest
 * ({@code minecraft:chests/village/*}) or any of our mod's structure chests ({@code thevillagefightsback:chests/*},
 * which is what the 7 NBT buildings fill from). One modifier covers both -- it inspects the queried loot
 * table id rather than relying on per-table conditions.
 */
public class LimboPotionLootModifier extends LootModifier {
    public static final MapCodec<LimboPotionLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> LootModifier.codecStart(inst).and(
                    Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance)
            ).apply(inst, LimboPotionLootModifier::new));

    private final float chance;

    protected LimboPotionLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (VillageLoot.isVillageChest(context) && context.getRandom().nextFloat() < chance) {
            generatedLoot.add(new ItemStack(ModItems.LIMBO_POTION.get()));
        }
        return generatedLoot;
    }


    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
