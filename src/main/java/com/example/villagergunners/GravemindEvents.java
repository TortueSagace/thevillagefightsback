package com.example.villagergunners;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.example.villagergunners.wall.WallBlock;
import com.example.villagergunners.wall.WallPlan;
import com.example.villagergunners.wall.WallPlanner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Drives the Village Gravemind: spawns one at the centre of each loaded village, grants sunset emerald
 * income, and keeps every gravemind's visual level in sync with the global {@link GravemindData}.
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GravemindEvents {
    private GravemindEvents() {}

    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");
    private static final int INCOME_RADIUS = 100;     // count defenders within this of a gravemind
    private static final int VILLAGE_SCAN_RADIUS = 64; // look for village centres this far from players
    private static final int TICK_INTERVAL = 40;       // run all of this every 2 seconds
    private static final long SUNSET_TIME = 12000L;     // day-time at dusk
    private static final double VILLAGER_BED_RATIO_CAP = 1.5; // skip reinforcement above this villagers:beds

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % TICK_INTERVAL != 0) return;

        GravemindData data = GravemindData.get(server);
        spawnInVillages(server, data);
        driveWalls(server, data);
        handleSunsetIncome(server, data);
        syncVisuals(server, data);
    }

    private static final int BED_SCAN_RADIUS = 64; // collect beds this far from a gravemind for the wall plan

    /**
     * For each gravemind: compute its defensive wall plan once (from the surrounding bed cluster), then
     * each pass re-check whether the wall has been completed (by the builders) and, when it has, remove
     * the temporary climb scaffolds and announce it.
     */
    private static void driveWalls(MinecraftServer server, GravemindData data) {
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;

            WallPlan plan = data.getWallPlan(gp);
            if (plan == null) {
                WallPlan computed = WallPlanner.plan(level, gp, collectBeds(level, gp.pos()));
                if (computed != null) {
                    data.putWallPlan(computed);
                    broadcast(server, "§7The Village Gravemind marks out a defensive wall §8("
                            + computed.blocks().size() + " blocks)§7 — villagers, to work.");
                }
                continue;
            }
            if (!plan.isComplete() && plan.recheckCompletion(level)) {
                cleanupScaffolds(level, plan);
                data.setDirty();
                broadcast(server, "§aThe village wall is complete.");
            }
        }
    }

    /** Bed positions (HOME POIs) within {@value #BED_SCAN_RADIUS} blocks of the gravemind. */
    private static List<BlockPos> collectBeds(ServerLevel level, BlockPos center) {
        List<BlockPos> beds = new ArrayList<>();
        level.getPoiManager()
                .getInRange(h -> h.is(PoiTypes.HOME), center, BED_SCAN_RADIUS, PoiManager.Occupancy.ANY)
                .forEach(record -> beds.add(record.getPos().immutable()));
        return beds;
    }

    /** Remove any temporary scaffolding the builders left while pillaring up to high courses. */
    private static void cleanupScaffolds(ServerLevel level, WallPlan plan) {
        for (BlockPos p : plan.scaffoldTemp()) {
            if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.SCAFFOLDING)) {
                level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        plan.scaffoldTemp().clear();
    }

    /**
     * DEBUG/admin: instantly stamp a gravemind's entire computed wall into the world (bypassing the
     * builders), computing the plan first if needed. Returns the block count, or -1 if there aren't enough
     * beds yet. Triggered by right-clicking the gravemind with a stick.
     */
    public static int debugPasteWall(ServerLevel level, BlockPos gravemindPos) {
        GlobalPos gp = GlobalPos.of(level.dimension(), gravemindPos.immutable());
        GravemindData data = GravemindData.get(level.getServer());
        WallPlan plan = data.getWallPlan(gp);
        if (plan == null) {
            plan = WallPlanner.plan(level, gp, collectBeds(level, gravemindPos));
            if (plan == null) return -1;
            data.putWallPlan(plan);
        }
        int placed = 0;
        for (WallBlock wb : plan.blocks()) {
            level.setBlock(wb.pos(), wb.toState(), 3);
            placed++;
        }
        return placed;
    }

    /** Place a gravemind at the centre (meeting point / bell) of each loaded village, once. */
    private static void spawnInVillages(MinecraftServer server, GravemindData data) {
        for (ServerLevel level : server.getAllLevels()) {
            PoiManager poi = level.getPoiManager();
            for (ServerPlayer player : level.players()) {
                poi.getInSquare(holder -> holder.is(PoiTypes.MEETING), player.blockPosition(),
                                VILLAGE_SCAN_RADIUS, PoiManager.Occupancy.ANY)
                        .forEach(record -> tryPlaceGravemind(level, data, record.getPos()));
            }
        }
    }

    private static void tryPlaceGravemind(ServerLevel level, GravemindData data, BlockPos center) {
        GlobalPos key = GlobalPos.of(level.dimension(), center.immutable());
        if (data.isServiced(key)) return;
        // The exact meeting-point column is usually topped by the BELL (whose top isn't a solid face), so
        // we search the columns AROUND it for the nearest open ground spot near the bell's height.
        BlockPos spot = findGravemindSpot(level, center);
        if (spot == null) return; // no good spot yet; retry next scan
        level.setBlock(spot, ModBlocks.VILLAGE_GRAVEMIND.get().defaultBlockState(), 3); // onPlace tracks it
        data.addServiced(key);
        broadcast(level.getServer(), "§5A Village Gravemind has risen at the heart of a village §8(near "
                + spot.getX() + ", " + spot.getY() + ", " + spot.getZ() + ")§5.");
    }

    /** Nearest open ground spot within 3 blocks of the village centre, close to the centre's height. */
    private static BlockPos findGravemindSpot(ServerLevel level, BlockPos center) {
        BlockPos best = null;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(dx, 0, dz));
                if (Math.abs(surface.getY() - center.getY()) > 5) continue;                   // skip roofs / cliffs
                if (!level.getBlockState(surface).canBeReplaced()) continue;                   // open spot
                if (!level.getBlockState(surface.below()).isFaceSturdy(level, surface.below(), Direction.UP)) continue;
                if (best == null || center.distSqr(surface) < center.distSqr(best)) best = surface;
            }
        }
        return best;
    }

    /** Once per day at sunset: +1 emerald per villager/guard alive within {@value #INCOME_RADIUS} blocks. */
    private static void handleSunsetIncome(MinecraftServer server, GravemindData data) {
        long dayTime = server.overworld().getDayTime();
        long day = Math.floorDiv(dayTime, 24000L);
        long timeOfDay = Math.floorMod(dayTime, 24000L);
        if (timeOfDay < SUNSET_TIME || day == data.getLastIncomeDay()) return;

        data.setLastIncomeDay(day);

        // Reinforcements first (independent of emerald income).
        spawnReinforcements(server, data);

        int income = countDefendersNearGraveminds(server, data);
        if (income > 0) {
            int gained = data.addEmeralds(income);
            broadcast(server, "§7The Village Gravemind drew §a" + income + "§7 emeralds from its defenders at sunset.");
            if (gained > 0) {
                broadcast(server, "§6The village evolved to level §e" + data.getLevel()
                        + (data.isMaxLevel() ? " §6(max)!" : "§6!"));
            }
        }
    }

    /**
     * Each loaded gravemind raises 1 guard + 2 villagers at sunset, UNLESS the villagers within
     * {@value #INCOME_RADIUS} blocks already exceed {@value #VILLAGER_BED_RATIO_CAP}x the bed count
     * (so a village can't endlessly overpopulate). Beds are counted via the village HOME POI. The
     * spawned mobs auto-arm through the normal entity-join handler.
     */
    private static void spawnReinforcements(MinecraftServer server, GravemindData data) {
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            BlockPos pos = gp.pos();
            AABB box = new AABB(pos).inflate(INCOME_RADIUS);
            int villagers = level.getEntitiesOfClass(Villager.class, box, Villager::isAlive).size();
            long beds = level.getPoiManager().getCountInRange(
                    h -> h.is(PoiTypes.HOME), pos, INCOME_RADIUS, PoiManager.Occupancy.ANY);
            if (beds <= 0 || villagers > VILLAGER_BED_RATIO_CAP * beds) continue;

            spawnDefender(level, pos, EntityType.VILLAGER);
            spawnDefender(level, pos, EntityType.VILLAGER);
            EntityType<?> guardType = ForgeRegistries.ENTITY_TYPES.getValue(GUARD_ID);
            if (guardType != null) spawnDefender(level, pos, guardType);
        }
    }

    /** Spawn one entity of {@code type} on the surface near {@code center} (it auto-arms on join). */
    private static void spawnDefender(ServerLevel level, BlockPos center, EntityType<?> type) {
        BlockPos spot = findSpawnSpot(level, center);
        Entity e = type.spawn(level, spot, MobSpawnType.EVENT);
        if (e instanceof Villager v) v.setBaby(false);
    }

    private static BlockPos findSpawnSpot(ServerLevel level, BlockPos center) {
        for (int i = 0; i < 12; i++) {
            BlockPos col = center.offset(level.getRandom().nextInt(9) - 4, 0, level.getRandom().nextInt(9) - 4);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
            if (level.getBlockState(surface).getCollisionShape(level, surface).isEmpty()
                    && level.getBlockState(surface.above()).getCollisionShape(level, surface.above()).isEmpty()
                    && level.getBlockState(surface.below()).isFaceSturdy(level, surface.below(), Direction.UP)) {
                return surface;
            }
        }
        return center.above();
    }

    private static int countDefendersNearGraveminds(MinecraftServer server, GravemindData data) {
        Set<UUID> counted = new HashSet<>();
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            AABB box = new AABB(gp.pos()).inflate(INCOME_RADIUS);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                    x -> x.isAlive() && isDefender(x))) {
                counted.add(e.getUUID());
            }
        }
        return counted.size();
    }

    /** Keep loaded gravemind blocks showing the current level; prune positions whose block is gone. */
    private static void syncVisuals(MinecraftServer server, GravemindData data) {
        int target = VillageGravemindBlock.chargesForLevel(data.getLevel());
        List<GlobalPos> stale = new ArrayList<>();
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            BlockState state = level.getBlockState(gp.pos());
            if (state.getBlock() instanceof VillageGravemindBlock) {
                if (state.getValue(VillageGravemindBlock.CHARGES) != target) {
                    level.setBlock(gp.pos(), state.setValue(VillageGravemindBlock.CHARGES, target), 3);
                }
            } else {
                stale.add(gp);
            }
        }
        for (GlobalPos gp : stale) data.removeBlock(gp);
    }

    private static boolean isDefender(Entity e) {
        return e instanceof Villager || GUARD_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }
}
