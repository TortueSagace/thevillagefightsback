package com.example.villagefightsback;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What the village thinks of each player, as a number, and the only thing allowed to change it.
 *
 * <p>ONE GLOBAL SCORE PER PLAYER, not one per village. A village's opinion of you is the village faction's
 * opinion of you: walk to a settlement you have never seen and it has already heard. Reputation therefore
 * moves wherever you are, with or without a brain node in range -- a trade in a village a thousand blocks
 * from the nearest node still counts.
 *
 * <p>THE STATUS EFFECTS ARE THIS NUMBER'S SHADOW, not its store. Hero of the Village and
 * {@link ModEffects#ENEMY_OF_THE_VILLAGE} are still real effects, re-derived from the score in
 * {@link #applyStatus}, and that is deliberate on two counts. The first is speed:
 * {@code VillageEvents.getAggroPlayer} is asked by four combat goals for every village unit several times a
 * second, and it can go on asking {@code hasEffect} -- a map lookup and a saved-data fetch on that path is
 * exactly the cost the 1.4.1 profiling pass spent a day removing. The second is that vanilla already scales
 * villager trade discounts by the Hero amplifier, so Hero I..V costs nothing to implement.
 *
 * <p>Because the score lives out here rather than in the effect, it survives everything that clears effects:
 * milk, a totem, death, and {@code LimboScytheEvents}' {@code removeAllEffects}. The effect coming back is
 * then just the next re-derive.
 */
public class ReputationData extends SavedData {
    private static final String NAME = "thevillagefightsback_reputation";

    /** Reputation at or above this is Hero of the Village, and every further 100 is another level. */
    public static final int HERO_STEP = 100;
    /** The most Hero levels. Hero V is the best trades the village will ever give. */
    public static final int MAX_HERO = 5;
    /** At or below this the village treats you as an enemy. */
    public static final int ENEMY_AT = -100;
    /** At or below this it hunts you at every sunset. */
    public static final int NIGHTMARE_AT = -500;

    /** Where each difficulty starts you. Peaceful opens on Hero II; Nightmare opens on a number you feel. */
    private static int startFor(Difficulty d) {
        return switch (d) {
            case PEACEFUL -> 200;
            case NEUTRAL -> 0;
            case HOSTILE -> -300;
            case NIGHTMARE -> -1_000_000;
        };
    }

    /**
     * The CEILING each difficulty puts on standing, which is what makes the hostile two permanent.
     *
     * <p>They are not frozen -- reputation moves on every difficulty, and a Hostile player who keeps hitting
     * villagers still sinks. What the ceiling does is make climbing out impossible: Hostile can rise to -100
     * and no further, so you are an Enemy of the Village for good, and Nightmare stops at -500, so the sunset
     * hunt never lifts. Nightly decay pulls both up to their ceiling and holds them there.
     */
    private static int maxFor(Difficulty d) {
        return switch (d) {
            case HOSTILE -> ENEMY_AT;
            case NIGHTMARE -> NIGHTMARE_AT;
            default -> Integer.MAX_VALUE;
        };
    }

    /** Peaceful is the only difficulty with a floor: the village will not turn on you there, ever. */
    private static int minFor(Difficulty d) {
        return d == Difficulty.PEACEFUL ? 0 : Integer.MIN_VALUE;
    }

    /** Long enough that the 20-tick re-derive never lets it lapse, short enough to expire if the mod is removed. */
    private static final int STATUS_TICKS = 20 * 30;

    /** How many log lines to keep. Matches the reflex and organic logs, and what the UI panel can show. */
    private static final int LOG_SIZE = 10;

    private final Map<UUID, Integer> rep = new HashMap<>();

    /**
     * The reputation log, newest last.
     *
     * <p>Transient, exactly like the reflex and organic logs it sits beside in the UI. The SCORE persists; the
     * running commentary on how it got there does not, and a log that survived a restart while the other two
     * did not would read as a bug in the other two.
     */
    private final ArrayDeque<String> log = new ArrayDeque<>();

    public static ReputationData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ReputationData::new, (tag, registries) -> load(tag, registries)), NAME);
    }

    // --- reading -------------------------------------------------------------------------------------

    /**
     * This player's reputation.
     *
     * <p>A player with nothing stored reads as their difficulty's STARTING value rather than as zero, so a
     * new world opens where the difficulty says it should without having to write a row for everybody at
     * login. The bounds are applied on the way out too, so a world switched to a harsher difficulty reports
     * the truth immediately without the stored number being rewritten -- switch back and the old score is
     * still there.
     */
    public int get(UUID player) {
        Difficulty d = standing();
        return clamp(d, rep.getOrDefault(player, startFor(d)));
    }

    /** Hero level 1..{@value #MAX_HERO}, or 0 for none. The effect amplifier is one less than this. */
    public static int heroLevel(int reputation) {
        if (reputation < HERO_STEP) return 0;
        return Math.min(MAX_HERO, reputation / HERO_STEP);
    }

    /** The last {@value #LOG_SIZE} reputation changes, oldest first, for the brain node UI. */
    public List<String> logLines() {
        return new ArrayList<>(log);
    }

    // --- writing: the one funnel ---------------------------------------------------------------------

    /**
     * Change {@code player}'s reputation by {@code delta} and say why.
     *
     * <p>EVERYTHING goes through here. It clamps to the difficulty, writes the log line, re-derives the
     * status effects and fires the celebration when a Hero level is reached, so no caller has to remember
     * any of that and no caller can get it half right.
     *
     * <p>It moves on every difficulty, including the hostile two -- what those do is cap how far it can
     * RISE, so the offences still land and the way out stays shut.
     */
    public void add(ServerPlayer player, int delta, String cause) {
        if (delta == 0) return;
        Difficulty d = standing();
        UUID id = player.getUUID();
        int before = get(id);
        int after = clamp(d, rep.getOrDefault(id, startFor(d)) + delta);
        if (after == before) return;

        rep.put(id, after);
        setDirty();
        record(player, delta, cause, after);
        applyStatus(player, before, after);
    }

    /**
     * Multiply every score by 0.9, which is what the village forgets overnight.
     *
     * <p>Both directions. A hero who stops showing up slides back toward indifference, and so does somebody
     * the village is furious with -- there is a way back from anything, it just takes nights. Truncation
     * toward zero is the reason nobody is stuck at 1 or -1 forever.
     */
    public void decayAll(MinecraftServer server, long day) {
        Difficulty d = standing();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            int before = get(id);
            int stored = rep.getOrDefault(id, startFor(d));
            int after = clamp(d, (int) (stored * 0.9));
            if (after == stored) continue;
            rep.put(id, after);
            setDirty();
            logLine("Day " + day + ": " + name(p) + " sunset decay -> " + after);
            applyStatus(p, before, get(id));
        }
    }

    /**
     * Death wipes a grudge but not a good name: a negative score returns to zero, a positive one is kept.
     *
     * <p>Dying to the village IS the punishment, so carrying the debt past it would mean being punished
     * twice for the same offence. Earned standing is not a debt and there is no reason a fall should cost
     * it. Untouched on Hostile and Nightmare, where the score never moves for any reason.
     */
    public void onDeath(ServerPlayer player) {
        Difficulty d = standing();
        // Not on the hostile two. Their whole design is that the standing cannot be climbed out of, and a
        // death that wiped the slate would be the cheapest possible way out -- on Nightmare it would trade a
        // million points of debt for a respawn.
        if (d == Difficulty.HOSTILE || d == Difficulty.NIGHTMARE) return;
        UUID id = player.getUUID();
        if (rep.getOrDefault(id, startFor(d)) >= 0) return;
        int before = get(id);
        rep.put(id, 0);
        setDirty();
        logLine(name(player) + " died: debt cleared -> 0");
        applyStatus(player, before, 0);
    }

    /**
     * The difficulty that decides the bounds, with the legacy {@code player_is_hostile} switch folded in.
     *
     * <p>That switch predates the difficulty screen and does the same job by another route: it kept a
     * permanent Enemy of the Village on every player. It cannot do that by hand any more -- reputation is the
     * only source of the status, and {@code StandingEffects} refuses every other -- so instead it says the
     * same thing in the new vocabulary: your standing is bounded where Hostile bounds it. Nightmare outranks
     * it, being strictly worse already.
     */
    private static Difficulty standing() {
        Difficulty d = Difficulty.current();
        if (d != Difficulty.NIGHTMARE && Config.playerIsHostile()) return Difficulty.HOSTILE;
        return d;
    }

    /** Peaceful floors at zero, the hostile two are capped from above, Neutral is free in both directions. */
    private static int clamp(Difficulty d, int value) {
        return Math.max(minFor(d), Math.min(maxFor(d), value));
    }

    // --- the status effects this number casts --------------------------------------------------------

    /**
     * Put the effects where the number says they should be, and celebrate a new Hero level.
     *
     * <p>Called on every change and again on a slow timer from {@link StandingEffects}, which is what makes
     * the statuses permanent: milk, a totem or a death removes the effect and the next re-derive puts it
     * straight back, because the thing that decides is not the effect.
     */
    public void applyStatus(ServerPlayer player, int before, int after) {
        refresh(player, after);
        int wasHero = heroLevel(before);
        int nowHero = heroLevel(after);
        if (nowHero > wasHero) celebrate(player, nowHero);
        // The CROSSING, once. Rallying on every application would re-rally the whole village on every
        // re-derive, which is a few times a minute forever.
        if (before > ENEMY_AT && after <= ENEMY_AT && player.level() instanceof ServerLevel sl) {
            VillageEvents.onBecameEnemy(sl, player);
        }
    }

    /** Set this player's effects to match {@code reputation} exactly, adding and removing as needed. */
    public void refresh(ServerPlayer player, int reputation) {
        int hero = heroLevel(reputation);
        if (hero > 0) {
            hold(player, MobEffects.HERO_OF_THE_VILLAGE, hero - 1);
        } else if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
            player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
        }

        boolean enemy = reputation <= ENEMY_AT;
        if (enemy) {
            hold(player, ModEffects.ENEMY_OF_THE_VILLAGE, 0);
        } else if (player.hasEffect(ModEffects.ENEMY_OF_THE_VILLAGE)) {
            player.removeEffect(ModEffects.ENEMY_OF_THE_VILLAGE);
        }

        boolean nightmare = reputation <= NIGHTMARE_AT;
        if (nightmare) {
            hold(player, ModEffects.NIGHTMARE_OF_THE_VILLAGE, 0);
        } else if (player.hasEffect(ModEffects.NIGHTMARE_OF_THE_VILLAGE)) {
            player.removeEffect(ModEffects.NIGHTMARE_OF_THE_VILLAGE);
        }
    }

    /**
     * Keep one effect present at one amplifier, re-applying only when it is missing, wrong or running out.
     *
     * <p>Re-applying an identical instance every pass would restart its particle and re-send it to the
     * client twenty times a second for nothing, so the common case -- already correct, plenty of time left
     * -- does nothing at all.
     *
     * <p>1.21 note: main built the instance and then called {@code inst.setCurativeItems(new ArrayList&lt;&gt;())}
     * on it -- "no milk, no totem: standing is not a poison". NeoForge has no per-instance cure API at all
     * (there is no {@code IMobEffectInstanceExtension}); an instance's cure set is filled by its constructor
     * from the EFFECT, through {@code IMobEffectExtension.fillEffectCures}, whose default installs
     * {@code EffectCures.DEFAULT_CURES} = {MILK, PROTECTED_BY_TOTEM}. So the milk-proofing has to move onto
     * the effect CLASSES, one per effect, which is where {@link EnemyOfVillageEffect#fillEffectCures} already
     * puts it on this branch. Two of the three effects held here cannot be covered that way from here:
     * {@code NightmareOfVillageEffect} needs the same empty override adding, and HERO OF THE VILLAGE IS
     * VANILLA'S and can never have one.
     *
     * <p>Nothing is actually lost by that, and the class javadoc above is why: a bucket of milk clears the
     * icon for at most a second and the {@link StandingEffects} re-derive puts it straight back, because the
     * thing that decides is not the effect. The effect is the shadow; the number is the store. What changes
     * is only that the shadow can now flicker.
     *
     * <p>The parameter is a {@code Holder<MobEffect>} rather than a {@code MobEffect} because 1.20.5 moved
     * every effect API -- {@code hasEffect}, {@code getEffect}, {@code removeEffect}, the
     * {@code MobEffectInstance} constructor -- onto holders.
     */
    private static void hold(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance held = player.getEffect(effect);
        if (held != null && held.getAmplifier() == amplifier && held.getDuration() > STATUS_TICKS / 2) return;
        MobEffectInstance inst = new MobEffectInstance(effect, STATUS_TICKS, amplifier, false, false, true);
        deriving = true;
        try {
            player.addEffect(inst);
        } finally {
            deriving = false;
        }
    }

    /**
     * Raised only while this class is applying a status of its own.
     *
     * <p>{@code StandingEffects.onApplicable} denies Hero, Enemy and Nightmare from every source, because
     * reputation is the only legitimate one -- and this pass is that source. A plain boolean is enough: the
     * server thread is the only thread that ever gets here, and the flag is cleared in a finally.
     */
    private static boolean deriving;

    /** True while {@link #hold} is applying a derived status. Read by {@code StandingEffects.onApplicable}. */
    public static boolean isDeriving() {
        return deriving;
    }

    /** Fireworks over the player's head, once, the first time each Hero level is reached. */
    private static void celebrate(ServerPlayer player, int heroLevel) {
        if (!(player.level() instanceof ServerLevel level)) return;
        BrainNodeEvents.launchCelebration(level, player.position().add(0.0, 1.0, 0.0), 2 + heroLevel / 2);
        player.sendSystemMessage(net.minecraft.network.chat.Component
                .literal("The village names you Hero of the Village " + roman(heroLevel) + ".")
                .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD));
    }

    private static String roman(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; default -> "V"; };
    }

    // --- the log -------------------------------------------------------------------------------------

    /**
     * One line per change, kept short on purpose.
     *
     * <p>The UI panel is 252px wide and wraps rather than truncating, so a long line costs a second row and
     * pushes an older entry off the bottom. Name and cause are both clipped so a player called something
     * enormous cannot crowd everything else out of the log.
     */
    private void record(ServerPlayer player, int delta, String cause, int total) {
        long day = player.serverLevel().getServer().overworld().getDayTime() / 24000L;
        String c = cause.length() > 22 ? cause.substring(0, 22) : cause;
        logLine("Day " + day + ": " + name(player) + " " + c + " " + (delta > 0 ? "+" : "") + delta
                + " (" + total + ")");
    }

    private void logLine(String line) {
        log.addLast(line);
        while (log.size() > LOG_SIZE) log.removeFirst();
    }

    private static String name(ServerPlayer player) {
        String n = player.getGameProfile().getName();
        return n.length() > 12 ? n.substring(0, 12) : n;
    }

    // --- persistence ---------------------------------------------------------------------------------

    public static ReputationData load(CompoundTag tag, HolderLookup.Provider registries) {
        ReputationData d = new ReputationData();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            d.rep.put(c.getUUID("player"), c.getInt("rep"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> e : rep.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("player", e.getKey());
            c.putInt("rep", e.getValue());
            list.add(c);
        }
        tag.put("players", list);
        return tag;
    }
}
