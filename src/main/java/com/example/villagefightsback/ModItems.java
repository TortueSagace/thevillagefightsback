package com.example.villagefightsback;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Plain item registration (non-block items). */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TheVillageFightsBack.MODID);

    /** The Alien Slasher melee weapon -- a fancier, virus-spreading netherite axe ({@link AlienSlasherItem}).
     *  Its on-hit payload (applied in {@link EmeraldVirusEvents}) always inflicts Emerald Virus I. */
    public static final RegistryObject<Item> ALIEN_SLASHER =
            ITEMS.register("alien_slasher", () -> new AlienSlasherItem(new Item.Properties().fireResistant()));

    /** The Alien Eradicator melee weapon -- a stronger Alien Slasher ({@link AlienEradicatorItem}). Same stats,
     *  but its on-hit payload (applied in {@link EmeraldVirusEvents}) inflicts Emerald Virus II (Wither II).
     *  Wielded by stage-4 defenders / Emeraldgers; vindictive guards keep the plain slasher (Virus I). */
    public static final RegistryObject<Item> ALIEN_ERADICATOR =
            ITEMS.register("alien_eradicator", () -> new AlienEradicatorItem(new Item.Properties().fireResistant()));

    /** The Rocket Launcher: a usable weapon (right-click fires a {@link RocketEntity}, consuming a Rocket).
     *  Held by gigachad golems and dropped by them. See {@link RocketLauncherItem}. */
    public static final RegistryObject<Item> ROCKET_LAUNCHER =
            ITEMS.register("rocket_launcher", () -> new RocketLauncherItem(new Item.Properties().stacksTo(1).fireResistant()));

    /** Rocket: the launcher's ammunition (also the flying rocket's billboard sprite). Crafted from fire charges
     *  + gunpowder; dropped by gigachad golems. */
    public static final RegistryObject<Item> ROCKET =
            ITEMS.register("rocket", () -> new Item(new Item.Properties()));

    /** The Mace: a native clone of the vanilla 1.21 mace (durability/rarity/enchant-value live in {@link MaceItem}).
     *  Smash + the mace enchantments are handled by {@link MaceCombatEvents}. Sold by the Weaponsmith + dropped
     *  by stage-3/4 guards (with Density 3 + Wind Burst 3 + Fire Aspect 2, see {@link GuardWeapons}). */
    public static final RegistryObject<Item> MACE =
            ITEMS.register("mace", () -> new MaceItem(new Item.Properties()));

    /** Wind Charge: a native clone of the vanilla 1.21 wind charge -- right-click hurls a {@link WindChargeEntity}
     *  that bursts into a knockback-only gust. Sold by the Weaponsmith + dropped by guards. See {@link WindChargeItem}. */
    public static final RegistryObject<Item> WIND_CHARGE =
            ITEMS.register("wind_charge", () -> new WindChargeItem(new Item.Properties()));

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

    /** Emerald Virus Arrow: a green arrow that infects whatever it hits with Virus I ({@link EmeraldVirusArrowItem}).
     *  Crafted from 8 arrows + a (regular) Potion of Emerald Virus. */
    public static final RegistryObject<Item> EMERALD_VIRUS_ARROW =
            ITEMS.register("emerald_virus_arrow", () -> new EmeraldVirusArrowItem(new Item.Properties(), 0));

    /** Emerald Virus Arrow II: the stronger arrow -- infects with Virus II (Wither II) on hit
     *  ({@link EmeraldVirusArrowItem}). Crafted from 8 arrows + a Potion of Emerald Virus II. */
    public static final RegistryObject<Item> EMERALD_VIRUS_ARROW_2 =
            ITEMS.register("emerald_virus_arrow_2", () -> new EmeraldVirusArrowItem(new Item.Properties(), 1));

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

    /** Fertile Chorus: a chorus-fruit food (feeds + teleports on eat) dropped by the Fertile Chorus Plant.
     *  Eating it has a 1% chance to inflict Emerald Diarrhea ({@link FertileChorusItem}). */
    public static final RegistryObject<Item> FERTILE_CHORUS =
            ITEMS.register("fertile_chorus", () -> new FertileChorusItem(new Item.Properties().food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(4).saturationMod(0.3F).build())));

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
    /** Emerald Hammer: an emerald pickaxe that mines a 3x3 area (surrounding blocks capped at iron-mineable). */
    public static final RegistryObject<Item> EMERALD_HAMMER = ITEMS.register("emerald_hammer",
            () -> new EmeraldHammerItem(EmeraldTier.INSTANCE, 1, -3.2F, new Item.Properties()));

    /** Limbo Pickaxe: a netherite-grade pick (smithed) that also smashes bedrock + reinforced deepslate
     *  ({@link LimboPickaxeItem} / {@link LimboPickaxeEvents}). */
    public static final RegistryObject<Item> LIMBO_PICKAXE = ITEMS.register("limbo_pickaxe",
            () -> new LimboPickaxeItem(new Item.Properties().fireResistant()));

    // --- The rest of the Limbo tool set: smithed from netherite, more efficient than netherite
    //     (LimboToolTier: speed 10 vs 9, 2500 vs 2031 uses). ---
    public static final RegistryObject<Item> LIMBO_SWORD = ITEMS.register("limbo_sword",
            () -> new net.minecraft.world.item.SwordItem(LimboToolTier.INSTANCE, 4, -2.4F, new Item.Properties().fireResistant()) { // +1 dmg vs netherite
                @Override public boolean isFoil(net.minecraft.world.item.ItemStack s) { return true; }
            });
    public static final RegistryObject<Item> LIMBO_AXE = ITEMS.register("limbo_axe",
            () -> new net.minecraft.world.item.AxeItem(LimboToolTier.INSTANCE, 5.0F, -3.0F, new Item.Properties().fireResistant()) {
                @Override public boolean isFoil(net.minecraft.world.item.ItemStack s) { return true; }
            });
    public static final RegistryObject<Item> LIMBO_SHOVEL = ITEMS.register("limbo_shovel",
            () -> new net.minecraft.world.item.ShovelItem(LimboToolTier.INSTANCE, 1.5F, -3.0F, new Item.Properties().fireResistant()) {
                @Override public boolean isFoil(net.minecraft.world.item.ItemStack s) { return true; }
            });
    public static final RegistryObject<Item> LIMBO_HOE = ITEMS.register("limbo_hoe",
            () -> new net.minecraft.world.item.HoeItem(LimboToolTier.INSTANCE, -4, 0.0F, new Item.Properties().fireResistant()) {
                @Override public boolean isFoil(net.minecraft.world.item.ItemStack s) { return true; }
            });

    /** Forgotten Powder: dropped by Forgotten Glowstone AND by the Limbo raiders (the Forgotten Dust used to
     *  craft the block back). Foil + an animated icon, matching the Limbo gear's shimmer. */
    public static final RegistryObject<Item> FORGOTTEN_POWDER =
            ITEMS.register("forgotten_powder", () -> new Item(new Item.Properties()) {
                @Override public boolean isFoil(net.minecraft.world.item.ItemStack s) { return true; }
            });

    /** The emerald tools, in tab order. */
    public static final java.util.List<RegistryObject<Item>> EMERALD_TOOLS = java.util.List.of(
            EMERALD_SWORD, EMERALD_PICKAXE, EMERALD_AXE, EMERALD_SHOVEL, EMERALD_HOE, EMERALD_HAMMER);

    /** Creative spawn egg for the Limbo Phantom. */
    public static final RegistryObject<Item> LIMBO_PHANTOM_SPAWN =
            ITEMS.register("limbo_phantom_spawn", () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                    ModEntities.LIMBO_PHANTOM, 0x101018, 0xE8F0E8, new Item.Properties()));

    /** Creative spawn egg for the Emeraldger (the stage-4 virus warrior). */
    public static final RegistryObject<Item> EMERALDGER_SPAWN =
            ITEMS.register("emeraldger_spawn", () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                    ModEntities.EMERALDGER, 0x17DD62, 0x0A6E2E, new Item.Properties()));

    /** Creative spawn egg for the Rich Sheep (the fertile/rich-looking, emerald-dropping sheep). */
    public static final RegistryObject<Item> RICH_SHEEP_SPAWN =
            ITEMS.register("rich_sheep_spawn", () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                    ModEntities.RICH_SHEEP, 0x2BBF4E, 0x17DD62, new Item.Properties()));

    /** Creative spawn eggs for the unleashed boss's vindictive minions. */
    public static final RegistryObject<Item> VINDICTIVE_GUARD_SPAWN =
            ITEMS.register("vindictive_guard_spawn", () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                    ModEntities.VINDICTIVE_GUARD, 0x3A5F3A, 0x2BBF4E, new Item.Properties()));
    public static final RegistryObject<Item> VINDICTIVE_GIGACHAD_SPAWN =
            ITEMS.register("vindictive_gigachad_spawn", () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                    ModEntities.VINDICTIVE_GIGACHAD, 0xB8860B, 0x2BBF4E, new Item.Properties()));

    // --- Limbo raider spawn eggs: a custom animated/foil icon (item/generated, not the egg template) so they
    //     shimmer exactly like the Limbo gear. ---
    private static RegistryObject<Item> limboEgg(String name,
            java.util.function.Supplier<? extends net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>> type,
            int primary, int secondary) {
        return ITEMS.register(name, () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                type, primary, secondary, new Item.Properties()) {
            @Override public boolean isFoil(net.minecraft.world.item.ItemStack s) { return true; }
        });
    }

    public static final RegistryObject<Item> LIMBO_PILLAGER_SPAWN =
            limboEgg("limbo_pillager_spawn", ModEntities.LIMBO_PILLAGER, 0x283830, 0x96E1AF);
    public static final RegistryObject<Item> LIMBO_VINDICATOR_SPAWN =
            limboEgg("limbo_vindicator_spawn", ModEntities.LIMBO_VINDICATOR, 0x323E32, 0xAAEB96);
    public static final RegistryObject<Item> LIMBO_EVOKER_SPAWN =
            limboEgg("limbo_evoker_spawn", ModEntities.LIMBO_EVOKER, 0x222E38, 0x8CEBCD);
    public static final RegistryObject<Item> LIMBO_RAVAGER_SPAWN =
            limboEgg("limbo_ravager_spawn", ModEntities.LIMBO_RAVAGER, 0x1C221E, 0x78C88C);
    public static final RegistryObject<Item> LIMBO_VEX_SPAWN =
            limboEgg("limbo_vex_spawn", ModEntities.LIMBO_VEX, 0x2E3E48, 0xB9F5DE);
    public static final RegistryObject<Item> LIMBO_WITCH_SPAWN =
            limboEgg("limbo_witch_spawn", ModEntities.LIMBO_WITCH, 0x241E2E, 0xC9BCE6);

    /** The Limbo raider spawn eggs, in tab order. */
    public static final java.util.List<RegistryObject<Item>> LIMBO_RAID_EGGS = java.util.List.of(
            LIMBO_PILLAGER_SPAWN, LIMBO_VINDICATOR_SPAWN, LIMBO_EVOKER_SPAWN, LIMBO_RAVAGER_SPAWN, LIMBO_VEX_SPAWN,
            LIMBO_WITCH_SPAWN);

    // --- Staged-defender spawn eggs: place a villager/guard/golem already configured to a BrainNode stage,
    //     even with no brainNode present (see StagedDefenderEggItem). ---
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
