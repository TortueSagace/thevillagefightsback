package com.example.villagefightsback.platform;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Stand-in for Forge's {@code ForgeRegistries}, over the vanilla {@code BuiltInRegistries}. Only the registries
 * this mod registers into or looks things up in are here; add to it rather than reaching for BuiltInRegistries
 * directly, so the Forge branch and this one keep reading alike.
 */
public final class ModRegistries {
    private ModRegistries() {}

    public static final ModRegistry<Block> BLOCKS = new ModRegistry<>(BuiltInRegistries.BLOCK);
    public static final ModRegistry<Item> ITEMS = new ModRegistry<>(BuiltInRegistries.ITEM);
    public static final ModRegistry<EntityType<?>> ENTITY_TYPES = new ModRegistry<>(BuiltInRegistries.ENTITY_TYPE);
    public static final ModRegistry<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            new ModRegistry<>(BuiltInRegistries.BLOCK_ENTITY_TYPE);
    public static final ModRegistry<MobEffect> MOB_EFFECTS = new ModRegistry<>(BuiltInRegistries.MOB_EFFECT);
    public static final ModRegistry<Potion> POTIONS = new ModRegistry<>(BuiltInRegistries.POTION);
    public static final ModRegistry<Enchantment> ENCHANTMENTS = new ModRegistry<>(BuiltInRegistries.ENCHANTMENT);
    public static final ModRegistry<ParticleType<?>> PARTICLE_TYPES =
            new ModRegistry<>(BuiltInRegistries.PARTICLE_TYPE);
    public static final ModRegistry<SoundEvent> SOUND_EVENTS = new ModRegistry<>(BuiltInRegistries.SOUND_EVENT);
    public static final ModRegistry<PoiType> POI_TYPES = new ModRegistry<>(BuiltInRegistries.POINT_OF_INTEREST_TYPE);
    public static final ModRegistry<VillagerProfession> VILLAGER_PROFESSIONS =
            new ModRegistry<>(BuiltInRegistries.VILLAGER_PROFESSION);
}
