package com.example.villagefightsback;

import java.util.List;
import java.util.Map;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The Gas Mask's material: leather, exactly.
 *
 * <p>Same durability, same 1 point of protection, same enchantability, same repair item. It is a filter, not
 * a helmet -- what it is worth is that it keeps the Limbo's air out of you ({@link SlowAnnihilationEvents}),
 * and giving it armour value on top would quietly make it the best head slot in the mod for anyone who found
 * one in a chest.
 *
 * <p>It exists as its own material only so the worn layer resolves to ours: the material's registered name is
 * what Forge builds {@code textures/models/armor/gas_mask_layer_1.png} from, and borrowing vanilla's leather
 * material would put the wearer in a leather cap.
 *
 * <p>In 1.21 {@code ArmorMaterial} is a registered record exposed as a {@code Holder}; the holder is
 * registered in {@link ModArmor} and aliased here, exactly as {@link LimboArmorMaterial} does.
 */
public final class GasMaskArmorMaterial {
    private GasMaskArmorMaterial() {}

    /** The registered material holder (registered in {@link ModArmor}). */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> INSTANCE = ModArmor.GAS_MASK_MATERIAL;

    /** Leather's multiplier. */
    private static final int DURABILITY_MULT = 5;

    public static int durabilityForType(ArmorItem.Type type) {
        int base = switch (type) {
            case BOOTS -> 13;
            case LEGGINGS -> 15;
            case CHESTPLATE -> 16;
            case HELMET -> 11;
            case BODY -> 16;
        };
        return base * DURABILITY_MULT;
    }

    /** Builds the gas-mask {@link ArmorMaterial} record. Called from {@link ModArmor}. */
    public static ArmorMaterial create() {
        return new ArmorMaterial(
                Map.of(
                        ArmorItem.Type.BOOTS, 1,
                        ArmorItem.Type.LEGGINGS, 2,
                        ArmorItem.Type.CHESTPLATE, 3,
                        ArmorItem.Type.HELMET, 1),
                15,
                SoundEvents.ARMOR_EQUIP_LEATHER,
                () -> Ingredient.of(Items.LEATHER),
                List.of(new ArmorMaterial.Layer(TheVillageFightsBack.id("gas_mask"))),
                0.0F,
                0.0F);
    }
}
