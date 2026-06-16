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
    private static final double RADIUS = 100.0;  // the brain node's influence radius (== INCOME_RADIUS)

    private static final long WIN_10M = 12000L;  // 10 in-game minutes (ticks)
    private static final long WIN_5M  = 6000L;
    private static final long WIN_5S  = 100L;
    private static final long CD_5M   = 6000L;
    private static final long CD_10M  = 12000L;
    private static final long CD_30M  = 36000L;
    private static final int  BED_CAP = 100;     // abilities that grow the village switch off above this many beds

    /** Per-brain-node reflex bookkeeping. Transient (rebuilt from events; reset on world unload). */
    private static final class State {
        final ArrayDeque<Long> villagerKills = new ArrayDeque<>(); // game-times, pruned to the last 10 min
        final ArrayDeque<Long> guardKills = new ArrayDeque<>();
        long lastVillagerKill = 0L;          // 0 = none yet (game-time is monotonic from world start)
        boolean pendingVillagerKill;         // a villager died since the last tick -> try the guard reflex
        boolean pendingGolemKill;            // a golem died since the last tick -> try the lure-tower reflex
        long lastInnerGateBreak = Long.MIN_VALUE; // game-time a wall fake-cobblestone was destroyed
        long lastOuterGateBreak = Long.MIN_VALUE; // game-time an outer-wall fake-obsidian was destroyed
        boolean firstHurtArmed;              // a villager was hurt this stage and the golem-factory reflex is armed
        int firstHurtFiredStage;             // the stage at which the golem-factory reflex last fired (0 = never)
        long cdPeacetime, cdGuard, cdGolem, cdBarracks, cdLure; // game-time each cooldown expires
        final ArrayDeque<String> log = new ArrayDeque<>();      // last 10 reflex lines, newest last
    }

    private static final Map<GlobalPos, State> STATES = new HashMap<>();
    private static State state(GlobalPos node) { return STATES.computeIfAbsent(node, k -> new State()); }

    /** The brain node's last (up to 10) reflex log lines, oldest first, for the UI Logs tab. */
    public static List<String> logFor(GlobalPos node) {
        State s = STATES.get(node);
        return s == null ? List.of() : new ArrayList<>(s.log);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) { STATES.clear(); }

    // --- event recording (cheap: only stamps timers/flags; the tick does the work) --------------------

    @SubscribeEvent
    public static void onVillagerHurt(LivingHurtEvent event) {
        LivingEntity v = event.getEntity();
        if (!(v instanceof Villager) || v instanceof EmeraldgerEntity) return;
        if (!(v.level() instanceof ServerLevel sl)) return;
        BrainNodeData data = BrainNodeData.get(sl.getServer());
        GlobalPos node = BrainNodeEvents.nearestBrainNode(data, sl, v.blockPosition(), RADIUS);
        if (node == null) return;
        State s = state(node);
        // Arm the golem-factory reflex on the FIRST villager hurt of the current stage (re-arms each stage-up).
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

        // A0 (all stages): first villager hurt this stage -> a settler raises a Golem Factory (yellow TNT). 10 gems.
        if (s.firstHurtArmed && econ.spendEmeralds(10)) {
            ExpansionManager.summonReflexSettler(level, gp, inner, ExpansionManager.TNT_YELLOW);
            s.firstHurtArmed = false;
            s.firstHurtFiredStage = stage;
            log(s, level, "Settler sent: Golem Factory (-10)");
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
                s.cdGuard = now + CD_10M;
                log(s, level, "Guard raised (-3)");
            }
        }

        // A3 (stage 1+): 5+ villagers killed in the past 10 min -> forge an iron golem. 9 gems, 5-min cooldown.
        if (stage >= 1 && s.villagerKills.size() >= 5 && now >= s.cdGolem && econ.spendEmeralds(9)) {
            spawnInside(level, brain, EntityType.IRON_GOLEM);
            s.cdGolem = now + CD_5M;
            log(s, level, "Iron golem forged (-9)");
        }

        // A6 (stage 3+): a golem was killed -> a settler builds a Lure Tower (purple TNT). 12 gems, 30-min cd, off at 100+ beds.
        if (stage >= 3 && s.pendingGolemKill) {
            s.pendingGolemKill = false;
            if (now >= s.cdLure && beds < BED_CAP && econ.spendEmeralds(12)) {
                ExpansionManager.summonReflexSettler(level, gp, inner, ExpansionManager.TNT_PURPLE);
                s.cdLure = now + CD_30M;
                log(s, level, "Settler sent: Lure Tower (-12)");
            }
        }

        // A5 (stage 2+): 3+ guards killed in the past 10 min -> a settler builds a Barracks (orange TNT). 12 gems, 30-min cd, off at 100+ beds.
        if (stage >= 2 && s.guardKills.size() >= 3 && now >= s.cdBarracks && beds < BED_CAP && econ.spendEmeralds(12)) {
            ExpansionManager.summonReflexSettler(level, gp, inner, ExpansionManager.TNT_ORANGE);
            s.cdBarracks = now + CD_30M; // rolling-window + 30-min cooldown (kills age out of the 10-min window)
            log(s, level, "Settler sent: Barracks (-12)");
        }

        // A1 (stage 1+): peace (no villager killed in 10 min) -> +1 villager inside + a settler founds a house
        //                (blue TNT) outside. 5 gems, 10-min cooldown, off at 100+ beds.
        if (stage >= 1 && now >= s.cdPeacetime && beds < BED_CAP && (now - s.lastVillagerKill) > WIN_10M
                && econ.spendEmeralds(5)) {
            spawnInside(level, brain, EntityType.VILLAGER);
            ExpansionManager.summonReflexSettler(level, gp, inner, ExpansionManager.TNT_BLUE);
            s.cdPeacetime = now + CD_10M;
            log(s, level, "Villager + house settler (-5)");
        }
    }

    private static void prune(State s, long now) {
        while (!s.villagerKills.isEmpty() && now - s.villagerKills.peekFirst() > WIN_10M) s.villagerKills.removeFirst();
        while (!s.guardKills.isEmpty() && now - s.guardKills.peekFirst() > WIN_10M) s.guardKills.removeFirst();
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
