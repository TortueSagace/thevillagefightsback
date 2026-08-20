package com.example.villagefightsback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The game mode chosen for THIS world, stored with the save (on the overworld's data storage) so that every
 * world keeps its own answer. Without this the three infection switches would live only in the shared
 * {@code config/thevillagefightsback-common.toml}, and creating a second world would silently re-configure the
 * first one.
 *
 * <p>Three pieces of state:
 * <ul>
 *   <li>{@code pending} -- this world was freshly created and nobody has answered the mode screen yet, so the
 *       next player who is allowed to answer gets it (see {@link WorldModeEvents});</li>
 *   <li>{@code chosen} -- an answer exists, so the flags below override the .toml while this world is loaded;</li>
 *   <li>the three flags themselves, which start as a {@link WorldMode} preset but are kept in step afterwards
 *       with any {@code /thevillagefightsback config} change, so a customised world still restores correctly.</li>
 * </ul>
 */
public class WorldModeData extends SavedData {
    private static final String NAME = "thevillagefightsback_world_mode";

    private boolean pending;
    private boolean chosen;
    private boolean blockInfection;
    private boolean mobInfection;
    private boolean emeraldInfestation;
    /** -1 until the difficulty screen is answered, so an old save can be told from one that picked Peaceful. */
    private int difficulty = -1;

    /**
     * Survival experience, as opposed to Creative.
     *
     * <p>Creative is the mod as it has always been: you watch a village evolve. Survival makes the village
     * something you have to work FOR -- brain nodes found in the world are dead until you revive one, the
     * starter bell is replaced by a map to a buried one, and evolution costs metal as well as emeralds.
     *
     * <p>Defaults false, which is what every world created before this existed answers, so an old save keeps
     * playing exactly as it did.
     */
    private boolean survival;
    /** True from world creation until BOTH creation screens have been answered. */
    private boolean setupPending;

    /**
     * Server tick this world was first seen still waiting for an answer. Deliberately NOT saved: it restarts
     * with the server, so a save that was left waiting gets one fresh grace period per load and then unsticks
     * itself for good.
     */
    private transient long firstPendingTick = -1L;

    /** How long the whole mod will hold for an answer before giving up and running on the config defaults. */
    private static final long SETUP_GRACE_TICKS = 20L * 120L;   // 2 minutes

    public WorldModeData() {}

    public static WorldModeData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(WorldModeData::load, WorldModeData::new, NAME);
    }

    /** The stored data, or null when this world has never had any (used to tell a brand-new save from an old one). */
    public static WorldModeData getExisting(MinecraftServer server) {
        return server.overworld().getDataStorage().get(WorldModeData::load, NAME);
    }

    public boolean isPending() { return pending; }
    public boolean isChosen() { return chosen; }

    /** Flag this world as freshly created, so the first eligible player is asked to pick a mode. */
    public void markPending() {
        if (pending || chosen) return;
        pending = true;
        setupPending = true;
        setDirty();
    }

    /**
     * True while the world-creation screens are still being answered -- EITHER of them.
     *
     * <p>The mode is answered first and the difficulty a moment later, and the world is running throughout.
     * Gating anything on the mode alone leaves a live window between the two screens, which is where "The birth
     * of a brain" was being handed out before the player had finished being asked what kind of world they
     * wanted. A save from before difficulties existed has this false and is unaffected.
     */
    public boolean isSetupPending() { return setupPending; }

    /**
     * Should the mod hold still and wait for the creation screens? True while they are genuinely being
     * answered, false once they are -- or once we have waited long enough that they clearly never will be.
     *
     * <p><b>The grace period is the point.</b> {@code setupPending} is only ever cleared by an answer to the
     * DIFFICULTY screen, and that answer comes from the client. Anything that stops it arriving -- another
     * mod's GUI taking the screen, the player pressing escape, a disconnect between the two questions -- used
     * to leave this true forever, and it is written to the save, so the world stayed that way across restarts.
     * With the whole server tick gated on it, the result was a world where the mod simply did nothing: a bell
     * raised no brain node, and a node placed by hand just sat there. Worse, the re-prompt on login keyed off
     * {@code pending}, which the MODE answer had already cleared, so nothing ever asked again.
     *
     * <p>So the wait is now bounded. After {@value #SETUP_GRACE_TICKS} ticks the world starts anyway on the
     * config defaults, and the flag is cleared and saved so it never blocks again.
     */
    public boolean holdForSetup(MinecraftServer server) {
        if (!setupPending) return false;
        long now = server.getTickCount();
        if (firstPendingTick < 0L) firstPendingTick = now;
        if (now - firstPendingTick < SETUP_GRACE_TICKS) return true;
        setupPending = false;
        setDirty();
        TheVillageFightsBack.LOGGER.warn(
                "[The Village Fights Back] the world-creation screens were never answered ({}s). Starting on the "
                + "config defaults so the mod is not held back; use /thevillagefightsback config to change them.",
                SETUP_GRACE_TICKS / 20L);
        return false;
    }

    /** Record an answer to the mode screen and push it over the .toml values for this session. */
    public void choose(WorldMode mode) {
        blockInfection = mode.blockInfection();
        mobInfection = mode.mobInfection();
        emeraldInfestation = mode.emeraldInfestation();
        chosen = true;
        pending = false;
        setDirty();
        pushToConfig();
    }

    /**
     * Record an answer to the DIFFICULTY screen, which is asked straight after the mode screen.
     *
     * <p>Separate from {@link #choose(WorldMode)} because the two screens ask different questions -- what the
     * mod does, then how it treats you -- and because a world created before difficulties existed has answered
     * the first and not the second. Such a world keeps difficulty -1 and simply plays on the config default.
     */
    public void chooseDifficulty(Difficulty d) {
        difficulty = d.id();
        setupPending = false;   // both questions are in; the world may start
        setDirty();
        pushToConfig();
    }

    /** Record an answer to the EXPERIENCE screen, asked between the mode and difficulty screens. */
    public void chooseExperience(boolean survivalExperience) {
        this.survival = survivalExperience;
        setDirty();
        // setupPending is NOT cleared here: the difficulty answer still has to arrive, and on the Creative
        // path it arrives a moment later as an automatic Neutral rather than from a screen.
    }

    /** True in a Survival-experience world. */
    public boolean isSurvival() {
        return survival;
    }

    /** Survival for the world this server is running, false if it was never asked. Null-safe for early calls. */
    public static boolean isSurvival(MinecraftServer server) {
        WorldModeData d = getExisting(server);
        return d != null && d.survival;
    }

    /** The difficulty this world was created with, or null if it was never asked (a pre-existing save). */
    public Difficulty chosenDifficulty() {
        return difficulty < 0 ? null : Difficulty.byId(difficulty);
    }

    /**
     * What {@link #pushToConfig} last wrote into the live config. Transient: it is a record of what THIS
     * process did, not world state, and it is what lets {@link #reconcileFromConfig} tell an edit apart from
     * a reload.
     */
    private transient boolean[] lastPushed;

    /** Push the stored flags over the live config values (in memory only -- the .toml file is never written). */
    public void pushToConfig() {
        if (chosen) {
            Config.applyInfectionFlags(blockInfection, mobInfection, emeraldInfestation);
            lastPushed = new boolean[]{blockInfection, mobInfection, emeraldInfestation};
        }
        if (difficulty >= 0) Config.DIFFICULTY.set(difficulty);
    }

    /**
     * A .toml reload happened. Adopt any infection flag the FILE has changed, then push.
     *
     * <p>Pushing unconditionally was wrong, and quietly so: the world's mode was re-asserted over the live
     * config on EVERY reload, so hand-editing {@code enable_mob_infection = true} in the .toml was reverted
     * within a tick by whatever was chosen at world creation. Evolving Village is (false, false, false), so a
     * player who picked it and then switched mob infection on in the file saw nothing happen at all. The
     * in-game {@code config} command escaped this only because it mirrors the change into this object first.
     *
     * <p>Telling an edit from a plain reload needs no guesswork: we know exactly what we last wrote, so a
     * value that no longer matches was changed by somebody else, and that somebody is the player.
     */
    public void reconcileFromConfig() {
        if (!chosen || lastPushed == null) { pushToConfig(); return; }
        boolean b = Config.ENABLE_BLOCK_INFECTION.get();
        boolean m = Config.ENABLE_MOB_INFECTION.get();
        boolean i = Config.ENABLE_EMERALD_INFESTATION.get();
        boolean changed = false;
        if (b != lastPushed[0]) { blockInfection = b; Config.noteInfectionFlag("enable_block_infection", b); changed = true; }
        if (m != lastPushed[1]) { mobInfection = m; Config.noteInfectionFlag("enable_mob_infection", m); changed = true; }
        if (i != lastPushed[2]) { emeraldInfestation = i; Config.noteInfectionFlag("enable_emerald_infestation", i); changed = true; }
        if (changed) {
            setDirty();
            TheVillageFightsBack.LOGGER.info(
                    "[The Village Fights Back] adopted a hand-edited infection setting from the config file "
                    + "(block={}, mob={}, infestation={}).", blockInfection, mobInfection, emeraldInfestation);
        }
        pushToConfig();
    }

    /** The preset these flags currently match, or null once the player has customised them individually. */
    public WorldMode currentMode() {
        return chosen ? WorldMode.matching(blockInfection, mobInfection, emeraldInfestation) : null;
    }

    /**
     * Mirror a {@code /thevillagefightsback config <key> <value>} change of one of the three infection switches
     * into this world's stored answer, so the change survives a reload instead of being overwritten by the
     * mode that was picked at creation. Any other key is ignored.
     */
    public static void onConfigCommandSet(MinecraftServer server, String key, Object newValue) {
        if (server == null || !(newValue instanceof Boolean v)) return;
        boolean known = switch (key) {
            case "enable_block_infection", "enable_mob_infection", "enable_emerald_infestation" -> true;
            default -> false;
        };
        if (!known) return;
        WorldModeData data = get(server);
        switch (key) {
            case "enable_block_infection" -> data.blockInfection = v;
            case "enable_mob_infection" -> data.mobInfection = v;
            default -> data.emeraldInfestation = v;
        }
        if (!data.chosen) {
            // The world never answered the screen (an old save, or a fresh one edited before spawning): adopt the
            // other two flags as they stand right now so the stored triple is complete and self-consistent.
            if (!"enable_block_infection".equals(key)) data.blockInfection = Config.ENABLE_BLOCK_INFECTION.get();
            if (!"enable_mob_infection".equals(key)) data.mobInfection = Config.ENABLE_MOB_INFECTION.get();
            if (!"enable_emerald_infestation".equals(key)) data.emeraldInfestation = Config.ENABLE_EMERALD_INFESTATION.get();
            data.chosen = true;
            data.pending = false;
        }
        data.setDirty();
        Config.noteInfectionFlag(key, v); // keep the .toml snapshot in step so server-stop does not undo this
    }

    /** Drop this world's override entirely (used by {@code config reset_default_configs}). */
    public static void clear(MinecraftServer server) {
        if (server == null) return;
        WorldModeData data = get(server);
        data.chosen = false;
        data.pending = false;
        data.setDirty();
    }

    public static WorldModeData load(CompoundTag tag) {
        WorldModeData data = new WorldModeData();
        data.pending = tag.getBoolean("Pending");
        data.chosen = tag.getBoolean("Chosen");
        data.blockInfection = tag.getBoolean("BlockInfection");
        data.mobInfection = tag.getBoolean("MobInfection");
        data.emeraldInfestation = tag.getBoolean("EmeraldInfestation");
        data.difficulty = tag.contains("Difficulty") ? tag.getInt("Difficulty") : -1;
        data.survival = tag.getBoolean("Survival");
        data.setupPending = tag.getBoolean("SetupPending");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Pending", pending);
        tag.putBoolean("Chosen", chosen);
        tag.putBoolean("BlockInfection", blockInfection);
        tag.putBoolean("MobInfection", mobInfection);
        tag.putBoolean("EmeraldInfestation", emeraldInfestation);
        tag.putInt("Difficulty", difficulty);
        tag.putBoolean("Survival", survival);
        tag.putBoolean("SetupPending", setupPending);
        return tag;
    }
}
