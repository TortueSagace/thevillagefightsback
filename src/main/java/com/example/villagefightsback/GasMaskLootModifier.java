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
 * The Gas Mask, at 2% in a village's chests.
 *
 * <p>It has no recipe on purpose. The only thing that keeps the Limbo's air out of you is something you
 * find in somebody else's cupboard, which makes the Limbo a place you prepare for rather than a place you
 * kit up for -- and makes a village worth searching even once you have outgrown its emeralds.
 *
 * <p>"A village's chests" means the ones the brain node owns; see {@link VillageLoot}.
 */
public class GasMaskLootModifier extends LootModifier {
    public static final Supplier<Codec<GasMaskLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> LootModifier.codecStart(inst).and(
                    Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance)
            ).apply(inst, GasMaskLootModifier::new)));

    private final float chance;

    protected GasMaskLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (VillageLoot.isVillageChest(context) && context.getRandom().nextFloat() < chance) {
            generatedLoot.add(new ItemStack(ModArmor.GAS_MASK.get()));
        }
        return generatedLoot;
    }


    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
