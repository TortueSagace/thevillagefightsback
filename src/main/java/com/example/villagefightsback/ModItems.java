package com.example.villagefightsback;

import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Plain item registration (non-block items). */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, TheVillageFightsBack.MODID);

    /** The Alien Slasher melee weapon -- a fancier, virus-spreading netherite axe ({@link AlienSlasherItem}).
     *  Its on-hit payload (applied in {@link EmeraldVirusEvents}) always inflicts Emerald Virus I. */
    public static final DeferredHolder<Item, Item> ALIEN_SLASHER =
            ITEMS.register("alien_slasher", () -> new AlienSlasherItem(new Item.Properties().fireResistant()));

    /** The Alien Eradicator melee weapon -- a stronger Alien Slasher ({@link AlienEradicatorItem}). Same stats,
     *  but its on-hit payload (applied in {@link EmeraldVirusEvents}) inflicts Emerald Virus II (Wither II).
     *  Wielded by stage-4 defenders / Emeraldgers; vindictive guards keep the plain slasher (Virus I). */
    public static final DeferredHolder<Item, Item> ALIEN_ERADICATOR =
            ITEMS.register("alien_eradicator", () -> new AlienEradicatorItem(new Item.Properties().fireResistant()));

    /** The Rocket Launcher: a usable weapon (right-click fires a {@link RocketEntity}, consuming a Rocket).
     *  Held by gigachad golems and dropped by them. See {@link RocketLauncherItem}. */
    public static final DeferredHolder<Item, Item> ROCKET_LAUNCHER =
            ITEMS.register("rocket_launcher", () -> new RocketLauncherItem(new Item.Properties().stacksTo(1).fireResistant()));

    /** Rocket: the launcher's ammunition (also the flying rocket's billboard sprite). Crafted from fire charges
     *  + gunpowder; dropped by gigachad golems. */
    public static final DeferredHolder<Item, Item> ROCKET =
            ITEMS.register("rocket", () -> new Item(new Item.Properties()));

    /** The Mace: a native clone of the vanilla 1.21 mace (durability/rarity/enchant-value live in {@link MaceItem}).
     *  Smash + the mace enchantments are handled by {@link MaceCombatEvents}. Sold by the Weaponsmith + dropped
     *  by stage-3/4 guards (with Density 3 + Wind Burst 3 + Fire Aspect 2, see {@link GuardWeapons}). */
    public static final DeferredHolder<Item, Item> MACE =
            ITEMS.register("mace", () -> new MaceItem(new Item.Properties()));

    /** The Alien Smasher: a Limbo-forged Mace upgrade (Mace + Limbo Ingot at a smithing table). It IS a mace
     *  ({@link AlienSmasherItem} extends {@link MaceItem}), so the smash + mace enchants all apply; it carries
     *  a guaranteed guard-mace baseline (Density 3 + Wind Burst 3 + Fire Aspect 2, enforced in
     *  {@link MaceCombatEvents}) and a signature triple no-creature-damage block-and-fire blast beneath the
     *  target (see {@link AlienSmasherEvents}). Fire-resistant like the Limbo gear it is forged with. */
    public static final DeferredHolder<Item, Item> ALIEN_SMASHER =
            ITEMS.register("alien_smasher", () -> new AlienSmasherItem(new Item.Properties().fireResistant()));

    /** Wind Charge: a native clone of the vanilla 1.21 wind charge -- right-click hurls a {@link WindChargeEntity}
     *  that bursts into a knockback-only gust. Sold by the Weaponsmith + dropped by guards. See {@link WindChargeItem}. */
    public static final DeferredHolder<Item, Item> WIND_CHARGE =
            ITEMS.register("wind_charge", () -> new WindChargeItem(new Item.Properties()));

    /** Limbo Potion: drink it to be flung into Limbo (a Limbo-gravel marker is auto-dropped where you stood,
     *  so destroying it -- or sleeping in the Limbo bed -- brings you back). See {@link LimboPotionItem}. */
    public static final DeferredHolder<Item, Item> LIMBO_POTION =
            ITEMS.register("limbo_potion", () -> new LimboPotionItem(
                    new Item.Properties().stacksTo(16).craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)));

    /** Volatile Limbo Potion: a THROWN (not lingering) limbo potion that bursts and banishes everyone in the
     *  blast to Limbo ({@link VolatileLimboPotionItem}). The area-weapon counterpart of the drinkable potion. */
    public static final DeferredHolder<Item, Item> VOLATILE_LIMBO_POTION =
            ITEMS.register("volatile_limbo_potion", () -> new VolatileLimboPotionItem(
                    new Item.Properties().stacksTo(16)));

    /** Limbo Membrane: a black, faintly green-glowing phantom membrane dropped by the Limbo Phantom. Eight of
     *  them + an Emerald Apple craft a Limbo Ingot. */
    public static final DeferredHolder<Item, Item> LIMBO_MEMBRANE =
            ITEMS.register("limbo_membrane", () -> new Item(new Item.Properties()));

    /** Limbo Ingot: crafted from 8 Limbo Membrane + 1 Emerald Apple. The "addition" that smiths netherite gear
     *  into Limbo gear, and the repair material for the set. */
    public static final DeferredHolder<Item, Item> LIMBO_INGOT =
            ITEMS.register("limbo_ingot", () -> new Item(new Item.Properties().fireResistant()));

    /** Limbo Smithing Template: a rare (1%) village-chest find. Together with a Limbo Ingot it upgrades a
     *  netherite piece into Limbo gear at a smithing table. */
    public static final DeferredHolder<Item, Item> LIMBO_SMITHING_TEMPLATE =
            ITEMS.register("limbo_smithing_template", () -> new Item(new Item.Properties()));

    /** Emerald Virus Arrow: a green arrow that infects whatever it hits with Virus I ({@link EmeraldVirusArrowItem}).
     *  Crafted from 8 arrows + a (regular) Potion of Emerald Virus. */
    public static final DeferredHolder<Item, Item> EMERALD_VIRUS_ARROW =
            ITEMS.register("emerald_virus_arrow", () -> new EmeraldVirusArrowItem(new Item.Properties(), 0));

    /** Emerald Virus Arrow II: the stronger arrow -- infects with Virus II (Wither II) on hit
     *  ({@link EmeraldVirusArrowItem}). Crafted from 8 arrows + a Potion of Emerald Virus II. */
    public static final DeferredHolder<Item, Item> EMERALD_VIRUS_ARROW_2 =
            ITEMS.register("emerald_virus_arrow_2", () -> new EmeraldVirusArrowItem(new Item.Properties(), 1));

    /** Fire Grenade: a throwable fire bomb (our native replacement for JEG's molotov). See {@link FireGrenadeItem}. */
    public static final DeferredHolder<Item, Item> FIRE_GRENADE =
            ITEMS.register("fire_grenade", () -> new FireGrenadeItem(new Item.Properties().stacksTo(16)));

    /** Emerald Egg: a throwable egg that inflicts the Emerald Virus on the creature it hits. Emerald chickens
     *  lay it. See {@link EmeraldEggItem} / {@link EmeraldEggEntity}. */
    public static final DeferredHolder<Item, Item> EMERALD_EGG =
            ITEMS.register("emerald_egg", () -> new EmeraldEggItem(new Item.Properties().stacksTo(16)));

    /** Glow Wand: right-click to make every nearby unit of this mod (incl. villagers/guards/golems) glow for 3s,
     *  and to mark the 4 summoning node blocks. See {@link GlowWandItem}. */
    public static final DeferredHolder<Item, Item> GLOW_WAND =
            ITEMS.register("glow_wand", () -> new GlowWandItem(new Item.Properties().stacksTo(1)));

    /** Emerald Apple: edible, but eating it inflicts the Emerald Virus. The base brewing component for the
     *  Emerald Virus potion (and, via that potion's tipped arrows, the Emerald Virus arrows). Drops rarely
     *  from fertile leaves. */
    public static final DeferredHolder<Item, Item> EMERALD_APPLE =
            ITEMS.register("emerald_apple", () -> new Item(new Item.Properties().food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(4).saturationModifier(0.3F).alwaysEdible()
                            .effect(new net.minecraft.world.effect.MobEffectInstance(
                                    ModEffects.EMERALD_VIRUS, EmeraldVirus.DURATION_TICKS), 1.0F)
                            .build())));

    /** Fertile Chorus: a chorus-fruit food (feeds + teleports on eat) dropped by the Fertile Chorus Plant.
     *  Eating it has a 1% chance to inflict Emerald Diarrhea ({@link FertileChorusItem}). */
    public static final DeferredHolder<Item, Item> FERTILE_CHORUS =
            ITEMS.register("fertile_chorus", () -> new FertileChorusItem(new Item.Properties().food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(4).saturationModifier(0.3F).build())));

    // --- Emerald tools: stats between iron and diamond, like the emerald armour (see EmeraldTier). In 1.21 the
    //     attack damage/speed are baked into Item.Properties via <Type>Item.createAttributes(tier, dmg, speed). ---
    public static final DeferredHolder<Item, Item> EMERALD_SWORD = ITEMS.register("emerald_sword",
            () -> new net.minecraft.world.item.SwordItem(EmeraldTier.INSTANCE, new Item.Properties()
                    .attributes(net.minecraft.world.item.SwordItem.createAttributes(EmeraldTier.INSTANCE, 3, -2.4F))));
    public static final DeferredHolder<Item, Item> EMERALD_PICKAXE = ITEMS.register("emerald_pickaxe",
            () -> new net.minecraft.world.item.PickaxeItem(EmeraldTier.INSTANCE, new Item.Properties()
                    .attributes(net.minecraft.world.item.PickaxeItem.createAttributes(EmeraldTier.INSTANCE, 1.0F, -2.8F))));
    public static final DeferredHolder<Item, Item> EMERALD_AXE = ITEMS.register("emerald_axe",
            () -> new net.minecraft.world.item.AxeItem(EmeraldTier.INSTANCE, new Item.Properties()
                    .attributes(net.minecraft.world.item.AxeItem.createAttributes(EmeraldTier.INSTANCE, 6.0F, -3.1F))));
    public static final DeferredHolder<Item, Item> EMERALD_SHOVEL = ITEMS.register("emerald_shovel",
            () -> new net.minecraft.world.item.ShovelItem(EmeraldTier.INSTANCE, new Item.Properties()
                    .attributes(net.minecraft.world.item.ShovelItem.createAttributes(EmeraldTier.INSTANCE, 1.5F, -3.0F))));
    public static final DeferredHolder<Item, Item> EMERALD_HOE = ITEMS.register("emerald_hoe",
            () -> new net.minecraft.world.item.HoeItem(EmeraldTier.INSTANCE, new Item.Properties()
                    .attributes(net.minecraft.world.item.HoeItem.createAttributes(EmeraldTier.INSTANCE, -2.0F, -1.0F))));
    /** Emerald Hammer: an emerald pickaxe that mines a 3x3 area (surrounding blocks capped at iron-mineable). */
    public static final DeferredHolder<Item, Item> EMERALD_HAMMER = ITEMS.register("emerald_hammer",
            () -> new EmeraldHammerItem(EmeraldTier.INSTANCE, 1, -3.2F, new Item.Properties()));

    /** Limbo Pickaxe: a netherite-grade pick (smithed) that also smashes bedrock + reinforced deepslate
     *  ({@link LimboPickaxeItem} / {@link LimboPickaxeEvents}). */
    public static final DeferredHolder<Item, Item> LIMBO_PICKAXE = ITEMS.register("limbo_pickaxe",
            () -> new LimboPickaxeItem(new Item.Properties().fireResistant()));

    // --- The rest of the Limbo tool set: smithed from netherite, more efficient than netherite
    //     (LimboToolTier: speed 10 vs 9, 2500 vs 2031 uses). ---
    public static final DeferredHolder<Item, Item> LIMBO_SWORD = ITEMS.register("limbo_sword",
            () -> new net.minecraft.world.item.SwordItem(LimboToolTier.INSTANCE, new Item.Properties().fireResistant()
                    .attributes(net.minecraft.world.item.SwordItem.createAttributes(LimboToolTier.INSTANCE, 4, -2.4F))) { // +1 dmg vs netherite
                @Override public boolean isFoil(net.minecraft.world.item.ItemStack s) { return true; }
            });
    public static final DeferredHolder<Item, Item> LIMBO_AXE = ITEMS.register("limbo_axe",
            () -> new net.minecraft.world.item.AxeItem(LimboToolTier.INSTANCE, new Item.Properties().fireResistant()
                    .attributes(net.minecraft.world.item.AxeItem.createAttributes(LimboToolTier.INSTANCE, 5.0F, -3.0F))) {
                @Override public boolean isFoil(net.minecraft.world.item.ItemStack s) { return true; }
            });
    public static final DeferredHolder<Item, Item> LIMBO_SHOVEL = ITEMS.register("limbo_shovel",
            () -> new net.minecraft.world.item.ShovelItem(LimboToolTier.INSTANCE, new Item.Properties().fireResistant()
                    .attributes(net.minecraft.world.item.ShovelItem.createAttributes(LimboToolTier.INSTANCE, 1.5F, -3.0F))) {
                @Override public boolean isFoil(net.minecraft.world.item.ItemStack s) { return true; }
            });
    public static final DeferredHolder<Item, Item> LIMBO_HOE = ITEMS.register("limbo_hoe",
            () -> new net.minecraft.world.item.HoeItem(LimboToolTier.INSTANCE, new Item.Properties().fireResistant()
                    .attributes(net.minecraft.world.item.HoeItem.createAttributes(LimboToolTier.INSTANCE, -4.0F, 0.0F))) {
                @Override public boolean isFoil(net.minecraft.world.item.ItemStack s) { return true; }
            });

    /** Limbo Scythe: the reaper's tool, crafted from a Limbo hoe. Stores souls reaped from substantial
     *  creatures and spends them raising the village's dead from their graves. See {@link LimboScytheItem}. */
    /** Obliterator: an evolved Limbo Sword (14 damage). With an Obliteration Furnace burning, its hits mark
     *  prey for erasure from the spawn tables. See {@link ObliteratorItem} / {@link ObliterationEvents}. */
    public static final DeferredHolder<Item, Item> OBLITERATOR = ITEMS.register("obliterator",
            () -> new ObliteratorItem(new Item.Properties().stacksTo(1).fireResistant()));

    /** Reputation Book: the rules of standing with the village, read-only, handed out at spawn and craftable
     *  from a book and an emerald. Its pages change with the difficulty. See {@link ReputationBookItem}. */
    public static final DeferredHolder<Item, Item> REPUTATION_BOOK = ITEMS.register("reputation_book",
            () -> new ReputationBookItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, Item> LIMBO_SCYTHE = ITEMS.register("limbo_scythe",
            () -> new LimboScytheItem(new Item.Properties().stacksTo(1).fireResistant()));

    /** Forgotten Powder: dropped by Forgotten Glowstone AND by the Limbo raiders (the Forgotten Dust used to
     *  craft the block back). Foil + an animated icon, matching the Limbo gear's shimmer. */
    public static final DeferredHolder<Item, Item> FORGOTTEN_POWDER =
            ITEMS.register("forgotten_powder", () -> new Item(new Item.Properties()) {
                @Override public boolean isFoil(net.minecraft.world.item.ItemStack s) { return true; }
            });

    /** The emerald tools, in tab order. */
    public static final java.util.List<DeferredHolder<Item, Item>> EMERALD_TOOLS = java.util.List.of(
            EMERALD_SWORD, EMERALD_PICKAXE, EMERALD_AXE, EMERALD_SHOVEL, EMERALD_HOE, EMERALD_HAMMER);

    /** Creative spawn egg for the Limbo Phantom. */
    public static final DeferredHolder<Item, Item> LIMBO_PHANTOM_SPAWN =
            ITEMS.register("limbo_phantom_spawn", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                    ModEntities.LIMBO_PHANTOM, 0x101018, 0xE8F0E8, new Item.Properties()));

    /** Creative spawn egg for the Emeraldger (the stage-4 virus warrior). */
    public static final DeferredHolder<Item, Item> EMERALDGER_SPAWN =
            ITEMS.register("emeraldger_spawn", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                    ModEntities.EMERALDGER, 0x17DD62, 0x0A6E2E, new Item.Properties()));

    /** Creative spawn egg for the Rich Sheep (the fertile/rich-looking, emerald-dropping sheep). */
    public static final DeferredHolder<Item, Item> RICH_SHEEP_SPAWN =
            ITEMS.register("rich_sheep_spawn", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                    ModEntities.RICH_SHEEP, 0x2BBF4E, 0x17DD62, new Item.Properties()));

    /** The emerald-faction tint every emerald mob egg is shifted toward. */
    private static final int EMERALD_TINT = 0x2BBF4E;

    /** Blend {@code color} 45% toward the emerald tint, so an egg still reads as its vanilla creature (its base
     *  / spot hues survive) while clearly being an emerald-green unit. */
    private static int greenify(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        int er = (EMERALD_TINT >> 16) & 0xFF, eg = (EMERALD_TINT >> 8) & 0xFF, eb = EMERALD_TINT & 0xFF;
        int nr = Math.round(r * 0.55F + er * 0.45F);
        int ng = Math.round(g * 0.55F + eg * 0.45F);
        int nb = Math.round(b * 0.55F + eb * 0.45F);
        return (nr << 16) | (ng << 8) | nb;
    }

    /** Creative spawn egg for an emerald-faction mob: its two colours are the VANILLA creature's spawn-egg
     *  colours green-shifted ({@link #greenify}), so each of the 18 is distinct AND recognisable as its creature. */
    private static DeferredHolder<Item, Item> emeraldEgg(String name,
            java.util.function.Supplier<? extends net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>> type,
            int vanillaBase, int vanillaSpots) {
        return ITEMS.register(name, () -> new EmeraldSpawnEggItem(
                type, greenify(vanillaBase), greenify(vanillaSpots), new Item.Properties()));
    }
    public static final DeferredHolder<Item, Item> EMERALD_COW_SPAWN = emeraldEgg("emerald_cow_spawn", ModEntities.EMERALD_COW, 0x443626, 0xA1A1A1);
    public static final DeferredHolder<Item, Item> EMERALD_CHICKEN_SPAWN = emeraldEgg("emerald_chicken_spawn", ModEntities.EMERALD_CHICKEN, 0xA1A1A1, 0xFF0000);
    public static final DeferredHolder<Item, Item> EMERALD_CAT_SPAWN = emeraldEgg("emerald_cat_spawn", ModEntities.EMERALD_CAT, 0xEFC88E, 0x957256);
    public static final DeferredHolder<Item, Item> EMERALD_ENDERMITE_SPAWN = emeraldEgg("emerald_endermite_spawn", ModEntities.EMERALD_ENDERMITE, 0x161616, 0x6E6E6E);
    public static final DeferredHolder<Item, Item> EMERALD_WOLF_SPAWN = emeraldEgg("emerald_wolf_spawn", ModEntities.EMERALD_WOLF, 0xD7D3D3, 0xCEAF96);
    public static final DeferredHolder<Item, Item> EMERALD_HORSE_SPAWN = emeraldEgg("emerald_horse_spawn", ModEntities.EMERALD_HORSE, 0xC09E7D, 0xEEE500);
    public static final DeferredHolder<Item, Item> EMERALD_PIG_SPAWN = emeraldEgg("emerald_pig_spawn", ModEntities.EMERALD_PIG, 0xF0A5A2, 0xCF7B7B);
    public static final DeferredHolder<Item, Item> EMERALD_SALMON_SPAWN = emeraldEgg("emerald_salmon_spawn", ModEntities.EMERALD_SALMON, 0xA00F10, 0x0E8474);
    public static final DeferredHolder<Item, Item> EMERALD_BAT_SPAWN = emeraldEgg("emerald_bat_spawn", ModEntities.EMERALD_BAT, 0x4C3E30, 0x0F0F0F);
    public static final DeferredHolder<Item, Item> EMERALD_ZOMBIE_SPAWN = emeraldEgg("emerald_zombie_spawn", ModEntities.EMERALD_ZOMBIE, 0x00AFAF, 0x799C65);
    public static final DeferredHolder<Item, Item> EMERALD_SKELETON_SPAWN = emeraldEgg("emerald_skeleton_spawn", ModEntities.EMERALD_SKELETON, 0xC1C1C1, 0x494949);
    public static final DeferredHolder<Item, Item> EMERALD_CREEPER_SPAWN = emeraldEgg("emerald_creeper_spawn", ModEntities.EMERALD_CREEPER, 0x0DA70B, 0x000000);
    public static final DeferredHolder<Item, Item> EMERALD_ENDERMAN_SPAWN = emeraldEgg("emerald_enderman_spawn", ModEntities.EMERALD_ENDERMAN, 0x161616, 0x000000);
    public static final DeferredHolder<Item, Item> EMERALD_IRON_GOLEM_SPAWN = emeraldEgg("emerald_iron_golem_spawn", ModEntities.EMERALD_IRON_GOLEM, 0xDBD3D0, 0x9E8B79);
    public static final DeferredHolder<Item, Item> EMERALD_DEFENDER_GOLEM_SPAWN = emeraldEgg("emerald_defender_golem_spawn", ModEntities.EMERALD_DEFENDER_GOLEM, 0xDBD3D0, 0x4FB6C4);
    public static final DeferredHolder<Item, Item> EMERALD_GIGACHAD_GOLEM_SPAWN = emeraldEgg("emerald_gigachad_golem_spawn", ModEntities.EMERALD_GIGACHAD_GOLEM, 0xFCEE4B, 0xB8860B);
    public static final DeferredHolder<Item, Item> EMERALD_PHANTOM_SPAWN = emeraldEgg("emerald_phantom_spawn", ModEntities.EMERALD_PHANTOM, 0x43518A, 0x88FF00);
    public static final DeferredHolder<Item, Item> EMERALD_WITHER_SPAWN = emeraldEgg("emerald_wither_spawn", ModEntities.EMERALD_WITHER, 0x141414, 0x4D4D4D);
    public static final DeferredHolder<Item, Item> EMERALD_GUARDIAN_SPAWN = emeraldEgg("emerald_guardian_spawn", ModEntities.EMERALD_GUARDIAN, 0x4A7283, 0xD0B43E);
    public static final DeferredHolder<Item, Item> EMERALD_ELDER_GUARDIAN_SPAWN = emeraldEgg("emerald_elder_guardian_spawn", ModEntities.EMERALD_ELDER_GUARDIAN, 0xCECE9A, 0x747693);

    /** Creative spawn eggs for the unleashed boss's vindictive minions. */
    public static final DeferredHolder<Item, Item> VINDICTIVE_GUARD_SPAWN =
            ITEMS.register("vindictive_guard_spawn", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                    ModEntities.VINDICTIVE_GUARD, 0x3A5F3A, 0x2BBF4E, new Item.Properties()));
    public static final DeferredHolder<Item, Item> VINDICTIVE_GIGACHAD_SPAWN =
            ITEMS.register("vindictive_gigachad_spawn", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                    ModEntities.VINDICTIVE_GIGACHAD, 0xB8860B, 0x2BBF4E, new Item.Properties()));

    /** Creative spawn egg for General Darius (the stage-4 boss guard). */
    public static final DeferredHolder<Item, Item> GENERAL_DARIUS_SPAWN =
            ITEMS.register("general_darius_spawn", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                    ModEntities.GENERAL_DARIUS, 0x35402A, 0x5AE682, new Item.Properties()));

    // --- Limbo raider spawn eggs: a custom animated/foil icon (item/generated, not the egg template) so they
    //     shimmer exactly like the Limbo gear. ---
    private static DeferredHolder<Item, Item> limboEgg(String name,
            java.util.function.Supplier<? extends net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>> type,
            int primary, int secondary) {
        return ITEMS.register(name, () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                type, primary, secondary, new Item.Properties()) {
            @Override public boolean isFoil(net.minecraft.world.item.ItemStack s) { return true; }
        });
    }

    public static final DeferredHolder<Item, Item> LIMBO_PILLAGER_SPAWN =
            limboEgg("limbo_pillager_spawn", ModEntities.LIMBO_PILLAGER, 0x283830, 0x96E1AF);
    public static final DeferredHolder<Item, Item> LIMBO_VINDICATOR_SPAWN =
            limboEgg("limbo_vindicator_spawn", ModEntities.LIMBO_VINDICATOR, 0x323E32, 0xAAEB96);
    public static final DeferredHolder<Item, Item> LIMBO_EVOKER_SPAWN =
            limboEgg("limbo_evoker_spawn", ModEntities.LIMBO_EVOKER, 0x222E38, 0x8CEBCD);
    public static final DeferredHolder<Item, Item> LIMBO_RAVAGER_SPAWN =
            limboEgg("limbo_ravager_spawn", ModEntities.LIMBO_RAVAGER, 0x1C221E, 0x78C88C);
    public static final DeferredHolder<Item, Item> LIMBO_VEX_SPAWN =
            limboEgg("limbo_vex_spawn", ModEntities.LIMBO_VEX, 0x2E3E48, 0xB9F5DE);
    public static final DeferredHolder<Item, Item> LIMBO_WITCH_SPAWN =
            limboEgg("limbo_witch_spawn", ModEntities.LIMBO_WITCH, 0x241E2E, 0xC9BCE6);

    /** The Limbo raider spawn eggs, in tab order. */
    public static final java.util.List<DeferredHolder<Item, Item>> LIMBO_RAID_EGGS = java.util.List.of(
            LIMBO_PILLAGER_SPAWN, LIMBO_VINDICATOR_SPAWN, LIMBO_EVOKER_SPAWN, LIMBO_RAVAGER_SPAWN, LIMBO_VEX_SPAWN,
            LIMBO_WITCH_SPAWN);

    // --- Staged-defender spawn eggs: place a villager/guard/golem already configured to a BrainNode stage,
    //     even with no brainNode present (see StagedDefenderEggItem). ---
    private static DeferredHolder<Item, Item> egg(String name, StagedDefenderEggItem.Kind kind, int stage, int primary, int secondary) {
        return ITEMS.register(name, () -> new StagedDefenderEggItem(kind, stage, primary, secondary, new Item.Properties()));
    }

    public static final DeferredHolder<Item, Item> VILLAGER_STAGE1 = egg("villager_stage1", StagedDefenderEggItem.Kind.VILLAGER, 1, 0x8B6F47, 0xA0A0A0);
    public static final DeferredHolder<Item, Item> VILLAGER_STAGE3 = egg("villager_stage3", StagedDefenderEggItem.Kind.VILLAGER, 3, 0x8B6F47, 0x2BBF4E);
    public static final DeferredHolder<Item, Item> VILLAGER_STAGE4 = egg("villager_stage4", StagedDefenderEggItem.Kind.VILLAGER, 4, 0x2BBF4E, 0x0A5C28);
    public static final DeferredHolder<Item, Item> GUARD_STAGE1 = egg("guard_stage1", StagedDefenderEggItem.Kind.GUARD, 1, 0x8E9BA8, 0xA0A0A0);
    public static final DeferredHolder<Item, Item> GUARD_STAGE2 = egg("guard_stage2", StagedDefenderEggItem.Kind.GUARD, 2, 0x8E9BA8, 0x6FE08A);
    public static final DeferredHolder<Item, Item> GUARD_STAGE3 = egg("guard_stage3", StagedDefenderEggItem.Kind.GUARD, 3, 0x8E9BA8, 0x2BBF4E);
    public static final DeferredHolder<Item, Item> GUARD_STAGE4 = egg("guard_stage4", StagedDefenderEggItem.Kind.GUARD, 4, 0x2BBF4E, 0x0A5C28);
    public static final DeferredHolder<Item, Item> IRON_GOLEM = egg("iron_golem", StagedDefenderEggItem.Kind.GOLEM, 1, 0xDBD3D0, 0x70A35A);
    public static final DeferredHolder<Item, Item> GIGACHAD_GOLEM = egg("gigachad_golem", StagedDefenderEggItem.Kind.GOLEM, 4, 0xFFD24D, 0xB8860B); // golden; stage 4 == the fully-evolved gigachad type

    /** All staged-defender eggs, in creative-tab / colour-handler order. */
    public static final java.util.List<DeferredHolder<Item, Item>> STAGED_EGGS = java.util.List.of(
            VILLAGER_STAGE1, VILLAGER_STAGE3, VILLAGER_STAGE4,
            GUARD_STAGE1, GUARD_STAGE2, GUARD_STAGE3, GUARD_STAGE4,
            IRON_GOLEM, GIGACHAD_GOLEM);

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
