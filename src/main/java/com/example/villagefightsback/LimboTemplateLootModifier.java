package com.example.villagefightsback;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import java.util.function.Supplier;

/**
 * Global loot modifier: with a small {@code chance}, drops a Limbo Smithing Template into any VILLAGE chest
 * ({@code minecraft:chests/village/*}). The template is the rare key that lets a player smith netherite gear
 * into Limbo gear -- so it is intentionally vanilla-village-only (not our own structure chests).
 */
public class LimboTemplateLootModifier extends LootModifier {
    public static final Supplier<Codec<LimboTemplateLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> LootModifier.codecStart(inst).and(
                    Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance)
            ).apply(inst, LimboTemplateLootModifier::new)));

    private final float chance;

    protected LimboTemplateLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (VillageLoot.isVillageChest(context) && context.getRandom().nextFloat() < chance) {
            generatedLoot.add(new ItemStack(ModItems.LIMBO_SMITHING_TEMPLATE.get()));
        }
        return generatedLoot;
    }


    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
