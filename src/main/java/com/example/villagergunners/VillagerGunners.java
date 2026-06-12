package com.example.villagergunners;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * TEMPORARY EXERCISE MOD.
 *
 * Arms naturally-generated village villagers and iron golems with Just Enough Guns weapons
 * and makes them fight any hostile mob. Event handlers live in {@link GunnerEvents}, which is
 * auto-registered to the Forge event bus via {@code @Mod.EventBusSubscriber}.
 */
@Mod(VillagerGunners.MODID)
public class VillagerGunners {
    public static final String MODID = "gobacktoyourworld";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VillagerGunners() {
        ModPathTypes.init(); // register the custom BlockPathTypes during mod construction
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModArmor.register(modBus);
        ModEffects.register(modBus);
        ModPotions.register(modBus);
        ModEntities.register(modBus);
        ModParticles.register(modBus);
        ModLoot.register(modBus);
        modBus.addListener(this::commonSetup); // brewing recipes (must be added off the main init thread)
        com.example.villagergunners.network.ModNetwork.register(); // gravemind UI packet channel
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON, Config.SPEC); // config/gobacktoyourworld-common.toml
        LOGGER.info("[Go Back To Your World] loaded.");
    }

    private void commonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
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
