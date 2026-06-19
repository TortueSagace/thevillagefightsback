package com.example.villagefightsback;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The status report for the Village Brain Node nearest the player -- its evolution stage, the emeralds still
 * needed to reach the next stage, and how many villagers, guards and iron golems are currently associated with
 * it (within its income/defender radius). Exposed as {@code /thevillagefightsback village_status_report}
 * ({@link ModCommands}); this class also runs the 10s node-highlight glow.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillageStatusCommand {
    private VillageStatusCommand() {}

    public static int report(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BrainNodeData data = BrainNodeData.get(level.getServer());

        // The "current" brain node = the nearest one in this dimension.
        BlockPos here = player.blockPosition();
        GlobalPos nearest = null;
        double bestSqr = Double.MAX_VALUE;
        for (GlobalPos gp : data.blocks()) {
            if (!gp.dimension().equals(level.dimension())) continue;
            double d = gp.pos().distSqr(here);
            if (d < bestSqr) { bestSqr = d; nearest = gp; }
        }
        if (nearest == null) {
            src.sendFailure(Component.literal("No Village Brain Node found in this dimension."));
            return 0;
        }

        final BlockPos pos = nearest.pos();
        requestGlow(level, pos); // highlight the node with a bright glow for 10s so the player can find it
        final BrainNodeData.Econ econ = data.econ(nearest);
        final int[] def = BrainNodeEvents.countDefenderTypes(level, pos); // [villagers, guards, golems] in radius
        final int dist = (int) Math.round(Math.sqrt(bestSqr));

        src.sendSuccess(() -> Component.literal("§6=== Village Status Report ==="), false);
        src.sendSuccess(() -> Component.literal("§7Brain Node at §f"
                + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + " §7(§a" + dist + "§7 blocks away)"), false);
        src.sendSuccess(() -> Component.literal("§7Stage: §e" + econ.getLevel()
                + " §7/ §e" + BrainNodeData.MAX_LEVEL), false);
        if (econ.isMaxLevel()) {
            src.sendSuccess(() -> Component.literal("§7Emeralds: §aMax stage reached §7("
                    + econ.getEmeralds() + " stored)"), false);
        } else {
            final int remaining = Math.max(0, econ.costForNext() - econ.getEmeralds());
            src.sendSuccess(() -> Component.literal("§7Emeralds to next stage: §a" + remaining
                    + " §7(" + econ.getEmeralds() + "§7/§a" + econ.costForNext() + "§7)"), false);
        }
        src.sendSuccess(() -> Component.literal("§7Villagers: §a" + def[0]
                + "   §7Guards: §a" + def[1] + "   §7Golems: §a" + def[2]), false);
        return 1;
    }

    // --- "glow" the reported node for 10 seconds -------------------------------------------------
    private static final int GLOW_TICKS = 200; // 10 seconds

    /** An active node highlight: bright particles around {@code pos} until {@code endGameTime}. */
    private record Glow(ServerLevel level, BlockPos pos, long endGameTime) {}

    private static final List<Glow> GLOWS = new ArrayList<>();

    private static void requestGlow(ServerLevel level, BlockPos pos) {
        // Replace any existing glow on the same node so a re-run just refreshes the 10s timer.
        GLOWS.removeIf(g -> g.level == level && g.pos.equals(pos));
        GLOWS.add(new Glow(level, pos, level.getGameTime() + GLOW_TICKS));
    }

    /** Each tick, emit a bright fullbright aura around every actively-glowing node; drop expired ones. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || GLOWS.isEmpty()) return;
        GLOWS.removeIf(g -> {
            if (g.level.getGameTime() >= g.endGameTime) return true;
            if (g.level.isLoaded(g.pos) && (g.level.getGameTime() & 1L) == 0L) emitGlow(g.level, g.pos);
            return false;
        });
    }

    /** Force a small cloud of END_ROD (fullbright) + GLOW particles around the node, visible from far away. */
    private static void emitGlow(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        for (ServerPlayer p : level.players()) {
            level.sendParticles(p, ParticleTypes.END_ROD, true, cx, cy, cz, 6, 0.45, 0.5, 0.45, 0.015);
            level.sendParticles(p, ParticleTypes.GLOW, true, cx, cy, cz, 4, 0.5, 0.55, 0.5, 0.0);
        }
    }
}
