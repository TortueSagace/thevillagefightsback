package com.example.villagefightsback.compat;

import com.example.villagefightsback.platform.PersistentData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/**
 * Belt-and-braces durability for the villager role flags, for modpacks that also run MCA Reborn.
 *
 * <p>MCA can REPLACE a vanilla villager (discard it, rebuild it as an MCA human). Its copy routine carries the
 * name, gender, age, villager data, trades and the SCOREBOARD TAGS across, but NOT
 * {@link Entity#getPersistentData()} -- which is where every role flag in this mod lives. A villager caught by
 * that replacement therefore comes out the other side with its settler / builder / miner / upgrade state wiped.
 *
 * <p>{@link MCACompat} already prevents this for the villagers we spawn ourselves (we build the MCA entity
 * directly, so there is nothing for MCA to convert). This class covers the cases we do NOT control:
 * <ul>
 *   <li>the player turned {@code mca_compat} off but kept MCA installed, so we are spawning vanilla villagers
 *       into MCA's queue again;</li>
 *   <li>the player added {@code mob_summoned} / {@code event} to MCA's {@code allowedSpawnReasons}, or added
 *       one of our entities to its {@code moddedVillagerWhitelist};</li>
 *   <li>a naturally spawned villager that had already been given a role in the same tick it is converted.</li>
 * </ul>
 *
 * <p>The mirror is a no-op unless MCA is installed, so a pack without MCA never grows a single extra scoreboard
 * tag. It is deliberately NOT gated on the {@code mca_compat} toggle: switching that off puts our villagers
 * back into MCA's conversion queue, which is precisely when this protection matters most.
 *
 * <p>Encoding: a boolean flag is mirrored as the bare tag {@code tvfb.<key>} (present == true); a numeric flag
 * as {@code tvfb.<key>.<value>}. Only SET flags are mirrored, so an unroled villager carries no tags at all.
 */
public final class VillagerRoleTags {
    private VillagerRoleTags() {}

    private static final String PREFIX = "tvfb.";

    /** Boolean role flags. Presence of the tag means true; a false/absent flag is never mirrored. */
    private static final String[] BOOL_KEYS = {
            "vgun_upgraded",   // UpgradeState: full stage-3 loadout
            "vgun_stage2",     // UpgradeState: stage-2 virus archer
            "vgun_stage4",     // UpgradeState: attacks apply Emerald Virus II
            "vgun_expansion",  // ExpansionManager: this villager is an expansion settler
            "vgun_founding",   // FoundingManager: this villager is a founding settler
            "vgun_settled",    // SettlerGoal: already settled, never settles twice
            "vgun_miner",      // MinerRole
            "vgun_builder",    // BuilderRole
    };

    /** Integer role payloads (small values: a TNT colour, a door index). */
    private static final String[] INT_KEYS = {
            "vgun_exp_tnt",      // ExpansionManager: which structure TNT this settler carries
            "vgun_builder_door", // BuilderRole: the door this builder owns
            "vgun_kills",        // VillagerKills: hostiles killed, which decides whether it earns a grave
    };

    /** Long role payloads (a packed BlockPos). */
    private static final String[] LONG_KEYS = {
            "vgun_exp_gm",       // ExpansionManager: the brain node this settler belongs to
    };

    /** Copy the villager's set role flags out to scoreboard tags, so they survive an MCA replacement. */
    public static void mirror(Entity e) {
        if (!(e instanceof Villager) || !MCACompat.present()) return;
        CompoundTag data = PersistentData.of(e);
        for (String key : BOOL_KEYS) {
            if (data.getBoolean(key)) e.addTag(PREFIX + key);
        }
        for (String key : INT_KEYS) {
            if (data.contains(key)) writeNumber(e, key, data.getInt(key));
        }
        for (String key : LONG_KEYS) {
            if (data.contains(key)) writeNumber(e, key, data.getLong(key));
        }
    }

    /**
     * Restore any role flag that survives only as a scoreboard tag. Call this BEFORE anything reads the flags
     * (the top of the entity-join handler). Existing persistent data always wins -- this only ever fills gaps,
     * so a villager whose data came through intact is left exactly as it was.
     */
    public static void restore(Entity e) {
        if (!(e instanceof Villager) || !MCACompat.present()) return;
        if (e.getTags().isEmpty()) return; // fast path: the overwhelming majority of villagers
        CompoundTag data = PersistentData.of(e);
        for (String key : BOOL_KEYS) {
            if (!data.getBoolean(key) && e.getTags().contains(PREFIX + key)) data.putBoolean(key, true);
        }
        for (String key : INT_KEYS) {
            if (data.contains(key)) continue;
            long v = readNumber(e, key);
            if (v != Long.MIN_VALUE) data.putInt(key, (int) v);
        }
        for (String key : LONG_KEYS) {
            if (data.contains(key)) continue;
            long v = readNumber(e, key);
            if (v != Long.MIN_VALUE) data.putLong(key, v);
        }
    }

    /** Write {@code tvfb.<key>.<value>}, dropping any stale tag for the same key first. */
    private static void writeNumber(Entity e, String key, long value) {
        String head = PREFIX + key + ".";
        String wanted = head + value;
        if (e.getTags().contains(wanted)) return; // already current -- nothing to rewrite
        // Copy the tag set before removing: getTags() is the live set and would throw on concurrent removal.
        for (String tag : e.getTags().toArray(new String[0])) {
            if (tag.startsWith(head)) e.removeTag(tag);
        }
        e.addTag(wanted);
    }

    /** The mirrored value for {@code key}, or {@link Long#MIN_VALUE} when there is no (parsable) tag. */
    private static long readNumber(Entity e, String key) {
        String head = PREFIX + key + ".";
        for (String tag : e.getTags()) {
            if (!tag.startsWith(head)) continue;
            try {
                return Long.parseLong(tag.substring(head.length()));
            } catch (NumberFormatException malformed) {
                return Long.MIN_VALUE; // hand-edited tag -- ignore rather than crash the join handler
            }
        }
        return Long.MIN_VALUE;
    }
}
