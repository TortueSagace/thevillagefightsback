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
     *  so destroying it -- or sleeping in the Limbo bed -- brings you back). See {@link LimboPotionItem}. */
    public static final RegistryObject<Item> LIMBO_POTION =
            ITEMS.register("limbo_potion", () -> new LimboPotionItem(
                    new Item.Properties().stacksTo(16).craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)));

    /** Volatile Limbo Potion: a THROWN (not lingering) limbo potion that bursts and banishes everyone in the
     *  blast to Limbo ({@link VolatileLimboPotionItem}). The area-weapon counterpart of the drinkable potion. */
    public static final RegistryObject<Item> VOLATILE_LIMBO_POTION =
            ITEMS.register("volatile_limbo_potion", () -> new VolatileLimboPotionItem(
                    new Item.Properties().stacksTo(16)));

    /** Limbo Membrane: a black, faintly green-glowing phantom membrane dropped by the Limbo Phantom. Eight of
     *  them + an Emerald Apple craft a Limbo Ingot. */
    public static final RegistryObject<Item> LIMBO_MEMBRANE =
            ITEMS.register("limbo_membrane", () -> new Item(new Item.Properties()));

    /** Limbo Ingot: crafted from 8 Limbo Membrane + 1 Emerald Apple. The "addition" that smiths netherite gear
     *  into Limbo gear, and the repair material for the set. */
    public static final RegistryObject<Item> LIMBO_INGOT =
            ITEMS.register("limbo_ingot", () -> new Item(new Item.Properties().fireResistant()));

    /** Limbo Smithing Template: a rare (1%) village-chest find. Together with a Limbo Ingot it upgrades a
     *  netherite piece into Limbo gear at a smithing table. */
    public static final RegistryObject<Item> LIMBO_SMITHING_TEMPLATE =
            ITEMS.register("limbo_smithing_template", () -> new Item(new Item.Properties()));

    /** Emerald Virus Arrow: a green arrow that infects whatever it hits ({@link EmeraldVirusArrowItem}).
     *  Crafted from 8 arrows + a (regular) Potion of Emerald Virus. */
    public static final RegistryObject<Item> EMERALD_VIRUS_ARROW =
            ITEMS.register("emerald_virus_arrow", () -> new EmeraldVirusArrowItem(new Item.Properties()));

    /** Fire Grenade: a throwable fire bomb (our native replacement for JEG's molotov). See {@link FireGrenadeItem}. */
    public static final RegistryObject<Item> FIRE_GRENADE =
            ITEMS.register("fire_grenade", () -> new FireGrenadeItem(new Item.Properties().stacksTo(16)));

    /** Emerald Apple: edible, but eating it inflicts the Emerald Virus. The base brewing component for the
     *  Emerald Virus potion (and, via that potion's tipped arrows, the Emerald Virus arrows). Drops rarely
     *  from fertile leaves. */
    public static final RegistryObject<Item> EMERALD_APPLE =
            ITEMS.register("emerald_apple", () -> new Item(new Item.Properties().food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(4).saturationMod(0.3F).alwaysEat()
                            .effect(() -> new net.minecraft.world.effect.MobEffectInstance(
                                    ModEffects.EMERALD_VIRUS.get(), EmeraldVirus.DURATION_TICKS), 1.0F)
                            .build())));

    // --- Emerald tools: stats between iron and diamond, like the emerald armour (see EmeraldTier). ---
    public static final RegistryObject<Item> EMERALD_SWORD = ITEMS.register("emerald_sword",
            () -> new net.minecraft.world.item.SwordItem(EmeraldTier.INSTANCE, 3, -2.4F, new Item.Properties()));
    public static final RegistryObject<Item> EMERALD_PICKAXE = ITEMS.register("emerald_pickaxe",
            () -> new net.minecraft.world.item.PickaxeItem(EmeraldTier.INSTANCE, 1, -2.8F, new Item.Properties()));
    public static final RegistryObject<Item> EMERALD_AXE = ITEMS.register("emerald_axe",
            () -> new net.minecraft.world.item.AxeItem(EmeraldTier.INSTANCE, 6.0F, -3.1F, new Item.Properties()));
    public static final RegistryObject<Item> EMERALD_SHOVEL = ITEMS.register("emerald_shovel",
            () -> new net.minecraft.world.item.ShovelItem(EmeraldTier.INSTANCE, 1.5F, -3.0F, new Item.Properties()));
    public static final RegistryObject<Item> EMERALD_HOE = ITEMS.register("emerald_hoe",
            () -> new net.minecraft.world.item.HoeItem(EmeraldTier.INSTANCE, -2, -1.0F, new Item.Properties()));

    /** Limbo Pickaxe: a netherite-grade pick (smithed) that also smashes bedrock + reinforced deepslate
     *  ({@link LimboPickaxeItem} / {@link LimboPickaxeEvents}). */
    public static final RegistryObject<Item> LIMBO_PICKAXE = ITEMS.register("limbo_pickaxe",
            () -> new LimboPickaxeItem(new Item.Properties().fireResistant()));

    /** Forgotten Powder: dropped by Forgotten Glowstone (like glowstone dust). Four craft the block back. */
    public static final RegistryObject<Item> FORGOTTEN_POWDER =
            ITEMS.register("forgotten_powder", () -> new Item(new Item.Properties()));

    /** The emerald tools, in tab order. */
    public static final java.util.List<RegistryObject<Item>> EMERALD_TOOLS = java.util.List.of(
            EMERALD_SWORD, EMERALD_PICKAXE, EMERALD_AXE, EMERALD_SHOVEL, EMERALD_HOE);

    /** Creative spawn egg for the Limbo Phantom. */
    public static final RegistryObject<Item> LIMBO_PHANTOM_SPAWN =
            ITEMS.register("limbo_phantom_spawn", () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                    ModEntities.LIMBO_PHANTOM, 0x101018, 0xE8F0E8, new Item.Properties()));

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
