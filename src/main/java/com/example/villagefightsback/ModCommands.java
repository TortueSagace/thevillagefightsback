package com.example.villagefightsback;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * All of the mod's commands, under the {@code /thevillagefightsback} root:
 * <ul>
 *   <li>{@code config <key> [value]} -- read (no value) or set (op only) any of the tunable {@link Config}
 *       options; each key is its own subcommand with tab-completion and type/range-checked values;</li>
 *   <li>{@code brain_stage_get} / {@code brain_stage_set <1-4>} -- read / force the stage of the nearest brain node;</li>
 *   <li>{@code village_status_report} -- the nearest brain node's status report (replaces the old
 *       {@code /village_status_report}; see {@link VillageStatusCommand});</li>
 *   <li>{@code tp_to_nearest_node} -- teleport just above the nearest brain node.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModCommands {
    private ModCommands() {}

    private static final int OP = 2; // permission level for the mutating/cheaty subcommands

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("thevillagefightsback");

        // --- config <key> [value] ---
        LiteralArgumentBuilder<CommandSourceStack> config = Commands.literal("config")
                .executes(c -> { listConfig(c.getSource()); return 1; });
        for (var entry : Config.INDEX.entrySet()) {
            config.then(buildKey(entry.getKey(), entry.getValue()));
        }
        // config reset_default_configs -- restore EVERY option to its default value (op only).
        config.then(Commands.literal("reset_default_configs").requires(s -> s.hasPermission(OP))
                .executes(c -> resetConfigs(c.getSource())));
        root.then(config);

        // --- brain_stage_get / brain_stage_set <1-4> ---
        root.then(Commands.literal("brain_stage_get").executes(c -> brainStageGet(c.getSource())));
        root.then(Commands.literal("brain_stage_set").requires(s -> s.hasPermission(OP))
                .then(Commands.argument("stage", IntegerArgumentType.integer(1, BrainNodeData.MAX_LEVEL))
                        .executes(c -> brainStageSet(c.getSource(), IntegerArgumentType.getInteger(c, "stage")))));

        // --- village_status_report (replaces the old /village_status_report) ---
        root.then(Commands.literal("village_status_report").executes(c -> VillageStatusCommand.report(c.getSource())));

        // --- tp_to_nearest_node ---
        root.then(Commands.literal("tp_to_nearest_node").requires(s -> s.hasPermission(OP))
                .executes(c -> tpToNearestNode(c.getSource())));

        // --- clear_forced_chunks: release every force-loaded chunk in the current dimension. Cleans up chunks
        //     orphaned by air strikes that were interrupted before they could free their force-load (those kept
        //     villages/guards ticking forever). NOTE: also clears any chunks you set with vanilla /forceload. ---
        root.then(Commands.literal("clear_forced_chunks").requires(s -> s.hasPermission(OP))
                .executes(c -> clearForcedChunks(c.getSource())));

        event.getDispatcher().register(root);
    }

    // --- config command building -------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildKey(String key, ForgeConfigSpec.ConfigValue<?> value) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(key)
                .executes(c -> getConfig(c.getSource(), key, value));
        if (value instanceof ForgeConfigSpec.BooleanValue bv) {
            node.then(Commands.argument("value", BoolArgumentType.bool()).requires(s -> s.hasPermission(OP))
                    .executes(c -> setConfig(c.getSource(), key, bv, BoolArgumentType.getBool(c, "value"))));
        } else if (value instanceof ForgeConfigSpec.IntValue iv) {
            node.then(Commands.argument("value", intArgFor(key)).requires(s -> s.hasPermission(OP))
                    .executes(c -> setConfig(c.getSource(), key, iv, IntegerArgumentType.getInteger(c, "value"))));
        } else if (value instanceof ForgeConfigSpec.DoubleValue dv) {
            node.then(Commands.argument("value", DoubleArgumentType.doubleArg(Config.MULT_MIN, Config.MULT_MAX))
                    .requires(s -> s.hasPermission(OP))
                    .executes(c -> setConfig(c.getSource(), key, dv, DoubleArgumentType.getDouble(c, "value"))));
        }
        return node;
    }

    /** Per-key integer bounds so the command validates input (matches the defineInRange spec). */
    private static IntegerArgumentType intArgFor(String key) {
        return switch (key) {
            case "air_strikes_appear_at_stage" -> IntegerArgumentType.integer(1, 4);
            case "guards_spawning_with_general_darius" -> IntegerArgumentType.integer(0, 64);
            case "unstable_limbo_gravel_HP_threshold" -> IntegerArgumentType.integer(1, 100000);
            default -> IntegerArgumentType.integer();
        };
    }

    private static int getConfig(CommandSourceStack src, String key, ForgeConfigSpec.ConfigValue<?> value) {
        src.sendSuccess(() -> Component.literal("§6" + key + " §7= §e" + value.get()), false);
        return 1;
    }

    private static <T> int setConfig(CommandSourceStack src, String key, ForgeConfigSpec.ConfigValue<T> value, T newValue) {
        value.set(newValue);
        try { Config.SPEC.save(); } catch (Exception ignored) {} // persist to the .toml if it is loaded
        src.sendSuccess(() -> Component.literal("§a" + key + " §7set to §e" + newValue), true);
        return 1;
    }

    private static void listConfig(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("§6=== The Village Fights Back -- config ==="), false);
        Config.INDEX.forEach((k, v) ->
                src.sendSuccess(() -> Component.literal("§7" + k + " §8= §f" + v.get()), false));
        src.sendSuccess(() -> Component.literal("§8Set with /thevillagefightsback config <key> <value>"), false);
        src.sendSuccess(() -> Component.literal("§8Reset all with /thevillagefightsback config reset_default_configs"), false);
    }

    /** Restore EVERY tunable option to its default value (the value passed to define(...) when the spec was built). */
    private static int resetConfigs(CommandSourceStack src) {
        int n = 0;
        for (ForgeConfigSpec.ConfigValue<?> v : Config.INDEX.values()) { resetOne(v); n++; }
        try { Config.SPEC.save(); } catch (Exception ignored) {} // persist to the .toml if it is loaded
        final int count = n;
        src.sendSuccess(() -> Component.literal("§aReset §e" + count
                + "§a config option(s) to their default values."), true);
        return 1;
    }

    private static <T> void resetOne(ForgeConfigSpec.ConfigValue<T> v) {
        v.set(v.getDefault()); // ConfigValue.getDefault() returns the value passed to define(...)
    }

    // --- brain stage + teleport --------------------------------------------------------------------

    private static GlobalPos nearestBrain(ServerPlayer player) {
        BrainNodeData data = BrainNodeData.get(player.getServer());
        BlockPos here = player.blockPosition();
        GlobalPos best = null;
        double bestSqr = Double.MAX_VALUE;
        for (GlobalPos gp : data.blocks()) {
            if (!gp.dimension().equals(player.level().dimension())) continue;
            double d = gp.pos().distSqr(here);
            if (d < bestSqr) { bestSqr = d; best = gp; }
        }
        return best;
    }

    private static int brainStageGet(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        GlobalPos brain = nearestBrain(player);
        if (brain == null) { src.sendFailure(Component.literal("No Village Brain Node found in this dimension.")); return 0; }
        int stage = BrainNodeData.get(player.getServer()).econ(brain).getLevel();
        src.sendSuccess(() -> Component.literal("§7Nearest brain node stage: §e" + stage + " §7/ §e" + BrainNodeData.MAX_LEVEL), false);
        return 1;
    }

    private static int brainStageSet(CommandSourceStack src, int stage) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        GlobalPos brain = nearestBrain(player);
        if (brain == null) { src.sendFailure(Component.literal("No Village Brain Node found in this dimension.")); return 0; }
        BrainNodeData.get(player.getServer()).econ(brain).setLevel(stage);
        src.sendSuccess(() -> Component.literal("§aNearest brain node stage set to §e" + stage), true);
        return 1;
    }

    private static int clearForcedChunks(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerLevel level = src.getPlayerOrException().serverLevel();
        java.util.List<Long> forced = new java.util.ArrayList<>(level.getForcedChunks());
        for (long key : forced) {
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(key);
            level.setChunkForced(cp.x, cp.z, false);
        }
        int count = forced.size();
        src.sendSuccess(() -> Component.literal("§aReleased §e" + count
                + "§a force-loaded chunk(s) in this dimension."), true);
        return 1;
    }

    private static int tpToNearestNode(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        GlobalPos brain = nearestBrain(player);
        if (brain == null) { src.sendFailure(Component.literal("No Village Brain Node found in this dimension.")); return 0; }
        BlockPos p = brain.pos();
        player.teleportTo(player.serverLevel(), p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, player.getYRot(), player.getXRot());
        src.sendSuccess(() -> Component.literal("§aTeleported above the nearest brain node at §e"
                + p.getX() + ", " + (p.getY() + 1) + ", " + p.getZ()), false);
        return 1;
    }
}
