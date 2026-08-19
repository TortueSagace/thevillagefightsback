package com.example.villagefightsback;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.DamageEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import com.example.villagefightsback.platform.registry.DeferredRegister;
import com.example.villagefightsback.platform.ModRegistries;
import com.example.villagefightsback.platform.registry.RegistryObject;

/**
 * The three native mace enchantments (cloned from vanilla 1.21 so the mod owns them outright):
 *   - Density   (UNCOMMON, max 5): boosts the smash's fall-scaling bonus damage.
 *   - Breach    (RARE, max 4): ignores part of the target's armour.
 *   - Wind Burst(RARE treasure, max 3): launches the attacker upward on a successful smash.
 * All three only apply to our {@link MaceItem} (via a custom mace {@link EnchantmentCategory}); their effects
 * are applied by {@link MaceCombatEvents}. Registered in the {@link TheVillageFightsBack} constructor.
 */
public final class ModEnchantments {
    private ModEnchantments() {}

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ModRegistries.ENCHANTMENTS, TheVillageFightsBack.MODID);

    /**
     * These enchantments only ever go on our mace.
     *
     * <p>Forge lets a mod add an EnchantmentCategory; the enum cannot be extended here, so the category is the
     * closest vanilla one and each enchantment overrides {@code canEnchant} to demand an actual mace. That is the
     * method the enchanting table, the anvil and every command path all consult, so nothing else can pick these
     * up -- the same gate, applied one level lower.
     */
    public static final EnchantmentCategory MACE_CATEGORY = EnchantmentCategory.WEAPON;

    /** Shared by the three mace enchantments below: only a mace may carry them. */
    private abstract static class MaceEnchantment extends Enchantment {
        MaceEnchantment(Rarity rarity, EquipmentSlot... slots) {
            super(rarity, MACE_CATEGORY, slots);
        }

        @Override
        public boolean canEnchant(net.minecraft.world.item.ItemStack stack) {
            return stack.getItem() instanceof MaceItem;
        }
    }

    private static final EquipmentSlot[] MAINHAND = { EquipmentSlot.MAINHAND };

    public static final RegistryObject<Enchantment> DENSITY =
            ENCHANTMENTS.register("density", DensityEnchantment::new);
    public static final RegistryObject<Enchantment> BREACH =
            ENCHANTMENTS.register("breach", BreachEnchantment::new);
    public static final RegistryObject<Enchantment> WIND_BURST =
            ENCHANTMENTS.register("wind_burst", WindBurstEnchantment::new);

    public static void register() {
        ENCHANTMENTS.register();
    }

    // --- effect helpers (read off a held stack) ----------------------------------------------------

    /** Density's extra smash damage PER block of fall distance (0.5 * level). 0 if not present. */
    public static float getDensityBonusPerFall(ItemStack stack) {
        return 0.5F * EnchantmentHelper.getItemEnchantmentLevel(DENSITY.get(), stack);
    }

    /** Breach's armour-penetration fraction (0.15 * level). 0 if not present. */
    public static float getBreachFraction(ItemStack stack) {
        return 0.15F * EnchantmentHelper.getItemEnchantmentLevel(BREACH.get(), stack);
    }

    /** Wind Burst's upward launch strength for the attacker (level1=1.2, level2=1.75, level3=2.2). 0 if absent. */
    public static double getWindBurstLaunch(ItemStack stack) {
        int level = EnchantmentHelper.getItemEnchantmentLevel(WIND_BURST.get(), stack);
        return switch (level) {
            case 1 -> 1.2;
            case 2 -> 1.75;
            case 3 -> 2.2;
            default -> 0.0;
        };
    }

    // --- the three enchantments --------------------------------------------------------------------

    /** Density: UNCOMMON, max 5, fall-based smash bonus. Incompatible with Breach + sharpness/smite/bane. */
    public static final class DensityEnchantment extends MaceEnchantment {
        DensityEnchantment() {
            super(Rarity.UNCOMMON, MAINHAND);
        }

        @Override public int getMaxLevel() { return 5; }
        @Override public int getMinCost(int level) { return 5 + (level - 1) * 8; }
        @Override public int getMaxCost(int level) { return 25 + (level - 1) * 8; }

        @Override
        protected boolean checkCompatibility(Enchantment other) {
            return super.checkCompatibility(other)
                    && !(other instanceof BreachEnchantment)
                    && !(other instanceof DamageEnchantment);
        }
    }

    /** Breach: RARE, max 4, reduces target armour effectiveness. Incompatible with Density. */
    public static final class BreachEnchantment extends MaceEnchantment {
        BreachEnchantment() {
            super(Rarity.RARE, MAINHAND);
        }

        @Override public int getMaxLevel() { return 4; }
        @Override public int getMinCost(int level) { return 15 + (level - 1) * 9; }
        @Override public int getMaxCost(int level) { return 65 + (level - 1) * 9; }

        @Override
        protected boolean checkCompatibility(Enchantment other) {
            return super.checkCompatibility(other) && !(other instanceof DensityEnchantment);
        }
    }

    /** Wind Burst: RARE treasure-only, max 3, launches the attacker up on a smash. Compatible with all. */
    public static final class WindBurstEnchantment extends MaceEnchantment {
        WindBurstEnchantment() {
            super(Rarity.RARE, MAINHAND);
        }

        @Override public int getMaxLevel() { return 3; }
        @Override public int getMinCost(int level) { return 15 + (level - 1) * 9; }
        @Override public int getMaxCost(int level) { return 65 + (level - 1) * 9; }
        @Override public boolean isTreasureOnly() { return true; }
    }
}
