package com.example.villagergunners;

import com.example.villagergunners.wall.WallPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Efficient, event-driven wall repair. When a wall block is broken -- by a player ({@link BlockEvent.BreakEvent})
 * or an explosion ({@link ExplosionEvent.Detonate}) -- the exact position is looked up against nearby
 * gravemind wall plans (O(1) hash lookup) and reopened, so the existing wall builders rebuild just that
 * block. There is NO periodic scanning of the wall: work happens only on an actual break, and only for
 * graveminds within wall range of the break.
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WallRepairEvents {
    private WallRepairEvents() {}

    // The wall never extends past the survey radius (~80) from its gravemind; skip graveminds farther than this.
    private static final double MAX_WALL_DIST_SQR = 96.0 * 96.0;

    // Amortized catch-all scan: covers breakage from ANY source (mobs/aliens that bypass break events).
    private static final int SCAN_INTERVAL = 20;  // run the scan ~once per second
    private static final int SCAN_WINDOW = 48;    // blocks verified per gravemind per scan tick (round-robin)

    /**
     * Source-agnostic safety net: a small, round-robin slice of each loaded gravemind's completed wall is
     * verified every {@value #SCAN_INTERVAL} ticks. Cost is O({@value #SCAN_WINDOW}) per gravemind per
     * second regardless of wall size, so even mob/alien destruction that fires no block-break event is
     * detected and repaired within a few seconds. The break/explosion events above remain the instant path.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % SCAN_INTERVAL != 0) return;
        GravemindData data = GravemindData.get(server);
        for (GlobalPos gp : data.blocks()) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            WallPlan plan = data.getWallPlan(gp);
            if (plan != null && plan.scanForDamage(level, SCAN_WINDOW)) {
                data.setDirty();
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            reopenIfWall(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        for (BlockPos pos : event.getAffectedBlocks()) {
            reopenIfWall(level, pos);
        }
    }

    /** If {@code pos} belongs to a nearby gravemind's wall, reopen that block for the builders to rebuild. */
    private static void reopenIfWall(ServerLevel level, BlockPos pos) {
        GravemindData data = GravemindData.get(level.getServer());
        for (GlobalPos gp : data.blocks()) {
            if (!gp.dimension().equals(level.dimension())) continue;
            if (gp.pos().distSqr(pos) > MAX_WALL_DIST_SQR) continue; // bound to nearby graveminds (cheap)
            WallPlan plan = data.getWallPlan(gp);
            if (plan != null && plan.reopenAt(pos)) {
                data.setDirty();
                return; // a position belongs to at most one wall
            }
        }
    }
}
