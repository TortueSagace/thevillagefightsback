package com.example.villagefightsback;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The three mace enchantments (Density, Breach, Wind Burst) are VANILLA in 1.21.1, so the mod no longer
 * registers its own clones. This class is now a thin holder-resolution helper around the vanilla enchantment
 * {@link ResourceKey}s, plus the same numeric effect getters used by {@link MaceCombatEvents}. The custom
 * {@code Enchantment} subclasses, the {@code MACE_CATEGORY}, and the {@code DeferredRegister<Enchantment>}
 * were removed (data-driven enchantments cannot be subclassed in 1.21). Whether the mace can take these
 * enchantments is now controlled by the {@code data/minecraft/tags/.../enchantable/mace.json} item tags.
 */
public final class ModEnchantments {
    private ModEnchantments() {}

    // Vanilla enchantment keys (resolved to Holders against the world's registry access at use time).
    public static final ResourceKey<Enchantment> DENSITY = Enchantments.DENSITY;
    public static final ResourceKey<Enchantment> BREACH = Enchantments.BREACH;
    public static final ResourceKey<Enchantment> WIND_BURST = Enchantments.WIND_BURST;

    /** Resolve a vanilla enchantment key to its Holder via a registry access. */
    public static Holder<Enchantment> holder(RegistryAccess access, ResourceKey<Enchantment> key) {
        return access.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    /** Resolve a vanilla enchantment key to its Holder via a living entity's level. */
    public static Holder<Enchantment> holder(LivingEntity holderEntity, ResourceKey<Enchantment> key) {
        return holder(holderEntity.level().registryAccess(), key);
    }

    private static int levelOf(LivingEntity holderEntity, ResourceKey<Enchantment> key, ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(holder(holderEntity, key), stack);
    }

    // --- effect helpers (read off a held stack) ----------------------------------------------------

    /** Density's extra smash damage PER block of fall distance (0.5 * level). 0 if not present. */
    public static float getDensityBonusPerFall(LivingEntity holderEntity, ItemStack stack) {
        return 0.5F * levelOf(holderEntity, DENSITY, stack);
    }

    /** Breach's armour-penetration fraction (0.15 * level). 0 if not present. */
    public static float getBreachFraction(LivingEntity holderEntity, ItemStack stack) {
        return 0.15F * levelOf(holderEntity, BREACH, stack);
    }

    /** Wind Burst's upward launch strength for the attacker (level1=1.2, level2=1.75, level3=2.2). 0 if absent. */
    public static double getWindBurstLaunch(LivingEntity holderEntity, ItemStack stack) {
        int level = levelOf(holderEntity, WIND_BURST, stack);
        return switch (level) {
            case 1 -> 1.2;
            case 2 -> 1.75;
            case 3 -> 2.2;
            default -> 0.0;
        };
    }

    /** Resolve a vanilla enchantment key to its Holder via a HolderLookup provider (e.g. for data setup). */
    public static Holder<Enchantment> holder(HolderLookup.Provider provider, ResourceKey<Enchantment> key) {
        return provider.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }
}
