package com.example.villagefightsback;

import com.mojang.serialization.Codec;

import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Global loot modifier serializer registration (the chest Limbo-potion drop). */
public final class ModLoot {
    private ModLoot() {}

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, TheVillageFightsBack.MODID);

    public static final RegistryObject<Codec<LimboPotionLootModifier>> LIMBO_POTION =
            SERIALIZERS.register("limbo_potion", LimboPotionLootModifier.CODEC);

    public static final RegistryObject<Codec<LimboTemplateLootModifier>> LIMBO_SMITHING_TEMPLATE =
            SERIALIZERS.register("limbo_smithing_template", LimboTemplateLootModifier.CODEC);

    /** The Gas Mask, 2% in a village's chests. See {@link GasMaskLootModifier}. */
    public static final RegistryObject<Codec<GasMaskLootModifier>> GAS_MASK =
            SERIALIZERS.register("gas_mask", GasMaskLootModifier.CODEC);

    public static final RegistryObject<Codec<RickrollLootModifier>> RICKROLL =
            SERIALIZERS.register("rickroll", RickrollLootModifier.CODEC);

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
