package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * The Limbo raid: spend more than 30s straight in the Limbo and you earn the {@link VeryBadOmenEffect Very Bad
 * Omen}; carry it back into a village and a Limbo raid erupts -- a vanilla-style wave raid where every illager
 * is its ghostly 3x {@link LimboRaider} version and Limbo Phantoms swoop in alongside them.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboRaidManager {
    private LimboRaidManager() {}

    private static final int OMEN_TICKS = 600;        // 30s straight in the Limbo earns the omen
    private static final int OMEN_DURATION = 12000;   // the omen lasts 10 min (time to get home)
    private static final int VILLAGE_SCAN = 64;       // a village center this close counts as "in a village"
    private static final double NEAR = 80.0;          // raid bar / messages reach players this close to center
    /** Tag stamped on every raid mob so a leftover from a previous session can be swept on reload. */
    static final String TAG = "tvfb_limbo_raider";

    /** UUID -> consecutive ticks the player has been in the Limbo (transient). */
    private static final Map<UUID, Integer> limboTime = new HashMap<>();
    private static final List<LimboRaid> raids = new ArrayList<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long _p = ModProfiler.now();
        raids.removeIf(r -> { r.tick(); return r.finished; }); // raids tick every tick (smooth bar + timing)
        if (server.getTickCount() % 20 == 0) scanPlayers(server); // omen + return check once a second
        ModProfiler.add("limbo.raid", _p);
    }

    /** A raid mob rejoining the world without an active raid tracking it (a previous-session leftover) is swept. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity e = event.getEntity();
        if (e.getTags().contains(TAG) && !isTracked(e.getUUID())) e.discard();
    }

    /** Drop the in-Limbo timer on logout (no map leak; the 30s window restarts on relog). */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        limboTime.remove(event.getEntity().getUUID());
    }

    private static boolean isTracked(UUID id) {
        for (LimboRaid r : raids) if (r.alive.contains(id)) return true;
        return false;
    }

    /** Track time-in-Limbo (-> omen) and watch for omen-carriers returning to a village (-> raid). */
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
                    if (center != null && !raidActiveNear(level, center)) {
                        p.removeEffect(ModEffects.VERY_BAD_OMEN.get());
                        raids.add(new LimboRaid(level, center));
                    }
                }
            }
        }
    }

    /** Apply the omen as a silent flag (visible=false -> NO particles; icon shown) + a red warning. */
    private static void applyOmen(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(ModEffects.VERY_BAD_OMEN.get(), OMEN_DURATION, 0,
                false, false, true)); // not ambient, NO particles, show icon
        p.sendSystemMessage(Component.literal(
                "§4Something in the Limbo has marked you. You should leave -- now."));
    }

    /** The nearest village center (a MEETING point, else the nearest villager) within {@link #VILLAGE_SCAN}. */
    private static BlockPos findVillageCenter(ServerLevel level, BlockPos pos) {
        Optional<BlockPos> meeting = level.getPoiManager()
                .getInRange(h -> h.is(PoiTypes.MEETING), pos, VILLAGE_SCAN, PoiManager.Occupancy.ANY)
                .map(r -> r.getPos().immutable())
                .min(Comparator.comparingDouble(b -> b.distSqr(pos)));
        if (meeting.isPresent()) return meeting.get();
        return level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(48.0), x -> x.isAlive()).stream()
                .min(Comparator.comparingDouble(v -> v.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                .map(Entity::blockPosition)
                .orElse(null);
    }

    private static boolean raidActiveNear(ServerLevel level, BlockPos center) {
        for (LimboRaid r : raids) {
            if (r.level == level && r.center.distSqr(center) <= 100.0 * 100.0) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------
    /** One active Limbo raid: a few waves of 3x raiders + phantoms around a village, with a boss bar. */
    private static final class LimboRaid {
        private static final int TOTAL_WAVES = 3;
        private static final int PRE = 50;          // ticks of warning before wave 1 (the two red messages first)
        private static final int WAVE_GAP = 80;     // pause between cleared waves
        private static final int MAX_AGE = 9600;    // 8 min hard safety timeout
        private static final int IDLE_TIMEOUT = 1200; // abandon if no player is near for 1 min

        private final ServerLevel level;
        private final BlockPos center;
        private final ServerBossEvent bar;
        private final List<UUID> alive = new ArrayList<>();
        private final Map<UUID, BlockPos> lastPos = new HashMap<>(); // to tell "unloaded" from "dead"
        private int age, wave = -1, waveCount, nextWaveDelay, idle;
        private boolean started, finished;

        LimboRaid(ServerLevel level, BlockPos center) {
            this.level = level;
            this.center = center;
            this.bar = new ServerBossEvent(Component.literal("§4Limbo Raid"),
                    BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
            this.bar.setProgress(0.0F);
        }

        void tick() {
            age++;
            if (age == 1) announce("§4You should have left when I told you to.");
            if (age == 25) announce("§4Incoming...");
            if (!started) {
                if (age >= PRE) { started = true; spawnWave(); }
                return;
            }
            refreshBarPlayers();
            if (!anyPlayerNear()) { // nobody home: pause progression (and abandon if it stays empty too long)
                if (++idle > IDLE_TIMEOUT) finish(false);
                return;
            }
            idle = 0;
            pruneDead();
            if (alive.isEmpty()) {
                bar.setProgress(0.0F);
                if (wave >= TOTAL_WAVES - 1) { finish(true); return; }
                if (nextWaveDelay > 0) nextWaveDelay--; else spawnWave();
            } else {
                bar.setProgress(waveCount == 0 ? 0.0F : (float) alive.size() / waveCount);
            }
            if (age > MAX_AGE) finish(false);
        }

        /** Remove dead raiders -- but NOT ones merely sitting in an unloaded chunk (else a wave "completes" the
         *  moment players walk off, leaving orphaned 3x raiders and a false victory). */
        private void pruneDead() {
            alive.removeIf(uuid -> {
                Entity e = level.getEntity(uuid);
                if (e != null && e.isAlive()) { lastPos.put(uuid, e.blockPosition()); return false; }
                BlockPos lp = lastPos.get(uuid);
                if (lp != null && !level.hasChunkAt(lp)) return false; // unloaded, not dead -> keep
                lastPos.remove(uuid);
                return true;
            });
        }

        private void spawnWave() {
            wave++;
            bar.setName(Component.literal("§4Limbo Raid §7-- Wave " + (wave + 1) + "/" + TOTAL_WAVES));
            alive.clear();
            lastPos.clear();
            switch (wave) {
                case 0 -> { ground(ModEntities.LIMBO_PILLAGER.get(), 2); ground(ModEntities.LIMBO_VINDICATOR.get(), 1); phantoms(2); }
                case 1 -> { ground(ModEntities.LIMBO_PILLAGER.get(), 2); ground(ModEntities.LIMBO_VINDICATOR.get(), 2);
                            ground(ModEntities.LIMBO_EVOKER.get(), 1); phantoms(2); }
                default -> { ground(ModEntities.LIMBO_RAVAGER.get(), 1); ground(ModEntities.LIMBO_VINDICATOR.get(), 2);
                             ground(ModEntities.LIMBO_EVOKER.get(), 1); phantoms(3); }
            }
            waveCount = alive.size();
            nextWaveDelay = WAVE_GAP;
            bar.setProgress(waveCount == 0 ? 0.0F : 1.0F);
        }

        private void ground(EntityType<? extends Mob> type, int n) {
            for (int i = 0; i < n; i++) spawn(type, false);
        }

        private void phantoms(int n) {
            for (int i = 0; i < n; i++) spawn(ModEntities.LIMBO_PHANTOM.get(), true);
        }

        private void spawn(EntityType<? extends Mob> type, boolean flying) {
            Mob m = type.create(level);
            if (m == null) return;
            if (m instanceof Raider r) r.setCanJoinRaid(false); // keep out of the vanilla raid system (before finalize)
            m.setPersistenceRequired();                          // don't despawn mid-fight when a player steps away
            // Find a spot whose (3x) box is clear, so the giant raiders don't suffocate among the buildings.
            BlockPos pos = groundSpot(flying);
            for (int a = 0; a < 8; a++) {
                m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
                if (level.noCollision(m)) break;
                pos = groundSpot(flying);
            }
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            for (EquipmentSlot s : EquipmentSlot.values()) m.setDropChance(s, 0.0F); // no vanilla gear drops
            level.addFreshEntity(m);     // join fires here -- tag not yet set, so the sweeper leaves it
            alive.add(m.getUUID());
            lastPos.put(m.getUUID(), m.blockPosition());
            m.addTag(TAG);               // now tagged, so a reload without this raid sweeps it
        }

        /** A surface spot 14-28 blocks from the center (out past the buildings); flying mobs are lifted above it. */
        private BlockPos groundSpot(boolean flying) {
            double a = level.getRandom().nextDouble() * Math.PI * 2.0;
            int r = 14 + level.getRandom().nextInt(15);
            int x = center.getX() + (int) Math.round(Math.cos(a) * r);
            int z = center.getZ() + (int) Math.round(Math.sin(a) * r);
            BlockPos surf = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, center.getY(), z));
            return flying ? surf.above(10 + level.getRandom().nextInt(10)) : surf;
        }

        private boolean anyPlayerNear() {
            for (ServerPlayer p : level.players()) if (p.blockPosition().closerThan(center, NEAR)) return true;
            return false;
        }

        private void refreshBarPlayers() {
            for (ServerPlayer p : level.players()) {
                if (p.blockPosition().closerThan(center, NEAR)) bar.addPlayer(p); else bar.removePlayer(p);
            }
        }

        private void announce(String msg) {
            Component c = Component.literal(msg);
            for (ServerPlayer p : level.players()) {
                if (p.blockPosition().closerThan(center, NEAR)) p.sendSystemMessage(c);
            }
        }

        private void finish(boolean victory) {
            if (victory) {
                announce("§aThe Limbo raid is over. The village holds.");
            } else {
                announce("§7The Limbo presence recedes...");
                for (UUID id : alive) { // clean up any still-loaded raiders so a timed-out/abandoned raid leaves nothing
                    Entity e = level.getEntity(id);
                    if (e != null) e.discard();
                }
            }
            bar.removeAllPlayers();
            finished = true;
        }
    }
}
