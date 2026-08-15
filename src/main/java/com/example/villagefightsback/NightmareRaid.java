package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Nightmare difficulty: at every sunset the village stops defending and comes for you.
 *
 * <p>The brain node marks one player with the {@link ModEffects#NIGHTMARE_SCENT}, draws a raid from whoever is
 * not currently busy defending the village, and sends them. They follow across the Overworld, dig through what
 * you put in the way, and do not stop when the mark expires.
 *
 * <p>ONE player per node per sunset, the nearest to it. A raid split between two players is two half raids, and
 * the point of the roster sizes below is that the village arrives in force.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NightmareRaid {
    private NightmareRaid() {}

    /** How long the mark lasts. The raid outlives it: see {@link #NBT_HUNTING}. */
    public static final int SCENT_TICKS = 20 * 60 * 10;   // 10 minutes

    /** Dusk, in day-time ticks: the raid may only go out from here to dawn. */
    private static final long SUNSET_TIME = 12000L;

    /** Raid size by village stage: the village commits more of itself the further it has evolved. */
    private static final int[] RAID_SIZE_BY_STAGE = {4, 4, 8, 16, 32};

    /** How far from the node the roster is drawn. Matches the village's own sense of "its people". */
    private static final double ROSTER_RANGE = 96.0;

    /** Set on a raider for as long as it is hunting. Read by SettlerGoal, and by the bell-on-kill below. */
    public static final String NBT_HUNTING = "vgun_nightmare_hunting";
    /** Who it is hunting, so a raider that loses its target can be told it is done. */
    private static final String NBT_HUNTED = "vgun_nightmare_hunted";

    /** The last sunset period this node raided on, so one sunset raises exactly one raid. */
    private static final java.util.Map<GlobalPos, Long> LAST_RAID = new java.util.HashMap<>();

    /** Which chunk each raider currently holds a ticket on, so the old one can be released when it moves. */
    private static final java.util.Map<java.util.UUID, net.minecraft.world.level.ChunkPos> HELD =
            new java.util.HashMap<>();

    /** The player this mob is hunting, or null. Public so the pursuit goal can steer by it. */
    public static ServerPlayer huntedBy(net.minecraft.world.entity.Entity e) {
        if (!isHunting(e) || !(e.level() instanceof ServerLevel level)) return null;
        String id = e.getPersistentData().getString(NBT_HUNTED);
        if (id.isEmpty()) return null;
        return level.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(id));
    }

    /** True while this mob is out hunting a player, which is what suspends its ordinary village duties. */
    public static boolean isHunting(net.minecraft.world.entity.Entity e) {
        return e.getPersistentData().getBoolean(NBT_HUNTING);
    }

    /**
     * Raise this node's sunset raid, if Nightmare is on and there is anyone to hunt.
     *
     * <p>Called from the brain node's existing sunset pass, so it fires on the same schedule as the village's
     * reinforcements rather than needing a clock of its own.
     */
    public static void trySunsetRaid(ServerLevel level, BrainNodeData data, GlobalPos node, long period) {
        if (!Difficulty.current().huntsAtSunset()) return;
        // AT SUNSET, and only then. The period counter this hangs off ticks over the first time a world runs
        // whatever the hour, so without this the very first raid went out the moment the player spawned in --
        // at dawn, on a brand-new world, before they had done anything at all.
        if (net.minecraft.util.Mth.positiveModulo(level.getDayTime(), 24000L) < SUNSET_TIME) return;
        if (LAST_RAID.getOrDefault(node, Long.MIN_VALUE) == period) return;   // already raided this sunset

        ServerPlayer target = nearestPlayerTo(level, node.pos());
        if (target == null) return;
        LAST_RAID.put(node, period);

        int stage = Math.max(0, Math.min(RAID_SIZE_BY_STAGE.length - 1, data.econ(node).getLevel()));
        int size = RAID_SIZE_BY_STAGE[stage];

        List<PathfinderMob> roster = drawRoster(level, node.pos(), size);
        if (roster.isEmpty()) return;

        mark(target);
        for (PathfinderMob raider : roster) send(raider, target);

        TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] Nightmare raid: {} units from the node at {} hunting {} (stage {}).",
                roster.size(), node.pos(), target.getGameProfile().getName(), stage);
    }

    /**
     * The living player nearest this node.
     *
     * <p>CREATIVE COUNTS. A creative player is standing right there and the village has no way of knowing the
     * difference; excluding them also made the whole feature untestable without dying to it. Spectators are
     * still skipped, because a spectator is not present in any sense the village could react to.
     */
    private static ServerPlayer nearestPlayerTo(ServerLevel level, BlockPos node) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double d = p.blockPosition().distSqr(node);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    /**
     * Pick up to {@code size} of the village's people, at random, from those not currently defending it.
     *
     * <p>"Not actively fighting to protect the village" is read as having no target: a defender mid-fight is
     * doing the job the village needs doing, and pulling it away to chase a player across the map would leave
     * the thing it was fighting standing in the village square.
     */
    private static List<PathfinderMob> drawRoster(ServerLevel level, BlockPos node, int size) {
        AABB box = new AABB(node).inflate(ROSTER_RANGE);
        List<PathfinderMob> pool = new ArrayList<>();
        for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, box, NightmareRaid::eligible)) {
            pool.add(m);
        }
        Collections.shuffle(pool, new java.util.Random(level.random.nextLong()));
        return pool.subList(0, Math.min(size, pool.size()));
    }

    /** A villager, guard, golem or Darius that belongs to the village and is not already busy. */
    private static boolean eligible(net.minecraft.world.entity.Entity e) {
        if (!(e instanceof PathfinderMob m) || !m.isAlive()) return false;
        if (isHunting(m)) return false;                                  // already out on a hunt
        if (m.getTarget() != null && m.getTarget().isAlive()) return false; // busy defending
        if (Factions.isInfected(m) || Factions.isEmerald(m)) return false;  // not the village's to send
        if (com.example.villagefightsback.compat.MCACompat.isHostileVillager(m)) return false;
        // A builder is mid-job on the village's wall; taking it on a hunt strands a half-built course and, when
        // it had the dig goal, buried the builder in its own hole.
        if (m instanceof Villager v && com.example.villagefightsback.BuilderRole.isBuilder(v)) return false;
        return m instanceof Villager || m instanceof IronGolem
                || m instanceof GeneralDariusEntity || Factions.isGuard(m);
    }

    /** The scoreboard team that exists only to colour the glow. */
    private static final String SCENT_TEAM = "tvfb_nightmare_scent";

    /**
     * Put {@code p} on the scent team, which is what makes the outline RED.
     *
     * <p>Vanilla's glow has no colour of its own: it is drawn in the entity's TEAM colour, and an entity with
     * no team glows white. There is no other hook for it short of replacing the outline renderer, so the team
     * is the mechanism. It carries no other meaning here -- nothing in this mod reads it -- and the player is
     * taken off it again the moment the scent lapses.
     */
    private static void joinScentTeam(ServerPlayer p) {
        net.minecraft.world.scores.Scoreboard sb = p.serverLevel().getScoreboard();
        net.minecraft.world.scores.PlayerTeam team = sb.getPlayerTeam(SCENT_TEAM);
        if (team == null) {
            team = sb.addPlayerTeam(SCENT_TEAM);
            team.setColor(ChatFormatting.DARK_RED);
            team.setDisplayName(Component.literal("Hunted"));
        }
        sb.addPlayerToTeam(p.getScoreboardName(), team);
    }

    /** Take a player off the scent team once the scent is gone, so they stop glowing red. */
    private static void leaveScentTeam(ServerPlayer p) {
        net.minecraft.world.scores.Scoreboard sb = p.serverLevel().getScoreboard();
        net.minecraft.world.scores.PlayerTeam team = sb.getPlayerTeam(SCENT_TEAM);
        if (team != null && team.getPlayers().contains(p.getScoreboardName())) {
            sb.removePlayerFromTeam(p.getScoreboardName(), team);
        }
    }

    /** Mark the player: the scent, the glow that makes them visible through walls, and the warning. */
    private static void mark(ServerPlayer p) {
        MobEffectInstance scent = new MobEffectInstance(
                ModEffects.NIGHTMARE_SCENT.get(), SCENT_TICKS, 0, false, false, true);
        scent.setCurativeItems(new ArrayList<>());   // neither milk nor a totem lifts it
        p.addEffect(scent);
        // The red glow the brief calls for. Vanilla GLOWING is the only outline the client can draw, and it is
        // also the honest one: it tells the hunted player exactly what the raid can see.
        p.addEffect(new MobEffectInstance(MobEffects.GLOWING, SCENT_TICKS, 0, false, false, true));
        joinScentTeam(p);   // the glow is drawn in the team colour; without a team it is white
        p.sendSystemMessage(Component.literal("Prepare...").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    /**
     * Put a raider on a player and get it fighting.
     *
     * <p>setTarget alone was NOT enough, and that is why raiders arrived and punched for nothing. The village's
     * combat goals do not read getTarget: AxeCombatGoal, StoneAxeDefenseGoal and the wall defender all ask
     * {@link VillageEvents#getAggroPlayer}, which reads the aggro record. A raider with only a target set had
     * no goal willing to swing its axe, so it walked up and threw the bare-handed animation a Mob does by
     * default -- no weapon, no damage. Writing the record puts the hunt through the same path every other
     * fight in this mod uses, with the same axe and the same damage.
     */
    private static void send(PathfinderMob raider, ServerPlayer target) {
        raider.getPersistentData().putBoolean(NBT_HUNTING, true);
        raider.getPersistentData().putString(NBT_HUNTED, target.getStringUUID());
        raider.setTarget(target);
        markAggro(raider, target);
        // The pursuit goal is what carries a raider across ground it cannot see over. Added once, and harmless
        // when idle: it does nothing at all unless this mob is actually out on a hunt.
        if (raider.goalSelector.getAvailableGoals().stream()
                .noneMatch(g -> g.getGoal() instanceof com.example.villagefightsback.goal.NightmareHuntGoal)) {
            raider.goalSelector.addGoal(3, new com.example.villagefightsback.goal.NightmareHuntGoal(raider));
        }
        // Digging comes with the draft, not with being a villager, and never for a builder. A builder handed a
        // shovel digs itself into a hole and stays there, which is what the village's own wall crews were doing.
        boolean builder = raider instanceof Villager v
                && com.example.villagefightsback.BuilderRole.isBuilder(v);
        if (!builder && raider.goalSelector.getAvailableGoals().stream()
                .noneMatch(g -> g.getGoal() instanceof com.example.villagefightsback.goal.EmeraldDigGoal)) {
            raider.goalSelector.addGoal(5, new com.example.villagefightsback.goal.EmeraldDigGoal(raider, true));
        }
        raider.setPersistenceRequired();                                 // never despawn mid-hunt
    }

    /**
     * Keep raiders on their target, and let them go when it dies.
     *
     * <p>A target set once does not stick: the ordinary target goals will clear it the moment the player leaves
     * their follow range, which for a hunt across the Overworld is immediately. So the target is re-asserted
     * while the hunt is on. It is deliberately NOT tied to the scent: the brief says the raiders remain when
     * the scent lapses, and they do, still hunting, just no longer able to see through walls.
     */
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        // Every 2s, not every tick. The sweep below is O(loaded entities) per level because a hunt has no
        // bounding box -- the raiders are wherever the player led them -- so it cannot be a chunk query. Two
        // seconds is far inside the window where a target being re-asserted late would be visible, and it is
        // 40x cheaper than doing it every tick.
        if (server.getTickCount() % 40 != 0) return;

        // Anyone off the scent goes back to glowing like everybody else.
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.hasEffect(ModEffects.NIGHTMARE_SCENT.get())) leaveScentTeam(p);
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class,
                    new AABB(-30000000, level.getMinBuildHeight(), -30000000,
                             30000000, level.getMaxBuildHeight(), 30000000),
                    NightmareRaid::isHunting)) {
                String hunted = m.getPersistentData().getString(NBT_HUNTED);
                ServerPlayer p = hunted.isEmpty() ? null
                        : server.getPlayerList().getPlayer(java.util.UUID.fromString(hunted));
                if (p == null || !p.isAlive() || p.isSpectator()) {
                    stopHunting(m);
                    continue;
                }
                if (m.getTarget() != p) m.setTarget(p);
                markAggro(m, p);          // the record expires on its own; a live hunt keeps renewing it
                holdChunk(level, m);
            }
        }
    }

    /**
     * Keep the chunk a raider stands in loaded, following it as it moves.
     *
     * <p>Without this the hunt quietly ends at the edge of the player's view distance: a raider a thousand
     * blocks away sits in an unloaded chunk, stops ticking, and never arrives -- which reads as the village
     * giving up rather than as a technical limit. One ticket per raider, moved rather than accumulated, and
     * released the moment the hunt ends.
     */
    private static void holdChunk(ServerLevel level, PathfinderMob m) {
        net.minecraft.world.level.ChunkPos now = m.chunkPosition();
        net.minecraft.world.level.ChunkPos was = HELD.get(m.getUUID());
        if (now.equals(was)) return;
        if (was != null) {
            net.minecraftforge.common.world.ForgeChunkManager.forceChunk(
                    level, TheVillageFightsBack.MODID, m.getUUID(), was.x, was.z, false, true);
        }
        net.minecraftforge.common.world.ForgeChunkManager.forceChunk(
                level, TheVillageFightsBack.MODID, m.getUUID(), now.x, now.z, true, true);
        HELD.put(m.getUUID(), now);
    }

    /** Drop a raider's chunk ticket. Called when its hunt ends, so nothing is force-loaded forever. */
    private static void releaseChunk(PathfinderMob m) {
        net.minecraft.world.level.ChunkPos was = HELD.remove(m.getUUID());
        if (was == null || !(m.level() instanceof ServerLevel level)) return;
        net.minecraftforge.common.world.ForgeChunkManager.forceChunk(
                level, TheVillageFightsBack.MODID, m.getUUID(), was.x, was.z, false, true);
    }

    /** Stamp the village's aggro record, which is what the combat goals actually read. */
    private static void markAggro(PathfinderMob raider, ServerPlayer target) {
        net.minecraft.nbt.CompoundTag data = raider.getPersistentData();
        data.putUUID(VillageEvents.NBT_AGGRO_UUID, target.getUUID());
        // Comfortably longer than the re-assert interval, so it never lapses mid-hunt between renewals.
        data.putLong(VillageEvents.NBT_AGGRO_UNTIL, raider.level().getGameTime() + 200L);
    }

    /** The hunt is over for this unit: it keeps where it stands, as the brief asks, and goes back to normal. */
    private static void stopHunting(PathfinderMob m) {
        releaseChunk(m);
        m.getPersistentData().remove(VillageEvents.NBT_AGGRO_UUID);
        m.getPersistentData().remove(VillageEvents.NBT_AGGRO_UNTIL);
        m.getPersistentData().remove(NBT_HUNTING);
        m.getPersistentData().remove(NBT_HUNTED);
        m.setTarget(null);
    }

    /**
     * A raider that kills its quarry plants a bell where they fell.
     *
     * <p>The village claims the ground it won. A bell is a village centre waiting to happen, so the brain
     * node's ordinary placement scan may well raise a node there in its own time -- which is the point: die
     * out in the wild on Nightmare and the village follows you there permanently.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHuntedDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!p.hasEffect(ModEffects.NIGHTMARE_SCENT.get())) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity killer) || !isHunting(killer)) return;
        if (!(p.level() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;          // a bell in the Nether founds nothing

        BlockPos at = p.blockPosition();
        if (!level.getBlockState(at).canBeReplaced()) at = at.above();
        if (!level.getBlockState(at).canBeReplaced()) return;
        BlockPos below = at.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) return;

        level.setBlock(at, net.minecraft.world.level.block.Blocks.BELL.defaultBlockState(), 3);
        TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] Nightmare: a raider killed {} at {}; the village planted a bell there.",
                p.getGameProfile().getName(), at);
    }
}
