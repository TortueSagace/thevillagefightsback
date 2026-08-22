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
 * Global loot modifier: with a small {@code chance}, drops the Rickroll painting into any village chest.
 *
 * <p>Broader than {@link LimboTemplateLootModifier}, which is vanilla-village-only because the Smithing
 * Template is a progression key and belongs on the vanilla loot curve. This is a joke, and the joke is better
 * the more places it can turn up, so it covers OUR structures' chests as well: a basic house and a barracks
 * are village chests too.
 *
 * <p>The item is creative-only in the sense that it has no recipe. This is the one way to find one in
 * survival, and at 0.5% it will take a while.
 */
public class RickrollLootModifier extends LootModifier {
    public static final MapCodec<RickrollLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> LootModifier.codecStart(inst).and(
                    Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance)
            ).apply(inst, RickrollLootModifier::new));

    private final float chance;

    protected RickrollLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (VillageLoot.isVillageChest(context) && context.getRandom().nextFloat() < chance) {
            generatedLoot.add(new ItemStack(ModItems.RICKROLL_PAINTING.get()));
        }
        return generatedLoot;
    }

    /** Vanilla village chests, plus every chest our own village structures generate. */

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
