package com.example.villagefightsback;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.example.villagefightsback.goal.ExpansionSettlerGoal;
import com.example.villagefightsback.wall.WallBlock;
import com.example.villagefightsback.wall.WallPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * STAGE-3 "EXPANSION": when a village's brainNode first reaches level 3, it dispatches a wave of settlers
 * that stream out beyond the wall and found a ring of new buildings around it. The settler count N scales
 * with the wall's size (minimum 6). Each settler walks out, finds a clear spot that won't wreck the wall or
 * another in-progress build, and -- on a STAGGERED timer, so they react to each other's claims -- drops a
 * Blue TNT (60%, a 7x7 house) or a Green TNT (40%, a 15x15 tower); then it reverts to an ordinary villager.
 * If a settler dies before it places, the TNT goes down at its death location, wherever that is.
 *
 * Runs once per brainNode (guarded by {@link BrainNodeData#isExpanded}). The actual walk/seek/place logic
 * lives in {@link ExpansionSettlerGoal}; this class handles the trigger, spawning, build-spot claims, and
 * the death fallback.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExpansionManager {
    private ExpansionManager() {}

    /** Marks a villager as an unplaced expansion settler. Removed once it drops its TNT (or on the death fallback). */
    public static final String NBT_EXPANSION = "vgun_expansion";
    /** Gems the brain node paid for this settler, so a settler that cannot build can hand them back. 0 = free. */
    public static final String NBT_COST = "vgun_settler_cost";
    public static final String NBT_TNT = "vgun_exp_tnt";   // 0 = blue (house), 1 = green (tower)
    public static final String NBT_BRAIN_NODE = "vgun_exp_gm"; // packed brainNode pos, for door facing

    private static final int MIN_BUILDINGS = 6;
    private static final int MAX_BUILDINGS = 12;            // safety cap so a huge wall can't spawn a swarm
    private static final double BUILDING_SPACING = 16.0;    // outer-ring length budgeted per building
    private static final double BUILD_RING = 11.0;          // build centre this far beyond the wall radius
    private static final double DEFAULT_RADIUS = 24.0;      // when no wall plan exists yet
    private static final double SPAWN_OFFSET = 4.0;         // settlers appear just outside the wall
    private static final int SPAWN_SEARCH = 6;
    private static final float BLUE_CHANCE = 0.60F;
    private static final int STAGGER_BASE = 15;             // min placement delay (ticks)
    private static final int STAGGER_RAND = 90;             // + up to this, per settler, so placements spread out

    /** Active build-spot claims: packed centre pos -> game-time the claim expires. Static, pruned on use. */
    private static final Map<Long, Long> CLAIMS = new HashMap<>();
    private static final long CLAIM_TTL = 6000L;            // ~5 minutes, long enough for the wave to finish

    // --- trigger -------------------------------------------------------------------------------------
    /** Called once per loaded village at sunset. Fires the expansion the first time the village is level 3+. */
    public static void maybeExpand(ServerLevel level, GlobalPos gp, BrainNodeData data) {
        if (data.econ(gp).getLevel() < 3) return;
        if (data.isExpanded(gp)) return;
        data.markExpanded(gp); // mark first, so a partial failure never re-triggers the wave
        expand(level, gp, data);
    }

    private static void expand(ServerLevel level, GlobalPos gp, BrainNodeData data) {
        BlockPos gm = gp.pos();
        WallPlan plan = data.getWallPlan(gp);
        double wallRadius = wallRadius(plan, gm);
        // multiplier_number_of_settlers scales the wave size; the ring divides by the actual count so the
        // expansion settlers stay evenly spread around it (the claims/stagger system still prevents overlap).
        int n = Math.max(1, (int) Math.round(estimateBuildings(wallRadius) * Config.settlerCountMult()));

        int spawned = 0;
        for (int k = 0; k < n; k++) {
            double angle = (2.0 * Math.PI * k) / n + (level.random.nextDouble() - 0.5) * (Math.PI / n); // spread + jitter
            BlockPos aim = columnAt(gm, angle, wallRadius + SPAWN_OFFSET);
            BlockPos spot = surfaceStandable(level, aim, SPAWN_SEARCH);
            if (spot == null) continue;
            int color = level.random.nextFloat() < BLUE_CHANCE ? 0 : 1;
            int delay = STAGGER_BASE + level.random.nextInt(STAGGER_RAND);
            spawnSettler(level, spot, gm, angle, wallRadius, color, delay);
            spawned++;
        }
    }

    /** Buildings the outer ring can hold, from the wall's size; minimum 6, capped for safety. */
    private static int estimateBuildings(double wallRadius) {
        double ringCircumference = 2.0 * Math.PI * (wallRadius + BUILD_RING);
        int n = (int) Math.round(ringCircumference / BUILDING_SPACING);
        return Math.max(MIN_BUILDINGS, Math.min(MAX_BUILDINGS, n));
    }

    /** The wall's outer radius: the farthest (horizontal) a wall block sits from the brain node. */
    private static double wallRadius(WallPlan plan, BlockPos gm) {
        if (plan == null) return DEFAULT_RADIUS;
        double max = 0;
        for (WallBlock wb : plan.blocks()) {
            double dx = wb.pos().getX() - gm.getX();
            double dz = wb.pos().getZ() - gm.getZ();
            max = Math.max(max, Math.sqrt(dx * dx + dz * dz));
        }
        return max > 1.0 ? max : DEFAULT_RADIUS;
    }

    // --- spawning ------------------------------------------------------------------------------------
    private static void spawnSettler(ServerLevel level, BlockPos spot, BlockPos gm,
                                     double angle, double wallRadius, int color, int delay) {
        spawnSettler(level, spot, gm, angle, wallRadius, color, delay, 0);
    }

    private static void spawnSettler(ServerLevel level, BlockPos spot, BlockPos gm,
                                     double angle, double wallRadius, int color, int delay, int cost) {
        // A house settler summoned by a stage-2+ node carries the grand-house charge instead. Done here, at the
        // one point every summoning route passes through, so the reflexes and the expansion wave both inherit it.
        if (color == TNT_BLUE) color = houseTnt(level, gm);
        // An MCA human when MCA Reborn is installed, else a vanilla villager (see MCACompat).
        Villager v = com.example.villagefightsback.compat.MCACompat.createVillager(level);
        if (v == null) return;
        v.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        v.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.MOB_SUMMONED, null, null);
        v.setBaby(false);
        v.getPersistentData().putBoolean(NBT_EXPANSION, true);
        v.getPersistentData().putInt(NBT_TNT, color);
        v.getPersistentData().putLong(NBT_BRAIN_NODE, gm.asLong());
        if (cost > 0) v.getPersistentData().putInt(NBT_COST, cost);
        com.example.villagefightsback.compat.VillagerRoleTags.mirror(v); // survive an MCA replacement we did not cause
        level.addFreshEntity(v); // onEntityJoin arms it (nerfed) + adds the dormant villager goals
        v.goalSelector.addGoal(1, new ExpansionSettlerGoal(v, gm, angle, wallRadius, color, delay));
    }

    // --- TNT placement (shared by the goal and the death fallback) -----------------------------------
    // Settler TNT colours: 0 blue (house), 1 green (tower), 2 yellow (golem factory), 3 orange (barracks),
    // 4 purple (lure tower). 2-4 are the node-structure TNTs the brain's reflexes dispatch.
    public static final int TNT_BLUE = 0, TNT_GREEN = 1, TNT_YELLOW = 2, TNT_ORANGE = 3, TNT_PURPLE = 4;
    /** 5 cyan: the grand house. Blue's stage-2+ replacement, never chosen directly -- see {@link #houseTnt}. */
    public static final int TNT_CYAN = 5;
    /** 6 black: General Darius's statue. Dispatched once, by his twentieth kill. */
    public static final int TNT_BLACK = 6;

    /**
     * Which house charge a settler working for {@code brainNode} should carry: Cyan once that node has reached
     * stage 2, Blue below it.
     *
     * <p>The village stops building small the moment it starts fertilizing, so the grand houses are what a
     * mature village raises from then on. Keyed on the SUMMONING node rather than on the settler's route, so
     * every path that hands out a house charge -- the expansion wave, the brain's reflexes, the founding flow
     * and the organic settler -- agrees without each having to know the rule.
     */
    public static int houseTnt(ServerLevel level, BlockPos brainNode) {
        BrainNodeData data = BrainNodeData.get(level.getServer());
        GlobalPos gp = GlobalPos.of(level.dimension(), brainNode);
        return data.econ(gp).getLevel() >= 2 ? TNT_CYAN : TNT_BLUE;
    }

    /** The house charge for {@code brainNode} as a placeable state, for the two goals that bypass placeTnt. */
    public static BlockState houseTntState(ServerLevel level, BlockPos brainNode, Direction facing) {
        Block b = houseTnt(level, brainNode) == TNT_CYAN ? ModBlocks.CYAN_TNT.get() : ModBlocks.BLUE_TNT.get();
        return b.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    /**
     * Clear ground a settler carrying {@code color} must find before it drops its charge.
     *
     * <p>A grand house is up to {@value com.example.villagefightsback.HousePlacer#BIG_HOUSE_MAX_SIDE} blocks
     * across against the small houses' seven, so it needs proportionally more room or it would be refused the
     * moment it detonated.
     */
    public static int clearanceFor(int color) {
        // The statue is 18 blocks across and 36 tall, so it needs more room than any house.
        if (color == TNT_BLACK) return 12;
        return color == TNT_CYAN ? CyanTntBlock.CLEARANCE : 8;
    }

    /** Place the settler's TNT at {@code pos}: Blue (house) faces the brain node; the rest have no facing. */
    public static void placeTnt(ServerLevel level, BlockPos pos, int color, BlockPos brainNode) {
        BlockState tnt = switch (color) {
            case TNT_GREEN  -> ModBlocks.GREEN_TNT.get().defaultBlockState();
            case TNT_CYAN   -> {
                Direction face = Direction.getNearest(brainNode.getX() - pos.getX(), 0, brainNode.getZ() - pos.getZ());
                if (face.getAxis().isVertical()) face = Direction.NORTH;
                yield ModBlocks.CYAN_TNT.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, face);
            }
            case TNT_YELLOW -> ModBlocks.YELLOW_TNT.get().defaultBlockState();
            case TNT_ORANGE -> ModBlocks.ORANGE_TNT.get().defaultBlockState();
            case TNT_PURPLE -> ModBlocks.PURPLE_TNT.get().defaultBlockState();
            case TNT_BLACK  -> ModBlocks.BLACK_TNT.get().defaultBlockState();
            default -> {
                Direction face = Direction.getNearest(brainNode.getX() - pos.getX(), 0, brainNode.getZ() - pos.getZ());
                if (face.getAxis().isVertical()) face = Direction.NORTH;
                yield ModBlocks.BLUE_TNT.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, face);
            }
        };
        level.setBlock(pos, tnt, 3); // detonates on placement -> builds the structure
    }

    /** A brain-node reflex dispatches ONE settler that walks out past the wall and founds the structure for
     *  {@code tntColor} (golem factory / barracks / lure tower / house). Reuses the full expansion machinery
     *  (clear-spot seeking, no bulldozing, death fallback). No-op if no spot just outside the wall is found. */
    /** The free form: the stage-3 wave and mega-expansion, which cost the node nothing. */
    public static void summonReflexSettler(ServerLevel level, GlobalPos brainNode, WallPlan plan, int tntColor) {
        summonReflexSettler(level, brainNode, plan, tntColor, 0);
    }

    /** {@code cost} gems were debited for this settler; it hands them back if it never manages to build. */
    public static void summonReflexSettler(ServerLevel level, GlobalPos brainNode, WallPlan plan, int tntColor,
                                           int cost) {
        BlockPos gm = brainNode.pos();
        double wallRadius = wallRadius(plan, gm);
        double angle = level.random.nextDouble() * 2.0 * Math.PI;
        BlockPos aim = columnAt(gm, angle, wallRadius + SPAWN_OFFSET);
        BlockPos spot = surfaceStandable(level, aim, SPAWN_SEARCH);
        if (spot == null) return;
        int delay = STAGGER_BASE + level.random.nextInt(STAGGER_RAND);
        spawnSettler(level, spot, gm, angle, wallRadius, tntColor, delay, cost);
    }

    /**
     * Would a building centred here fit without eating one that already exists?
     *
     * <p>{@link #isClear} answers a different and much weaker question: it consults a static, transient map of
     * THIS WAVE's own pending claims, which is empty after a restart and has never known anything about the
     * houses already standing in the world. This asks the durable record instead.
     *
     * <p>{@code radius} is the half-extent to reserve. 8 covers the largest template (a 15x15 tower) without
     * threading the real template size through every settler goal, none of which knows it.
     */
    public static boolean footprintFree(ServerLevel level, BlockPos centre, int radius) {
        int minX = centre.getX() - radius, minY = centre.getY() - 2, minZ = centre.getZ() - radius;
        int maxX = centre.getX() + radius, maxY = centre.getY() + 12, maxZ = centre.getZ() + radius;
        if (com.example.villagefightsback.infection.StructureZones.get(level)
                .intersectsForeign(minX, minY, minZ, maxX, maxY, maxZ)) return false;
        // A player's "Do not build here" sign. Checked here because all four settler routes funnel through
        // this one method, so a claim cannot be missed by whichever goal happened to pick the spot.
        return !com.example.villagefightsback.infection.MarkerZones.get(level)
                .blocksBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Hand a dead or dismissed settler's gems back to the node that paid for them.
     *
     * <p>Modelled on {@code GeneralDariusEntity.refundAndDespawn}: check the node is still there before
     * crediting it, so a refund to a village that has since been destroyed is dropped rather than
     * materialising an economy for a node that no longer exists.
     *
     * <p>Founding and stage-3 settlers are FREE and store no cost, so this is a no-op for them -- which is
     * deliberate. They are not despawned either: a settler that cannot find a spot simply goes back to being
     * an ordinary villager. Deleting one would be population loss in the phase where the village can least
     * afford it, and there is nothing to refund to justify it.
     */
    static void refund(ServerLevel level, Villager v, BlockPos brainNode, String reason) {
        int cost = v.getPersistentData().getInt(NBT_COST);
        if (cost <= 0) return;
        v.getPersistentData().remove(NBT_COST);
        GlobalPos gp = GlobalPos.of(level.dimension(), brainNode);
        BrainNodeData data = BrainNodeData.get(level.getServer());
        if (!data.blocks().contains(gp)) return;   // the village it belonged to is gone
        data.econ(gp).addEmeralds(cost);
        com.example.villagefightsback.BrainReflexes.logExternal(level, brainNode, reason + " (+" + cost + ")");
    }

    // --- build-spot claims (so settlers don't drop overlapping buildings) ----------------------------
    /** True if {@code centre} is at least {@code minSpacing} (horizontally) from every active claim. */
    public static boolean isClear(ServerLevel level, BlockPos centre, double minSpacing) {
        long now = level.getGameTime();
        double s2 = minSpacing * minSpacing;
        boolean clear = true;
        Iterator<Map.Entry<Long, Long>> it = CLAIMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> e = it.next();
            if (e.getValue() < now) { it.remove(); continue; }
            BlockPos c = BlockPos.of(e.getKey());
            double dx = c.getX() - centre.getX(), dz = c.getZ() - centre.getZ();
            if (dx * dx + dz * dz < s2) clear = false;
        }
        return clear;
    }

    public static void claim(ServerLevel level, BlockPos centre) {
        CLAIMS.put(centre.asLong(), level.getGameTime() + CLAIM_TTL);
    }

    /** True if any unplaced expansion settler (still carrying {@link #NBT_EXPANSION}) is alive within
     *  {@code radius} of {@code centre} -- i.e. not every expansion building has been dropped yet. */
    public static boolean hasActiveSettlersNear(ServerLevel level, BlockPos centre, double radius) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(centre).inflate(radius);
        return !level.getEntitiesOfClass(Villager.class, box,
                v -> v.isAlive() && v.getPersistentData().getBoolean(NBT_EXPANSION)).isEmpty();
    }

    // --- death fallback: an unplaced settler that dies drops its TNT where it falls ------------------
    @SubscribeEvent
    public static void onSettlerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager v)) return;
        if (!v.getPersistentData().getBoolean(NBT_EXPANSION)) return; // already placed / not a settler
        if (!(v.level() instanceof ServerLevel level)) return;
        int color = v.getPersistentData().getInt(NBT_TNT);
        BlockPos gm = BlockPos.of(v.getPersistentData().getLong(NBT_BRAIN_NODE));
        v.getPersistentData().remove(NBT_EXPANSION); // consume so it can't fire twice

        // ONLY WHERE IT WOULD HAVE BEEN LEGAL ANYWAY. This used to drop the building wherever the settler
        // happened to fall, which is the worst case in the whole feature: a settler killed inside its own
        // village raised a house on top of the one it was standing next to, and the build carved that one to
        // air. A death is not a permit. If the spot is not free, the gems go back instead.
        if (footprintFree(level, v.blockPosition(), clearanceFor(color))) {
            placeTnt(level, v.blockPosition(), color, gm);
            return;
        }
        refund(level, v, gm, "Settler lost, no room to build");
    }

    // --- helpers -------------------------------------------------------------------------------------
    private static BlockPos columnAt(BlockPos gm, double angle, double radius) {
        return new BlockPos(
                gm.getX() + (int) Math.round(Math.cos(angle) * radius),
                gm.getY(),
                gm.getZ() + (int) Math.round(Math.sin(angle) * radius));
    }

    /** The surface around column {@code aim} (searching outward up to {@code r}) where a villager can stand. */
    public static BlockPos surfaceStandable(ServerLevel level, BlockPos aim, int r) {
        for (int ring = 0; ring <= r; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue; // only the new ring shell
                    BlockPos col = new BlockPos(aim.getX() + dx, aim.getY(), aim.getZ() + dz);
                    BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
                    if (standable(level, surface)) return surface;
                }
            }
        }
        return null;
    }

    /** A 1x1x2 air pocket with a solid, non-fluid floor -- room for a villager / a TNT base. */
    private static boolean standable(ServerLevel level, BlockPos p) {
        if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
        if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) return false;
        BlockPos below = p.below();
        BlockState b = level.getBlockState(below);
        return !b.getFluidState().isEmpty() ? false : b.isFaceSturdy(level, below, Direction.UP)
                && b.getBlock() != Blocks.BEDROCK;
    }

    private static void broadcast(ServerLevel level, String msg) {
        if (level.getServer() != null) {
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
        }
    }
}
