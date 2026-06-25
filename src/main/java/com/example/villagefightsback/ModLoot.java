package com.example.villagefightsback;

import com.mojang.serialization.MapCodec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Global loot modifier serializer registration (the chest Limbo-potion drop). */
public final class ModLoot {
    private ModLoot() {}

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, TheVillageFightsBack.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<LimboPotionLootModifier>> LIMBO_POTION =
            SERIALIZERS.register("limbo_potion", () -> LimboPotionLootModifier.CODEC);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<LimboTemplateLootModifier>> LIMBO_SMITHING_TEMPLATE =
            SERIALIZERS.register("limbo_smithing_template", () -> LimboTemplateLootModifier.CODEC);

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
