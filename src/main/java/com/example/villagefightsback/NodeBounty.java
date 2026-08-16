package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * At sunset the brain node names the biggest thing near it and pays reputation for its head.
 *
 * <p>The reason this exists is that every other way of earning the village's good opinion is something you do
 * INSIDE the village -- trade, donate, defend the place you are already standing in. Left there, the winning
 * play is to move into a village and never leave, which is the opposite of interesting and which makes an
 * infection mod installed alongside this one irrelevant: the infection never has to interact with the player
 * at all, because the player is never anywhere it can reach.
 *
 * <p>So the village pays you to go out. The target is chosen exactly the way General Darius chooses his, and
 * the reward is the thing's own maximum health, which means the bounty scales itself against whatever is out
 * there without knowing a single thing about it. On a vanilla world that is a ravager or a warden; with an
 * infection mod installed it is whatever that mod's worst creature is, from the day it is installed, with no
 * compatibility code.
 *
 * <p>NOT WHILE DARIUS IS OUT. He is the village's answer to a heavy threat, and a village that has already
 * sent its champion is not also shopping the job out to a freelancer. It would also read as the village
 * paying you for a kill its own general was about to make.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NodeBounty {
    private NodeBounty() {}

    /** How far the node looks. The brain's own influence radius, and Darius's hunting range. */
    private static final double RADIUS = 100.0;
    /** Below this the thing is not worth a bounty: the village can handle it, and saying so would be noise. */
    private static final double MIN_MAX_HP = 75.0;
    /** Ten minutes, in ticks. */
    private static final int DEADLINE_TICKS = 20 * 60 * 10;

    /** One live bounty per brain node. */
    private record Bounty(UUID hunter, String hunterName, UUID target, String targetName,
                          int reward, long expiresAt) {}

    /** Transient: a bounty is a ten-minute thing and is not worth carrying across a restart. */
    private static final Map<GlobalPos, Bounty> ACTIVE = new HashMap<>();

    /**
     * Offer this node's bounty, if everything lines up. Called once per village per day, at dusk.
     *
     * <p>Peaceful and Neutral only. On Hostile and Nightmare reputation cannot move at all, so a bounty would
     * be an offer of something the village is incapable of paying.
     */
    public static void offer(ServerLevel level, GlobalPos node) {
        Difficulty d = Difficulty.current();
        if (d != Difficulty.PEACEFUL && d != Difficulty.NEUTRAL) return;
        if (ACTIVE.containsKey(node)) return;                      // one at a time per node
        BlockPos brain = node.pos();
        if (!level.isLoaded(brain)) return;
        if (!noDariusNear(level, brain)) return;                   // the village already sent its champion

        ServerPlayer hunter = pickHunter(level, brain);
        if (hunter == null) return;
        LivingEntity target = GeneralDariusEntity.biggestThreatNear(level, brain, RADIUS, MIN_MAX_HP);
        if (target == null) return;

        int reward = (int) target.getMaxHealth();
        BlockPos at = target.blockPosition();
        ACTIVE.put(node, new Bounty(hunter.getUUID(), hunter.getGameProfile().getName(),
                target.getUUID(), target.getType().getDescription().getString(),
                reward, level.getGameTime() + DEADLINE_TICKS));

        broadcast(level, Component.literal(hunter.getGameProfile().getName()
                + ", the mighty Brain Node will offer you a chance to prove you're worthy. Kill "
                + target.getType().getDescription().getString()
                + " at " + at.getX() + ", " + at.getY() + ", " + at.getZ()
                + " to get " + reward + " reputation points! You have 10 minutes.")
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * Whoever the village thinks best of, among the players it can see.
     *
     * <p>Ties are broken at random rather than by who logged in first, so on a server where two players are
     * level the job does not silently always go to the same one.
     */
    private static ServerPlayer pickHunter(ServerLevel level, BlockPos brain) {
        ReputationData rep = ReputationData.get(level.getServer());
        List<ServerPlayer> best = new ArrayList<>();
        int bestRep = Integer.MIN_VALUE;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (p.blockPosition().distSqr(brain) > RADIUS * RADIUS) continue;
            int r = rep.get(p.getUUID());
            if (r <= ReputationData.ENEMY_AT) continue;            // the village does not hire its enemies
            if (r > bestRep) { bestRep = r; best.clear(); best.add(p); }
            else if (r == bestRep) best.add(p);
        }
        if (best.isEmpty()) return null;
        return best.get(level.getRandom().nextInt(best.size()));
    }

    /** True if no General Darius is serving this brain. Mirrors {@code BrainReflexes.noDariusNear}. */
    private static boolean noDariusNear(ServerLevel level, BlockPos brain) {
        return level.getEntitiesOfClass(GeneralDariusEntity.class,
                new net.minecraft.world.phys.AABB(brain).inflate(RADIUS)).isEmpty();
    }

    /**
     * Pay out when the named creature dies, and only to the player who was asked.
     *
     * <p>Somebody else's kill, or a fall, or a creeper, all count as a failure -- the offer was to prove
     * YOURSELF worthy, and standing back while it happens is not that.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (ACTIVE.isEmpty()) return;
        UUID dead = event.getEntity().getUUID();
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        for (Map.Entry<GlobalPos, Bounty> e : new ArrayList<>(ACTIVE.entrySet())) {
            Bounty b = e.getValue();
            if (!b.target().equals(dead)) continue;
            ACTIVE.remove(e.getKey());

            boolean byHunter = event.getSource().getEntity() instanceof ServerPlayer killer
                    && killer.getUUID().equals(b.hunter());
            if (!byHunter) {
                broadcast(level, Component.literal(b.hunterName() + " did not kill " + b.targetName())
                        .withStyle(ChatFormatting.GRAY));
                continue;
            }
            ServerPlayer hunter = (ServerPlayer) event.getSource().getEntity();
            ReputationData.get(level.getServer()).add(hunter, b.reward(), "bounty: " + b.targetName());
            broadcast(level, Component.literal(b.hunterName() + " kills " + b.targetName()
                    + " and collects the bounty! The Village is proud of you!")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /**
     * Drop bounties whose ten minutes are up, and say so.
     *
     * <p>Driven from the brain node's existing tick, so it runs on the same throttle as the rest of the
     * village's housekeeping. The {@code isEmpty} guard is what keeps a world with no bounty out from paying
     * anything at all for the feature.
     */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        for (Map.Entry<GlobalPos, Bounty> e : new ArrayList<>(ACTIVE.entrySet())) {
            ServerLevel level = server.getLevel(e.getKey().dimension());
            if (level == null) continue;
            Bounty b = e.getValue();
            if (level.getGameTime() < b.expiresAt()) continue;
            ACTIVE.remove(e.getKey());
            broadcast(level, Component.literal(b.hunterName() + " did not kill " + b.targetName())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Forget every bounty when the server stops, so a later world does not inherit one. */
    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        ACTIVE.clear();
    }

    private static void broadcast(ServerLevel level, Component message) {
        level.getServer().getPlayerList().broadcastSystemMessage(message, false);
    }
}
