package com.example.villagefightsback;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * The Village Fights Back -- main mod class. A Village Brain Node evolves the village through stages and arms
 * its villagers, guards and iron golems (all native weapons/abilities; no third-party deps) to repel any
 * threat. Registers all content here; event handlers live in {@link VillageEvents} and friends. The registry
 * namespace (the {@code @Mod} id / {@link #MODID}) is "thevillagefightsback"; it was renamed from the original
 * "gobacktoyourworld", which orphans this mod's content in worlds saved under the old id.
 */
@Mod(TheVillageFightsBack.MODID)
public class TheVillageFightsBack {
    public static final String MODID = "thevillagefightsback";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TheVillageFightsBack() {
        ModPathTypes.init(); // register the custom BlockPathTypes during mod construction
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModArmor.register(modBus);
        ModEffects.register(modBus);
        ModPotions.register(modBus);
        ModEntities.register(modBus);
        ModVillagers.register(modBus); // Limbo Sage profession + its POI
        ModEnchantments.register(modBus);
        ModParticles.register(modBus);
        ModSounds.register(modBus);
        ModLoot.register(modBus);
        modBus.addListener(this::commonSetup); // brewing recipes (must be added off the main init thread)
        modBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent e) -> {
            if (e.getConfig().getSpec() != Config.SPEC) return;
            Config.apply();                          // push drift chances into LimboDrift
            WorldModeEvents.reapplyToRunningServer(); // a .toml reload must not drop the loaded world's mode
        });
        com.example.villagefightsback.network.ModNetwork.register(); // brainNode UI packet channel
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON, Config.SPEC); // config/thevillagefightsback-common.toml
        LOGGER.info("[The Village Fights Back] loaded.");
    }

    private void commonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModTriggers.register(); // custom advancement triggers (brainNode stage)
            // Emerald Apple brews the Potion of Emerald Virus (awkward base); gunpowder makes the splash
            // version. The Emerald Virus arrow is crafted cheaply from that potion + 8 arrows (see its recipe),
            // so we deliberately do NOT add the lingering step (no expensive vanilla tipped-arrow path).
            // StrictNBTIngredient so only the exact source potion matches.
            net.minecraft.world.item.ItemStack awkward = potion(net.minecraft.world.item.Items.POTION,
                    net.minecraft.world.item.alchemy.Potions.AWKWARD);
            net.minecraft.world.item.ItemStack virus = potion(net.minecraft.world.item.Items.POTION, null);
            net.minecraft.world.item.ItemStack splash = potion(net.minecraft.world.item.Items.SPLASH_POTION, null);
            net.minecraftforge.common.brewing.BrewingRecipeRegistry.addRecipe(
                    net.minecraftforge.common.crafting.StrictNBTIngredient.of(awkward),
                    net.minecraft.world.item.crafting.Ingredient.of(ModItems.EMERALD_APPLE.get()), virus);
            net.minecraftforge.common.brewing.BrewingRecipeRegistry.addRecipe(
                    net.minecraftforge.common.crafting.StrictNBTIngredient.of(virus),
                    net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.GUNPOWDER), splash);

            // Virus I -> Virus II with glowstone dust (mirrors vanilla strong-potion brewing); gunpowder then
            // makes the Virus II splash, exactly as the Virus I splash above.
            net.minecraft.world.item.ItemStack virus2 = potion(net.minecraft.world.item.Items.POTION,
                    ModPotions.EMERALD_VIRUS_STRONG.get());
            net.minecraft.world.item.ItemStack splash2 = potion(net.minecraft.world.item.Items.SPLASH_POTION,
                    ModPotions.EMERALD_VIRUS_STRONG.get());
            net.minecraftforge.common.brewing.BrewingRecipeRegistry.addRecipe(
                    net.minecraftforge.common.crafting.StrictNBTIngredient.of(virus),
                    net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.GLOWSTONE_DUST), virus2);
            net.minecraftforge.common.brewing.BrewingRecipeRegistry.addRecipe(
                    net.minecraftforge.common.crafting.StrictNBTIngredient.of(virus2),
                    net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.GUNPOWDER), splash2);

            // Karat's Will: the BASE potion is loot-only (no awkward -> base recipe). From the looted base, the
            // standard brewing rules give the variants: gunpowder -> volatile (splash), splash + dragon's breath
            // -> lingering, redstone -> long. (Splash/lingering of the long variant follow from vanilla's generic
            // gunpowder/dragon's-breath container recipes.)
            net.minecraft.world.item.ItemStack kw = potion(net.minecraft.world.item.Items.POTION,
                    ModPotions.KARATS_WILL.get());
            net.minecraft.world.item.ItemStack kwSplash = potion(net.minecraft.world.item.Items.SPLASH_POTION,
                    ModPotions.KARATS_WILL.get());
            net.minecraft.world.item.ItemStack kwLinger = potion(net.minecraft.world.item.Items.LINGERING_POTION,
                    ModPotions.KARATS_WILL.get());
            net.minecraft.world.item.ItemStack kwLong = potion(net.minecraft.world.item.Items.POTION,
                    ModPotions.KARATS_WILL_LONG.get());
            net.minecraftforge.common.brewing.BrewingRecipeRegistry.addRecipe(
                    net.minecraftforge.common.crafting.StrictNBTIngredient.of(kw),
                    net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.GUNPOWDER), kwSplash);
            net.minecraftforge.common.brewing.BrewingRecipeRegistry.addRecipe(
                    net.minecraftforge.common.crafting.StrictNBTIngredient.of(kwSplash),
                    net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.DRAGON_BREATH), kwLinger);
            net.minecraftforge.common.brewing.BrewingRecipeRegistry.addRecipe(
                    net.minecraftforge.common.crafting.StrictNBTIngredient.of(kw),
                    net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.REDSTONE), kwLong);
        });
    }

    /** A potion-tagged stack of the given container item; {@code potion == null} means our Emerald Virus potion. */
    private static net.minecraft.world.item.ItemStack potion(net.minecraft.world.item.Item container,
                                                             net.minecraft.world.item.alchemy.Potion potion) {
        return net.minecraft.world.item.alchemy.PotionUtils.setPotion(
                new net.minecraft.world.item.ItemStack(container),
                potion != null ? potion : ModPotions.EMERALD_VIRUS.get());
    }
}
