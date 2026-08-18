package com.example.villagefightsback;

import java.util.ArrayList;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
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

    /** Set on a raider for as long as it is hunting. Read by SettlerGoal, and by the bell-on-kill below. */
    public static final String NBT_HUNTING = "vgun_nightmare_hunting";
    /**
     * A plain scoreboard tag on every drafted unit, villagers and golems alike.
     *
     * <p>Purely for looking at: {@code /tag @e[tag=raider] list}, or the F3 screen. The hunt itself runs off
     * NBT_HUNTING and never reads this. It exists so that a village full of villagers can be told apart at a
     * glance from the handful that are actually coming for you, which is otherwise guesswork from the outside.
     */
    public static final String TAG_RAIDER = "raider";
    /** Who it is hunting, so a raider that loses its target can be told it is done. */
    private static final String NBT_HUNTED = "vgun_nightmare_hunted";

    /** The last DAY this node raided on, so each night raises exactly one raid however the clock moves. */
    private static final java.util.Map<GlobalPos, Long> LAST_RAID = new java.util.HashMap<>();

    /**
     * Every unit currently out on a hunt, by UUID. THE ONLY WAY THE SWEEP BELOW FINDS ITS RAIDERS.
     *
     * <p>It used to find them by asking the level for every PathfinderMob inside a box the size of the world,
     * and filtering. That box spans 3.75 million section columns per axis, and vanilla's entity lookup walks
     * every one of them against an AVL tree of section ids before it looks at a single entity -- roughly 3.75
     * million tree operations per level, every two seconds, whether or not a raid was ever raised, and on every
     * difficulty including the ones that never raid at all. That was the freeze.
     *
     * <p>A raid knows exactly who it sent, so nothing needs to be searched for. Membership is written in
     * {@link #send} and cleared in {@link #stopHunting}; {@link #onEntityJoin} puts a raider back after a
     * reload, since the hunt itself lives in the mob's saved NBT and outlives any server restart.
     */
    private static final java.util.Set<java.util.UUID> HUNTERS = new java.util.HashSet<>();

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
    public static void trySunsetRaid(ServerLevel level, BrainNodeData data, GlobalPos node, long day) {
        // AT SUNSET, and only then. The period counter this hangs off ticks over the first time a world runs
        // whatever the hour, so without this the very first raid went out the moment the player spawned in --
        // at dawn, on a brand-new world, before they had done anything at all.
        if (net.minecraft.util.Mth.positiveModulo(level.getDayTime(), 24000L) < SUNSET_TIME) return;
        if (LAST_RAID.getOrDefault(node, Long.MIN_VALUE) == day) return;      // already raided tonight

        // WHO, not WHICH DIFFICULTY. The hunt used to be a property of the Nightmare difficulty setting; it is
        // now a property of a player, earned by falling to -500 reputation -- a "Nightmare of the Village". The
        // Nightmare difficulty simply pins you there from the first morning, so it behaves exactly as it did,
        // and a Neutral world can now produce the same nightly hunt if somebody works hard enough at it.
        ServerPlayer target = nearestHuntedTo(level, node.pos());
        if (target == null) return;
        LAST_RAID.put(node, day);

        int stage = Math.max(0, Math.min(RAID_SIZE_BY_STAGE.length - 1, data.econ(node).getLevel()));
        int size = RAID_SIZE_BY_STAGE[stage];

        List<PathfinderMob> roster = raiseRaid(level, target, size, stage);
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
    private static ServerPlayer nearestHuntedTo(ServerLevel level, BlockPos node) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            // Only a Nightmare of the Village is hunted. Everybody else, however badly the village thinks of
            // them, is dealt with by the defenders who are already standing there.
            // Tier TWO of Enemy of the Village, not a second effect. Amplifier 1 is what -500 reputation
            // buys; amplifier 0 is an ordinary enemy, whom the defenders standing there already handle.
            MobEffectInstance mark = p.getEffect(ModEffects.ENEMY_OF_THE_VILLAGE.get());
            if (mark == null || mark.getAmplifier() < 1) continue;
            double d = p.blockPosition().distSqr(node);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    /** How far from the player the raid forms up. Close enough to find you, far enough not to land on you. */
    private static final int RAID_DISTANCE = 20;

    /**
     * Raise the raid beside the player, rather than drafting whoever happened to be standing near the node.
     *
     * <p>Drafting was the first design and it did not survive contact: the village's people are spread over
     * chunks that are simply not loaded when the player is a thousand blocks away, so the draft counted
     * whatever was near the node and found almost nothing. Force-loading the village made that better and
     * never good, and it cost twenty-five chunks a node to do it.
     *
     * <p>Spawning beside the player sidesteps all of it. The raid is real either way -- the same villagers,
     * guards and golems the village fields, in the same proportions -- and it arrives where it can actually
     * walk to you.
     */
    private static List<PathfinderMob> raiseRaid(ServerLevel level, ServerPlayer target, int size, int stage) {
        BlockPos anchor = musterPoint(level, target.blockPosition());
        if (anchor == null) return List.of();

        List<PathfinderMob> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            net.minecraft.world.entity.EntityType<?> type = unitFor(level, stage, i);
            if (type == null) continue;
            net.minecraft.world.entity.Entity e = com.example.villagefightsback.BrainNodeEvents
                    .spawnUnitNear(level, anchor, type, spawned ->
                            com.example.villagefightsback.compat.MCACompat.makeGuard(spawned));
            if (e instanceof PathfinderMob m) out.add(m);
        }
        return out;
    }

    /**
     * What the raid is made of: mostly villagers, a guard in every four, and one golem from stage 3.
     *
     * <p>The same shape as the village's own sunset reinforcement, so a raid looks like the place it came from
     * rather than like a separate monster.
     */
    private static net.minecraft.world.entity.EntityType<?> unitFor(ServerLevel level, int stage, int index) {
        // GUARDS AND GOLEMS, never a plain villager. A raid used to be three quarters civilians, and it read
        // as one: a crowd of farmers walking at you and losing. The village sends soldiers now, and the roster
        // sizes in RAID_SIZE_BY_STAGE mean the same number of far more dangerous units.
        if (index % 4 == 1) return net.minecraft.world.entity.EntityType.IRON_GOLEM;
        net.minecraft.world.entity.EntityType<?> guard =
                com.example.villagefightsback.compat.MCACompat.guardType(level.getRandom());
        // Guard Villagers is a hard dependency, so this is unreachable in a correctly installed game. The
        // fallback is a GOLEM rather than the villager it used to be: if the roster ever cannot field a
        // soldier, it fields a heavier one rather than quietly breaking the rule this method exists to keep.
        return guard != null ? guard : net.minecraft.world.entity.EntityType.IRON_GOLEM;
    }

    /**
     * Somewhere {@value #RAID_DISTANCE} blocks from the player that a raid can actually stand on.
     *
     * <p>Tried at random angles, and every candidate has to be open ground: solid underfoot, room to stand, and
     * no fluid. Without the fluid test a raid could muster in a lava lake and burn to death before it took a
     * step, and on an ocean it would drown -- which would read as the village giving up rather than as the
     * village drowning.
     */
    private static BlockPos musterPoint(ServerLevel level, BlockPos around) {
        for (int i = 0; i < 16; i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0;
            int x = around.getX() + (int) Math.round(Math.cos(a) * RAID_DISTANCE);
            int z = around.getZ() + (int) Math.round(Math.sin(a) * RAID_DISTANCE);
            BlockPos surface = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));
            if (!level.getFluidState(surface).isEmpty()) continue;            // not in water or lava
            if (!level.getFluidState(surface.below()).isEmpty()) continue;    // nor standing on its surface
            if (!level.getBlockState(surface.below()).isFaceSturdy(
                    level, surface.below(), net.minecraft.core.Direction.UP)) continue;
            if (level.getBlockState(surface).blocksMotion()
                    || level.getBlockState(surface.above()).blocksMotion()) continue;   // room to stand
            return surface;
        }
        return null;
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
        // Thunder, and it has to reach the player rather than the muster point. level.playSound is
        // position-attenuated, so a raid forming twenty blocks behind you is audible and one forming behind a
        // hill is not -- and "the village has sent something after you" is not a fact that should depend on
        // the wind. playNotifySound is delivered straight to this client at full volume. The positional crack
        // over the muster point stays as well, so anyone else nearby hears where it came from.
        p.playNotifySound(net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                net.minecraft.sounds.SoundSource.WEATHER, 1.0F, 1.0F);
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
        raider.addTag(TAG_RAIDER);
        HUNTERS.add(raider.getUUID());   // the sweep finds its raiders here and nowhere else
        // Blocking the requisition only stops a raider being GIVEN a job. A villager that was already a miner
        // when it was drafted would keep it and try to do both, so the draft takes the job off it. Builders
        // cannot reach here at all -- they are not eligible in the first place.
        if (raider instanceof Villager v && com.example.villagefightsback.infection.MinerRole.isMiner(v)) {
            com.example.villagefightsback.infection.MinerRole.demote(v);
        }
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
        // Every 2s, not every tick. Two seconds is far inside the window where a target being re-asserted late
        // would be visible, and it is 40x cheaper than doing it every tick.
        if (server.getTickCount() % 40 != 0) return;

        // Anyone off the scent goes back to glowing like everybody else.
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.hasEffect(ModEffects.NIGHTMARE_SCENT.get())) leaveScentTeam(p);
        }

        // No raid out, nothing to re-assert. On every difficulty but Nightmare this is where the handler always
        // ends, and it is why a world that never raids no longer pays for the raid.
        if (HUNTERS.isEmpty()) return;

        // Over a copy: stopHunting() takes its raider out of HUNTERS.
        for (java.util.UUID id : new ArrayList<>(HUNTERS)) {
            PathfinderMob m = findRaider(server, id);
            // Dead, or standing in a chunk nobody has loaded. Forget it either way -- onEntityJoin puts a
            // raider that was merely unloaded straight back when its chunk returns, so this set stays the size
            // of the raid rather than the size of every raid the world has ever sent.
            if (m == null) { HUNTERS.remove(id); continue; }

            String hunted = m.getPersistentData().getString(NBT_HUNTED);
            ServerPlayer p = hunted.isEmpty() ? null
                    : server.getPlayerList().getPlayer(java.util.UUID.fromString(hunted));
            if (p == null || !p.isAlive() || p.isSpectator()) {
                stopHunting(m);
                continue;
            }
            if (m.getTarget() != p) m.setTarget(p);
            markAggro(m, p);          // the record expires on its own; a live hunt keeps renewing it
        }
    }

    /** The raider with this UUID, wherever it stands, or null if it is dead or its chunk is not loaded. */
    private static PathfinderMob findRaider(MinecraftServer server, java.util.UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof PathfinderMob m && isHunting(m)) return m;
        }
        return null;
    }

    /**
     * Put a raider back on the books when its chunk loads, or when the world does.
     *
     * <p>The hunt lives in the mob's saved NBT, so it survives a restart -- but {@link #HUNTERS} is in memory
     * and does not. Without this, a player who quit mid-raid would come back to raiders that still believed
     * they were hunting and were never told anything again.
     *
     * <p>The tag test first, deliberately. This fires for every entity that spawns or loads anywhere in the
     * world, and {@code getTags()} is a plain set the entity already holds, where reading persistent data
     * would build a compound tag for every mob in the world that is not a raider.
     */
    @SubscribeEvent
    public static void onEntityJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        net.minecraft.world.entity.Entity e = event.getEntity();
        if (!(e instanceof PathfinderMob) || !e.getTags().contains(TAG_RAIDER)) return;
        if (isHunting(e)) HUNTERS.add(e.getUUID());
    }

    /*
     * A raider used to drag a ticking force-load ticket along with it, one per raider, moved chunk by chunk as
     * it walked, so a hunt could carry on past the player's view distance. That was written when the raid was
     * drafted from the village and had to cross the map to reach you. It musters twenty blocks from the player
     * now: it is inside loaded ground from its first step to its last, and the tickets bought nothing.
     *
     * <p>They cost plenty. A ticking ticket runs a chunk's full tick with nobody near it, and the release only
     * ever ran when a hunt ended tidily -- a raider cut down mid-hunt, which is how most of them go, kept its
     * chunk forced with no owner left to release it. Forge writes those tickets to the level's save data, so
     * they came back every load: a save accumulated a permanently ticking chunk per raider killed, night after
     * night, and never gave one back.
     *
     * <p>Tickets already in existing 1.4.0 saves are handed back by the loading-validation callback in
     * {@code TheVillageFightsBack.releaseStaleChunkTickets}.
     */

    /** Stamp the village's aggro record, which is what the combat goals actually read. */
    private static void markAggro(PathfinderMob raider, ServerPlayer target) {
        net.minecraft.nbt.CompoundTag data = raider.getPersistentData();
        data.putUUID(VillageEvents.NBT_AGGRO_UUID, target.getUUID());
        // Comfortably longer than the re-assert interval, so it never lapses mid-hunt between renewals.
        data.putLong(VillageEvents.NBT_AGGRO_UNTIL, raider.level().getGameTime() + 200L);
    }

    /** The hunt is over for this unit: it keeps where it stands, as the brief asks, and goes back to normal. */
    private static void stopHunting(PathfinderMob m) {
        HUNTERS.remove(m.getUUID());
        m.removeTag(TAG_RAIDER);
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
