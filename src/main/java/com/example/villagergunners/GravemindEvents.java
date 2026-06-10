package com.example.villagergunners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
        manageDefenders(server, data); // level-2 gate turrets
        handleSunsetIncome(server, data);
        syncVisuals(server, data);
        CheapScaffoldManager.tick(server); // expire timed-out cheap scaffolds
    }

    /**
     * At village level 2+, once per gravemind, drop a wall-defender turret inside a 3x3 protecting-glass
     * shell above each of the wall's gates (if a wall plan still exists). The shell's panes face outward so
     * the defender shoots out while projectiles from outside are stopped.
     */
    private static void manageDefenders(MinecraftServer server, GravemindData data) {
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            if (data.areDefendersBuilt(gp)) continue;
            if (data.econ(gp).getLevel() < 2) continue;       // only from level 2
            WallPlan plan = data.getWallPlan(gp);
            if (plan == null) continue;                       // no wall -> nothing to defend
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            boolean any = false;
            for (BlockPos gate : plan.gates()) {
                if (buildDefenderShell(level, gate)) any = true;
            }
            if (any) {
                data.markDefendersBuilt(gp);
                broadcast(server, "§bThe Village Gravemind raises wall defenders above the gates.");
            }
        }
    }

    /** Build the 3x3 protecting-glass cube (panes facing outward) with a wall defender at its centre, a few
     *  blocks above {@code gate}. Returns true if it was built (the area was loaded). */
    private static boolean buildDefenderShell(ServerLevel level, BlockPos gate) {
        BlockPos c = gate.above(6); // sit the cube just above the wall top
        if (!level.isLoaded(c)) return false;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = c.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) continue;
                    if (dx == 0 && dy == 0 && dz == 0) {
                        level.setBlock(pos, ModBlocks.WALL_DEFENDER.get().defaultBlockState(), 3);
                    } else if (level.getBlockState(pos).canBeReplaced()) { // don't destroy existing builds
                        Direction f = dx != 0 ? (dx > 0 ? Direction.EAST : Direction.WEST)
                                : dz != 0 ? (dz > 0 ? Direction.SOUTH : Direction.NORTH)
                                : (dy > 0 ? Direction.UP : Direction.DOWN);
                        level.setBlock(pos, ModBlocks.PROTECTING_GLASS.get().defaultBlockState()
                                .setValue(ProtectingGlassBlock.FACING, f), 3);
                    }
                }
            }
        }
        return true;
    }

    private static final int BED_SCAN_RADIUS = 64; // collect beds this far from a gravemind for the wall plan

    private static final int COBBLE_SCAN_RADIUS = 80; // survey village material this far (horizontally) for the wall
    private static final int COBBLE_Y_BAND = 10;      // ... within +/- this many blocks of the gravemind's height
    private static final double ENSURE_GOAL_RADIUS = 96.0; // give villagers within this the build goal
    // The man-made "village material" the wall envelops: cobblestone, plus any planks or fences (house walls,
    // floors, fences) and farmland (village crop fields). All are ~never natural at the surface, so together
    // they track the built village well -- including its farms, which we want enclosed inside the wall.
    private static final Predicate<BlockState> IS_VILLAGE_MATERIAL = s ->
            s.is(Blocks.COBBLESTONE) || s.is(BlockTags.PLANKS) || s.is(BlockTags.FENCES)
            || s.is(Blocks.FARMLAND);

    // Back-off: graveminds that survey but yield no wall (no cobble / too large) aren't retried for a while,
    // so a no-plan village can't re-run the survey every 10s. Cleared once a plan forms or the gravemind is gone.
    private static final int SURVEY_BACKOFF_TICKS = 1200; // ~60s before retrying a village that produced no wall
    private static final Map<GlobalPos, Integer> surveyBackoff = new HashMap<>();

    /**
     * For each gravemind: compute its defensive wall plan once (from the surrounding village footprint),
     * make sure nearby villagers carry the build goal, and each pass re-check whether the wall has been
     * completed (by the builders) and, when it has, remove the temporary climb scaffolds and announce it.
     */
    private static void driveWalls(MinecraftServer server, GravemindData data) {
        boolean trySurvey = server.getTickCount() % 200 == 0; // the cobblestone survey is heavy; throttle it
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;

            WallPlan plan = data.getWallPlan(gp);
            if (plan == null) {
                if (!trySurvey) continue;
                Integer until = surveyBackoff.get(gp);
                if (until != null && server.getTickCount() < until) continue; // recently failed -> backed off
                WallPlan computed = WallPlanner.plan(level, gp, collectVillagePoints(level, gp.pos()));
                if (computed != null) {
                    computed.setStartTick(level.getGameTime()); // t0 for the per-layer builder stagger
                    data.putWallPlan(computed);
                    surveyBackoff.remove(gp);
                    broadcast(server, "§7The Village Gravemind marks out a defensive wall §8("
                            + computed.blocks().size() + " blocks)§7 — 6 builders, to work.");
                } else {
                    surveyBackoff.put(gp, server.getTickCount() + SURVEY_BACKOFF_TICKS);
                }
                continue;
            }
            if (!plan.isComplete()) {
                long now = level.getGameTime();
                // Scan out to the wall's own extent (not a fixed radius) so far-side builders are managed too.
                double radius = Math.max(ENSURE_GOAL_RADIUS, Math.sqrt(plan.joinRadiusSqr()));
                List<Villager> nearby = level.getEntitiesOfClass(Villager.class,
                        new AABB(gp.pos()).inflate(radius), v -> v.isAlive());
                // Every nearby villager carries the (dormant) build goal; only the builders below run it.
                for (Villager v : nearby) {
                    GunnerEvents.ensureWallBuildGoal(v);
                    // Keep-awake backstop: a builder must never get stuck asleep while its wall is unfinished
                    // (the goal only wakes itself while it is actively running).
                    if (BuilderRole.isBuilder(v) && v.isSleeping()) v.stopSleeping();
                }
                manageBuilders(level, gp.pos(), nearby, now); // keep BUILDER_COUNT builders staffed
                if (plan.recheckCompletion(level)) {
                    cleanupScaffolds(level, plan);
                    for (Villager v : nearby) BuilderRole.demote(v); // wall finished -> drop the builder tags
                    com.example.villagergunners.wall.WallBuild.clearPlan(gp); // free the reservation map
                    data.setDirty();
                    broadcast(server, "§aThe village wall is complete.");
                }
            }
        }
    }

    /**
     * Keep exactly one builder per wall course (0..{@link BuilderRole#BUILDER_COUNT}-1) among the villagers
     * nearby: demote duplicate-course builders, then fill any uncovered course from a free villager (that
     * isn't on the step-down cool-down). Spawn dedicated builders only to reach a minimum body count -- never
     * beyond -- so a village that's short on villagers gets staffed without the self-demotion cycle ever
     * spawning an ever-growing crowd. A course with no eligible villager is simply left uncovered this pass.
     */
    private static void manageBuilders(ServerLevel level, BlockPos gravemind, List<Villager> nearby, long now) {
        boolean[] covered = new boolean[BuilderRole.BUILDER_COUNT];
        for (Villager v : nearby) {
            if (!BuilderRole.isBuilder(v)) continue;
            int c = BuilderRole.course(v);
            if (c < 0 || c >= covered.length || covered[c]) {
                BuilderRole.demote(v); // stray / duplicate course -> free it
            } else {
                covered[c] = true;
            }
        }
        int population = nearby.size(); // cap spawning so churn can't grow the crowd past BUILDER_COUNT bodies
        java.util.Iterator<Villager> free = nearby.stream().filter(BuilderRole::eligible).iterator();
        for (int c = 0; c < covered.length; c++) {
            if (covered[c]) continue;
            Villager v;
            if (free.hasNext()) v = free.next();
            else if (population < BuilderRole.BUILDER_COUNT) { v = spawnBuilder(level, gravemind); if (v != null) population++; }
            else v = null;
            if (v == null) continue;
            BuilderRole.promote(v, c);
            GunnerEvents.ensureWallBuildGoal(v); // make sure the just-promoted/spawned builder can build
        }
    }

    /** Spawn an (armed-on-join) villager near the gravemind to serve as a wall builder, or null if no spot. */
    private static Villager spawnBuilder(ServerLevel level, BlockPos gravemind) {
        BlockPos spot = builderSpawnSpot(level, gravemind);
        if (spot == null) return null;
        Villager v = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (v == null) return null;
        v.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        v.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.MOB_SUMMONED, null, null);
        v.setBaby(false);
        level.addFreshEntity(v); // onEntityJoin arms it + adds the build goal
        return v;
    }

    /** A standable surface spot near the gravemind (ring search outward), or null. */
    private static BlockPos builderSpawnSpot(ServerLevel level, BlockPos gm) {
        for (int r = 1; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // ring only
                    BlockPos surf = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gm.offset(dx, 0, dz));
                    if (Math.abs(surf.getY() - gm.getY()) > 6) continue;
                    if (level.getBlockState(surf).getCollisionShape(level, surf).isEmpty()
                            && level.getBlockState(surf.above()).getCollisionShape(level, surf.above()).isEmpty()
                            && level.getBlockState(surf.below()).isFaceSturdy(level, surf.below(), Direction.UP)) {
                        return surf;
                    }
                }
            }
        }
        return null;
    }

    /**
     * The 2D points that define the village extent for the wall. Primary heuristic: columns containing
     * cobblestone within +/-{@value #COBBLE_Y_BAND} of the gravemind's height (cobblestone is essentially
     * never natural at the surface, so it tracks the man-made village well). Falls back to beds (HOME POIs)
     * if too little cobblestone is found.
     */
    private static List<BlockPos> collectVillagePoints(ServerLevel level, BlockPos center) {
        List<BlockPos> pts = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int r = COBBLE_SCAN_RADIUS;
        int yLo = Math.max(level.getMinBuildHeight(), center.getY() - COBBLE_Y_BAND);
        int yHi = Math.min(level.getMaxBuildHeight() - 1, center.getY() + COBBLE_Y_BAND);
        int secLo = level.getSectionIndex(yLo), secHi = level.getSectionIndex(yHi);
        int xMin = center.getX() - r, xMax = center.getX() + r;
        int zMin = center.getZ() - r, zMax = center.getZ() + r;

        // Iterate chunk by chunk. The vast majority of the survey area is grass with no village material in
        // its palette, so we skip those whole chunks via a cheap palette pre-filter and only ever read blocks
        // in the few chunks that actually contain some -- full resolution, a fraction of the work.
        for (int cx = xMin >> 4; cx <= (xMax >> 4); cx++) {
            for (int cz = zMin >> 4; cz <= (zMax >> 4); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue; // unloaded -> skip (never force-load)
                boolean maybe = false;
                for (int idx = secLo; idx <= secHi && !maybe; idx++) {
                    LevelChunkSection sec = chunk.getSection(idx);
                    if (!sec.hasOnlyAir() && sec.maybeHas(IS_VILLAGE_MATERIAL)) maybe = true;
                }
                if (!maybe) continue;
                int bx0 = Math.max(xMin, cx << 4), bx1 = Math.min(xMax, (cx << 4) + 15);
                int bz0 = Math.max(zMin, cz << 4), bz1 = Math.min(zMax, (cz << 4) + 15);
                for (int x = bx0; x <= bx1; x++) {
                    for (int z = bz0; z <= bz1; z++) {
                        for (int y = yHi; y >= yLo; y--) {
                            if (IS_VILLAGE_MATERIAL.test(level.getBlockState(m.set(x, y, z)))) {
                                pts.add(new BlockPos(x, 0, z));
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (pts.size() >= WallPlanner.MIN_BEDS) return pts;
        return collectBeds(level, center); // sparse-cobblestone village -> fall back to beds
    }

    /** Forget a gravemind's survey back-off (called when it's removed, so a replacement re-surveys at once). */
    public static void clearSurveyBackoff(GlobalPos gravemind) {
        surveyBackoff.remove(gravemind);
    }

    /** Bed positions (HOME POIs) within {@value #BED_SCAN_RADIUS} blocks of the gravemind. */
    private static List<BlockPos> collectBeds(ServerLevel level, BlockPos center) {
        List<BlockPos> beds = new ArrayList<>();
        level.getPoiManager()
                .getInRange(h -> h.is(PoiTypes.HOME), center, BED_SCAN_RADIUS, PoiManager.Occupancy.ANY)
                .forEach(record -> beds.add(record.getPos().immutable()));
        return beds;
    }

    /** Remove any temporary cheap scaffolds the builders left while pillaring up to high courses. */
    private static void cleanupScaffolds(ServerLevel level, WallPlan plan) {
        for (BlockPos p : plan.scaffoldTemp()) {
            if (level.getBlockState(p).is(ModBlocks.CHEAP_SCAFFOLD.get())) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
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
        data.removeWallPlan(gp); // always recompute with the current logic, then paste
        WallPlan plan = WallPlanner.plan(level, gp, collectVillagePoints(level, gravemindPos));
        if (plan == null) return -1;
        data.putWallPlan(plan);
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
                // Collect the meeting-point positions FIRST. tryPlaceGravemind places the gravemind and beds
                // (HOME POIs), which mutate the POI manager -- doing that while the POI stream is still open
                // throws ConcurrentModificationException, so we drain the stream before touching the world.
                List<BlockPos> centers = poi.getInSquare(holder -> holder.is(PoiTypes.MEETING),
                                player.blockPosition(), VILLAGE_SCAN_RADIUS, PoiManager.Occupancy.ANY)
                        .map(record -> record.getPos().immutable())
                        .toList();
                for (BlockPos bell : centers) tryPlaceGravemind(level, data, bell);
            }
        }
    }

    private static void tryPlaceGravemind(ServerLevel level, GravemindData data, BlockPos bell) {
        GlobalPos key = GlobalPos.of(level.dimension(), bell.immutable());
        if (data.isServiced(key)) return;

        // Preferred: the block directly under the bell (the pedestal). A full solid gravemind still
        // supports the floor-standing bell, so the bell stays. Fall back to a nearby ground spot only
        // when that block is air/bedrock (e.g. a wall-mounted bell over a gap).
        BlockPos under = bell.below();
        BlockState underState = level.getBlockState(under);
        boolean replaceable = !underState.isAir()
                && !underState.is(ModBlocks.VILLAGE_GRAVEMIND.get())
                && underState.getDestroySpeed(level, under) >= 0; // not bedrock/unbreakable
        BlockPos spot = replaceable ? under : findGravemindSpot(level, bell);
        if (spot == null) return; // no good spot yet; retry next scan

        // One gravemind per area: skip if another already exists within 100 blocks (overlapping
        // villages / extra bells). Not marked serviced, so it self-heals if that gravemind is removed.
        if (hasGravemindWithin(level, data, spot, 100.0)) return;

        level.setBlock(spot, ModBlocks.VILLAGE_GRAVEMIND.get().defaultBlockState(), 3); // onPlace tracks it
        data.addServiced(key);
        broadcast(level.getServer(), "§5A Village Gravemind has risen at the heart of a village §8(near "
                + spot.getX() + ", " + spot.getY() + ", " + spot.getZ() + ")§5.");

        // Furnish the village: drop a bed into every bedless cobblestone house near the gravemind.
        int beds = HouseBedFiller.fill(level, spot);
        if (beds > 0) {
            broadcast(level.getServer(), "§7The Village Gravemind furnishes §a" + beds + "§7 houses with beds.");
        }
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

    /** True if a gravemind already exists within {@code radius} blocks of {@code pos} in this level. */
    private static boolean hasGravemindWithin(ServerLevel level, GravemindData data, BlockPos pos, double radius) {
        double r2 = radius * radius;
        for (GlobalPos gp : data.blocks()) {
            if (gp.dimension().equals(level.dimension()) && gp.pos().distSqr(pos) <= r2) return true;
        }
        return false;
    }

    /** Once per day at sunset, INDEPENDENTLY PER VILLAGE: reinforcements + 1 emerald per nearby defender. */
    private static void handleSunsetIncome(MinecraftServer server, GravemindData data) {
        long dayTime = server.overworld().getDayTime();
        long day = Math.floorDiv(dayTime, 24000L);
        long timeOfDay = Math.floorMod(dayTime, 24000L);
        if (timeOfDay < SUNSET_TIME) return;

        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            GravemindData.Econ econ = data.econ(gp);
            if (econ.getLastIncomeDay() == day) continue; // this village already received income today
            econ.setLastIncomeDay(day);

            reinforceVillage(level, gp.pos()); // reinforcements (independent of emerald income)

            int income = countDefendersNear(level, gp.pos());
            if (income <= 0) continue;
            int gained = econ.addEmeralds(income);
            broadcast(server, "§7A Village Gravemind drew §a" + income + "§7 emeralds from its defenders at sunset.");
            if (gained > 0) {
                broadcast(server, "§6A village evolved to level §e" + econ.getLevel()
                        + (econ.isMaxLevel() ? " §6(max)!" : "§6!"));
            }
        }
    }

    /**
     * A village raises 1 guard + 2 villagers at sunset, UNLESS the villagers within {@value #INCOME_RADIUS}
     * blocks already exceed {@value #VILLAGER_BED_RATIO_CAP}x the bed count (so it can't endlessly
     * overpopulate). The spawned mobs auto-arm through the normal entity-join handler.
     */
    private static void reinforceVillage(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(INCOME_RADIUS);
        int villagers = level.getEntitiesOfClass(Villager.class, box, Villager::isAlive).size();
        long beds = level.getPoiManager().getCountInRange(
                h -> h.is(PoiTypes.HOME), pos, INCOME_RADIUS, PoiManager.Occupancy.ANY);
        if (beds <= 0 || villagers > VILLAGER_BED_RATIO_CAP * beds) return;

        spawnDefender(level, pos, EntityType.VILLAGER);
        spawnDefender(level, pos, EntityType.VILLAGER);
        EntityType<?> guardType = ForgeRegistries.ENTITY_TYPES.getValue(GUARD_ID);
        if (guardType != null) spawnDefender(level, pos, guardType);
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

    /** Alive villagers/guards within {@value #INCOME_RADIUS} blocks of one gravemind. */
    private static int countDefendersNear(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(INCOME_RADIUS);
        return level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && isDefender(x)).size();
    }

    /** Keep each loaded gravemind showing its OWN village's level; prune positions whose block is gone. */
    private static void syncVisuals(MinecraftServer server, GravemindData data) {
        List<GlobalPos> stale = new ArrayList<>();
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            BlockState state = level.getBlockState(gp.pos());
            if (state.getBlock() instanceof VillageGravemindBlock) {
                int target = VillageGravemindBlock.chargesForLevel(data.econ(gp).getLevel());
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
