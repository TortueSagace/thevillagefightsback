package com.example.villagefightsback;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * The Village Fights Back -- main mod class. A Village Gravemind evolves the village through stages and arms
 * its villagers, guards and iron golems (all native weapons/abilities; no third-party deps) to repel any
 * threat. Registers all content here; event handlers live in {@link VillageEvents} and friends. Note the
 * registry namespace (the {@code @Mod} id / {@link #MODID}) is kept as "gobacktoyourworld" so existing saved
 * worlds keep working -- only the display name and code were renamed.
 */
@Mod(TheVillageFightsBack.MODID)
public class TheVillageFightsBack {
    public static final String MODID = "gobacktoyourworld";
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
        ModParticles.register(modBus);
        ModSounds.register(modBus);
        ModLoot.register(modBus);
        modBus.addListener(this::commonSetup); // brewing recipes (must be added off the main init thread)
        modBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent e) -> {
            if (e.getConfig().getSpec() == Config.SPEC) Config.apply(); // push drift chances into LimboDrift
        });
        com.example.villagefightsback.network.ModNetwork.register(); // gravemind UI packet channel
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON, Config.SPEC); // config/gobacktoyourworld-common.toml
        LOGGER.info("[The Village Fights Back] loaded.");
    }

    private void commonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModTriggers.register(); // custom advancement triggers (gravemind stage)
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
