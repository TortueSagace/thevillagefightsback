package com.example.villagergunners;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Plain item registration (non-block items). */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, VillagerGunners.MODID);

    /** The Alien Slasher melee weapon -- a fancier, virus-spreading netherite axe ({@link AlienSlasherItem}). */
    public static final RegistryObject<Item> ALIEN_SLASHER =
            ITEMS.register("alien_slasher", () -> new AlienSlasherItem(new Item.Properties().fireResistant()));

    /** The iron golem's rocket launcher -- a native clone of JEG's, held for the visual ({@link RocketEntity}). */
    public static final RegistryObject<Item> ROCKET_LAUNCHER =
            ITEMS.register("rocket_launcher", () -> new Item(new Item.Properties().stacksTo(1).fireResistant()));

    /** The flying rocket's billboard sprite (the projectile renders this; not obtainable on its own). */
    public static final RegistryObject<Item> ROCKET =
            ITEMS.register("rocket", () -> new Item(new Item.Properties()));

    /** Limbo Potion: drink it to be flung into Limbo (a Limbo-gravel marker is auto-dropped where you stood,
     *  so destroying it -- or sleeping in the Limbo bed -- brings you back). See {@link LimboPotionItem}.
     *  Leaves a glass bottle when used in crafting (the Limbo Arrow recipe). */
    public static final RegistryObject<Item> LIMBO_POTION =
            ITEMS.register("limbo_potion", () -> new LimboPotionItem(
                    new Item.Properties().stacksTo(16).craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)));

    /** Limbo Arrow: a black-tipped arrow that banishes whatever it hits to Limbo ({@link LimboArrowItem}). */
    public static final RegistryObject<Item> LIMBO_ARROW =
            ITEMS.register("limbo_arrow", () -> new LimboArrowItem(new Item.Properties()));

    /** Creative spawn egg for the Emeraldger (the stage-4 virus warrior). */
    public static final RegistryObject<Item> EMERALDGER_SPAWN =
            ITEMS.register("emeraldger_spawn", () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                    ModEntities.EMERALDGER, 0x17DD62, 0x0A6E2E, new Item.Properties()));

    // --- Staged-defender spawn eggs: place a villager/guard/golem already configured to a Gravemind stage,
    //     even with no gravemind present (see StagedDefenderEggItem). ---
    private static RegistryObject<Item> egg(String name, StagedDefenderEggItem.Kind kind, int stage, int primary, int secondary) {
        return ITEMS.register(name, () -> new StagedDefenderEggItem(kind, stage, primary, secondary, new Item.Properties()));
    }

    public static final RegistryObject<Item> VILLAGER_STAGE1 = egg("villager_stage1", StagedDefenderEggItem.Kind.VILLAGER, 1, 0x8B6F47, 0xA0A0A0);
    public static final RegistryObject<Item> VILLAGER_STAGE3 = egg("villager_stage3", StagedDefenderEggItem.Kind.VILLAGER, 3, 0x8B6F47, 0x2BBF4E);
    public static final RegistryObject<Item> VILLAGER_STAGE4 = egg("villager_stage4", StagedDefenderEggItem.Kind.VILLAGER, 4, 0x2BBF4E, 0x0A5C28);
    public static final RegistryObject<Item> GUARD_STAGE1 = egg("guard_stage1", StagedDefenderEggItem.Kind.GUARD, 1, 0x8E9BA8, 0xA0A0A0);
    public static final RegistryObject<Item> GUARD_STAGE3 = egg("guard_stage3", StagedDefenderEggItem.Kind.GUARD, 3, 0x8E9BA8, 0x2BBF4E);
    public static final RegistryObject<Item> GUARD_STAGE4 = egg("guard_stage4", StagedDefenderEggItem.Kind.GUARD, 4, 0x2BBF4E, 0x0A5C28);
    public static final RegistryObject<Item> IRON_GOLEM = egg("iron_golem", StagedDefenderEggItem.Kind.GOLEM, 1, 0xDBD3D0, 0x70A35A);
    public static final RegistryObject<Item> GIGACHAD_GOLEM = egg("gigachad_golem", StagedDefenderEggItem.Kind.GOLEM, 3, 0xFFD24D, 0xB8860B); // golden

    /** All staged-defender eggs, in creative-tab / colour-handler order. */
    public static final java.util.List<RegistryObject<Item>> STAGED_EGGS = java.util.List.of(
            VILLAGER_STAGE1, VILLAGER_STAGE3, VILLAGER_STAGE4,
            GUARD_STAGE1, GUARD_STAGE3, GUARD_STAGE4,
            IRON_GOLEM, GIGACHAD_GOLEM);

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
