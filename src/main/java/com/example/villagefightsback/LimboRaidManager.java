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
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
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
    private static final double VILLAGE_SCAN = 64.0;  // a village center this close counts as "in a village"
    private static final double NEAR = 80.0;          // raid bar / messages reach players this close to the center

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

    /** Apply the omen with NO particles (it is a silent flag) and a red warning. */
    private static void applyOmen(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(ModEffects.VERY_BAD_OMEN.get(), OMEN_DURATION, 0,
                false, false, true)); // ambient, NO particles, show icon
        p.sendSystemMessage(Component.literal(
                "§4Something in the Limbo has marked you. You should leave — now."));
    }

    /** The nearest village center (a MEETING point, else a nearby villager) within {@link #VILLAGE_SCAN}. */
    private static BlockPos findVillageCenter(ServerLevel level, BlockPos pos) {
        Optional<BlockPos> meeting = level.getPoiManager()
                .getInRange(h -> h.is(PoiTypes.MEETING), pos, (int) VILLAGE_SCAN, PoiManager.Occupancy.ANY)
                .map(r -> r.getPos().immutable())
                .min(Comparator.comparingDouble(b -> b.distSqr(pos)));
        if (meeting.isPresent()) return meeting.get();
        List<Villager> v = level.getEntitiesOfClass(Villager.class,
                new AABB(pos).inflate(48.0), x -> x.isAlive());
        return v.isEmpty() ? null : v.get(0).blockPosition();
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
        private static final int PRE = 50;       // ticks of warning before wave 1 (the two red messages first)
        private static final int WAVE_GAP = 80;  // pause between cleared waves
        private static final int MAX_AGE = 6000; // 5 min safety timeout

        private final ServerLevel level;
        private final BlockPos center;
        private final ServerBossEvent bar;
        private final List<UUID> alive = new ArrayList<>();
        private int age, wave = -1, waveCount, nextWaveDelay;
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
            // The two warnings, in red, just before the bar starts to fill.
            if (age == 1) announce("§4You should have left when I told you to.");
            if (age == 25) announce("§4Incoming...");
            if (!started) {
                if (age >= PRE) { started = true; spawnWave(); }
                return;
            }
            refreshBarPlayers();
            alive.removeIf(uuid -> { Entity e = level.getEntity(uuid); return e == null || !e.isAlive(); });
            if (alive.isEmpty()) {
                if (wave >= TOTAL_WAVES - 1) { finish(true); return; }
                if (nextWaveDelay > 0) nextWaveDelay--; else spawnWave();
            } else {
                bar.setProgress(waveCount == 0 ? 0.0F : (float) alive.size() / waveCount);
            }
            if (age > MAX_AGE) finish(false);
        }

        private void spawnWave() {
            wave++;
            bar.setName(Component.literal("§4Limbo Raid §7— Wave " + (wave + 1) + "/" + TOTAL_WAVES));
            alive.clear();
            switch (wave) {
                case 0 -> { ground(ModEntities.LIMBO_PILLAGER.get(), 2); ground(ModEntities.LIMBO_VINDICATOR.get(), 1); phantoms(2); }
                case 1 -> { ground(ModEntities.LIMBO_PILLAGER.get(), 2); ground(ModEntities.LIMBO_VINDICATOR.get(), 2);
                            ground(ModEntities.LIMBO_EVOKER.get(), 1); phantoms(2); }
                default -> { ground(ModEntities.LIMBO_RAVAGER.get(), 1); ground(ModEntities.LIMBO_VINDICATOR.get(), 2);
                             ground(ModEntities.LIMBO_EVOKER.get(), 1); phantoms(3); }
            }
            waveCount = alive.size();
            nextWaveDelay = WAVE_GAP;
            bar.setProgress(1.0F);
        }

        private void ground(EntityType<? extends Mob> type, int n) {
            for (int i = 0; i < n; i++) spawn(type, groundSpot(false));
        }

        private void phantoms(int n) {
            for (int i = 0; i < n; i++) spawn(ModEntities.LIMBO_PHANTOM.get(), groundSpot(true));
        }

        private void spawn(EntityType<? extends Mob> type, BlockPos pos) {
            Mob m = type.create(level);
            if (m == null) return;
            m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            m.setPersistenceRequired();
            if (m instanceof Raider r) r.setCanJoinRaid(false);   // keep them out of the vanilla raid system
            for (EquipmentSlot s : EquipmentSlot.values()) m.setDropChance(s, 0.0F); // no vanilla gear drops
            level.addFreshEntity(m);
            alive.add(m.getUUID());
        }

        /** A surface spot 10-24 blocks from the center; flying mobs are lifted well above it. */
        private BlockPos groundSpot(boolean flying) {
            double a = level.getRandom().nextDouble() * Math.PI * 2.0;
            int r = 10 + level.getRandom().nextInt(15);
            int x = center.getX() + (int) Math.round(Math.cos(a) * r);
            int z = center.getZ() + (int) Math.round(Math.sin(a) * r);
            BlockPos surf = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, center.getY(), z));
            return flying ? surf.above(10 + level.getRandom().nextInt(10)) : surf;
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
            if (victory) announce("§aThe Limbo raid is over. The village holds.");
            bar.removeAllPlayers();
            finished = true;
        }
    }
}
