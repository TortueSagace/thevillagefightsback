package com.example.villagefightsback;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Global loot modifier: with a small {@code chance}, drops a Limbo Smithing Template into any VILLAGE chest
 * ({@code minecraft:chests/village/*}). The template is the rare key that lets a player smith netherite gear
 * into Limbo gear -- so it is intentionally vanilla-village-only (not our own structure chests).
 */
public class LimboTemplateLootModifier extends LootModifier {
    public static final MapCodec<LimboTemplateLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> LootModifier.codecStart(inst).and(
                    Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance)
            ).apply(inst, LimboTemplateLootModifier::new));

    private final float chance;

    protected LimboTemplateLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ResourceLocation table = context.getQueriedLootTableId();
        if (table != null && isVillageChest(table) && context.getRandom().nextFloat() < chance) {
            generatedLoot.add(new ItemStack(ModItems.LIMBO_SMITHING_TEMPLATE.get()));
        }
        return generatedLoot;
    }

    private static boolean isVillageChest(ResourceLocation id) {
        return "minecraft".equals(id.getNamespace()) && id.getPath().startsWith("chests/village/");
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
