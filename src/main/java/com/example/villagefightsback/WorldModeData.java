package com.example.villagefightsback;

import net.minecraft.core.HolderLookup;
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

    public WorldModeData() {}

    /**
     * The constructor/deserialiser pair the data storage needs. 1.21 replaced the two loose method references
     * {@code computeIfAbsent} used to take with a single {@link SavedData.Factory}, and threads a
     * {@link HolderLookup.Provider} through the deserialiser; this data is plain booleans so the provider is
     * unused, but it has to be in the signature. Shared by {@link #get} and {@link #getExisting} so both agree
     * on how the file is read, exactly as the paired method references did on main.
     */
    private static SavedData.Factory<WorldModeData> factory() {
        return new SavedData.Factory<>(WorldModeData::new, WorldModeData::load);
    }

    public static WorldModeData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    /** The stored data, or null when this world has never had any (used to tell a brand-new save from an old one). */
    public static WorldModeData getExisting(MinecraftServer server) {
        return server.overworld().getDataStorage().get(factory(), NAME);
    }

    public boolean isPending() { return pending; }
    public boolean isChosen() { return chosen; }

    /** Flag this world as freshly created, so the first eligible player is asked to pick a mode. */
    public void markPending() {
        if (pending || chosen) return;
        pending = true;
        setDirty();
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

    /** Push the stored flags over the live config values (in memory only -- the .toml file is never written). */
    public void pushToConfig() {
        if (!chosen) return;
        Config.applyInfectionFlags(blockInfection, mobInfection, emeraldInfestation);
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

    public static WorldModeData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldModeData data = new WorldModeData();
        data.pending = tag.getBoolean("Pending");
        data.chosen = tag.getBoolean("Chosen");
        data.blockInfection = tag.getBoolean("BlockInfection");
        data.mobInfection = tag.getBoolean("MobInfection");
        data.emeraldInfestation = tag.getBoolean("EmeraldInfestation");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Pending", pending);
        tag.putBoolean("Chosen", chosen);
        tag.putBoolean("BlockInfection", blockInfection);
        tag.putBoolean("MobInfection", mobInfection);
        tag.putBoolean("EmeraldInfestation", emeraldInfestation);
        return tag;
    }
}
