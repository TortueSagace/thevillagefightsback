package com.example.villagefightsback;

import com.example.villagefightsback.mixin.PotionBrewingInvoker;
import com.example.villagefightsback.platform.ModEventHandlers;
import com.example.villagefightsback.platform.ServerRef;
import com.example.villagefightsback.platform.event.ModEventSources;
import com.mojang.logging.LogUtils;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;

import org.slf4j.Logger;

/**
 * The Village Fights Back -- main mod class. A Village Brain Node evolves the village through stages and arms
 * its villagers, guards and iron golems (all native weapons/abilities; no third-party deps) to repel any
 * threat. Registers all content here; event handlers live in {@link VillageEvents} and friends. The registry
 * namespace ({@link #MODID}) is "thevillagefightsback"; it was renamed from the original "gobacktoyourworld",
 * which orphans this mod's content in worlds saved under the old id.
 *
 * <p>This is the Fabric build. Where Forge defers registration to its own registry events, Fabric registers on
 * the spot, so the order of the calls in {@link #onInitialize} is the registration order and matters: entity
 * types first (the spawn eggs resolve them while the item list is being built), then blocks, then items.
 */
public class TheVillageFightsBack implements ModInitializer {
    public static final String MODID = "thevillagefightsback";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        ModPathTypes.init();   // the custom BlockPathTypes constant, before anything can path

        ModEntities.register(); // first: the spawn eggs below resolve entity types as they are built
        ModBlocks.register();
        ModItems.register();
        ModArmor.register();
        ModEffects.register();
        ModPotions.register();
        ModPaintings.register();
        ModVillagers.register(); // Limbo Sage profession + its POI (reads the brain node's block states)
        ModEnchantments.register();
        ModParticles.register();
        ModSounds.register();
        ModLoot.register();      // the village-chest extras, as loot-table modifications
        CheapScaffoldBlock.registerFlammability();
        FertileLeavesBlock.registerFlammability();
        LimboElytraEvents.register();

        ModTriggers.register();  // custom advancement triggers (brain node stage)
        registerBrewing();

        com.example.villagefightsback.network.ModNetwork.register();

        Config.SPEC.loadFrom(FabricLoader.getInstance().getConfigDir().resolve(MODID + "-common.toml"));
        Config.apply();          // push drift chances into LimboDrift and scale the brain radii

        ServerRef.init();
        ModEventHandlers.registerCommon();
        ModEventSources.init();  // the Fabric callbacks and mixin hooks that fire the shim events

        LOGGER.info("[The Village Fights Back] loaded.");
    }

    /**
     * The mod's brewing recipes.
     *
     * <p>The Forge branch registers these through {@code BrewingRecipeRegistry} with NBT-exact ingredients.
     * Vanilla's own {@code PotionBrewing.addMix} says the same thing in three lines, because everything else
     * falls out of vanilla's generic container mixes: gunpowder makes any potion a splash, dragon's breath makes
     * any splash a lingering. So only the three potion-to-potion steps have to be declared.
     *
     * <p>One small difference from the Forge build: addMix also matches the splash and lingering forms of the
     * input potion, so brewing an emerald apple into a splash awkward potion gives a splash virus directly.
     * That is how every vanilla potion already behaves.
     */
    private static void registerBrewing() {
        // Emerald Apple turns an Awkward Potion into the Potion of Emerald Virus; glowstone strengthens it to II.
        PotionBrewingInvoker.invokeAddMix(Potions.AWKWARD, ModItems.EMERALD_APPLE.get(),
                ModPotions.EMERALD_VIRUS.get());
        PotionBrewingInvoker.invokeAddMix(ModPotions.EMERALD_VIRUS.get(), Items.GLOWSTONE_DUST,
                ModPotions.EMERALD_VIRUS_STRONG.get());

        // Darius' Special Sauce has no awkward-to-base recipe on purpose: the base potion is loot-only. From the
        // looted base, redstone gives the long variant and the vanilla container mixes give the rest.
        PotionBrewingInvoker.invokeAddMix(ModPotions.DARIUS_SPECIAL_EFFECT.get(), Items.REDSTONE,
                ModPotions.DARIUS_SPECIAL_EFFECT_LONG.get());
    }
}
