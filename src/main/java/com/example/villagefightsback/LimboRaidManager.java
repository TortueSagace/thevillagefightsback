package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * The Limbo raid. Spend more than 30s straight in the Limbo and you earn the {@link VeryBadOmenEffect Very Bad
 * Omen}; carry it back into a village and a raid erupts. This is a REAL vanilla raid (triggered through the
 * vanilla {@link net.minecraft.world.entity.raid.Raids} system, so it has the exact vanilla waves, raid bar,
 * horn, village AI and victory/loss) -- the only change is that every illager vanilla spawns is immediately
 * swapped for its ghostly Limbo counterpart, and each wave is reinforced with a few Limbo Phantoms.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboRaidManager {
    private LimboRaidManager() {}

    private static final int OMEN_TICKS = 600;        // 30s straight in the Limbo earns the omen
    private static final int OMEN_DURATION = 12000;   // the omen lasts 10 min (time to get home)
    private static final int VILLAGE_SCAN = 64;       // a village this close counts as "in a village"
    private static final double NEAR = 64.0;          // the red warning reaches players this close to the raid

    /** UUID -> consecutive ticks the player has been in the Limbo (transient). */
    private static final Map<UUID, Integer> limboTime = new HashMap<>();
    /** Raids we have marked as "Limbo" (their raiders get swapped) -> the level they run in. */
    private static final Map<Raid, ServerLevel> limboRaids = new IdentityHashMap<>();
    /** Per Limbo raid: how many spawned wave-groups we have already reinforced with phantoms. */
    private static final Map<Raid, Integer> phantomGroups = new IdentityHashMap<>();
    /** Limbo raids whose guaranteed final-wave Limbo Evoker has already been spawned. */
    private static final java.util.Set<Raid> finalEvokerDone =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    /** Vanilla raiders that spawned into a Limbo raid, awaiting swap (processed next tick to avoid mid-spawn CME). */
    private static final List<Raider> pendingSwaps = new ArrayList<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long _p = ModProfiler.now();
        processSwaps();
        reinforceWaves();
        if (server.getTickCount() % 20 == 0) scanPlayers(server); // omen + village-return once a second
        limboRaids.keySet().removeIf(r -> !r.isActive()); // forget finished raids
        phantomGroups.keySet().removeIf(r -> !r.isActive());
        finalEvokerDone.removeIf(r -> !r.isActive());
        ModProfiler.add("limbo.raid", _p);
    }

    /** Drop the in-Limbo timer on logout (no map leak; the 30s window restarts on relog). */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        limboTime.remove(event.getEntity().getUUID());
    }

    /** A vanilla raider that spawned into a Limbo raid is queued for replacement (done next tick, not during the
     *  vanilla wave-spawn loop, so we never mutate the raid's roster while it is being iterated). */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Raider raider && !(raider instanceof LimboRaider)) {
            Raid raid = raider.getCurrentRaid();
            if (raid != null && limboRaids.containsKey(raid)) pendingSwaps.add(raider);
        }
    }

    private static void processSwaps() {
        if (pendingSwaps.isEmpty()) return;
        List<Raider> batch = new ArrayList<>(pendingSwaps);
        pendingSwaps.clear();
        for (Raider vanilla : batch) {
            if (!vanilla.isAlive()) continue;
            Raid raid = vanilla.getCurrentRaid();
            if (raid == null || !limboRaids.containsKey(raid)) continue;
            if (!(vanilla.level() instanceof ServerLevel level)) continue;
            EntityType<? extends Mob> type = limboType(vanilla);
            if (type == null) continue;
            Mob created = type.create(level);
            if (!(created instanceof Raider limbo)) { if (created != null) created.discard(); continue; }
            // joinRaid with pos + recruited=false makes the limbo raider a real member of the SAME wave and
            // (vanilla code) positions it, finalizes it, applies the wave's raid buffs, and adds it to the level.
            raid.joinRaid(vanilla.getWave(), limbo, vanilla.blockPosition(), false);
            vanilla.setCurrentRaid(null); // detach first so discarding the vanilla one doesn't dent the raid count
            vanilla.discard();
        }
    }

    /** Each time the vanilla raid spawns a new wave-group, drop in a few Limbo Phantoms alongside it, and on the
     *  FINAL wave always add a Limbo Evoker (joined to the raid, so it must be beaten to win). */
    private static void reinforceWaves() {
        for (Map.Entry<Raid, ServerLevel> e : limboRaids.entrySet()) {
            Raid raid = e.getKey();
            ServerLevel level = e.getValue();
            if (!raid.isActive()) continue;
            int groups = raid.getGroupsSpawned();
            if (groups > phantomGroups.getOrDefault(raid, 0)) {
                phantomGroups.put(raid, groups);
                BlockPos center = raid.getCenter();
                int phantoms = (int) Math.round((2 + level.getRandom().nextInt(2)) * Config.limboRaidersMult());
                spawnPhantoms(level, center, phantoms);
                if (groups >= raid.getNumGroups(level.getDifficulty()) && finalEvokerDone.add(raid)) {
                    spawnFinalEvoker(level, raid, center, groups);
                }
            }
        }
    }

    /** Track time-in-Limbo (-> the omen) and watch for omen-carriers returning to a village (-> a Limbo raid). */
    private static void scanPlayers(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerLevel level = p.serverLevel();
            UUID id = p.getUUID();
            if (LimboDimension.LIMBO.equals(level.dimension())) {
                int t = limboTime.merge(id, 20, Integer::sum);
                if (t >= OMEN_TICKS && !p.hasEffect(ModEffects.VERY_BAD_OMEN.get())) applyOmen(p);
            } else {
                limboTime.remove(id);
                if (p.hasEffect(ModEffects.VERY_BAD_OMEN.get())) {
                    BlockPos center = findVillageCenter(level, p.blockPosition());
                    if (center != null && !raidActiveNear(center)) triggerLimboRaid(p, level, center);
                }
            }
        }
    }

    /** Apply the omen (visible=false -> NO particles) and warn the just-marked player in red: "Incoming...". */
    private static void applyOmen(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(ModEffects.VERY_BAD_OMEN.get(), OMEN_DURATION, 0, false, false, true));
        p.sendSystemMessage(Component.literal("§4Incoming..."));
    }

    /** Convert the Very Bad Omen into a REAL vanilla raid at the village, then mark it so its raiders are swapped. */
    private static void triggerLimboRaid(ServerPlayer player, ServerLevel level, BlockPos center) {
        player.removeEffect(ModEffects.VERY_BAD_OMEN.get());
        // Hand the vanilla raid system a Bad Omen and let it build the raid; createOrExtendRaid absorbs it.
        player.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, 100, 0, false, false, false));
        Raid raid = level.getRaids().createOrExtendRaid(player);
        if (raid == null) { player.removeEffect(MobEffects.BAD_OMEN); return; } // no raid possible -> don't leak omen
        limboRaids.put(raid, level);
        // The payoff line as the raid actually begins, shown to everyone nearby (the "Incoming..." warning was
        // shown earlier, when the player was marked in the Limbo).
        announce(level, center, "§4You should have left when I told you to.");
    }

    private static void announce(ServerLevel level, BlockPos center, String msg) {
        Component c = Component.literal(msg);
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().closerThan(center, NEAR)) p.sendSystemMessage(c);
        }
    }

    /** Vanilla -> Limbo entity-type mapping for the full raid roster. */
    private static EntityType<? extends Mob> limboType(Raider r) {
        if (r instanceof Ravager) return ModEntities.LIMBO_RAVAGER.get();
        if (r instanceof Evoker) return ModEntities.LIMBO_EVOKER.get();
        if (r instanceof Vindicator) return ModEntities.LIMBO_VINDICATOR.get();
        if (r instanceof Witch) return ModEntities.LIMBO_WITCH.get();
        if (r instanceof Pillager) return ModEntities.LIMBO_PILLAGER.get();
        return null;
    }

    private static void spawnPhantoms(ServerLevel level, BlockPos center, int n) {
        for (int i = 0; i < n; i++) {
            LimboPhantomEntity ph = ModEntities.LIMBO_PHANTOM.get().create(level);
            if (ph == null) continue;
            double a = level.getRandom().nextDouble() * Math.PI * 2.0;
            int r = 10 + level.getRandom().nextInt(12);
            int x = center.getX() + (int) Math.round(Math.cos(a) * r);
            int z = center.getZ() + (int) Math.round(Math.sin(a) * r);
            BlockPos surf = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, center.getY(), z));
            ph.moveTo(x + 0.5, surf.getY() + 8 + level.getRandom().nextInt(8), z + 0.5,
                    level.getRandom().nextFloat() * 360.0F, 0.0F);
            ph.finalizeSpawn(level, level.getCurrentDifficultyAt(ph.blockPosition()), MobSpawnType.EVENT, null, null);
            level.addFreshEntity(ph);
        }
    }

    /** The final wave always brings a Limbo Evoker, joined to the raid (so it counts toward the bar and must be
     *  beaten to win). Spawned on the surface just outside the village centre. */
    private static void spawnFinalEvoker(ServerLevel level, Raid raid, BlockPos center, int wave) {
        LimboEvokerEntity ev = ModEntities.LIMBO_EVOKER.get().create(level);
        if (ev == null) return;
        double a = level.getRandom().nextDouble() * Math.PI * 2.0;
        int r = 8 + level.getRandom().nextInt(8);
        int x = center.getX() + (int) Math.round(Math.cos(a) * r);
        int z = center.getZ() + (int) Math.round(Math.sin(a) * r);
        BlockPos surf = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, center.getY(), z));
        raid.joinRaid(wave, ev, surf, false); // positions + finalizes + buffs + adds it to the level as a member
    }

    /** The nearest village center (a VILLAGE POI, else the nearest villager) within {@link #VILLAGE_SCAN}. */
    private static BlockPos findVillageCenter(ServerLevel level, BlockPos pos) {
        Optional<BlockPos> poi = level.getPoiManager()
                .getInRange(h -> h.is(PoiTypeTags.VILLAGE), pos, VILLAGE_SCAN, PoiManager.Occupancy.ANY)
                .map(r -> r.getPos().immutable())
                .min(Comparator.comparingDouble(b -> b.distSqr(pos)));
        if (poi.isPresent()) return poi.get();
        return level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(48.0), Villager::isAlive).stream()
                .min(Comparator.comparingDouble(v -> v.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                .map(Entity::blockPosition)
                .orElse(null);
    }

    private static boolean raidActiveNear(BlockPos center) {
        for (Map.Entry<Raid, ServerLevel> e : limboRaids.entrySet()) {
            if (e.getKey().isActive() && e.getKey().getCenter().distSqr(center) <= 96.0 * 96.0) return true;
        }
        return false;
    }
}
