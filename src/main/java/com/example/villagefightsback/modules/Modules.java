package com.example.villagefightsback.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.villagefightsback.AirStrikeManager;
import com.example.villagefightsback.AlienSmasherEvents;
import com.example.villagefightsback.ArmorStandEvents;
import com.example.villagefightsback.BrainNodeAchievementEvents;
import com.example.villagefightsback.BrainNodeEvents;
import com.example.villagefightsback.BrainReflexes;
import com.example.villagefightsback.ChunkPortalManager;
import com.example.villagefightsback.DariusSpecialEffectEvents;
import com.example.villagefightsback.DariusSpecialEffectReversals;
import com.example.villagefightsback.DariusStatue;
import com.example.villagefightsback.DefenderGolemForming;
import com.example.villagefightsback.ElegantStandEvents;
import com.example.villagefightsback.EmeraldConversion;
import com.example.villagefightsback.EmeraldHammerItem;
import com.example.villagefightsback.EmeraldPopulation;
import com.example.villagefightsback.EmeraldVirusEvents;
import com.example.villagefightsback.ExpansionManager;
import com.example.villagefightsback.FoundingManager;
import com.example.villagefightsback.Graves;
import com.example.villagefightsback.GuardDeathExplosionEvents;
import com.example.villagefightsback.GuardWeapons;
import com.example.villagefightsback.HostileStart;
import com.example.villagefightsback.HouseBuildManager;
import com.example.villagefightsback.KillstreakMarker;
import com.example.villagefightsback.LimboBanishFx;
import com.example.villagefightsback.LimboBookEvents;
import com.example.villagefightsback.LimboDimension;
import com.example.villagefightsback.LimboDriftEvents;
import com.example.villagefightsback.LimboGearEvents;
import com.example.villagefightsback.LimboGravelItemEvents;
import com.example.villagefightsback.LimboManager;
import com.example.villagefightsback.LimboMobControl;
import com.example.villagefightsback.LimboPhantomSpawner;
import com.example.villagefightsback.LimboPickaxeEvents;
import com.example.villagefightsback.LimboPortalFlatgen;
import com.example.villagefightsback.LimboPortalIgniteEvents;
import com.example.villagefightsback.LimboRaidEvents;
import com.example.villagefightsback.LimboRaidManager;
import com.example.villagefightsback.LimboSageEvents;
import com.example.villagefightsback.LimboSageTrades;
import com.example.villagefightsback.LimboScytheEvents;
import com.example.villagefightsback.LimboSleepEvents;
import com.example.villagefightsback.LimboWorldgenEvents;
import com.example.villagefightsback.MaceCombatEvents;
import com.example.villagefightsback.MegaBrutal;
import com.example.villagefightsback.MemorialScytheEvents;
import com.example.villagefightsback.ModCapabilities;
import com.example.villagefightsback.ModCommands;
import com.example.villagefightsback.ModEntities;
import com.example.villagefightsback.ModProfiler;
import com.example.villagefightsback.ModTooltips;
import com.example.villagefightsback.NightmareRaid;
import com.example.villagefightsback.NodeBounty;
import com.example.villagefightsback.NodeGuard;
import com.example.villagefightsback.NodeSpawnScheduler;
import com.example.villagefightsback.ObliterationEvents;
import com.example.villagefightsback.ReputationEvents;
import com.example.villagefightsback.ReversedGravity;
import com.example.villagefightsback.RichBlockEvents;
import com.example.villagefightsback.SculkCatalystEvents;
import com.example.villagefightsback.ShieldBashEvents;
import com.example.villagefightsback.SlowAnnihilationEvents;
import com.example.villagefightsback.SpawnInVillage;
import com.example.villagefightsback.StandingEffects;
import com.example.villagefightsback.StartingItems;
import com.example.villagefightsback.SurvivalCache;
import com.example.villagefightsback.UnleashedManager;
import com.example.villagefightsback.UpgradeEvents;
import com.example.villagefightsback.VillageChestProtection;
import com.example.villagefightsback.VillageEvents;
import com.example.villagefightsback.VillageStatusCommand;
import com.example.villagefightsback.VillagerKills;
import com.example.villagefightsback.VoidFallEvents;
import com.example.villagefightsback.WallRepairEvents;
import com.example.villagefightsback.WaterClutchEvents;
import com.example.villagefightsback.WorldModeEvents;
import com.example.villagefightsback.client.EmeraldTintClient;
import com.example.villagefightsback.client.LimboAbilityClient;
import com.example.villagefightsback.client.LimboAbilityHud;
import com.example.villagefightsback.client.LimboAbilityKeybinds;
import com.example.villagefightsback.client.LimboBedrockPhaseClient;
import com.example.villagefightsback.client.LimboSkyClient;
import com.example.villagefightsback.client.ReversedGravityClient;
import com.example.villagefightsback.client.ScreenShakeClient;
import com.example.villagefightsback.client.TheVillageFightsBackClient;
import com.example.villagefightsback.client.VillageDiscoveryHud;
import com.example.villagefightsback.client.VillageDiscoveryTicker;
import com.example.villagefightsback.infection.InfectionManager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;

/**
 * The mod's module table: every feature belongs to exactly one {@link Module}, and every module owns the content
 * and the event handlers that implement it.
 *
 * <p>Adding a module: write a nested {@code Module} subclass here, declare its handler classes, and add it to
 * {@link #ALL}. Content is assigned by name through {@link #ownerOf(String)} (plus the explicit rules at the
 * top of that method); nothing else needs editing -- the registration sites already ask this class.
 *
 * <p>{@code core} is the always-on module: configuration, commands, the capability/entity plumbing and anything
 * so cross-cutting that turning it off would break the loader rather than remove a feature.
 */
public final class Modules {
    private Modules() {}

    /** Always on: config, commands, capabilities, entity attribute plumbing, profiler. */
    public static final Module CORE = new Core();
    /** The Village Brain Node itself: stages, upgrades, the node blocks, achievements, its guards and bounty. */
    public static final Module BRAIN_NODE = new BrainNode();
    /** Villagers and guards arming themselves, walls, houses, founding, expansion, chest protection, graves. */
    public static final Module VILLAGE_DEFENSE = new VillageDefense();
    /** Emerald creatures, emerald conversion/population, the virus, block+mob infection, organic mass. */
    public static final Module EMERALD_ARMY = new EmeraldArmy();
    /** Weapons and tools: alien gear, maces, rockets, air strikes, shields, the obliterator. */
    public static final Module WEAPONS = new Weapons();
    /** Limbo: the dimension, its portals, drift, raids, the sage, limbo gear and books. */
    public static final Module LIMBO = new Limbo();
    /** General Darius, his squad, his special effect and the statue. */
    public static final Module DARIUS = new Darius();
    /** The Vindictive Village Spirit boss and the memorial that summons it. */
    public static final Module VILLAGE_SPIRIT = new VillageSpirit();
    /** Reputation, world modes, difficulty, spawn behaviour, starting kit and survival aids. */
    public static final Module WORLD_MODE = new WorldMode();
    /** HUD, key binds, sky, tint and screen shake: client-side only, but still a module. */
    public static final Module CLIENT = new Client();

    public static final List<Module> ALL = List.of(
            CORE, BRAIN_NODE, VILLAGE_DEFENSE, EMERALD_ARMY, WEAPONS, LIMBO, DARIUS, VILLAGE_SPIRIT, WORLD_MODE, CLIENT);

    private static final Map<String, Module> BY_ID = index();

    private static Map<String, Module> index() {
        Map<String, Module> byId = new java.util.LinkedHashMap<>();
        for (Module m : ALL) {
            byId.put(m.id, m);
        }
        return java.util.Collections.unmodifiableMap(byId);
    }

    public static Module byId(String id) {
        return BY_ID.get(id);
    }

    // --- content ownership -----------------------------------------------------------------------------------

    /**
     * Which module owns a registry entry, by name. Unclaimed names belong to {@code core} (null here) and are
     * always registered, so a missed rule degrades to "content stays" rather than "content vanishes".
     *
     * <p>Blocks and their block items share names deliberately: a disabled module drops both, which matters
     * because a block item's supplier reads {@code BLOCK.get()} and would blow up on its own.
     */
    public static Module ownerOf(String name) {
        if (name.contains("limbo") || name.startsWith("chunk_portal")
                || name.startsWith("obliteration") || name.startsWith("slow_annihilation")) {
            return LIMBO;
        }
        // contains(), not startsWith(): the strong/long potion variants are named "strong_emerald_virus" and
        // "long_darius_special_effect", and reading their effect is what owns them.
        if (name.contains("emerald") || name.startsWith("fertile") || name.startsWith("rich_")
                || name.startsWith("infested") || name.contains("virus")) {
            return EMERALD_ARMY;
        }
        // contains(): the node's creatures are named "unleashed_brain_node", "brain_node_warden" and friends.
        if (name.contains("brain") || name.startsWith("golem_factory") || name.startsWith("barracks")
                || name.startsWith("lure_tower")) {
            return BRAIN_NODE;
        }
        if (name.contains("darius")) return DARIUS;
        if (name.startsWith("village_spirit") || name.startsWith("memorial") || name.startsWith("vindictive")) {
            return VILLAGE_SPIRIT;
        }
        if (name.startsWith("alien") || name.startsWith("mace") || name.startsWith("rocket")
                || name.startsWith("wind_charge") || name.startsWith("fire_grenade") || name.startsWith("glow_wand")
                || name.startsWith("paladin_shield") || name.startsWith("gas_mask")) {
            return WEAPONS;
        }
        if (name.startsWith("wall") || name.startsWith("village_path") || name.startsWith("no_build_sign")
                || name.startsWith("fake_") || name.startsWith("cheap_scaffold") || name.startsWith("reinforced_glass")
                || name.startsWith("protecting_glass") || name.startsWith("obsidian_stairs")
                || name.startsWith("iron_golem_head") || name.startsWith("elegant_stand") || name.startsWith("debris")
                || name.startsWith("dome")) {
            return VILLAGE_DEFENSE;
        }
        if (name.startsWith("reputation")) return WORLD_MODE;
        return null;
    }

    /** Whether an entry named {@code name} in the given registry may be registered right now. */
    public static boolean contentEnabled(String registryPath, String name) {
        Module owner = ownerOf(name);
        return owner == null || owner.isEnabled();
    }

    /** Whether the item behind a stack belongs to an enabled module (used to filter the creative tabs). */
    public static boolean contentEnabledForItem(net.minecraft.world.item.Item item) {
        var key = BuiltInRegistries.ITEM.getKey(item);
        return key == null || contentEnabled("item", key.getPath());
    }

    /**
     * Wraps a creative-tab sink so that stacks from disabled modules never appear. One wrapper at each tab's
     * {@code displayItems} lambda keeps the ~200 explicit {@code output.accept(...)} lines untouched.
     */
    public static CreativeModeTab.Output gate(CreativeModeTab.Output out) {
        return new CreativeModeTab.Output() {
            @Override
            public void accept(net.minecraft.world.item.ItemStack stack, CreativeModeTab.TabVisibility visibility) {
                if (stack.isEmpty() || contentEnabledForItem(stack.getItem())) {
                    out.accept(stack, visibility);
                }
            }
        };
    }

    // --- module definitions ----------------------------------------------------------------------------------

    private static final class Core extends Module {
        Core() {
            super("core", "Core", ModuleCategory.MANAGEMENT,
                    "Config, commands and the loader plumbing every other module builds on. Always on.", true, true);
            modHandlers(ModCapabilities.class, ModEntities.class);
            handlers(ModCommands.class, ModProfiler.class, VillageStatusCommand.class);
        }
    }

    private static final class BrainNode extends Module {
        BrainNode() {
            super("brain_node", "Village Brain Node", ModuleCategory.MANAGEMENT,
                    "The Village Brain Node: its four stages and upgrades, the node blocks (factory, barracks, lure"
                            + " tower, dead node), the node's guards, bounty, spawns and achievements.");
            handlers(BrainNodeEvents.class, BrainNodeAchievementEvents.class, BrainReflexes.class, NodeBounty.class,
                    NodeGuard.class, NodeSpawnScheduler.class, UnleashedManager.class, UpgradeEvents.class,
                    StandingEffects.class);
        }
    }

    private static final class VillageDefense extends Module {
        VillageDefense() {
            super("village_defense", "Village Defense", ModuleCategory.DEFENSE,
                    "The village arming itself: guard weapons and kill effects, defenders, walls and wall repair,"
                            + " house building, founding and expansion, chest protection, graves and raids.");
            requires("brain_node");
            handlers(VillageEvents.class, GuardWeapons.class, GuardDeathExplosionEvents.class,
                    DefenderGolemForming.class, WallRepairEvents.class, HouseBuildManager.class,
                    FoundingManager.class, ExpansionManager.class, VillageChestProtection.class, Graves.class,
                    KillstreakMarker.class, NightmareRaid.class, ArmorStandEvents.class, ElegantStandEvents.class,
                    WaterClutchEvents.class);
        }
    }

    private static final class EmeraldArmy extends Module {
        EmeraldArmy() {
            super("emerald_army", "Emerald Army", ModuleCategory.CREATURES,
                    "The emerald ecosystem: emerald creatures and conversion, population control, the emerald"
                            + " virus, block and mob infection, and the organic mass system.");
            requires("brain_node");
            handlers(EmeraldConversion.class, EmeraldPopulation.class, EmeraldVirusEvents.class,
                    InfectionManager.class, SculkCatalystEvents.class, RichBlockEvents.class);
        }
    }

    private static final class Weapons extends Module {
        Weapons() {
            super("weapons", "Weapons & Gear", ModuleCategory.COMBAT,
                    "Weapons and tools: alien slasher/eradicator/smasher, maces, rockets and the launcher, wind"
                            + " charges, fire grenades, shields, the obliterator and the air-strike system.");
            handlers(AirStrikeManager.class, AlienSmasherEvents.class, MaceCombatEvents.class,
                    ShieldBashEvents.class, ObliterationEvents.class, EmeraldHammerItem.FaceTracker.class);
        }
    }

    private static final class Limbo extends Module {
        Limbo() {
            super("limbo", "Limbo", ModuleCategory.WORLD,
                    "The Limbo dimension: portals and chunk portals, dimensional drift, limbo raids and the sage,"
                            + " limbo gear, books, beds and graves' worth of gravel.");
            handlers(ChunkPortalManager.class, LimboBanishFx.class, LimboBookEvents.class, LimboDimension.class,
                    LimboDriftEvents.class, LimboGearEvents.class, LimboGravelItemEvents.class, LimboManager.class,
                    LimboMobControl.class, LimboPhantomSpawner.class, LimboPickaxeEvents.class,
                    LimboPortalFlatgen.class, LimboPortalIgniteEvents.class, LimboRaidEvents.class,
                    LimboRaidManager.class, LimboSageEvents.class, LimboSageTrades.class, LimboScytheEvents.class,
                    LimboSleepEvents.class, LimboWorldgenEvents.class, SlowAnnihilationEvents.class);
        }
    }

    private static final class Darius extends Module {
        Darius() {
            super("darius", "General Darius", ModuleCategory.COMBAT,
                    "General Darius: the boss himself, his squad, his special effects and reversals, and the"
                            + " statue that remembers him.");
            requires("brain_node");
            handlers(DariusSpecialEffectEvents.class, DariusSpecialEffectReversals.class, DariusStatue.class,
                    ReversedGravity.class);
        }
    }

    private static final class VillageSpirit extends Module {
        VillageSpirit() {
            super("village_spirit", "Village Spirit", ModuleCategory.COMBAT,
                    "The Vindictive Village Spirit, the boss a destroyed brain node unleashes, and the memorial"
                            + " scythe that calls it.");
            requires("brain_node");
            handlers(MemorialScytheEvents.class);
        }
    }

    private static final class WorldMode extends Module {
        WorldMode() {
            super("world_mode", "Reputation & World Modes", ModuleCategory.MANAGEMENT,
                    "Village reputation and hero/enemy standing, difficulty and brutal modes, where players spawn,"
                            + " the starting kit and the survival aids around it.");
            handlers(ReputationEvents.class, WorldModeEvents.class, MegaBrutal.class, HostileStart.class,
                    SpawnInVillage.class, StartingItems.class, SurvivalCache.class, VoidFallEvents.class,
                    VillagerKills.class);
        }
    }

    private static final class Client extends Module {
        Client() {
            super("client", "Client", ModuleCategory.CLIENT,
                    "Client-side presentation: the HUD panel and discovery ticker, key binds, limbo sky and"
                            + " bedrock phase, reversed-gravity flip, screen shake and tooltips.");
            clientHandlers(EmeraldTintClient.class, LimboAbilityClient.class, LimboBedrockPhaseClient.class,
                    LimboSkyClient.class, ReversedGravityClient.class, ScreenShakeClient.class,
                    VillageDiscoveryTicker.class, ModTooltips.class);
            clientModHandlers(LimboAbilityHud.class, LimboAbilityKeybinds.class, VillageDiscoveryHud.class,
                    TheVillageFightsBackClient.class);
        }
    }

    /** Convenience for the command listing. */
    public static List<Module> sortedByCategory() {
        List<Module> out = new ArrayList<>(ALL);
        out.sort(java.util.Comparator.comparing((Module m) -> m.category.ordinal()).thenComparing(m -> m.id));
        return out;
    }
}
