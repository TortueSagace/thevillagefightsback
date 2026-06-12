package com.example.villagergunners;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import java.util.function.Supplier;

/**
 * Global loot modifier: with a small {@code chance}, drops a Limbo Potion into any VILLAGE chest
 * ({@code minecraft:chests/village/*}) or any of our mod's structure chests ({@code gobacktoyourworld:chests/*},
 * which is what the 7 NBT buildings fill from). One modifier covers both -- it inspects the queried loot
 * table id rather than relying on per-table conditions.
 */
public class LimboPotionLootModifier extends LootModifier {
    public static final Supplier<Codec<LimboPotionLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> LootModifier.codecStart(inst).and(
                    Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance)
            ).apply(inst, LimboPotionLootModifier::new)));

    private final float chance;

    protected LimboPotionLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ResourceLocation table = context.getQueriedLootTableId();
        if (table != null && isTargetChest(table) && context.getRandom().nextFloat() < chance) {
            generatedLoot.add(new ItemStack(ModItems.LIMBO_POTION.get()));
        }
        return generatedLoot;
    }

    private static boolean isTargetChest(ResourceLocation id) {
        if ("minecraft".equals(id.getNamespace()) && id.getPath().startsWith("chests/village/")) return true;
        return VillagerGunners.MODID.equals(id.getNamespace()) && id.getPath().startsWith("chests/");
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
