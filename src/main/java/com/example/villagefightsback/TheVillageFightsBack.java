package com.example.villagefightsback;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
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

    /** Convenience factory for a ResourceLocation in this mod's namespace (the 1.21 constructor is private). */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public TheVillageFightsBack(IEventBus modBus, ModContainer modContainer) {
        ModPathTypes.init(); // register the custom BlockPathTypes during mod construction
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModArmor.register(modBus);
        ModEffects.register(modBus);
        ModPotions.register(modBus);
        ModEntities.register(modBus);
        ModVillagers.register(modBus); // Limbo Sage profession + its POI
        ModParticles.register(modBus);
        ModSounds.register(modBus);
        ModLoot.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::onRegisterBrewingRecipes); // brewing recipes (now a dedicated mod-bus event)
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent e) -> {
            if (e.getConfig().getSpec() == Config.SPEC) Config.apply(); // push drift chances into LimboDrift
        });
        com.example.villagefightsback.network.ModNetwork.register(modBus); // brainNode UI packet channel
        modContainer.registerConfig(
                net.neoforged.fml.config.ModConfig.Type.COMMON, Config.SPEC); // config/thevillagefightsback-common.toml
        LOGGER.info("[The Village Fights Back] loaded.");
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModTriggers.register(); // custom advancement triggers (brainNode stage)
        });
    }

    /**
     * Brewing recipes. In 1.21 these are registered through {@code RegisterBrewingRecipesEvent}'s
     * {@link net.minecraft.world.item.alchemy.PotionBrewing.Builder}; splash/lingering variants come for
     * free from vanilla's generic container recipes (gunpowder -> splash, dragon's breath -> lingering), so
     * only the base potion mixes are registered here.
     */
    private void onRegisterBrewingRecipes(net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent event) {
        net.minecraft.world.item.alchemy.PotionBrewing.Builder builder = event.getBuilder();
        // Emerald Apple brews the Potion of Emerald Virus (awkward base); glowstone dust strengthens it to Virus II.
        // The Emerald Virus arrow is crafted cheaply from that potion + 8 arrows (see its recipe), so we
        // deliberately do NOT add a lingering/tipped-arrow path of our own.
        builder.addMix(net.minecraft.world.item.alchemy.Potions.AWKWARD,
                ModItems.EMERALD_APPLE.get(), ModPotions.EMERALD_VIRUS);
        builder.addMix(ModPotions.EMERALD_VIRUS,
                net.minecraft.world.item.Items.GLOWSTONE_DUST, ModPotions.EMERALD_VIRUS_STRONG);

        // Karat's Will: the BASE potion is loot-only (no awkward -> base recipe). From the looted base, redstone
        // brews the "long" variant; the splash/lingering forms follow from vanilla's generic container recipes.
        builder.addMix(ModPotions.KARATS_WILL,
                net.minecraft.world.item.Items.REDSTONE, ModPotions.KARATS_WILL_LONG);
    }
}
