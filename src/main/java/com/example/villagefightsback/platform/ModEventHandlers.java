package com.example.villagefightsback.platform;

/**
 * The handler classes, listed by hand because Fabric has nothing like Forge's classpath scan for
 * {@code @Mod.EventBusSubscriber}.
 *
 * <p>A new class with {@link SubscribeEvent} methods is invisible until it is added here. {@link EventBus#register}
 * throws if a listed class has no handler methods, so a stale entry fails loudly at startup rather than quietly
 * doing nothing.
 */
public final class ModEventHandlers {
    private ModEventHandlers() {}

    /** Everything that runs on both sides. Called from the mod initializer. */
    public static void registerCommon() {
        EventBus.register(com.example.villagefightsback.AirStrikeManager.class);
        EventBus.register(com.example.villagefightsback.AlienSmasherEvents.class);
        EventBus.register(com.example.villagefightsback.ArmorStandEvents.class);
        EventBus.register(com.example.villagefightsback.BrainNodeAchievementEvents.class);
        EventBus.register(com.example.villagefightsback.BrainNodeEvents.class);
        EventBus.register(com.example.villagefightsback.BrainReflexes.class);
        EventBus.register(com.example.villagefightsback.ChunkPortalManager.class);
        EventBus.register(com.example.villagefightsback.DariusSpecialEffectEvents.class);
        EventBus.register(com.example.villagefightsback.DariusSpecialEffectReversals.class);
        EventBus.register(com.example.villagefightsback.DefenderGolemForming.class);
        EventBus.register(com.example.villagefightsback.ElegantStandEvents.class);
        EventBus.register(com.example.villagefightsback.EmeraldConversion.class);
        EventBus.register(com.example.villagefightsback.EmeraldHammerItem.class);
        EventBus.register(com.example.villagefightsback.EmeraldPopulation.class);
        EventBus.register(com.example.villagefightsback.EmeraldVirusEvents.class);
        EventBus.register(com.example.villagefightsback.ExpansionManager.class);
        EventBus.register(com.example.villagefightsback.FoundingManager.class);
        EventBus.register(com.example.villagefightsback.Graves.class);
        EventBus.register(com.example.villagefightsback.GuardDeathExplosionEvents.class);
        EventBus.register(com.example.villagefightsback.HostileStart.class);
        EventBus.register(com.example.villagefightsback.HouseBuildManager.class);
        EventBus.register(com.example.villagefightsback.KillstreakMarker.class);
        EventBus.register(com.example.villagefightsback.LimboBanishFx.class);
        EventBus.register(com.example.villagefightsback.LimboBookEvents.class);
        EventBus.register(com.example.villagefightsback.LimboDimension.class);
        EventBus.register(com.example.villagefightsback.LimboDriftEvents.class);
        EventBus.register(com.example.villagefightsback.LimboGearEvents.class);
        EventBus.register(com.example.villagefightsback.LimboGravelItemEvents.class);
        EventBus.register(com.example.villagefightsback.LimboManager.class);
        EventBus.register(com.example.villagefightsback.LimboMobControl.class);
        EventBus.register(com.example.villagefightsback.LimboPhantomSpawner.class);
        EventBus.register(com.example.villagefightsback.LimboPickaxeEvents.class);
        EventBus.register(com.example.villagefightsback.LimboPortalIgniteEvents.class);
        EventBus.register(com.example.villagefightsback.LimboRaidEvents.class);
        EventBus.register(com.example.villagefightsback.LimboRaidManager.class);
        EventBus.register(com.example.villagefightsback.LimboSageEvents.class);
        EventBus.register(com.example.villagefightsback.LimboSageTrades.class);
        EventBus.register(com.example.villagefightsback.LimboScytheEvents.class);
        EventBus.register(com.example.villagefightsback.LimboSleepEvents.class);
        EventBus.register(com.example.villagefightsback.LimboWorldgenEvents.class);
        EventBus.register(com.example.villagefightsback.MaceCombatEvents.class);
        EventBus.register(com.example.villagefightsback.MegaBrutal.class);
        EventBus.register(com.example.villagefightsback.MemorialScytheEvents.class);
        EventBus.register(com.example.villagefightsback.ModCommands.class);
        EventBus.register(com.example.villagefightsback.ModProfiler.class);
        EventBus.register(com.example.villagefightsback.NightmareRaid.class);
        EventBus.register(com.example.villagefightsback.NodeBounty.class);
        EventBus.register(com.example.villagefightsback.NodeGuard.class);
        EventBus.register(com.example.villagefightsback.NodeSpawnScheduler.class);
        EventBus.register(com.example.villagefightsback.ObliterationEvents.class);
        EventBus.register(com.example.villagefightsback.ReputationEvents.class);
        EventBus.register(com.example.villagefightsback.ReversedGravity.class);
        EventBus.register(com.example.villagefightsback.RichBlockEvents.class);
        EventBus.register(com.example.villagefightsback.SculkCatalystEvents.class);
        EventBus.register(com.example.villagefightsback.SpawnInVillage.class);
        EventBus.register(com.example.villagefightsback.StandingEffects.class);
        EventBus.register(com.example.villagefightsback.StartingItems.class);
        EventBus.register(com.example.villagefightsback.UnleashedManager.class);
        EventBus.register(com.example.villagefightsback.UpgradeEvents.class);
        EventBus.register(com.example.villagefightsback.VillageChestProtection.class);
        EventBus.register(com.example.villagefightsback.VillageEvents.class);
        EventBus.register(com.example.villagefightsback.VillageStatusCommand.class);
        EventBus.register(com.example.villagefightsback.VillagerKills.class);
        EventBus.register(com.example.villagefightsback.VoidFallEvents.class);
        EventBus.register(com.example.villagefightsback.WallRepairEvents.class);
        EventBus.register(com.example.villagefightsback.WaterClutchEvents.class);
        EventBus.register(com.example.villagefightsback.WorldModeEvents.class);
        EventBus.register(com.example.villagefightsback.infection.InfectionManager.class);
    }

    /** Client-only handlers. Called from the client initializer, so a dedicated server never loads these classes. */
    public static void registerClient() {
        EventBus.register(com.example.villagefightsback.ModTooltips.class);
        EventBus.register(com.example.villagefightsback.client.EmeraldTintClient.class);
        EventBus.register(com.example.villagefightsback.client.LimboAbilityClient.class);
        EventBus.register(com.example.villagefightsback.client.LimboBedrockPhaseClient.class);
        EventBus.register(com.example.villagefightsback.client.LimboSkyClient.class);
        EventBus.register(com.example.villagefightsback.client.ReversedGravityClient.class);
        EventBus.register(com.example.villagefightsback.client.ScreenShakeClient.class);
        EventBus.register(com.example.villagefightsback.client.VillageDiscoveryTicker.class);
    }
}
