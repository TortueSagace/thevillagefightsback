package com.example.villagefightsback;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Everything the village notices you doing, and what it costs or earns.
 *
 * <p>The whole scale lives here as constants rather than scattered through the systems that fire them, so the
 * question "what is a villager's life worth to this village" has one answer in one place. Every one of them
 * ends at {@link ReputationData#add}, which is the only thing that may change the number.
 *
 * <p>Note what is NOT here: nothing checks whether a brain node is nearby. The village faction's opinion of
 * you is not a local matter -- trade in a hamlet a thousand blocks from the nearest node and the news still
 * travels. Only the two rules that are about village PROPERTY (mining its metal, robbing its chests) ask
 * where you are, because those are about a specific village's things.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ReputationEvents {
    private ReputationEvents() {}

    /** A completed trade. Small and repeatable: the ordinary way to be worth something to a village. */
    public static final int TRADE = 2;
    /** Killing something the village would have had to fight itself. */
    public static final int THREAT_KILL = 5;
    /** Striking any village member: villager, guard, golem, wall defender, Darius. */
    public static final int HIT_ALLY = -20;
    /** ON TOP of the blow that did it. A hit is an offence; a death is the loss of somebody. */
    public static final int KILL_VILLAGER = -60;
    /** Per stack taken from a village chest without the standing to be trusted with it. */
    public static final int STEAL_STACK = -10;
    /** Per iron/gold/diamond/emerald block mined inside a village without Hero V. */
    public static final int MINE_BLOCK = -50;
    /** Breaking a village chest open rather than taking from it. */
    public static final int BREAK_CHEST = -50;
    /** Swinging at the brain node. Once a day, and once is enough to make you its nightmare. */
    public static final int NODE_MINING_PENALTY = -500;
    /** Swinging at a golem factory, barracks or lure tower: village property, not its heart. */
    public static final int NODE_BLOCK_PENALTY = -50;

    /** Hero level that may take from village chests freely. */
    public static final int CHEST_HERO_LEVEL = 1;
    /** Hero level that may mine the village's metal freely. */
    public static final int MINING_HERO_LEVEL = 5;

    /** The storage blocks a village leaves lying around, and which are far too good to be free on day one. */
    private static boolean isProtectedBlock(BlockState state) {
        return state.is(Blocks.IRON_BLOCK) || state.is(Blocks.GOLD_BLOCK)
                || state.is(Blocks.DIAMOND_BLOCK) || state.is(Blocks.EMERALD_BLOCK);
    }

    /** This player's Hero level right now, 0..5. */
    public static int heroLevel(ServerPlayer player) {
        ReputationData rep = ReputationData.get(player.serverLevel().getServer());
        return ReputationData.heroLevel(rep.get(player.getUUID()));
    }

    // --- earning -------------------------------------------------------------------------------------

    /** Every completed trade is worth {@value #TRADE}. Fires server-side only, once per transaction. */
    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ReputationData.get(p.serverLevel().getServer()).add(p, TRADE, "traded");
        }
    }

    /**
     * Killing something the village would have fought earns {@value #THREAT_KILL}; killing one of its own
     * costs {@value #KILL_VILLAGER} on top of the blow that landed.
     *
     * <p>{@code Factions.isVillageThreat} is the same predicate the village's own defenders hunt by, so the
     * rule reads exactly as it sounds: kill what the village would have killed. It covers modded mobs for
     * free, since it tests {@code Enemy} rather than a list of names -- which is the point when this mod is
     * played alongside an infection mod whose roster it has never heard of.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        LivingEntity dead = event.getEntity();
        if (dead instanceof net.minecraft.world.entity.player.Player) return;
        ReputationData rep = ReputationData.get(killer.serverLevel().getServer());

        if (VillageEvents.isVillageAlly(dead) && !Factions.isInfected(dead)) {
            if (dead instanceof Villager || Factions.isGuard(dead)) {
                rep.add(killer, KILL_VILLAGER, "killed a villager");
            }
            return;   // a golem or wall defender costs only the blow; it is machinery, not somebody
        }
        if (Factions.isVillageThreat(dead)) {
            rep.add(killer, THREAT_KILL, "killed " + shortName(dead));
        }
    }

    /** A short entity name for the log. The panel is 252px wide and wraps, so long names cost a whole row. */
    static String shortName(LivingEntity e) {
        String n = e.getType().getDescription().getString();
        return n.length() > 14 ? n.substring(0, 14) : n;
    }

    // --- village property ----------------------------------------------------------------------------

    /**
     * Warn before the swing lands, so the price is never a surprise.
     *
     * <p>On the action bar rather than in chat: a held left-click fires this every tick, and the action bar
     * overwrites itself where chat would scroll twenty lines a second past everything else being said.
     */
    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (p.getAbilities().instabuild) return;
        BlockState state = level.getBlockState(event.getPos());
        if (!isProtectedBlock(state)) return;
        if (!VillageChestProtection.withinBrainNodeRange(level, event.getPos())) return;
        if (heroLevel(p) >= MINING_HERO_LEVEL) return;
        p.displayClientMessage(net.minecraft.network.chat.Component
                .literal("You are not allowed to mine that before reaching Hero of the Village V!")
                .withStyle(net.minecraft.ChatFormatting.RED), true);
    }

    /**
     * The charge lands when the block does, not when the swing starts.
     *
     * <p>Splitting it that way is the whole reason the warning above is separate: you are told on the first
     * swing and charged only if you go through with it, so nobody loses fifty points to a misclick.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (p.getAbilities().instabuild) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        if (isProtectedBlock(state) && VillageChestProtection.withinBrainNodeRange(level, pos)
                && heroLevel(p) < MINING_HERO_LEVEL) {
            ReputationData.get(level.getServer()).add(p, MINE_BLOCK, "mined village metal");
            return;
        }
        // Breaking a chest open is the cruder way to rob one, and is priced like the metal rather than like a
        // single stack -- otherwise smashing a chest would be cheaper than emptying it.
        if (VillageChestProtection.isProtectedContainer(level, pos) && heroLevel(p) < CHEST_HERO_LEVEL) {
            ReputationData.get(level.getServer()).add(p, BREAK_CHEST, "broke a village chest");
        }
    }

    /** Per-player day of the last brain-node swing charge, so a held click is one offence and not a thousand. */
    private static final Map<UUID, Long> lastNodeSwingDay = new HashMap<>();

    /** Swinging at the brain node: {@value #NODE_MINING_PENALTY}, at most once per day. */
    public static void onMinedBrainNode(ServerPlayer player) {
        if (player.getAbilities().instabuild) return;
        long day = player.serverLevel().getServer().overworld().getDayTime() / 24000L;
        Long last = lastNodeSwingDay.get(player.getUUID());
        if (last != null && last == day) return;
        lastNodeSwingDay.put(player.getUUID(), day);
        ReputationData.get(player.serverLevel().getServer())
                .add(player, NODE_MINING_PENALTY, "struck the brain node");
    }

    /** Swinging at a golem factory / barracks / lure tower: village property, priced like its metal. */
    public static void onMinedVillageNode(ServerPlayer player) {
        if (player.getAbilities().instabuild) return;
        ReputationData.get(player.serverLevel().getServer())
                .add(player, NODE_BLOCK_PENALTY, "struck a village node");
    }

    // --- forgiveness ---------------------------------------------------------------------------------

    /**
     * Dying clears a debt but never a good name.
     *
     * <p>{@code PlayerEvent.Clone} with {@code isWasDeath}, not {@code LivingDeathEvent}: the Limbo Scythe
     * CANCELS the death event when it buys a death back, so a handler there would fire for deaths that never
     * happened. {@code BrainReflexes.onPlayerRespawn} learned this the same way.
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (event.getEntity() instanceof ServerPlayer p) {
            ReputationData.get(p.serverLevel().getServer()).onDeath(p);
        }
    }
}
