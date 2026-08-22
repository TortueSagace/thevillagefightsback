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
 * The Gas Mask, at 2% in a village's chests.
 *
 * <p>It has no recipe on purpose. The only thing that keeps the Limbo's air out of you is something you find
 * in somebody else's cupboard, which makes the Limbo a place you prepare for rather than kit up for.
 *
 * <p>"A village's chests" means the ones the brain node owns; see {@link VillageLoot}.
 */
public class GasMaskLootModifier extends LootModifier {
    public static final MapCodec<GasMaskLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> LootModifier.codecStart(inst).and(
                    Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance)
            ).apply(inst, GasMaskLootModifier::new));

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
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
