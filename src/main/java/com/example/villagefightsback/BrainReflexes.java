package com.example.villagefightsback;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.villagefightsback.wall.WallBlock;
import com.example.villagefightsback.wall.WallPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Village Brain Node's emerald-funded "immune reflexes": reactive abilities the brain pays for out of its
 * accumulated gems ({@link BrainNodeData.Econ#spendEmeralds}) when the village comes under stress. Forge
 * events ({@link LivingHurtEvent}/{@link LivingDeathEvent}/{@link BlockEvent.BreakEvent}) only RECORD what
 * happened near each brain node; {@link #tick} (driven from {@link BrainNodeEvents}'s 40-tick loop) evaluates
 * every ability, spends the gems, performs the action, and appends a line to that brain's reflex log (shown
 * in the UI's Logs tab via {@link #logFor}). Spawned reinforcements never land on the stage-4 glass dome:
 * inside-wall spawns search around the brain node's own Y (under the dome) and settlers walk out past the wall.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BrainReflexes {
    private BrainReflexes() {}

    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");
    static double RADIUS = 100.0;  // brain influence radius (set from Config.apply(); base 100, == INCOME_RADIUS)
    private static final double WITHER_RADIUS = 200.0; // detect a wither storm to summon Darius within this

    private static final long WIN_10M = 12000L;  // 10 in-game minutes (ticks)
    private static final long WIN_5M  = 6000L;
    private static final long WIN_3M  = 3600L;   // 3 in-game minutes (Darius dangerous-player + threat window)
    private static final long WIN_5S  = 100L;
    private static final long CD_1M   = 1200L;   // 1 in-game minute (fire-extinguish + mega-expansion cooldowns)
    private static final long CD_5M   = 6000L;
    private static final long CD_10M  = 12000L;
    private static final long CD_30M  = 36000L;

    /** Scale a base cooldown by multiplier_brain_initiatives_cooldown (>1 = rarer initiatives). */
    private static long cd(long base) { return (long) (base * Config.brainInitiativeCooldownMult()); }

    /** Vanilla fire blocks (normal + soul). The mod adds no fire blocks of its own; other mods' fire is ignored. */
    private static final java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> IS_FIRE =
            s -> s.is(net.minecraft.world.level.block.Blocks.FIRE) || s.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE);
    private static final int  BED_CAP = 100;     // abilities that grow the village switch off above this many beds

    /** Per-brain-node reflex bookkeeping. Transient (rebuilt from events; reset on world unload). */
    private static final class State {
        final ArrayDeque<Long> villagerKills = new ArrayDeque<>(); // game-times, pruned to the last 10 min
        final ArrayDeque<Long> guardKills = new ArrayDeque<>();
        // Per-player recent villager/guard kill times (game-times, pruned to the last 3 min), keyed by attacker
        // UUID -- used to detect a "dangerous player" for the General Darius spawn reflex.
        final Map<java.util.UUID, ArrayDeque<Long>> playerKills = new HashMap<>();
        long cdDarius;                       // game-time the Darius reflex cooldown expires (anti-spam)
        long lastVillagerKill = 0L;          // 0 = none yet (game-time is monotonic from world start)
        boolean pendingVillagerKill;         // a villager died since the last tick -> try the guard reflex
        boolean pendingGolemKill;            // a golem died since the last tick -> try the lure-tower reflex
        long lastInnerGateBreak = Long.MIN_VALUE; // game-time a wall fake-cobblestone was destroyed
        long lastOuterGateBreak = Long.MIN_VALUE; // game-time an outer-wall fake-obsidian was destroyed
        boolean firstHurtArmed;              // a villager was hurt this stage and the golem-factory reflex is armed
        int firstHurtFiredStage;             // the stage at which the golem-factory reflex last fired (0 = never)
        long cdPeacetime, cdGuard, cdGolem, cdBarracks, cdLure; // game-time each cooldown expires
        long cdFireExtinguish, cdMegaExpansion;                 // game-time the fire-douse / mega-expansion cooldowns expire
        final ArrayDeque<String> log = new ArrayDeque<>();      // last 10 reflex lines, newest last
    }

    private static final Map<GlobalPos, State> STATES = new HashMap<>();
    private static State state(GlobalPos node) { return STATES.computeIfAbsent(node, k -> new State()); }

    /** The brain node's last (up to 10) reflex log lines, oldest first, for the UI Logs tab. */
    public static List<String> logFor(GlobalPos node) {
        State s = STATES.get(node);
        return s == null ? List.of() : new ArrayList<>(s.log);
    }

    /** Append a line to the reflex log of the brain node nearest {@code pos} (for systems outside this class,
     *  e.g. the stage-4 air strike). No-op if there is no brain node within range. */
    public static void logExternal(ServerLevel level, BlockPos pos, String message) {
        BrainNodeData data = BrainNodeData.get(level.getServer());
        GlobalPos node = BrainNodeEvents.nearestBrainNode(data, level, pos, RADIUS);
        if (node == null) return;
        log(state(node), level, message);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) { STATES.clear(); }

    // --- event recording (cheap: only stamps timers/flags; the tick does the work) --------------------

    @SubscribeEvent
    public static void onVillagerHurt(LivingHurtEvent event) {
        LivingEntity v = event.getEntity();
        if (!(v instanceof Villager) || v instanceof EmeraldgerEntity) return;
        if (!(v.level() instanceof ServerLevel sl)) return;
        // Only a REAL attack arms the golem-factory reflex: the damage must come from an attacker entity
        // (a hostile mob or a player, directly or via a projectile). Wall suffocation, fire, and fall damage
        // are sourceless (no attacker entity), so they do NOT count as an attack.
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;
        BrainNodeData data = BrainNodeData.get(sl.getServer());
        GlobalPos node = BrainNodeEvents.nearestBrainNode(data, sl, v.blockPosition(), RADIUS);
        if (node == null) return;
        State s = state(node);
        // Arm the golem-factory reflex on the FIRST real attack on a villager this stage (re-arms each stage-up).
        if (s.firstHurtFiredStage < data.econ(node).getLevel()) s.firstHurtArmed = true;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity v = event.getEntity();
        if (!(v.level() instanceof ServerLevel sl)) return;
        boolean villager = v instanceof Villager && !(v instanceof EmeraldgerEntity);
        boolean golem = v instanceof IronGolem;
        boolean guard = GUARD_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(v.getType()));
        if (!villager && !golem && !guard) return;
        BrainNodeData data = BrainNodeData.get(sl.getServer());
        GlobalPos node = BrainNodeEvents.nearestBrainNode(data, sl, v.blockPosition(), RADIUS);
        if (node == null) return;
        State s = state(node);
        long now = sl.getGameTime();
        if (villager) { s.villagerKills.addLast(now); s.lastVillagerKill = now; s.pendingVillagerKill = true; }
        else if (guard) { s.guardKills.addLast(now); }
        else { s.pendingGolemKill = true; }

        // Per-player kill tracking for the Darius "dangerous player" check: a villager OR guard slain by a
        // player (directly or via a projectile) stamps that player's recent-kill deque (pruned to 3 min).
        if (villager || guard) {
            net.minecraft.world.entity.Entity attacker = event.getSource().getEntity();
            if (attacker instanceof net.minecraft.world.entity.player.Player p && !p.isCreative()) {
                ArrayDeque<Long> dq = s.playerKills.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
                dq.addLast(now);
            }
        }
    }

    /** True if {@code player} has killed at least {@value GeneralDariusEntity#DANGEROUS_PLAYER_KILLS} villagers
     *  or guards near {@code node} within the last 3 in-game minutes. Public so the Darius targeting brain can
     *  re-confirm a "dangerous player" lock. {@code node} may be null (then it scans no state -> not dangerous). */
    public static boolean isDangerousPlayer(ServerLevel level, net.minecraft.world.entity.player.Player player,
                                            GlobalPos node) {
        if (node == null || player.isCreative() || player.isSpectator()) return false;
        State s = STATES.get(node);
        if (s == null) return false;
        ArrayDeque<Long> dq = s.playerKills.get(player.getUUID());
        if (dq == null) return false;
        long now = level.getGameTime();
        int recent = 0;
        for (Long t : dq) if (now - t <= WIN_3M) recent++;
        return recent >= GeneralDariusEntity.DANGEROUS_PLAYER_KILLS;
    }

    @SubscribeEvent
    public static void onWallBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        var block = event.getState().getBlock();
        boolean obsidian = block instanceof FakeObsidianBlock;                 // outer wall gate
        boolean cobble = !obsidian && block instanceof FakeCobblestoneBlock;   // inner wall gate
        if (!obsidian && !cobble) return;
        BrainNodeData data = BrainNodeData.get(sl.getServer());
        BlockPos pos = event.getPos();
        GlobalPos node = BrainNodeEvents.nearestBrainNode(data, sl, pos, RADIUS);
        if (node == null) return;
        long now = sl.getGameTime();
        if (obsidian) {
            WallPlan outer = data.getOuterWallPlan(node);
            if (belongsTo(outer, pos, WallBlock.BlockType.FAKE_OBSIDIAN)) state(node).lastOuterGateBreak = now;
        } else {
            WallPlan inner = data.getWallPlan(node);
            if (belongsTo(inner, pos, WallBlock.BlockType.FAKE_COBBLE)) state(node).lastInnerGateBreak = now;
        }
    }

    private static boolean belongsTo(WallPlan plan, BlockPos pos, WallBlock.BlockType type) {
        if (plan == null) return false;
        WallBlock wb = plan.blockAt(pos);
        return wb != null && wb.type() == type;
    }

    // --- the driver: evaluate every reflex for every loaded brain node (every 40 ticks) ---------------

    public static void tick(MinecraftServer server, BrainNodeData data) {
        STATES.keySet().retainAll(data.blocks()); // forget reflex state for brain nodes that no longer exist
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            evaluate(level, data, gp, state(gp), data.econ(gp), data.econ(gp).getLevel(), level.getGameTime());
        }
    }

    private static void evaluate(ServerLevel level, BrainNodeData data, GlobalPos gp, State s,
                                 BrainNodeData.Econ econ, int stage, long now) {
        prune(s, now);
        BlockPos brain = gp.pos();
        WallPlan inner = data.getWallPlan(gp);
        long beds = level.getPoiManager().getCountInRange(h -> h.is(PoiTypes.HOME), brain, (int) RADIUS, PoiManager.Occupancy.ANY);

        // A-FIRE (all stages): on a 1-minute cooldown, douse ALL fire within the brain's radius -- free (no gem
        // cost) and not logged. Only runs when fire is actually present, so the cooldown resets only after a real
        // douse. Vanilla fire + soul fire only (the mod adds none of its own; other mods' custom fire is untouched).
        if (Config.brainExtinguishesFire() && now >= s.cdFireExtinguish) {
            int doused = extinguishFire(level, brain, (int) RADIUS);
            if (doused > 0) {
                s.cdFireExtinguish = now + CD_1M;
            }
        }

        // MEGA-EXPANSION (all stages, opt-in): every minute send a settler to place a random colored TNT block.
        // The normal settler placement rules (far enough from existing buildings, etc.) still apply.
        if (Config.megaExpansionMode() && now >= s.cdMegaExpansion && inner != null) {
            ExpansionManager.summonReflexSettler(level, gp, inner, level.random.nextInt(5)); // TNT_BLUE..TNT_PURPLE
            s.cdMegaExpansion = now + CD_1M;
            log(s, level, "Mega-expansion settler sent");
        }

        // A0 (all stages): once a villager has been ATTACKED this stage, raising a Golem Factory (yellow TNT,
        // 10 gems) is the TOP priority. Buy it the instant the treasury can afford it (once per stage); until
        // then, RESERVE the gems -- return early so the cheaper reflexes below cannot drain the treasury under
        // its cost. We never give up while armed; we just keep waiting for the gems.
        if (s.firstHurtArmed) {
            if (econ.spendEmeralds(10)) {
                ExpansionManager.summonReflexSettler(level, gp, inner, ExpansionManager.TNT_YELLOW);
                s.firstHurtArmed = false;
                s.firstHurtFiredStage = stage;
                log(s, level, "Settler sent: Golem Factory (-10)");
            } else {
                return; // can't afford the factory yet -> reserve the treasury for it and retry next tick
            }
        }

        // A4 (stage 1+): an inner-wall door broke in the past 5s -> regenerate all inner doors. 1 gem.
        if (stage >= 1 && now - s.lastInnerGateBreak <= WIN_5S && inner != null) {
            s.lastInnerGateBreak = regenDoors(level, inner, WallBlock.BlockType.FAKE_COBBLE, econ, 1, s, "door") ? Long.MIN_VALUE : s.lastInnerGateBreak;
        }

        // A7 (stage 3+): an outer-wall door broke in the past 5s -> regenerate all outer obsidian doors. 10 gems.
        if (stage >= 3 && now - s.lastOuterGateBreak <= WIN_5S) {
            WallPlan outer = data.getOuterWallPlan(gp);
            if (outer != null) {
                s.lastOuterGateBreak = regenDoors(level, outer, WallBlock.BlockType.FAKE_OBSIDIAN, econ, 10, s, "outer door") ? Long.MIN_VALUE : s.lastOuterGateBreak;
            }
        }

        // A2 (stage 1+): a villager was killed -> raise a guard. 3 gems, 10-min cooldown.
        if (stage >= 1 && s.pendingVillagerKill) {
            s.pendingVillagerKill = false;
            if (now >= s.cdGuard && econ.spendEmeralds(3)) {
                spawnInside(level, brain, guardType());
                s.cdGuard = now + cd(CD_10M);
                log(s, level, "Guard raised (-3)");
            }
        }

        // A3 (stage 1+): 5+ villagers killed in the past 10 min -> forge an iron golem. 9 gems, 5-min cooldown.
        if (stage >= 1 && s.villagerKills.size() >= 5 && now >= s.cdGolem && econ.spendEmeralds(9)) {
            spawnInside(level, brain, EntityType.IRON_GOLEM);
            s.cdGolem = now + cd(CD_5M);
            log(s, level, "Iron golem forged (-9)");
        }

        // A6 (stage 3+): a golem was killed -> a settler builds a Lure Tower (purple TNT). 12 gems, 30-min cd, off at 100+ beds.
        if (stage >= 3 && s.pendingGolemKill) {
            s.pendingGolemKill = false;
            if (now >= s.cdLure && beds < BED_CAP && econ.spendEmeralds(12)) {
                ExpansionManager.summonReflexSettler(level, gp, inner, ExpansionManager.TNT_PURPLE);
                s.cdLure = now + cd(CD_30M);
                log(s, level, "Settler sent: Lure Tower (-12)");
            }
        }

        // A5 (stage 2+): 3+ guards killed in the past 10 min -> a settler builds a Barracks (orange TNT). 12 gems, 30-min cd, off at 100+ beds.
        if (stage >= 2 && s.guardKills.size() >= 3 && now >= s.cdBarracks && beds < BED_CAP && econ.spendEmeralds(12)) {
            ExpansionManager.summonReflexSettler(level, gp, inner, ExpansionManager.TNT_ORANGE);
            s.cdBarracks = now + cd(CD_30M); // rolling-window + 30-min cooldown (kills age out of the 10-min window)
            log(s, level, "Settler sent: Barracks (-12)");
        }

        // A8 (stage 4 ONLY): General Darius. Normally he marches only when a HEAVY threat is present -- an enemy
        // with >= 100 max HP, OR a "dangerous" player (>= 3 villager/guard kills in the last 3 min). SPECIAL CASE:
        // when a WITHER STORM is detected (hitbox-independent, so it counts even with its hitbox off), Darius does
        // NOT wait for any condition -- he marches the instant the treasury holds 64 gems at stage 4. Costs 64
        // gems (refunded when he returns home); a short cooldown stops re-spawn spam if he is killed.
        boolean witherStorm = Factions.findNearestWitherStorm(level,
                net.minecraft.world.phys.Vec3.atCenterOf(brain), WITHER_RADIUS) != null;
        if (stage >= 4 && now >= s.cdDarius && econ.getEmeralds() >= 64 && noDariusNear(level, brain)
                && (witherStorm || heavyThreatNear(level, brain, gp))) {
            if (econ.spendEmeralds(64)) {
                spawnDarius(level, gp, brain);
                s.cdDarius = now + cd(WIN_5M); // brief anti-spam cooldown
                log(s, level, "General Darius marches (-64)");
            }
        }

        // A1 (stage 1+): peace (no villager killed in 10 min) -> +1 villager inside + a settler founds a house
        //                (blue TNT) outside. 5 gems, 10-min cooldown, off at 100+ beds.
        if (stage >= 1 && now >= s.cdPeacetime && beds < BED_CAP && (now - s.lastVillagerKill) > WIN_10M
                && econ.spendEmeralds(5)) {
            spawnInside(level, brain, EntityType.VILLAGER);
            ExpansionManager.summonReflexSettler(level, gp, inner, ExpansionManager.TNT_BLUE);
            s.cdPeacetime = now + cd(CD_10M);
            log(s, level, "Villager + house settler (-5)");
        }
    }

    /** Douse all vanilla fire within {@code radius} (horizontal) and a +/-32 Y band of the brain. Whole chunk
     *  sections whose palette holds no fire are skipped via a cheap palette pre-filter, so when nothing is
     *  burning this costs only a handful of section checks. Returns how many fire blocks were removed. */
    private static int extinguishFire(ServerLevel level, BlockPos brain, int radius) {
        int yLo = Math.max(level.getMinBuildHeight(), brain.getY() - 32);
        int yHi = Math.min(level.getMaxBuildHeight() - 1, brain.getY() + 32);
        int secLo = level.getSectionIndex(yLo), secHi = level.getSectionIndex(yHi);
        int xMin = brain.getX() - radius, xMax = brain.getX() + radius;
        int zMin = brain.getZ() - radius, zMax = brain.getZ() + radius;
        long r2 = (long) radius * radius;
        int removed = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int cx = xMin >> 4; cx <= (xMax >> 4); cx++) {
            for (int cz = zMin >> 4; cz <= (zMax >> 4); cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue; // unloaded -> skip (never force-load)
                boolean maybe = false;
                for (int idx = secLo; idx <= secHi && !maybe; idx++) {
                    net.minecraft.world.level.chunk.LevelChunkSection sec = chunk.getSection(idx);
                    if (!sec.hasOnlyAir() && sec.maybeHas(IS_FIRE)) maybe = true;
                }
                if (!maybe) continue;
                int bx0 = Math.max(xMin, cx << 4), bx1 = Math.min(xMax, (cx << 4) + 15);
                int bz0 = Math.max(zMin, cz << 4), bz1 = Math.min(zMax, (cz << 4) + 15);
                for (int x = bx0; x <= bx1; x++) {
                    for (int z = bz0; z <= bz1; z++) {
                        long dx = x - brain.getX(), dz = z - brain.getZ();
                        if (dx * dx + dz * dz > r2) continue;
                        for (int y = yLo; y <= yHi; y++) {
                            if (IS_FIRE.test(level.getBlockState(m.set(x, y, z)))) {
                                level.removeBlock(m, false);
                                removed++;
                            }
                        }
                    }
                }
            }
        }
        return removed;
    }

    private static void prune(State s, long now) {
        while (!s.villagerKills.isEmpty() && now - s.villagerKills.peekFirst() > WIN_10M) s.villagerKills.removeFirst();
        while (!s.guardKills.isEmpty() && now - s.guardKills.peekFirst() > WIN_10M) s.guardKills.removeFirst();
        // Prune per-player kill deques to the 3-min window; drop now-empty players so the map stays bounded.
        s.playerKills.values().forEach(dq -> {
            while (!dq.isEmpty() && now - dq.peekFirst() > WIN_3M) dq.removeFirst();
        });
        s.playerKills.values().removeIf(ArrayDeque::isEmpty);
    }

    // --- actions --------------------------------------------------------------------------------------

    /** Re-place every MISSING door ({@code type}) of {@code plan}, charging {@code cost} gems only if at least
     *  one was actually missing (the auto-repair may already have fixed it). Returns true if the trigger is
     *  handled (regenerated, or nothing to do) -> caller clears it; false if doors are missing but unaffordable
     *  -> caller keeps the trigger to retry within the 5s window. */
    private static boolean regenDoors(ServerLevel level, WallPlan plan, WallBlock.BlockType type,
                                      BrainNodeData.Econ econ, int cost, State s, String label) {
        List<WallBlock> missing = new ArrayList<>();
        for (WallBlock wb : plan.blocks()) {
            if (wb.type() != type) continue;
            if (!level.getBlockState(wb.pos()).is(wb.toState().getBlock())) missing.add(wb);
        }
        if (missing.isEmpty()) return true;            // already repaired elsewhere -> consume the trigger
        if (!econ.spendEmeralds(cost)) return false;   // can't afford yet -> keep the trigger, retry next tick
        for (WallBlock wb : missing) level.setBlock(wb.pos(), wb.toState(), 3);
        log(s, level, "Resealed " + missing.size() + " " + label + "(s) (-" + cost + ")");
        return true;
    }

    /** Spawn one entity just inside the wall near the brain node, at its OWN Y level so it never lands on the
     *  stage-4 dome roof. It auto-arms (and stage-adapts) through the normal entity-join handler. */
    private static void spawnInside(ServerLevel level, BlockPos brain, EntityType<?> type) {
        if (type == null) return;
        BlockPos spot = findInsideSpot(level, brain);
        Entity e = type.spawn(level, spot, MobSpawnType.EVENT);
        if (e instanceof Villager v) v.setBaby(false);
    }

    private static EntityType<?> guardType() { return ForgeRegistries.ENTITY_TYPES.getValue(GUARD_ID); }

    // --- General Darius (stage-4 reflex) -------------------------------------------------------------

    /** True if no General Darius is already serving this brain (at most one per brain, within its RADIUS). */
    private static boolean noDariusNear(ServerLevel level, BlockPos brain) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(brain).inflate(RADIUS);
        return level.getEntitiesOfClass(GeneralDariusEntity.class, box).isEmpty();
    }

    /** True if a heavy threat is within the brain's RADIUS: an enemy with >= 100 max HP, or a dangerous player. */
    private static boolean heavyThreatNear(ServerLevel level, BlockPos brain, GlobalPos node) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(brain).inflate(RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (Factions.isInfected(e)) continue;
            if (e instanceof net.minecraft.world.entity.player.Player p) {
                if (isDangerousPlayer(level, p, node)) return true;
            } else if (e instanceof net.minecraft.world.entity.monster.Enemy && e.getMaxHealth() >= 100.0) {
                return true;
            }
        }
        return false;
    }

    /** Spawn General Darius just inside the wall near the brain node and record his home brain (for refund). */
    private static void spawnDarius(ServerLevel level, GlobalPos node, BlockPos brain) {
        GeneralDariusEntity d = ModEntities.GENERAL_DARIUS.get().create(level);
        if (d == null) return;
        BlockPos spot = findInsideSpot(level, brain);
        d.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        d.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.MOB_SUMMONED, null, null);
        d.setHomeBrain(node);
        level.addFreshEntity(d);
        d.enrollEscort(level, Config.guardsWithGeneralDarius()); // guards_spawning_with_general_darius (default 0)
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, 30, 0.5, 0.8, 0.5, 0.02);
        level.playSound(null, spot, net.minecraft.sounds.SoundEvents.SCULK_CATALYST_BLOOM,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.6F);
    }

    /** A standable spot near the brain node, searched around its OWN Y (so it's under the dome, not on it). */
    private static BlockPos findInsideSpot(ServerLevel level, BlockPos brain) {
        for (int i = 0; i < 32; i++) {
            int dx = level.random.nextInt(13) - 6, dz = level.random.nextInt(13) - 6;
            if (dx == 0 && dz == 0) continue;
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos p = brain.offset(dx, dy, dz);
                if (standableAt(level, p)) return p;
            }
        }
        return brain.above();
    }

    private static boolean standableAt(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
    }

    private static void log(State s, ServerLevel level, String msg) {
        long day = level.getServer().overworld().getDayTime() / 24000L;
        s.log.addLast("Day " + day + ": " + msg);
        while (s.log.size() > 10) s.log.removeFirst();
    }
}
