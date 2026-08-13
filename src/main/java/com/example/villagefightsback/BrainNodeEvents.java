package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Drives the Village Brain Node: spawns one at the centre of each loaded village, grants sunset emerald
 * income, and keeps every brain node's visual level in sync with the global {@link BrainNodeData}.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class BrainNodeEvents {
    private BrainNodeEvents() {}

    private static final ResourceLocation GUARD_ID = ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard");
    static int INCOME_RADIUS = 100;     // brain influence radius (set from Config.apply(); base 100)
    private static final int VILLAGE_SCAN_RADIUS = 64; // look for village centres this far from players
    private static final int DISCOVERY_HUD_TICKS = 100; // 5s on-screen discovery message (bottom of screen)
    private static final int TICK_INTERVAL = 40;       // run all of this every 2 seconds
    private static final long SUNSET_TIME = 12000L;     // day-time at dusk
    private static final double VILLAGER_BED_RATIO_CAP = 1.5; // skip reinforcement above this villagers:beds

    // --- stage-4 air strikes ---
    private static final long STRIKE_INTERVAL = 12000L; // consider an air strike every 10 minutes
    private static final double WITHER_STRIKE_RANGE = 200.0; // detect a wither storm to air-strike within this
    private static final long ATTACK_WINDOW   = 12000L; // ...only if an ally was attacked within the past 10 minutes
    private static final Map<GlobalPos, Long> lastAllyHurt        = new HashMap<>(); // node -> gameTime an ally was last hit near it
    private static final Map<GlobalPos, Long> strikeCooldownUntil = new HashMap<>(); // node -> gameTime the 10-min cooldown ends (set only after an ACTUAL strike)

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % TICK_INTERVAL != 0) return;

        long _p = ModProfiler.now();
        BrainNodeData data = BrainNodeData.get(server);
        processPendingCats(server);            // emerald cats settled 2s ago now materialise above their brain node
        spawnInVillages(server, data);
        announceDiscoveredNodes(server, data); // brain-discovery banner the first time any player comes near a node
        retrofitBuildingProtection(server, data); // claim village buildings for nodes placed before that existed
        driveWalls(server, data);
        applyImmunityAura(server, data); // shield nearby villagers/golems at ANY stage
        manageDefenders(server, data); // level-2 gate turrets
        manageExpansion(server, data); // level-3 settler wave (fires spontaneously on reaching level 3)
        ChunkPortalManager.manage(server, data); // v1.2.1 chunk portals (no-op unless config on + a stage-3 brain in reach)
        driveOuterWalls(server, data); // level-3 OUTER obsidian wall around the expansion buildings
        manageDome(server, data);      // level-4 protective glass dome
        manageAirStrikes(server, data); // level-4 air strikes (every 10 min, if the village was attacked)
        BrainReflexes.tick(server, data); // emerald-funded immune reflexes (settlers, guards, golems, door regen)
        OrganicMass.tick(server, data); // v1.1.2: organic-mass army commander (no-op unless the config is on)
        handleSunsetIncome(server, data);
        collectLooseEmeralds(server, data); // brain vacuums up unpicked dropped emeralds near each node
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
                        || GUARD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(x.getType()))))) {
                if (e.distanceToSqr(center) > r2) continue;
                // ambient=false, showParticles=false, showIcon=false -> a silent, particle-free shield.
                e.addEffect(new MobEffectInstance(ModEffects.IMMUNITY, 200, 0, false, false, false));
                // Also clear any harmful effect already on it (VillageEvents blocks NEW ones from landing).
                // getEffect() now returns Holder<MobEffect>; .value() reaches the MobEffect for its category.
                List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> bad = e.getActiveEffects().stream()
                        .filter(i -> i.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL)
                        .map(MobEffectInstance::getEffect).toList();
                bad.forEach(e::removeEffect);
            }
        }
    }

    /** Remember which player placed a village bell (legacy; the founder reward was removed). Kept so old saves
     *  with bellPlacers still load and so the record is pruned over time. */
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
    public static void onAllyAttacked(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
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
            if (data.econ(gp).getLevel() < Config.airStrikesAppearAtStage()) continue; // configurable stage gate (default 4)
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            long now = level.getGameTime();
            Long until = strikeCooldownUntil.get(gp);
            if (until != null && now < until) continue;                 // still cooling down from the last strike
            Long hurt = lastAllyHurt.get(gp);
            if (hurt == null || now - hurt > ATTACK_WINDOW) continue;    // village not attacked in the window
            // FIRE WHEN READY: as soon as there's a valid clear target, strike -- then start the cooldown.
            // (No target right now just means we retry next pass; we don't burn the cooldown on an empty check.)
            LivingEntity target = pickAirStrikeTarget(level, data, gp.pos());
            if (target == null) continue;
            AirStrikeManager.launch(level, target);
            strikeCooldownUntil.put(gp, now + (long) (STRIKE_INTERVAL * Config.airStrikeCooldownMult()));
        }
    }

    /** The strongest (most-HP) hostile near the brain node whose strike column is clear; null if none qualify. */
    private static LivingEntity pickAirStrikeTarget(ServerLevel level, BrainNodeData data, BlockPos node) {
        // A wither storm is the apex threat: if one is present it is ALWAYS the target, with NO clear-column
        // requirement. Found by a hitbox-independent scan, so it is struck even when its hitbox is deactivated.
        LivingEntity ws = Factions.findNearestWitherStorm(level,
                net.minecraft.world.phys.Vec3.atCenterOf(node), WITHER_STRIKE_RANGE);
        if (ws != null) return ws;

        AABB box = new AABB(node).inflate(INCOME_RADIUS);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box, BrainNodeEvents::isAirStrikeEnemy);
        candidates.sort((a, b) -> Float.compare(b.getHealth(), a.getHealth())); // most HP first
        for (LivingEntity c : candidates) {
            // Only bomb a threat that stands clear of the village's own units: the column must be clear of village
            // structures AND no village ally may be inside the strike's blast footprint (so we never bomb our own).
            if (isStrikeColumnClear(level, c) && noAlliesNear(level, c)) return c;
        }
        return null;
    }

    // Allies must be clear of the WHOLE blast footprint, not just the drop column. The 3x3 grid's corner cells sit
    // ~CLEAR_RADIUS from the centre (by GRID_SPACING construction) and each rocket blast reaches EXPLOSION_RADIUS
    // further, so any village ally within this distance of the target could be caught -- if one is, we skip the
    // target. This is what makes the air strike pick heavy threats that are AWAY from the village's units.
    private static final double AIRSTRIKE_ALLY_CLEAR =
            AirStrikeManager.CLEAR_RADIUS + RocketEntity.EXPLOSION_RADIUS + 1.0;

    /** True if nobody the strike must protect ({@link #isAirStrikeProtected}) is within its blast footprint of
     *  the target, in 3D (so allies above, below, and around are all spared). */
    private static boolean noAlliesNear(ServerLevel level, LivingEntity target) {
        AABB box = target.getBoundingBox().inflate(AIRSTRIKE_ALLY_CLEAR);
        double cx = target.getX(), cy = target.getY(), cz = target.getZ();
        double r2 = AIRSTRIKE_ALLY_CLEAR * AIRSTRIKE_ALLY_CLEAR;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, BrainNodeEvents::isAirStrikeProtected)) {
            double dx = e.getX() - cx, dy = e.getY() - cy, dz = e.getZ() - cz;
            if (dx * dx + dy * dy + dz * dz <= r2) return false;
        }
        return true;
    }

    /** Air-strike target: a hostile mob, or a player flagged as a village enemy. Never one of our own. */
    private static boolean isAirStrikeEnemy(LivingEntity e) {
        if (!e.isAlive()) return false;
        // Village units, General Darius, the boss spirit and every emerald unit are off limits. Checked before
        // the Enemy test below, which several of them (Darius, emerald mobs) would otherwise pass.
        if (isAirStrikeProtected(e)) return false;
        if (e instanceof net.minecraft.world.entity.player.Player p) {
            return !p.isCreative() && !p.isSpectator() && p.hasEffect(ModEffects.ENEMY_OF_THE_VILLAGE);
        }
        if (e.getMaxHealth() < 50.0F) return false;                    // weak mobs aren't worth a rocket (overkill)
        return e instanceof net.minecraft.world.entity.monster.Enemy;
    }

    /** True if the vertical column above the target (radius {@link AirStrikeManager#CLEAR_RADIUS}, from the
     *  target up to the sky) is free of cobblestone/brick (village structures) -- rockets fall top-to-bottom, so
     *  we check the whole column UP, not a sphere. Ally clearance is a separate, wider 3D check ({@link #noAlliesNear}). */
    private static boolean isStrikeColumnClear(ServerLevel level, LivingEntity target) {
        int cx = (int) Math.floor(target.getX());
        int cz = (int) Math.floor(target.getZ());
        int baseY = (int) Math.floor(target.getY());
        int topY = level.getMaxBuildHeight() - 1;
        int r = AirStrikeManager.CLEAR_RADIUS;
        double r2 = (double) r * r;
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

    /** Villager, guard, or iron golem -- the civilian defenders whose being attacked counts as "the village is
     *  under attack" for the air-strike trigger ({@link #onAllyAttacked}). This is deliberately NOT the set the
     *  strike protects: for that see {@link #isAirStrikeProtected}. */
    private static boolean isVillageAlly(LivingEntity e) {
        return e instanceof Villager
                || e instanceof net.minecraft.world.entity.animal.IronGolem
                || GUARD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
    }

    /**
     * Everyone an air strike must never drop a rocket on: the village's own units (villagers, guards, golems,
     * wall defenders, General Darius) plus everyone the village spares (the Vindictive Village Spirit and the
     * whole emerald faction).
     *
     * <p>Delegates to {@link Factions} rather than listing types locally, the way every other village-side
     * threat scan in the mod does. A local list is what let a strike target General Darius: he extends
     * {@code Monster}, so he reads as an {@code Enemy}, and the old list (villager / iron golem / guard) did
     * not mention him. The rocket blast itself is indiscriminate, so this filter is the only thing standing
     * between the village and its own champion.
     */
    private static boolean isAirStrikeProtected(LivingEntity e) {
        return Factions.isVillage(e) || Factions.isSparedByVillage(e);
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
    private static final int OUTER_RAISE_PER_TICK = 4;  // obsidian blocks laid PER TICK -> a gradual, block-by-block raise
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
            // The stage-3 outer obsidian ring is convex and sits far out in wilderness, where villager builders
            // can't reach it. The brain node raises it ITSELF -- but GRADUALLY, block by block, in
            // raiseOuterWallsGradually() (every tick). Here (every 40 ticks) we only watch for completion and,
            // once it finishes, fire the celebration + instantly raise its gate turrets.
            if (!plan.isComplete() && plan.recheckCompletion(level)) {
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

    /**
     * Raise every incomplete stage-3 OUTER obsidian wall a few blocks PER TICK -- block by block, bottom-up,
     * the same gradual feel as the NBT-structure builder -- instead of stamping a whole layer at once. Runs
     * every tick; the planning + completion logic stays on the 40-tick {@link #driveOuterWalls} cadence.
     */
    @SubscribeEvent
    public static void raiseOuterWallsGradually(ServerTickEvent.Post event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        BrainNodeData data = BrainNodeData.get(server);
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            WallPlan plan = data.getOuterWallPlan(gp);
            if (plan == null || plan.isComplete()) continue;
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            plan.raiseDirectlyStep(level, OUTER_RAISE_PER_TICK);
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
        // 1.21: the firework data is the DataComponents.FIREWORKS component (Fireworks + FireworkExplosion records),
        // not a "Fireworks" CompoundTag. Same flight + per-burst shape/colour/flicker/trail values as before.
        java.util.List<net.minecraft.world.item.component.FireworkExplosion> explosions = new ArrayList<>();
        int bursts = 1 + rand.nextInt(2);
        for (int b = 0; b < bursts; b++) {
            net.minecraft.world.item.component.FireworkExplosion.Shape shape =
                    net.minecraft.world.item.component.FireworkExplosion.Shape.byId(rand.nextInt(5)); // 0 small ball .. 4 burst
            int[] colors = new int[1 + rand.nextInt(3)];
            for (int c = 0; c < colors.length; c++) colors[c] = FIREWORK_COLORS[rand.nextInt(FIREWORK_COLORS.length)];
            explosions.add(new net.minecraft.world.item.component.FireworkExplosion(
                    shape,
                    it.unimi.dsi.fastutil.ints.IntList.of(colors),
                    it.unimi.dsi.fastutil.ints.IntList.of(),       // no fade colours
                    rand.nextBoolean(),                            // Trail
                    rand.nextBoolean()));                          // Flicker (twinkle)
        }
        stack.set(net.minecraft.core.component.DataComponents.FIREWORKS,
                new net.minecraft.world.item.component.Fireworks(1 + rand.nextInt(2), explosions));
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

        // Where the brainNode settles:
        //  - Without MCA: it takes the bell's place DIRECTLY (replaces the bell block), so a village shows a
        //    brainNode at its heart instead of a bell -- but only if that spot is actually usable (see
        //    isGoodNodeSpot). A bell that is wall/ceiling-mounted, walled in, or under a low roof falls back.
        //  - With MCA Reborn: the bell IS MCA's Town Center building, and MCA's residency / marriage / taxes /
        //    guard systems hang off it, so consuming it would delete the village centre out from under MCA.
        //    There we always settle BESIDE the bell and leave it standing; MCA's town centre has an 8-block
        //    margin, so an adjacent spot is still the town hall.
        boolean keepBell = com.example.villagefightsback.compat.MCACompat.preserveTownCenter();
        BlockPos spot = (!keepBell && isGoodNodeSpot(level, bell, true))
                ? bell.immutable()
                : findBrainNodeSpot(level, bell);
        if (spot == null) {
            // Nowhere usable around this bell yet. Not marked serviced, so the next scan tries again, but log it:
            // a village silently never getting a node is otherwise invisible to diagnose.
            TheVillageFightsBack.LOGGER.warn(
                    "[The Village Fights Back] no usable brain node spot around the village centre at {}; will retry.",
                    bell);
            return;
        }

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
        // Note: founding a village no longer grants Hero of the Village, and the SETTLEMENT PHASE banner is NOT
        // broadcast here -- announceDiscoveredNodes fires it exactly once the first time any player is near the
        // node (which, for a hand-/auto-placed node, is essentially immediately), so it never double-fires.
        playStageBell(level, spot); // stage 1 reached (checkStageBells first-sees this node next pass, so no double-ring)

        // Claim the village's own buildings so the fertile spread never eats them. Reads the village's real
        // structure pieces, which covers the WHOLE settlement however far it sprawls -- the room detector in
        // HouseBedFiller only reaches 32 blocks, so on its own it left most of a village unprotected.
        int protectedBuildings = com.example.villagefightsback.infection.VillageBuildings.protectVillageAt(level, spot);
        TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] brain node at {} protected {} village buildings from block infection.",
                spot, protectedBuildings);

        // Furnish the village: drop a bed into every bedless cobblestone house near the brain node.
        // Its room detector also claims anything it recognises, which covers player-built or modded houses
        // that are not part of a generated village structure.
        HouseBedFiller.fill(level, spot);
    }

    /**
     * Brain-discovery banner: the first time ANY player comes within {@link #VILLAGE_SCAN_RADIUS} of a tracked
     * brain node (however that node came to exist -- auto-placed, hand-placed, or boss-reverted), broadcast the
     * settlement-phase banner once and persist the "announced" flag so it never double-fires across reloads.
     */
    /** Nodes whose village buildings have been claimed this session (see {@link #retrofitBuildingProtection}). */
    private static final java.util.Set<GlobalPos> buildingsProtected = new java.util.HashSet<>();

    /**
     * Claim the village buildings of brain nodes that already existed before building protection was added, so
     * an EXISTING world stops losing its village to the fertile spread instead of only new ones being spared.
     *
     * <p>Session-scoped and idempotent rather than persisted: the box store already drops an exact duplicate, so
     * re-claiming a village costs nothing, and doing it once per node per server start needs no new save data.
     * Only runs for nodes in a LOADED chunk, so it never forces chunk loading. Blocks already converted stay as
     * they are -- the original blocks are not recoverable -- but the buildings stop being eaten any further.
     */
    private static void retrofitBuildingProtection(MinecraftServer server, BrainNodeData data) {
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            if (buildingsProtected.contains(gp)) continue;
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            buildingsProtected.add(gp);
            int n = com.example.villagefightsback.infection.VillageBuildings.protectVillageAt(level, gp.pos());
            if (n > 0) {
                TheVillageFightsBack.LOGGER.info(
                        "[The Village Fights Back] claimed {} village buildings around the brain node at {}.", n, gp.pos());
            }
        }
    }

    private static void announceDiscoveredNodes(MinecraftServer server, BrainNodeData data) {
        double r2 = (double) VILLAGE_SCAN_RADIUS * VILLAGE_SCAN_RADIUS;
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            if (data.isAnnounced(gp)) continue;
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null) continue;
            java.util.List<ServerPlayer> near = new ArrayList<>();
            for (ServerPlayer player : level.players()) {
                if (player.blockPosition().distSqr(gp.pos()) <= r2) near.add(player);
            }
            if (near.isEmpty()) continue;
            broadcast(server, phaseBanner(1)); // same banner as placement: "SETTLEMENT PHASE"
            // Plus a two-line on-screen message (bottom of the screen, NOT chat) for the players who found it.
            for (ServerPlayer p : near) {
                com.example.villagefightsback.network.ModNetwork.sendTo(
                        p, new com.example.villagefightsback.network.VillageDiscoveryPacket(DISCOVERY_HUD_TICKS));
            }
            data.markAnnounced(gp);
        }
    }

    /** Nearest open ground spot within 3 blocks of the village centre, close to the centre's height. */
    /** How far from the village centre a brain node may settle. Kept inside MCA's 8-block town-centre margin. */
    private static final int NODE_SEARCH = 4;
    /** Max height difference from the village centre. Tighter than the old +/-5, which accepted low roofs. */
    private static final int NODE_Y_TOLERANCE = 3;
    /** A support block with air this close beneath it is a roof or platform, not ground. */
    private static final int GROUND_PROBE = 3;
    /** Clear cells needed out of the 8 around the node, so summoned units have somewhere to land. Deliberately
     *  5 rather than all 8: a vanilla bell commonly has support posts on two or three sides, and demanding a
     *  fully open ring would push the node off almost every village centre. */
    private static final int MIN_CLEAR_NEIGHBOURS = 5;
    /** Clear blocks above the node, so it is never buried. Only 1: many village bells sit under a canopy, and
     *  requiring more would relocate the node away from the bell in most vanilla villages. */
    private static final int NODE_HEADROOM = 1;

    /**
     * Is {@code spot} a good home for a brain node? The node has to be reachable and usable, not merely
     * supported, so this checks all three things a node needs:
     * <ul>
     *   <li><b>on real ground</b> -- solid support below, and no air within {@link #GROUND_PROBE} beneath that
     *       support. That last test is what rejects ROOFS: the surface heightmap happily reports the top of a
     *       building, and a node placed there strands every unit it summons;</li>
     *   <li><b>room to summon</b> -- at least {@link #MIN_CLEAR_NEIGHBOURS} of the 8 surrounding cells open with
     *       something solid under them, so spawned villagers/golems have ground to stand on;</li>
     *   <li><b>visible</b> -- {@link #NODE_HEADROOM} clear blocks above, so it is never buried or boxed in.</li>
     * </ul>
     *
     * @param replacingBell true for the bell's own cell, which we intend to overwrite, so its occupancy is fine
     */
    /**
     * Is {@code feet} standing on REAL GROUND rather than on a roof or a platform? A building's roof is solid
     * and has open air above it, so every "is this a valid surface" test passes on it; what gives it away is the
     * hollow underneath. Shared by node placement and by the spawn finders, because the surface heightmap
     * reports the top of a BUILDING for any column that passes through one.
     */
    static boolean onRealGround(ServerLevel level, BlockPos feet) {
        BlockPos support = feet.below();
        if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) return false;
        for (int d = 1; d <= GROUND_PROBE; d++) {                 // a roof/platform has open space under it
            BlockPos under = support.below(d);
            if (!level.isLoaded(under)) break;
            if (!level.getBlockState(under).blocksMotion()) return false;
        }
        return true;
    }

    private static boolean isGoodNodeSpot(ServerLevel level, BlockPos spot, boolean replacingBell) {
        if (!replacingBell && !level.getBlockState(spot).canBeReplaced()) return false;
        if (!onRealGround(level, spot)) return false;

        for (int dy = 1; dy <= NODE_HEADROOM; dy++) {
            if (level.getBlockState(spot.above(dy)).blocksMotion()) return false;
        }

        int clear = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos n = spot.offset(dx, 0, dz);
                if (!level.isLoaded(n)) continue;
                if (level.getBlockState(n).blocksMotion()) continue;            // blocked at node level
                if (level.getBlockState(n.above()).blocksMotion()) continue;    // 1-high crawlspace: nothing can stand
                BlockPos below = n.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue; // nowhere to stand
                clear++;
            }
        }
        return clear >= MIN_CLEAR_NEIGHBOURS;
    }

    /**
     * The best brain-node spot near {@code center} (the village bell): on the surface, close to the centre in
     * both plan and height, and passing {@link #isGoodNodeSpot}. Never returns the bell cell itself, so it is
     * safe to use when MCA's Town Center bell has to survive. Prefers the spot nearest the centre.
     */
    private static BlockPos findBrainNodeSpot(ServerLevel level, BlockPos center) {
        BlockPos best = search(level, center, true);
        // A village centre can legitimately be cramped: a bell boxed in by fences, lanterns and its own posts can
        // fail the "room to summon" ring on every candidate, and returning null there means the village NEVER
        // gets a brain node at all, which is far worse than a tight one. So relax the ring requirement and take
        // any real, open, ground-level spot. The roof and burial tests are NOT relaxed, since landing on a roof
        // strands everything the node summons.
        return best != null ? best : search(level, center, false);
    }

    /** Best spot near {@code center}; {@code roomy} also demands the summoning ring, see {@link #isGoodNodeSpot}. */
    private static BlockPos search(ServerLevel level, BlockPos center, boolean roomy) {
        BlockPos best = null;
        for (int dx = -NODE_SEARCH; dx <= NODE_SEARCH; dx++) {
            for (int dz = -NODE_SEARCH; dz <= NODE_SEARCH; dz++) {
                if (dx == 0 && dz == 0) continue;                 // leave the bell's own cell alone
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(dx, 0, dz));
                if (Math.abs(surface.getY() - center.getY()) > NODE_Y_TOLERANCE) continue; // roofs / cliffs
                boolean ok = roomy ? isGoodNodeSpot(level, surface, false)
                        : (level.getBlockState(surface).canBeReplaced()
                                && onRealGround(level, surface)
                                && !level.getBlockState(surface.above()).blocksMotion());
                if (!ok) continue;
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

        // UNIT SPAWNS fire N times per day at even intervals (N = multiplier_unit_spawn_frequency, rounded, >=1). The
        // period boundaries are offset so that with N=1 the single spawn still lands at sunset (as it always did).
        int spawnsPerDay = Config.spawnsPerDay();
        long interval = Math.max(1L, 24000L / spawnsPerDay);
        long period = Math.floorDiv(dayTime + (24000L - SUNSET_TIME), interval);

        // Every tracked node (golem-factory / barracks / lure-tower) spawns its payload once per period, even
        // when NO brain node is in range (standalone, at stage 1); a node within a village's radius uses that
        // (nearest) village's stage.
        if (lastNodePeriod != period) {
            lastNodePeriod = period;
            spawnAllNodes(server, data, period);
        }
        // Village reinforcements (scaled by stage) also fire once per period, per village.
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            if (lastReinforcePeriod.getOrDefault(gp, Long.MIN_VALUE) == period) continue;
            lastReinforcePeriod.put(gp, period);
            reinforceVillage(level, data.getWallPlan(gp), gp.pos(), data.econ(gp).getLevel());
        }

        // EMERALD HARVEST + the stage-3 one-time expansion wave stay ONCE PER DAY, at sunset.
        if (timeOfDay < SUNSET_TIME) return;
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            BrainNodeData.Econ econ = data.econ(gp);
            if (econ.getLastIncomeDay() == day) continue; // this village already received income today
            econ.setLastIncomeDay(day);

            // Every sunset each village gets an emerald CAT -- a nightly scout that roams out to find prey (cats
            // and mites are the only real explorers). Count = 1 x the brain-spawn mult. Uncapped (a deliberate
            // brain spawn) so it reliably appears even in a crowded area.
            int scouts = Math.max(0, (int) Math.round(1.0 * Config.emeraldBrainSpawnMult()));
            if (Config.mobInfectionEnabled()) {
                for (int i = 0; i < scouts; i++) spawnUncappedScout(level, ModEntities.EMERALD_CAT.get(), emeraldSpawnPos(level, gp.pos(), ModEntities.EMERALD_CAT.get()));
            }

            ExpansionManager.maybeExpand(level, gp, data); // stage-3 one-time expansion wave (no-ops below level 3)

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
    // A brand-new brain node's ONE-TIME scout cats: they appear ~2s after the node is born, just above it.
    // A tiny pending queue, drained each driver pass (onServerTick).
    private static final List<PendingCats> PENDING_CATS = new ArrayList<>();
    private record PendingCats(GlobalPos at, long dueTick, int count) {}

    /** A brain node was just BORN (freshly placed OR a bell that rose into one -- both fire onPlace): queue its
     *  one-time scout cats to materialise ~2s later, just above the node. */
    public static void scheduleBirthScouts(ServerLevel level, BlockPos pos) {
        if (!Config.mobInfectionEnabled()) return;
        int cats = Math.max(0, (int) Math.round(2.0 * Config.emeraldBrainSpawnMult()));
        if (cats > 0) {
            PENDING_CATS.add(new PendingCats(GlobalPos.of(level.dimension(), pos.immutable()),
                    level.getGameTime() + 40L, cats));
        }
    }

    private static void processPendingCats(MinecraftServer server) {
        if (PENDING_CATS.isEmpty()) return;
        java.util.Iterator<PendingCats> it = PENDING_CATS.iterator();
        while (it.hasNext()) {
            PendingCats pc = it.next();
            ServerLevel level = server.getLevel(pc.at().dimension());
            if (level == null) { it.remove(); continue; }
            if (level.getGameTime() < pc.dueTick()) continue;     // not yet -- wait ~2s after the node is born
            it.remove();
            BlockPos pos = pc.at().pos();
            if (!level.isLoaded(pos)) continue;                   // node unloaded -> give up on these cats
            AABB box = new AABB(pos).inflate(INCOME_RADIUS);
            if (level.getEntitiesOfClass(EmeraldCat.class, box, e -> e.isAlive()).size() >= pc.count() * 5) continue;
            for (int i = 0; i < pc.count(); i++) {
                spawnUncappedScout(level, ModEntities.EMERALD_CAT.get(), emeraldSpawnPos(level, pos, ModEntities.EMERALD_CAT.get())); // on the ground below an elevated node
            }
        }
    }

    /** Spawn one emerald scout of {@code type} at {@code at}, bypassing the emerald LAG cap (it is a DELIBERATE
     *  brain spawn -- the flag is set before the join event fires). */
    private static void spawnUncappedScout(ServerLevel level, net.minecraft.world.entity.EntityType<?> type, BlockPos at) {
        if (at == null) return; // nowhere it fits -> skip rather than bury it
        if (!(type.create(level) instanceof net.minecraft.world.entity.Mob mob)) return;
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.getPersistentData().putBoolean(EmeraldPopulation.UNCAPPED_TAG, true);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        level.addFreshEntity(mob);
    }

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
            // villagerType() is called PER villager so a batch comes out mixed-gender under MCA Reborn.
            for (int i = 0; i < villagersToSpawn; i++) {
                spawnDefender(level, pos, com.example.villagefightsback.compat.MCACompat
                        .villagerType(level.getRandom()), outside);
            }
            // (Emerald cats are NOT a sunset reinforcement -- they spawn once when the brain node is BORN, see
            //  scheduleBirthScouts. The recurring sunset emerald scout is the BAT, in handleSunsetIncome.)
        }

        // An MCA human wearing the mca:guard profession when MCA is installed, else the Guard Villagers guard.
        // Resolved per spawn rather than once up front: MCACompat picks a male/female MCA human at random, so
        // a garrison comes out mixed instead of five clones.
        for (int i = 0; i < guardsToSpawn; i++) {
            EntityType<?> guardType = com.example.villagefightsback.compat.MCACompat.guardType(level.getRandom());
            if (guardType == null) break; // no MCA and no Guard Villagers -> nothing to spawn
            com.example.villagefightsback.compat.MCACompat.makeGuard(spawnDefender(level, pos, guardType, outside));
        }
        for (int i = 0; i < golemsToSpawn; i++) spawnDefender(level, pos, EntityType.IRON_GOLEM, outside);
    }

    /** Spawn one entity of {@code type} (it auto-arms on join). When {@code outsideWall} is non-null (stage 4),
     *  spawn just outside that inner wall; otherwise on the surface near {@code center}. */
    private static Entity spawnDefender(ServerLevel level, BlockPos center, EntityType<?> type, WallPlan outsideWall) {
        return spawnDefender(level, center, type, outsideWall, null);
    }

    /**
     * As above, but {@code beforeJoin} runs on the new entity BEFORE it is added to the level.
     *
     * <p>That ordering matters. EntityType.spawn adds the entity itself, so anything stamped on afterwards is
     * too late for the entity-join handler, which is where this mod decides what a creature IS and arms it
     * accordingly. A guard whose profession was set after the fact therefore joined as a CIVILIAN and was given
     * the villager roles -- including the goal that walks a strayed villager home, which then fought MCA's own
     * guard AI for control and left the guard visibly dithering between fleeing and fighting.
     */
    private static Entity spawnDefender(ServerLevel level, BlockPos center, EntityType<?> type,
                                        WallPlan outsideWall, java.util.function.Consumer<Entity> beforeJoin) {
        BlockPos spot = outsideWall != null ? findSpawnSpotOutsideWall(level, outsideWall, type) : null;
        if (spot == null) spot = findSpawnSpot(level, center, type); // no wall / no valid outside spot
        if (spot == null) return null; // nowhere this creature actually fits -> skip; the next pass retries
        Entity e = type.create(level);
        if (e == null) return null;
        e.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (e instanceof net.minecraft.world.entity.Mob mob) {
            // 1.21.1 dropped the trailing CompoundTag argument from finalizeSpawn; nothing else changed.
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
        }
        if (e instanceof Villager v) v.setBaby(false);
        if (beforeJoin != null) beforeJoin.accept(e);   // stamped BEFORE the join handler reads it
        level.addFreshEntity(e);
        return e;
    }

    /** Where a brain-summoned emerald unit should appear. If the brain node is MORE THAN ONE BLOCK above the
     *  nearest solid ground (it floats, or sits on a thin perch), the unit spawns down on that ground so it isn't
     *  stranded on top of the node with no way down; otherwise it uses the normal nearby-surface spot. */
    private static BlockPos emeraldSpawnPos(ServerLevel level, BlockPos node, EntityType<?> type) {
        BlockPos.MutableBlockPos m = node.mutable().move(Direction.DOWN);
        int floor = level.getMinBuildHeight();
        int scanned = 0;
        while (m.getY() > floor && scanned < 160 && !level.getBlockState(m).blocksMotion()) {
            m.move(Direction.DOWN);
            scanned++;
        }
        BlockPos ground = m.above(); // the open cell on top of the first solid block below the node
        if (node.getY() - ground.getY() > 1) {
            return fits(level, ground, type) ? ground : nearestFit(level, ground, type, FIT_SEARCH_RADIUS);
        }
        return findSpawnSpot(level, node, type);
    }

    /** A surface spot near {@code center} where {@code type} genuinely fits: 12 random columns first (the old
     *  scatter, which keeps reinforcements from stacking), then the nearest fitting cell in growing rings so a
     *  big unit lands BESIDE a cramped node rather than in its ceiling. Null when nowhere fits -- the old code
     *  returned an unchecked {@code center.above()} here, which is exactly how summons ended up inside blocks. */
    private static BlockPos findSpawnSpot(ServerLevel level, BlockPos center, EntityType<?> type) {
        for (int i = 0; i < 12; i++) {
            BlockPos col = center.offset(level.getRandom().nextInt(9) - 4, 0, level.getRandom().nextInt(9) - 4);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
            // getHeightmapPos DISCARDS the Y it is given and returns the top of the column, which for a column
            // through a building is the cell above its ROOF. Unguarded, that is how summons ended up standing on
            // the town hall. Two tests fix it: stay near the node's own height, and be on real ground, not a roof.
            if (Math.abs(surface.getY() - center.getY()) > SPAWN_Y_TOLERANCE) continue;
            if (!onRealGround(level, surface)) continue;
            if (fits(level, surface, type)) return surface;
        }
        // nearestFit is already safe here: it walks rings at the node's own Y (and one or two steps down), so it
        // cannot wander onto a roof the way an unbounded heightmap lookup can.
        return nearestFit(level, center, type, FIT_SEARCH_RADIUS);
    }

    /** Pick a ground spot a few blocks OUTSIDE the inner wall, fanning out past a (random) gate so stage-4
     *  reinforcements muster between the inner and outer walls instead of on top of the dome. Null if none found. */
    private static BlockPos findSpawnSpotOutsideWall(ServerLevel level, WallPlan plan, EntityType<?> type) {
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
            if (!onRealGround(level, surface)) continue;                // same heightmap trap: never muster on a roof
            if (fits(level, surface, type)) return surface;
        }
        return null;
    }

    /**
     * Does {@code type}'s FULL hitbox fit with its feet at {@code feet}? Tests the entity's real AABB rather than
     * counting block cells, which is the only way to be right for every creature the village summons: an iron
     * golem is 1.4 wide and 2.7 tall (three cells, and wider than a 1-block shaft), an emerald enderman is 2.9
     * tall, a villager 1.95. The old two-cell test passed a golem into a 2-high pocket and buried its head.
     *
     * <p>No floor requirement -- see {@link #fits} for the surface variant. A creature spawned over air simply
     * falls, which is harmless; being spawned INSIDE blocks is what suffocates it.
     */
    static boolean fitsFreely(ServerLevel level, BlockPos feet, EntityType<?> type) {
        return level.noCollision(type.getAABB(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5));
    }

    /** {@link #fitsFreely} plus a sturdy floor: a spot the creature can actually stand on. */
    static boolean fits(ServerLevel level, BlockPos feet, EntityType<?> type) {
        BlockPos below = feet.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && fitsFreely(level, feet, type);
    }

    /** How far out {@link #nearestFit} will look for somewhere a summoned creature fits. */
    private static final int FIT_SEARCH_RADIUS = 5;

    /** How far above or below the node a heightmap-picked spawn spot may sit. Keeps summons off nearby roofs. */
    private static final int SPAWN_Y_TOLERANCE = 4;

    /**
     * The nearest cell to {@code center} where {@code type} fits, searched as growing square rings so a closer
     * spot always wins. This is what lets a BIG unit appear BESIDE the brain node when the column above it is
     * too tight: a 2.7-tall golem rarely fits directly over a node tucked under a canopy, but almost always fits
     * a block or two to the side. Each ring tries the node's own level first, then a step or two down for
     * sloping ground, then one up.
     *
     * @return null when nothing within {@code max} fits, so the caller can SKIP the spawn instead of choking it
     */
    static BlockPos nearestFit(ServerLevel level, BlockPos center, EntityType<?> type, int max) {
        int[] dys = {0, -1, -2, 1};
        for (int r = 1; r <= max; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // walk the ring shell only
                    for (int dy : dys) {
                        BlockPos p = center.offset(dx, dy, dz);
                        if (!level.isLoaded(p)) continue;
                        if (fits(level, p, type)) return p;
                    }
                }
            }
        }
        return null;
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
        // A node spawns its reinforcement even with NO brain node in range -- standalone, at STAGE 1; when a
        // brain IS within range it spawns at that village's stage instead. (The +10% gem bonus still needs a
        // brain in range; only the unit spawn is standalone.)
        int stage = (brain != null) ? data.econ(brain).getLevel() : 1;
        if (level.getBlockState(nodePos).getBlock() instanceof AbstractNodeBlock node) {
            node.spawnPayload(level, nodePos, stage);
        }
    }

    // Per-node spawn-period stamp so a node spawns its payload at most once per SPAWN PERIOD (dedupes across
    // overlapping village radii). With multiplier_unit_spawn_frequency=1 there is one period per day (at sunset);
    // higher values give more, evenly-spaced periods. Transient: a restart can re-run once -- harmless.
    private static final Map<GlobalPos, Long> nodeSpawnDay = new HashMap<>();

    // The last global node-spawn period, so spawnAllNodes runs at most once per period (invoked every tick).
    private static long lastNodePeriod = Long.MIN_VALUE;

    // Per-brain-node reinforcement-period stamp (reinforcements also fire once per spawn period). Transient.
    private static final Map<GlobalPos, Long> lastReinforcePeriod = new HashMap<>();

    /** Drop a removed node's stamps so the transient maps stay bounded (called from AbstractNodeBlock.onRemove). */
    static void forgetNode(GlobalPos node) { nodeSpawnDay.remove(node); lastReinforcePeriod.remove(node); }

    /** Once per day at sunset, spawn the payload of EVERY tracked node (across dimensions, loaded only).
     *  Standalone-capable: a node within {@value #INCOME_RADIUS} of a brain node spawns at that (nearest)
     *  village's stage; a node with no brain in range still spawns, at STAGE 1. The per-node day-stamp dedupes
     *  so each node spawns at most once per day. */
    private static void spawnAllNodes(MinecraftServer server, BrainNodeData data, long period) {
        for (GlobalPos node : new ArrayList<>(data.nodes())) {
            ServerLevel level = server.getLevel(node.dimension());
            if (level == null || !level.isLoaded(node.pos())) continue; // spawn only where the world is loaded
            if (!(level.getBlockState(node.pos()).getBlock() instanceof AbstractNodeBlock nb)) {
                data.removeNode(node);                                  // the block is gone -> stop tracking it
                nodeSpawnDay.remove(node);                              // ...and forget its stamp
                continue;
            }
            Long last = nodeSpawnDay.get(node);
            if (last != null && last == period) continue;               // already spawned this period
            nodeSpawnDay.put(node, period);
            GlobalPos brain = nearestBrainNode(data, level, node.pos(), INCOME_RADIUS);
            int stage = (brain != null) ? data.econ(brain).getLevel() : 1; // standalone node -> stage-1 units
            nb.spawnPayload(level, node.pos(), stage);
        }
    }

    /** Counts of [villagers, guards, iron golems] alive within {@value #INCOME_RADIUS} of a brainNode (for the
     *  UI). Emeraldgers are villager-based virus warriors, so they are NOT counted as villagers. */
    public static int[] countDefenderTypes(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(INCOME_RADIUS);
        int villagers = 0, guards = 0, golems = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e instanceof EmeraldgerEntity) continue;
            // Guards FIRST: an MCA guard is a Villager too, so the villager branch would swallow it and the
            // report would show a full garrison as "Guards: 0".
            if (Factions.isGuard(e)) guards++;
            else if (e instanceof Villager) villagers++;
            else if (e instanceof net.minecraft.world.entity.animal.IronGolem) golems++;
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
        return e instanceof Villager || GUARD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    /** Broadcast the §5 phase banner for {@code level} to all players. Used for the daily level-up announce and,
     *  from {@link BrainNodeData.Econ#addEmeralds}, to re-show a phase (e.g. SETTLEMENT PHASE) when a node devolves. */
    public static void broadcastPhaseBanner(MinecraftServer server, int level) {
        broadcast(server, phaseBanner(level));
    }

    /** Collection sweep: each brain node vacuums up unpicked emerald item-entities near it (~1s after they land,
     *  so players get first dibs). Efficient by construction -- it piggybacks the throttled {@value #TICK_INTERVAL}
     *  -tick driver and scans only a small {@link #COLLECT_RADIUS} AABB per node (never a full-village sweep). */
    private static void collectLooseEmeralds(MinecraftServer server, BrainNodeData data) {
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atCenterOf(gp.pos());
            double near2 = COLLECT_RADIUS * COLLECT_RADIUS;
            // Scan the brain's whole influence radius. An emerald is absorbed if it is either CLOSE to the node
            // (within COLLECT_RADIUS, the classic vacuum) OR -- at any range in reach -- it has come to rest on one
            // of OUR infection blocks (fertile / rich / infested path): the village reclaims every gem that lands on
            // its own ground and feeds it to the brain.
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(gp.pos()).inflate(INCOME_RADIUS);
            java.util.List<net.minecraft.world.entity.item.ItemEntity> loose = level.getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, box,
                    e -> e.isAlive() && e.getAge() >= COLLECT_MIN_AGE
                            && e.getItem().is(net.minecraft.world.item.Items.EMERALD));
            if (loose.isEmpty()) continue;
            int collected = 0;
            for (net.minecraft.world.entity.item.ItemEntity ie : loose) {
                boolean nearNode = ie.distanceToSqr(center) <= near2;
                boolean onInfection = ie.onGround()
                        && com.example.villagefightsback.infection.BlockMatcher.isInfection(ie.getBlockStateOn());
                if (!nearNode && !onInfection) continue;
                collected += ie.getItem().getCount();
                ie.discard();
            }
            if (collected > 0) {
                data.econ(gp).addEmeralds(collected);
                level.playSound(null, gp.pos(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 1.6F);
            }
        }
    }

    private static final double COLLECT_RADIUS = 32.0; // brain vacuums unpicked emeralds within this of a node
    private static final int COLLECT_MIN_AGE = 20;     // only after ~1s on the floor (players get first dibs)

    /** The short stage-transition banner shown when a brain node reaches {@code level}. All phases use the
     *  same purple as SETTLEMENT PHASE, so a level-up reads as the named phase, not a generic "evolved" line. */
    public static String phaseBanner(int level) {
        return switch (level) {
            case 1 -> "§5SETTLEMENT PHASE";
            case 2 -> "§5FERTILIZATION PHASE";
            case 3 -> "§5EXPANSION PHASE";
            default -> "§5CULMINATION PHASE"; // level 4 (max)
        };
    }
}
