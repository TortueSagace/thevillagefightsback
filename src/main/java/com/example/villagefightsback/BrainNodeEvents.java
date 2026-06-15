package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.example.villagefightsback.wall.WallBlock;
import com.example.villagefightsback.wall.WallPlan;
import com.example.villagefightsback.wall.WallPlanner;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Drives the Village Brain Node: spawns one at the centre of each loaded village, grants sunset emerald
 * income, and keeps every brain node's visual level in sync with the global {@link BrainNodeData}.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BrainNodeEvents {
    private BrainNodeEvents() {}

    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");
    private static final int INCOME_RADIUS = 100;     // count defenders within this of a brainNode
    private static final int VILLAGE_SCAN_RADIUS = 64; // look for village centres this far from players
    private static final int TICK_INTERVAL = 40;       // run all of this every 2 seconds
    private static final long SUNSET_TIME = 12000L;     // day-time at dusk
    private static final double VILLAGER_BED_RATIO_CAP = 1.5; // skip reinforcement above this villagers:beds

    // --- stage-4 air strikes ---
    private static final long STRIKE_INTERVAL = 12000L; // consider an air strike every 10 minutes
    private static final long ATTACK_WINDOW   = 12000L; // ...only if an ally was attacked within the past 10 minutes
    private static final Map<GlobalPos, Long> lastAllyHurt   = new HashMap<>(); // node -> gameTime an ally was last hit near it
    private static final Map<GlobalPos, Long> nextStrikeCheck = new HashMap<>(); // node -> next gameTime to consider a strike

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % TICK_INTERVAL != 0) return;

        long _p = ModProfiler.now();
        BrainNodeData data = BrainNodeData.get(server);
        spawnInVillages(server, data);
        driveWalls(server, data);
        applyImmunityAura(server, data); // shield nearby villagers/golems at ANY stage
        manageDefenders(server, data); // level-2 gate turrets
        manageExpansion(server, data); // level-3 settler wave (fires spontaneously on reaching level 3)
        driveOuterWalls(server, data); // level-3 OUTER obsidian wall around the expansion buildings
        manageDome(server, data);      // level-4 protective glass dome
        manageAirStrikes(server, data); // level-4 air strikes (every 10 min, if the village was attacked)
        BrainReflexes.tick(server, data); // emerald-funded immune reflexes (settlers, guards, golems, door regen)
        handleSunsetIncome(server, data);
        syncVisuals(server, data);
        checkStageBells(server, data);  // ring a bell whenever a brain node reaches a new stage
        pulseHeartbeats(server, data); // calm Warden heartbeat from every loaded brain node (~2s cadence)
        CheapScaffoldManager.tick(server); // expire timed-out cheap scaffolds
        ModProfiler.add("brain_node", _p);
    }

    private static final int HEARTBEAT_TICKS = 120; // ~6s between brain-node heartbeats (slow, ominous)

    /** Every loaded Village Brain Node emits the Warden's resting heartbeat -- a slow, ominous pulse every
     *  {@value #HEARTBEAT_TICKS} ticks. The UNLEASHED boss pulses it fast itself (see that entity). */
    private static void pulseHeartbeats(MinecraftServer server, BrainNodeData data) {
        if (server.getTickCount() % HEARTBEAT_TICKS != 0) return; // throttle below the 40-tick driver cadence
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            level.playSound(null, gp.pos(), net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT,
                    net.minecraft.sounds.SoundSource.BLOCKS, 2.0F, 1.0F);
        }
    }

    private static final int IMMUNITY_RADIUS = 10; // shield allies this close to a brainNode

    /** At ANY stage, a Village Brain Node shields the village core: every villager, guard and iron golem (incl.
     *  gigachad golems -- same entity class) within {@value #IMMUNITY_RADIUS} blocks gets our Immunity effect
     *  (takes no damage), refreshed each pass and with NO particles. */
    private static void applyImmunityAura(MinecraftServer server, BrainNodeData data) {
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atCenterOf(gp.pos());
            double r2 = (double) IMMUNITY_RADIUS * IMMUNITY_RADIUS;
            AABB box = new AABB(gp.pos()).inflate(IMMUNITY_RADIUS);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive()
                    && (x instanceof Villager
                        || x instanceof net.minecraft.world.entity.animal.IronGolem
                        || GUARD_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(x.getType()))))) {
                if (e.distanceToSqr(center) > r2) continue;
                // ambient=false, showParticles=false, showIcon=false -> a silent, particle-free shield.
                e.addEffect(new MobEffectInstance(ModEffects.IMMUNITY.get(), 200, 0, false, false, false));
                // Also clear any harmful effect already on it (VillageEvents blocks NEW ones from landing).
                List<net.minecraft.world.effect.MobEffect> bad = e.getActiveEffects().stream()
                        .filter(i -> i.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL)
                        .map(MobEffectInstance::getEffect).toList();
                bad.forEach(e::removeEffect);
            }
        }
    }

    private static final int HERO_TICKS = 60 * 60 * 20; // Hero of the Village for 1 hour

    /** Grant a brain node's founder Hero of the Village for an hour. */
    public static void rewardHero(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, HERO_TICKS, 0, false, false, true));
    }

    /** Remember which player placed a village bell, so the brain node that later rises there rewards them. */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getPlacedBlock().is(Blocks.BELL)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BrainNodeData.get(level.getServer()).recordBellPlacer(
                GlobalPos.of(level.dimension(), event.getPos().immutable()), player.getUUID());
    }

    /**
     * At village level 2+, once per brainNode, drop a wall-defender turret inside a 3x3 protecting-glass
     * shell above each of the wall's gates (if a wall plan still exists). The shell's panes face outward so
     * the defender shoots out while projectiles from outside are stopped.
     */
    private static void manageDefenders(MinecraftServer server, BrainNodeData data) {
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
            }
        }
    }

    /** Fire the stage-3 settler expansion the moment a village reaches level 3 (once per brainNode), rather
     *  than waiting for the next sunset. {@link ExpansionManager#maybeExpand} no-ops below level 3 / if done. */
    private static void manageExpansion(MinecraftServer server, BrainNodeData data) {
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            if (data.isExpanded(gp) || data.econ(gp).getLevel() < 3) continue;
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            ExpansionManager.maybeExpand(level, gp, data);
        }
    }

    /**
     * At village level 4, once per brainNode, raise the protective reinforced-glass dome over the village
     * (walls levelled to a common top plane, then a gentle full roof peaking over the brain node). Built
     * block-by-block via {@link HouseBuildManager} at the TNT-structure speed.
     */
    private static void manageDome(MinecraftServer server, BrainNodeData data) {
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            if (data.isDomeBuilt(gp)) continue;
            if (data.econ(gp).getLevel() < 4) continue;       // only at level 4
            WallPlan plan = data.getWallPlan(gp);
            if (plan == null) continue;                       // no wall -> nothing to anchor the dome to
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            DomeBuilder.build(level, plan, gp.pos());
            data.markDomeBuilt(gp);
        }
    }

    /** Cheap monitor: stamp the game-time an ally (villager/guard/golem) was last attacked, per nearby brain
     *  node, so a stage-4 air strike fires only when the village was actually under attack recently. */
    @SubscribeEvent
    public static void onAllyAttacked(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (!isVillageAlly(victim) || !(victim.level() instanceof ServerLevel sl)) return;
        BrainNodeData data = BrainNodeData.get(sl.getServer());
        long now = sl.getGameTime();
        BlockPos vp = victim.blockPosition();
        double r2 = (double) INCOME_RADIUS * INCOME_RADIUS;
        for (GlobalPos gp : data.blocks()) {
            if (gp.dimension().equals(sl.dimension()) && gp.pos().distSqr(vp) <= r2) {
                lastAllyHurt.merge(gp, now, Math::max);
            }
        }
    }

    /** Stage-4 air strikes: every {@value #STRIKE_INTERVAL} ticks, if a village ally was attacked within the
     *  past {@value #ATTACK_WINDOW} ticks, bomb the strongest valid enemy near the brain node. See
     *  {@link AirStrikeManager}. */
    private static void manageAirStrikes(MinecraftServer server, BrainNodeData data) {
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            if (data.econ(gp).getLevel() < 4) continue;                 // stage 4 only
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            long now = level.getGameTime();
            if (!nextStrikeCheck.containsKey(gp)) { nextStrikeCheck.put(gp, now + STRIKE_INTERVAL); continue; }
            if (now < nextStrikeCheck.get(gp)) continue;
            nextStrikeCheck.put(gp, now + STRIKE_INTERVAL);
            Long hurt = lastAllyHurt.get(gp);
            if (hurt == null || now - hurt > ATTACK_WINDOW) continue;    // village not attacked in the window
            LivingEntity target = pickAirStrikeTarget(level, data, gp.pos());
            if (target != null) AirStrikeManager.launch(level, target);
        }
    }

    /** The strongest (most-HP) hostile near the brain node whose strike column is clear; null if none qualify. */
    private static LivingEntity pickAirStrikeTarget(ServerLevel level, BrainNodeData data, BlockPos node) {
        AABB box = new AABB(node).inflate(INCOME_RADIUS);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box, e -> isAirStrikeEnemy(e, data));
        candidates.sort((a, b) -> Float.compare(b.getHealth(), a.getHealth())); // most HP first
        for (LivingEntity c : candidates) {
            if (isStrikeColumnClear(level, c)) return c;
        }
        return null;
    }

    /** Air-strike target: a hostile mob, or a player flagged as a village enemy. Never an ally or the boss. */
    private static boolean isAirStrikeEnemy(LivingEntity e, BrainNodeData data) {
        if (!e.isAlive()) return false;
        if (e instanceof net.minecraft.world.entity.player.Player p) {
            return !p.isCreative() && !p.isSpectator() && p.hasEffect(ModEffects.ENEMY_OF_THE_VILLAGE.get());
        }
        if (e instanceof UnleashedBrainNodeEntity) return false;        // the boss has its own arc
        if (isVillageAlly(e) || e instanceof EmeraldgerEntity || e instanceof WallDefenderEntity) return false;
        return e instanceof net.minecraft.world.entity.monster.Enemy;
    }

    /** True if the vertical column above the target (radius {@link AirStrikeManager#CLEAR_RADIUS}, from the
     *  target up to the sky) is free of village allies and of cobblestone/brick (village structures). Rockets
     *  fall top-to-bottom, so we check the whole column UP, not a sphere. */
    private static boolean isStrikeColumnClear(ServerLevel level, LivingEntity target) {
        int cx = (int) Math.floor(target.getX());
        int cz = (int) Math.floor(target.getZ());
        int baseY = (int) Math.floor(target.getY());
        int topY = level.getMaxBuildHeight() - 1;
        int r = AirStrikeManager.CLEAR_RADIUS;
        double r2 = (double) r * r;
        double ccx = cx + 0.5, ccz = cz + 0.5;
        AABB cyl = new AABB(cx - r, baseY, cz - r, cx + r + 1, topY, cz + r + 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, cyl, BrainNodeEvents::isVillageAlly)) {
            double dx = e.getX() - ccx, dz = e.getZ() - ccz;
            if (dx * dx + dz * dz <= r2) return false;
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r2) continue;
                for (int y = baseY; y <= topY; y++) {
                    BlockState s = level.getBlockState(m.set(cx + dx, y, cz + dz));
                    if (s.is(Blocks.COBBLESTONE) || s.is(Blocks.BRICKS)) return false;
                }
            }
        }
        return true;
    }

    /** Villager, guard, or iron golem -- the village allies the air strike protects (and never bombs). */
    private static boolean isVillageAlly(LivingEntity e) {
        return e instanceof Villager
                || e instanceof net.minecraft.world.entity.animal.IronGolem
                || GUARD_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
    }

    /** Build the 3x3 protecting-glass cube (panes facing outward, bulldozing whatever is there) with a wall
     *  defender creature at its centre, a few blocks above {@code gate}. Returns true if it was built. */
    private static boolean buildDefenderShell(ServerLevel level, BlockPos gate) {
        BlockPos c = gate.above(WallPlan.COURSES); // shell bottom row sits at the top (4th) wall layer
        if (!level.isLoaded(c)) return false;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = c.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) continue;
                    if (dx == 0 && dy == 0 && dz == 0) {
                        level.removeBlock(pos, false); // clear the centre for the defender creature
                    } else {
                        Direction f = dx != 0 ? (dx > 0 ? Direction.EAST : Direction.WEST)
                                : dz != 0 ? (dz > 0 ? Direction.SOUTH : Direction.NORTH)
                                : (dy > 0 ? Direction.UP : Direction.DOWN);
                        level.setBlock(pos, ModBlocks.PROTECTING_GLASS.get().defaultBlockState()
                                .setValue(ProtectingGlassBlock.FACING, f), 3); // bulldoze whatever is there
                    }
                }
            }
        }
        WallDefenderEntity defender = ModEntities.WALL_DEFENDER.get().create(level);
        if (defender != null) {
            defender.moveTo(c.getX() + 0.5, c.getY() + 0.1, c.getZ() + 0.5, 0.0F, 0.0F);
            level.addFreshEntity(defender);
        }
        return true;
    }

    private static final int BED_SCAN_RADIUS = 64; // collect beds this far from a brainNode for the wall plan

    private static final int COBBLE_SCAN_RADIUS = 80; // survey village material this far (horizontally) for the wall
    private static final int COBBLE_Y_BAND = 10;      // ... within +/- this many blocks of the brain node's height
    private static final double ENSURE_GOAL_RADIUS = 96.0; // give villagers within this the build goal
    // The man-made "village material" the wall envelops: cobblestone, plus any planks or fences (house walls,
    // floors, fences) and farmland (village crop fields). All are ~never natural at the surface, so together
    // they track the built village well -- including its farms, which we want enclosed inside the wall.
    private static final Predicate<BlockState> IS_VILLAGE_MATERIAL = s ->
            s.is(Blocks.COBBLESTONE) || s.is(BlockTags.PLANKS) || s.is(BlockTags.FENCES)
            || s.is(Blocks.FARMLAND)
            // brick structures (red + stone bricks) count as part of the village too
            || s.is(Blocks.BRICKS) || s.is(Blocks.BRICK_STAIRS) || s.is(Blocks.BRICK_SLAB) || s.is(Blocks.BRICK_WALL)
            || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.STONE_BRICK_STAIRS) || s.is(Blocks.STONE_BRICK_SLAB) || s.is(Blocks.STONE_BRICK_WALL);

    // Back-off: brainNodes that survey but yield no wall (no cobble / too large) aren't retried for a while,
    // so a no-plan village can't re-run the survey every 10s. Cleared once a plan forms or the brain node is gone.
    private static final int SURVEY_BACKOFF_TICKS = 1200; // ~60s before retrying a village that produced no wall
    private static final Map<GlobalPos, Integer> surveyBackoff = new HashMap<>();

    // Stage bell: last level we saw per brain node, to detect a level-up from ANY source (sunset income,
    // player feeding, miner emeralds, ...). Transient -- on (re)load a node is "first seen" and just records
    // its level WITHOUT ringing, so a server restart never spams bells. The stage-1 bell is played at the
    // moment the node is placed instead (see tryPlaceBrainNode).
    private static final Map<GlobalPos, Integer> lastStageLevel = new HashMap<>();

    // Respawn grace: when a brain node is destroyed by something that summons the boss, give the boss a few
    // seconds to emerge before the auto-placer is allowed to raise a NEW brain node anywhere nearby. Without
    // this, a removal that happens to land between scan passes (or while the boss has briefly roamed out of the
    // 128-block guard's reach) lets a duplicate brain node rise instead of leaving the field to the boss.
    // Transient (like the maps above): a server restart simply clears the grace, and the boss guard takes over.
    private static final int REMOVAL_COOLDOWN_TICKS = 100;       // ~5s grace after a boss-summoning removal
    private static final double REMOVAL_COOLDOWN_RADIUS = 128.0; // matches the boss-nearby guard radius below
    private static final Map<GlobalPos, Long> recentRemovals = new HashMap<>(); // removed node pos -> server tick

    /** Ring a bell at a brain node when it reaches a new stage (its placement, or a level-up). */
    private static void playStageBell(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BELL_BLOCK,
                net.minecraft.sounds.SoundSource.BLOCKS, 3.0F, 1.0F);
    }

    /** Each pass, ring a bell for any brain node whose level rose since last time (covers every income source). */
    private static void checkStageBells(MinecraftServer server, BrainNodeData data) {
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            int lvl = data.econ(gp).getLevel();
            Integer prev = lastStageLevel.put(gp, lvl);
            if (prev != null && lvl > prev) { // a real level-up (first-seen just records, no bell)
                ServerLevel level = server.getLevel(gp.dimension());
                if (level != null && level.isLoaded(gp.pos())) playStageBell(level, gp.pos());
            }
        }
    }

    /**
     * For each brainNode: compute its defensive wall plan once (from the surrounding village footprint),
     * make sure nearby villagers carry the build goal, and each pass re-check whether the wall has been
     * completed (by the builders) and, when it has, remove the temporary climb scaffolds and announce it.
     */
    private static void driveWalls(MinecraftServer server, BrainNodeData data) {
        boolean trySurvey = server.getTickCount() % 200 == 0; // the cobblestone survey is heavy; throttle it
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;

            // Stage 2+: the village stops building and switches to fertilization. No builder staffing here;
            // InfectionManager.seedVillage (once) demotes existing builders, finishes the wall, and starts
            // the infection -- after which 20% of the now-idle villagers are made miners.
            if (data.econ(gp).getLevel() >= 2) continue;

            WallPlan plan = data.getWallPlan(gp);
            if (plan == null) {
                // Empty village (no villagers): dispatch settlers to found houses first; FoundingManager
                // creates the plan once they're done. gate() returns true to let normal planning proceed.
                if (!FoundingManager.gate(server, level, gp, data)) continue;
                if (!trySurvey) continue;
                Integer until = surveyBackoff.get(gp);
                if (until != null && server.getTickCount() < until) continue; // recently failed -> backed off
                WallPlan computed = WallPlanner.plan(level, gp, collectVillagePoints(level, gp.pos()));
                if (computed != null) {
                    computed.setStartTick(level.getGameTime()); // t0 for the per-layer builder stagger
                    data.putWallPlan(computed);
                    surveyBackoff.remove(gp);
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
                    VillageEvents.ensureWallBuildGoal(v);
                    // Keep-awake backstop: a builder must never get stuck asleep while its wall is unfinished
                    // (the goal only wakes itself while it is actively running).
                    if (BuilderRole.isBuilder(v) && v.isSleeping()) v.stopSleeping();
                }
                manageBuilders(level, gp.pos(), nearby, plan, false); // inner wall: builders path there normally
                sealStuckHoles(level, plan); // brainNode finishes cells the builders can't reach
                if (plan.recheckCompletion(level)) {
                    cleanupScaffolds(level, plan);
                    for (Villager v : nearby) BuilderRole.demote(v); // wall finished -> drop the builder tags
                    com.example.villagefightsback.wall.WallBuild.clearPlan(gp); // free the reservation map
                    data.setDirty();
                    launchWallFireworks(level, plan); // celebrate over the gates
                }
            }
        }
    }

    private static final long OUTER_SETTLE_GRACE = 200L; // require this many ticks of expansion-quiet before fixing the plan
    private static final long OUTER_MAX_WAIT = 6000L;   // ...but never wait longer than this (a stuck settler/build can't block it)
    private static final int OUTER_SCAN_RADIUS = 140;   // larger survey so the obsidian ring encloses the expansion
    private static final Map<GlobalPos, Long> outerWallReadyAt = new HashMap<>(); // transient: earliest gametime to survey
    private static final Map<GlobalPos, Long> outerWallSince = new HashMap<>();   // transient: gametime we first became eligible

    /**
     * Stage-3 OUTER obsidian wall. After the settler expansion has launched its buildings (and a short delay so
     * they're actually placed), survey the now-larger village footprint and plan a bigger wall around it -- all
     * obsidian, with fake-obsidian gates. Builders raise it under the same rules as the inner wall; on completion
     * it launches fireworks and instantly raises its gate turrets (like the inner wall's level-2 defenders).
     */
    private static void driveOuterWalls(MinecraftServer server, BrainNodeData data) {
        boolean trySurvey = server.getTickCount() % 200 == 0; // the survey is heavy; throttle it
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            if (data.econ(gp).getLevel() < 3) continue;        // stage 3+
            if (!data.isExpanded(gp)) continue;                // only after the settler expansion launched
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;

            WallPlan plan = data.getOuterWallPlan(gp);
            if (plan == null) {
                long now = level.getGameTime();
                Long since = outerWallSince.get(gp);
                if (since == null) { outerWallSince.put(gp, now); since = now; }
                // Don't fix the plan until the expansion has fully materialized: every settler has dropped its
                // TNT AND the structure builder has finished. While still busy, keep pushing the survey time out,
                // so we only plan after OUTER_SETTLE_GRACE ticks of continuous quiet. BUT cap the total wait at
                // OUTER_MAX_WAIT so a settler that gets stuck (and never drops its flag) can't block the wall
                // forever -- past the cap we plan regardless.
                boolean forced = now - since >= OUTER_MAX_WAIT;
                boolean busy = !forced && (ExpansionManager.hasActiveSettlersNear(level, gp.pos(), OUTER_SCAN_RADIUS)
                        || HouseBuildManager.hasPendingNear(level, gp.pos(), OUTER_SCAN_RADIUS));
                if (busy) { outerWallReadyAt.put(gp, now + OUTER_SETTLE_GRACE); continue; }
                Long readyAt = outerWallReadyAt.get(gp);
                if (readyAt == null) { outerWallReadyAt.put(gp, now + OUTER_SETTLE_GRACE); continue; } // begin the grace
                if (now < readyAt || !trySurvey) continue;
                WallPlan computed = WallPlanner.plan(level, gp,
                        collectVillagePoints(level, gp.pos(), OUTER_SCAN_RADIUS), true /* obsidian */);
                if (computed != null) {
                    computed.setStartTick(now);
                    data.putOuterWallPlan(computed);
                    outerWallReadyAt.remove(gp);
                    outerWallSince.remove(gp);
                }
                continue;
            }
            if (!plan.isComplete()) {
                double radius = Math.max(ENSURE_GOAL_RADIUS, Math.sqrt(plan.joinRadiusSqr()));
                List<Villager> nearby = level.getEntitiesOfClass(Villager.class,
                        new AABB(gp.pos()).inflate(radius), v -> v.isAlive());
                for (Villager v : nearby) {
                    VillageEvents.ensureWallBuildGoal(v);
                    if (BuilderRole.isBuilder(v) && v.isSleeping()) v.stopSleeping();
                }
                manageBuilders(level, gp.pos(), nearby, plan, true); // outer wall: dispatch builders to their gate
                sealStuckHoles(level, plan);
                if (plan.recheckCompletion(level)) {
                    cleanupScaffolds(level, plan);
                    for (Villager v : nearby) BuilderRole.demote(v);
                    com.example.villagefightsback.wall.WallBuild.clearPlan(gp);
                    if (!data.areOuterDefendersBuilt(gp)) {     // instantly summon the outer gate turrets
                        for (BlockPos gate : plan.gates()) buildDefenderShell(level, gate);
                        data.markOuterDefendersBuilt(gp);
                    }
                    data.setDirty();
                    launchWallFireworks(level, plan);
                }
            }
        }
    }

    // Bright firework burst palette for the wall-completion celebration.
    private static final int[] FIREWORK_COLORS = {
            0xFF2D2D, 0xFF9A2D, 0xFFE92D, 0x57FF2D, 0x2DFFD0, 0x2D8CFF, 0x9A2DFF, 0xFF2DD0, 0xFFFFFF
    };

    /** Launch a handful of celebratory fireworks above the gates the moment the wall is finished. */
    private static void launchWallFireworks(ServerLevel level, WallPlan plan) {
        List<BlockPos> spots = new ArrayList<>(plan.gates());
        if (spots.isEmpty()) spots.add(plan.brainNode().pos()); // wall with no gates -> fire over the centre
        int n = Math.min(spots.size(), 6);
        for (int i = 0; i < n; i++) {
            BlockPos s = spots.get(i);
            double x = s.getX() + 0.5, y = s.getY() + WallPlan.COURSES + 1.0, z = s.getZ() + 0.5;
            level.addFreshEntity(new net.minecraft.world.entity.projectile.FireworkRocketEntity(
                    level, x, y, z, celebrationRocket(level.getRandom())));
        }
    }

    /** A random colourful firework rocket item (purely visual -- a free-flying firework deals no damage). */
    private static net.minecraft.world.item.ItemStack celebrationRocket(net.minecraft.util.RandomSource rand) {
        net.minecraft.world.item.ItemStack stack =
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET);
        net.minecraft.nbt.CompoundTag fireworks = new net.minecraft.nbt.CompoundTag();
        fireworks.putByte("Flight", (byte) (1 + rand.nextInt(2)));
        net.minecraft.nbt.ListTag explosions = new net.minecraft.nbt.ListTag();
        int bursts = 1 + rand.nextInt(2);
        for (int b = 0; b < bursts; b++) {
            net.minecraft.nbt.CompoundTag ex = new net.minecraft.nbt.CompoundTag();
            ex.putByte("Type", (byte) rand.nextInt(5));   // 0 small ball .. 4 burst
            ex.putBoolean("Flicker", rand.nextBoolean());
            ex.putBoolean("Trail", rand.nextBoolean());
            int[] colors = new int[1 + rand.nextInt(3)];
            for (int c = 0; c < colors.length; c++) colors[c] = FIREWORK_COLORS[rand.nextInt(FIREWORK_COLORS.length)];
            ex.putIntArray("Colors", colors);
            explosions.add(ex);
        }
        fireworks.put("Explosions", explosions);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put("Fireworks", fireworks);
        stack.setTag(tag);
        return stack;
    }

    // A cell abandoned by builders this many times is sealed directly by the brain node (builders can't reach
    // it -- e.g. it sits over a roof, a cliff, or inside a structure -- so it would otherwise stay a hole).
    private static final int SEAL_AFTER_ABANDONS = 2;

    /**
     * Safety net so the wall can never be left with mob-passable holes: any cell the builders have given up on
     * {@value #SEAL_AFTER_ABANDONS}+ times is placed directly by the brain node -- but ONLY where the world
     * cell is currently empty/replaceable (a real gap). Genuine obstacles (player blocks, bedrock, ...) are
     * left alone (they already count as "satisfied" gaps). Cheap: iterates only the small set of abandoned
     * cells, not the whole wall.
     */
    private static void sealStuckHoles(ServerLevel level, WallPlan plan) {
        java.util.Map<Long, Integer> abandons = com.example.villagefightsback.wall.WallBuild.abandons(plan.brainNode());
        if (abandons.isEmpty()) return;
        int sealed = 0;
        for (Map.Entry<Long, Integer> e : new ArrayList<>(abandons.entrySet())) {
            if (e.getValue() < SEAL_AFTER_ABANDONS) continue;
            BlockPos pos = BlockPos.of(e.getKey());
            if (!level.isLoaded(pos)) continue; // don't force-load
            WallBlock wb = plan.blockAt(pos);
            if (wb == null) { com.example.villagefightsback.wall.WallBuild.clearAbandon(plan.brainNode(), pos); continue; }
            // Only seal genuine gaps: if it's already built, or blocked by a real obstacle, leave it and stop
            // counting it (satisfied() treats both as done).
            if (plan.satisfied(level, wb) || !level.getBlockState(pos).canBeReplaced()) {
                com.example.villagefightsback.wall.WallBuild.clearAbandon(plan.brainNode(), pos);
                continue;
            }
            level.setBlock(pos, wb.toState(), 3);
            com.example.villagefightsback.wall.WallBuild.clearAbandon(plan.brainNode(), pos);
            sealed++;
        }
        // No broadcast: sealing happens often, so a message here would spam the chat.
    }

    /**
     * Keep exactly one builder per gate (door index 0..{@code doorCount}-1) among the villagers nearby:
     * demote duplicate/stray-door builders, then fill any uncovered door from a free villager (that isn't on
     * the step-down cool-down). Spawn dedicated builders only to reach a minimum body count -- never beyond --
     * so a village that's short on villagers gets staffed without the self-demotion cycle ever spawning an
     * ever-growing crowd. A door with no eligible villager is left uncovered this pass.
     */
    private static void manageBuilders(ServerLevel level, BlockPos brainNode, List<Villager> nearby,
                                       WallPlan plan, boolean dispatchToGate) {
        int doorCount = plan.doorColumns().length;
        int slots = doorCount > 0 ? Math.min(BuilderRole.BUILDER_COUNT, doorCount) : BuilderRole.BUILDER_COUNT;
        boolean[] covered = new boolean[slots];
        for (Villager v : nearby) {
            if (!BuilderRole.isBuilder(v)) continue;
            int d = BuilderRole.door(v);
            if (d < 0 || d >= covered.length || covered[d]) {
                BuilderRole.demote(v); // stray / duplicate door -> free it
            } else {
                covered[d] = true;
            }
        }
        // Fill uncovered doors ONLY from existing eligible villagers -- NEVER spawn new ones just because the
        // village is short on builders. A door with no eligible villager is simply left uncovered this pass.
        java.util.Iterator<Villager> free = nearby.stream().filter(BuilderRole::eligible).iterator();
        for (int d = 0; d < covered.length; d++) {
            if (covered[d] || !free.hasNext()) continue;
            Villager v = free.next();
            BuilderRole.promote(v, d);
            // The outer (obsidian) wall is far outside the inner-walled village, so a freshly-promoted builder
            // can't path out to it -- dispatch it straight to its gate on the outer perimeter, just outside.
            if (dispatchToGate) dispatchToDoor(level, brainNode, v, plan, d);
            VillageEvents.ensureWallBuildGoal(v); // make sure the just-promoted builder can build
        }
    }

    /** Teleport a builder to a spot just OUTSIDE its assigned gate (a couple of blocks out from the wall, on the
     *  surface), so it starts right at the future door instead of being trapped inside the inner wall. */
    private static void dispatchToDoor(ServerLevel level, BlockPos brainNode, Villager v, WallPlan plan, int doorIndex) {
        BlockPos gate = plan.doorBasePos(doorIndex);
        if (gate == null) return;
        double dx = gate.getX() - brainNode.getX(), dz = gate.getZ() - brainNode.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        double ox = len > 0.01 ? dx / len : 0.0, oz = len > 0.01 ? dz / len : 0.0; // outward = away from the centre
        int tx = (int) Math.floor(gate.getX() + 0.5 + ox * 2.0);
        int tz = (int) Math.floor(gate.getZ() + 0.5 + oz * 2.0);
        BlockPos surf = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(tx, gate.getY(), tz));
        if (v.isSleeping()) v.stopSleeping();
        v.moveTo(tx + 0.5, surf.getY(), tz + 0.5, v.getYRot(), 0.0F);
    }

    /**
     * The 2D points that define the village extent for the wall. Primary heuristic: columns containing
     * cobblestone within +/-{@value #COBBLE_Y_BAND} of the brain node's height (cobblestone is essentially
     * never natural at the surface, so it tracks the man-made village well). Falls back to beds (HOME POIs)
     * if too little cobblestone is found.
     */
    private static List<BlockPos> collectVillagePoints(ServerLevel level, BlockPos center) {
        return collectVillagePoints(level, center, COBBLE_SCAN_RADIUS);
    }

    private static List<BlockPos> collectVillagePoints(ServerLevel level, BlockPos center, int scanRadius) {
        List<BlockPos> pts = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int r = scanRadius;
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
        List<BlockPos> result = pts.size() >= WallPlanner.MIN_BEDS ? pts : collectBeds(level, center);
        // The BrainNode block itself must always sit INSIDE the wall envelope -- it isn't "village material",
        // so nothing else marks its column. Add its (x,z) so the footprint is always grown to enclose it.
        result.add(new BlockPos(center.getX(), 0, center.getZ()));
        return result;
    }

    /** Forget a brain node's survey back-off (called when it's removed, so a replacement re-surveys at once). */
    public static void clearSurveyBackoff(GlobalPos brainNode) {
        surveyBackoff.remove(brainNode);
    }

    /** Start the {@value #REMOVAL_COOLDOWN_TICKS}-tick respawn grace at {@code brainNode}: no NEW brain node may
     *  rise within {@value #REMOVAL_COOLDOWN_RADIUS} blocks until it elapses, giving the boss time to emerge.
     *  Called from {@link VillageBrainNodeBlock#onRemove} for boss-summoning (non-silk) removals. */
    public static void recordRemoval(GlobalPos brainNode) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) recentRemovals.put(brainNode, (long) server.getTickCount());
    }

    /** Bed positions (HOME POIs) within {@value #BED_SCAN_RADIUS} blocks of the brain node. */
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
     * DEBUG/admin: instantly stamp a brain node's entire computed wall into the world (bypassing the
     * builders), computing the plan first if needed. Returns the block count, or -1 if there aren't enough
     * beds yet. Triggered by right-clicking the brain node with a stick.
     */
    public static int debugPasteWall(ServerLevel level, BlockPos brainNodePos) {
        GlobalPos gp = GlobalPos.of(level.dimension(), brainNodePos.immutable());
        BrainNodeData data = BrainNodeData.get(level.getServer());
        data.removeWallPlan(gp); // always recompute with the current logic, then paste
        WallPlan plan = WallPlanner.plan(level, gp, collectVillagePoints(level, brainNodePos));
        if (plan == null) return -1;
        data.putWallPlan(plan);
        int placed = 0;
        for (WallBlock wb : plan.blocks()) {
            level.setBlock(wb.pos(), wb.toState(), 3);
            placed++;
        }
        return placed;
    }

    /**
     * Create + store this brain node's wall plan right now (used by the founding flow to enter the wall
     * phase promptly once the settlers' houses are up). Returns true if a plan was made or already exists.
     */
    public static boolean createWallPlanNow(ServerLevel level, GlobalPos gp) {
        BrainNodeData data = BrainNodeData.get(level.getServer());
        if (data.getWallPlan(gp) != null) return true;
        WallPlan plan = WallPlanner.plan(level, gp, collectVillagePoints(level, gp.pos()));
        if (plan == null) return false;
        plan.setStartTick(level.getGameTime());
        data.putWallPlan(plan);
        return true;
    }

    /** Place a brainNode at the centre (meeting point / bell) of each loaded village, once. */
    private static void spawnInVillages(MinecraftServer server, BrainNodeData data) {
        for (ServerLevel level : server.getAllLevels()) {
            PoiManager poi = level.getPoiManager();
            for (ServerPlayer player : level.players()) {
                // Collect the meeting-point positions FIRST. tryPlaceBrainNode places the brain node and beds
                // (HOME POIs), which mutate the POI manager -- doing that while the POI stream is still open
                // throws ConcurrentModificationException, so we drain the stream before touching the world.
                List<BlockPos> centers = poi.getInSquare(holder -> holder.is(PoiTypes.MEETING),
                                player.blockPosition(), VILLAGE_SCAN_RADIUS, PoiManager.Occupancy.ANY)
                        .map(record -> record.getPos().immutable())
                        .toList();
                for (BlockPos bell : centers) tryPlaceBrainNode(level, data, bell);
            }
        }
    }

    private static void tryPlaceBrainNode(ServerLevel level, BrainNodeData data, BlockPos bell) {
        GlobalPos key = GlobalPos.of(level.dimension(), bell.immutable());
        if (data.isServiced(key)) return;

        // The brainNode takes the bell's place DIRECTLY: it replaces the bell block, so a village shows a
        // brainNode at its heart instead of a bell. If the bell is wall/ceiling-mounted (nothing solid below
        // -> the brain node would float), fall back to the nearest open ground spot instead.
        BlockPos spot = level.getBlockState(bell.below()).isFaceSturdy(level, bell.below(), Direction.UP)
                ? bell.immutable()
                : findBrainNodeSpot(level, bell);
        if (spot == null) return; // no good spot yet; retry next scan

        // Respawn grace: if a brain node was destroyed near here within the last ~5s (and so summoned the boss),
        // wait. Once the grace elapses, the boss-nearby guard below decides -- a new node rises ONLY if the boss
        // never appeared / has since died. This is the "wait, then check the boss appeared before respawning".
        long nowTick = level.getServer().getTickCount();
        recentRemovals.values().removeIf(t -> nowTick - t > REMOVAL_COOLDOWN_TICKS); // self-pruning
        double cd2 = REMOVAL_COOLDOWN_RADIUS * REMOVAL_COOLDOWN_RADIUS;
        for (Map.Entry<GlobalPos, Long> e : recentRemovals.entrySet()) {
            if (e.getKey().dimension().equals(level.dimension()) && e.getKey().pos().distSqr(spot) <= cd2) return;
        }

        // One brainNode per area: skip if another already exists within 100 blocks (overlapping
        // villages / extra bells). Not marked serviced, so it self-heals if that brainNode is removed.
        if (hasBrainNodeWithin(level, data, spot, 100.0)) return;

        // While the unleashed brainNode boss is loose in the area, NO new brainNode may rise (no domino
        // re-spawn after it was destroyed). It resumes only once the boss is dead. Not marked serviced.
        if (!level.getEntitiesOfClass(UnleashedBrainNodeEntity.class,
                new net.minecraft.world.phys.AABB(spot).inflate(128.0)).isEmpty()) return;

        level.setBlock(spot, ModBlocks.VILLAGE_BRAIN_NODE.get().defaultBlockState(), 3); // onPlace tracks it
        data.addServiced(key);
        // If a player placed this bell, they founded the village -> Hero of the Village.
        java.util.UUID founder = data.takeBellPlacer(key);
        if (founder != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(founder);
            if (p != null) rewardHero(p);
        }
        broadcast(level.getServer(), phaseBanner(1)); // "SETTLEMENT PHASE"
        playStageBell(level, spot); // stage 1 reached (checkStageBells first-sees this node next pass, so no double-ring)

        // Furnish the village: drop a bed into every bedless cobblestone house near the brain node.
        HouseBedFiller.fill(level, spot);
    }

    /** Nearest open ground spot within 3 blocks of the village centre, close to the centre's height. */
    private static BlockPos findBrainNodeSpot(ServerLevel level, BlockPos center) {
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

    /** True if a brainNode already exists within {@code radius} blocks of {@code pos} in this level. */
    private static boolean hasBrainNodeWithin(ServerLevel level, BrainNodeData data, BlockPos pos, double radius) {
        double r2 = radius * radius;
        for (GlobalPos gp : data.blocks()) {
            if (gp.dimension().equals(level.dimension()) && gp.pos().distSqr(pos) <= r2) return true;
        }
        return false;
    }

    /** Once per day at sunset, INDEPENDENTLY PER VILLAGE: reinforcements + 1 emerald per nearby defender. */
    private static void handleSunsetIncome(MinecraftServer server, BrainNodeData data) {
        long dayTime = server.overworld().getDayTime();
        long day = Math.floorDiv(dayTime, 24000L);
        long timeOfDay = Math.floorMod(dayTime, 24000L);
        if (timeOfDay < SUNSET_TIME) return;

        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            BrainNodeData.Econ econ = data.econ(gp);
            if (econ.getLastIncomeDay() == day) continue; // this village already received income today
            econ.setLastIncomeDay(day);

            reinforceVillage(level, data.getWallPlan(gp), gp.pos(), econ.getLevel()); // reinforcements scale with the village's stage
            ExpansionManager.maybeExpand(level, gp, data); // stage-3 one-time expansion wave (no-ops below level 3)
            spawnFromNodes(level, data, gp, econ.getLevel(), day); // golem-factory / barracks / lure-tower payloads

            // Daily harvest = one emerald per nearby defender, increased by 10% for EACH node in the income
            // radius (e.g. base 10 with 3 nodes -> 10 * 1.30 = 13).
            int base = countDefendersNear(level, gp.pos());
            int income = (int) Math.round(base * (1.0 + 0.10 * countNodesNear(data, gp)));
            if (income <= 0) continue;
            int gained = econ.addEmeralds(income);
            if (gained > 0) {
                broadcast(server, phaseBanner(econ.getLevel())); // FERTILIZATION / EXPANSION / CULMINATION PHASE
            }
        }
    }

    /**
     * Daily sunset reinforcements, scaled by the village's brainNode {@code stage} (1..4):
     * <pre>
     *   stage 1: 2 villagers + 1 guard
     *   stage 2: 4 villagers + 2 guards
     *   stage 3: 6 villagers + 3 guards + 1 iron golem
     *   stage 4: 8 villagers + 4 guards + 2 iron golems
     * </pre>
     * Villagers consume beds, so the villager portion is skipped when the villagers within
     * {@value #INCOME_RADIUS} blocks already exceed {@value #VILLAGER_BED_RATIO_CAP}x the bed count (so the
     * worker population can't endlessly overpopulate). Guards and iron golems are military -- they don't use
     * beds, so they always arrive. The spawned mobs auto-arm through the normal entity-join handler.
     */
    private static void reinforceVillage(ServerLevel level, WallPlan innerWall, BlockPos pos, int stage) {
        int villagersToSpawn = 2 * stage;            // 2 / 4 / 6 / 8
        int guardsToSpawn = stage;                   // 1 / 2 / 3 / 4
        int golemsToSpawn = stage >= 3 ? 1 : 0;      // 0 / 0 / 1 / 1  (stage-4 daily golem is 1, not 2)

        // From stage 4 the village is capped by a glass dome, so the centre's heightmap surface is the DOME
        // ROOF -- spawning there would drop reinforcements on top of the dome. Instead, once a stage-4 village
        // has an inner wall, spawn the new mobs on open ground just OUTSIDE that inner wall (near its gates).
        WallPlan outside = (stage >= 4 && innerWall != null) ? innerWall : null;

        AABB box = new AABB(pos).inflate(INCOME_RADIUS);
        int villagers = level.getEntitiesOfClass(Villager.class, box, Villager::isAlive).size();
        long beds = level.getPoiManager().getCountInRange(
                h -> h.is(PoiTypes.HOME), pos, INCOME_RADIUS, PoiManager.Occupancy.ANY);
        boolean overpopulated = beds <= 0 || villagers > VILLAGER_BED_RATIO_CAP * beds;
        if (!overpopulated) {
            for (int i = 0; i < villagersToSpawn; i++) spawnDefender(level, pos, EntityType.VILLAGER, outside);
        }

        EntityType<?> guardType = ForgeRegistries.ENTITY_TYPES.getValue(GUARD_ID);
        if (guardType != null) {
            for (int i = 0; i < guardsToSpawn; i++) spawnDefender(level, pos, guardType, outside);
        }
        for (int i = 0; i < golemsToSpawn; i++) spawnDefender(level, pos, EntityType.IRON_GOLEM, outside);
    }

    /** Spawn one entity of {@code type} (it auto-arms on join). When {@code outsideWall} is non-null (stage 4),
     *  spawn just outside that inner wall; otherwise on the surface near {@code center}. */
    private static void spawnDefender(ServerLevel level, BlockPos center, EntityType<?> type, WallPlan outsideWall) {
        BlockPos spot = outsideWall != null ? findSpawnSpotOutsideWall(level, outsideWall) : null;
        if (spot == null) spot = findSpawnSpot(level, center); // no wall / no valid outside spot -> old behaviour
        Entity e = type.spawn(level, spot, MobSpawnType.EVENT);
        if (e instanceof Villager v) v.setBaby(false);
    }

    private static BlockPos findSpawnSpot(ServerLevel level, BlockPos center) {
        for (int i = 0; i < 12; i++) {
            BlockPos col = center.offset(level.getRandom().nextInt(9) - 4, 0, level.getRandom().nextInt(9) - 4);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
            if (spawnableSurface(level, surface)) return surface;
        }
        return center.above();
    }

    /** Pick a ground spot a few blocks OUTSIDE the inner wall, fanning out past a (random) gate so stage-4
     *  reinforcements muster between the inner and outer walls instead of on top of the dome. Null if none found. */
    private static BlockPos findSpawnSpotOutsideWall(ServerLevel level, WallPlan plan) {
        List<BlockPos> gates = plan.gates();
        if (gates.isEmpty()) return null;
        BlockPos centre = plan.brainNode().pos();
        for (int i = 0; i < 16; i++) {
            BlockPos gate = gates.get(level.getRandom().nextInt(gates.size()));
            double dx = gate.getX() + 0.5 - centre.getX();
            double dz = gate.getZ() + 0.5 - centre.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            double ox = len > 0.01 ? dx / len : 1.0, oz = len > 0.01 ? dz / len : 0.0;
            int dist = 3 + level.getRandom().nextInt(3);                 // 3..5 blocks beyond the gate
            double jitter = level.getRandom().nextInt(5) - 2;           // -2..2 along the wall so they don't stack
            int tx = (int) Math.floor(gate.getX() + 0.5 + ox * dist - oz * jitter);
            int tz = (int) Math.floor(gate.getZ() + 0.5 + oz * dist + ox * jitter);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(tx, gate.getY(), tz));
            if (plan.contains(surface)) continue;                       // still inside the ring -> push further next try
            if (spawnableSurface(level, surface)) return surface;
        }
        return null;
    }

    /** A standable open surface: feet + head clear, solid floor below. */
    private static boolean spawnableSurface(ServerLevel level, BlockPos surface) {
        return level.getBlockState(surface).getCollisionShape(level, surface).isEmpty()
                && level.getBlockState(surface.above()).getCollisionShape(level, surface.above()).isEmpty()
                && level.getBlockState(surface.below()).isFaceSturdy(level, surface.below(), Direction.UP);
    }

    /** Alive villagers/guards within {@value #INCOME_RADIUS} blocks of one brainNode. */
    private static int countDefendersNear(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(INCOME_RADIUS);
        return level.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && isDefender(x)).size();
    }

    /** Nodes (golem-factory / barracks / lure-tower) within {@value #INCOME_RADIUS} of a brainNode -- each
     *  raises the daily harvest by 10%. Counted from the tracked block set (cheap), not a world scan. */
    private static int countNodesNear(BrainNodeData data, GlobalPos brainNode) {
        double r2 = (double) INCOME_RADIUS * INCOME_RADIUS;
        int n = 0;
        for (GlobalPos node : data.nodes()) {
            if (node.dimension().equals(brainNode.dimension()) && node.pos().distSqr(brainNode.pos()) <= r2) n++;
        }
        return n;
    }

    /** Find the nearest brain node to {@code pos} (same dimension) within {@code radius}; null if none. */
    static GlobalPos nearestBrainNode(BrainNodeData data, ServerLevel level, BlockPos pos, double radius) {
        double best = radius * radius;
        GlobalPos nearest = null;
        for (GlobalPos gp : data.blocks()) {
            if (!gp.dimension().equals(level.dimension())) continue;
            double d = gp.pos().distSqr(pos);
            if (d <= best) { best = d; nearest = gp; }
        }
        return nearest;
    }

    /** Trigger a node's payload spawn (used on block creation AND each sunset): find the nearest brain node in
     *  range, and -- if there is one -- ask the node block to spawn its reinforcement at the brain node's stage. */
    public static void triggerNodeSpawn(ServerLevel level, BlockPos nodePos) {
        BrainNodeData data = BrainNodeData.get(level.getServer());
        GlobalPos brain = nearestBrainNode(data, level, nodePos, INCOME_RADIUS);
        if (brain == null) return; // no brain node in range -> the node only gives its +10% gem bonus
        int stage = data.econ(brain).getLevel();
        if (level.getBlockState(nodePos).getBlock() instanceof AbstractNodeBlock node) {
            node.spawnPayload(level, nodePos, stage);
        }
    }

    // Per-node day-stamp so a node spawns its payload at most once per in-game day (dedupes across overlapping
    // village radii). Transient: a restart at the exact sunset of the same day could spawn once more -- harmless.
    private static final Map<GlobalPos, Long> nodeSpawnDay = new HashMap<>();

    /** Drop a removed node's day-stamp so the transient map stays bounded (called from AbstractNodeBlock.onRemove). */
    static void forgetNode(GlobalPos node) { nodeSpawnDay.remove(node); }

    /** Once per day at sunset, spawn the payload of every node within {@code brain}'s radius (loaded only). */
    private static void spawnFromNodes(ServerLevel level, BrainNodeData data, GlobalPos brain, int stage, long day) {
        double r2 = (double) INCOME_RADIUS * INCOME_RADIUS;
        for (GlobalPos node : new ArrayList<>(data.nodes())) {
            if (!node.dimension().equals(brain.dimension())) continue;
            if (node.pos().distSqr(brain.pos()) > r2) continue;
            if (!level.isLoaded(node.pos())) continue;                  // spawn only where the world is loaded
            Long last = nodeSpawnDay.get(node);
            if (last != null && last == day) continue;                 // already spawned today (any village)
            nodeSpawnDay.put(node, day);
            if (level.getBlockState(node.pos()).getBlock() instanceof AbstractNodeBlock nb) {
                nb.spawnPayload(level, node.pos(), stage);
            } else {
                data.removeNode(node);                                 // the block is gone -> stop tracking it
                nodeSpawnDay.remove(node);                             // ...and forget its day-stamp
            }
        }
    }

    /** Counts of [villagers, guards, iron golems] alive within {@value #INCOME_RADIUS} of a brainNode (for the
     *  UI). Emeraldgers are villager-based virus warriors, so they are NOT counted as villagers. */
    public static int[] countDefenderTypes(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(INCOME_RADIUS);
        int villagers = 0, guards = 0, golems = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e instanceof EmeraldgerEntity) continue;
            if (e instanceof Villager) villagers++;
            else if (e instanceof net.minecraft.world.entity.animal.IronGolem) golems++;
            else if (GUARD_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()))) guards++;
        }
        return new int[]{villagers, guards, golems};
    }

    /** Keep each loaded brainNode showing its OWN village's level; prune positions whose block is gone. */
    private static void syncVisuals(MinecraftServer server, BrainNodeData data) {
        List<GlobalPos> stale = new ArrayList<>();
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            BlockState state = level.getBlockState(gp.pos());
            if (state.getBlock() instanceof VillageBrainNodeBlock) {
                int target = VillageBrainNodeBlock.chargesForLevel(data.econ(gp).getLevel());
                if (state.getValue(VillageBrainNodeBlock.CHARGES) != target) {
                    level.setBlock(gp.pos(), state.setValue(VillageBrainNodeBlock.CHARGES, target), 3);
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

    /** The short stage-transition banner shown when a brain node reaches {@code level}. Stage 1 keeps the
     *  purple of the old "has risen" line; stages 2-4 keep the gold of the old "evolved" line. */
    private static String phaseBanner(int level) {
        return switch (level) {
            case 1 -> "§5SETTLEMENT PHASE";
            case 2 -> "§6FERTILIZATION PHASE";
            case 3 -> "§6EXPANSION PHASE";
            default -> "§6CULMINATION PHASE"; // level 4 (max)
        };
    }
}
