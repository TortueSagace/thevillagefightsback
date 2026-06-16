package com.example.villagefightsback;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /village_status_report}: prints, in chat, the status of the Village Brain Node nearest the player --
 * its evolution stage, the emeralds still needed to reach the next stage, and how many villagers, guards and
 * iron golems are currently associated with it (within its income/defender radius).
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillageStatusCommand {
    private VillageStatusCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // No permission gate: any player can check the status of their own village.
        event.getDispatcher().register(Commands.literal("village_status_report")
                .executes(c -> report(c.getSource())));
    }

    private static int report(CommandSourceStack src) throws CommandSyntaxException {
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
}
