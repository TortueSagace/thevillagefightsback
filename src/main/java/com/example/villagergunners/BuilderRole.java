package com.example.villagergunners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;

/**
 * The "builder" role: a small number of villagers per village are tagged as wall builders and visibly named
 * "builder" while they work. Only builders run the wall-build goal. A builder that dies or stops moving for
 * too long loses the role (with a short cool-down so a different villager is chosen next), and the village
 * tops itself back up to {@link #BUILDER_COUNT} from the free villagers nearby.
 */
public final class BuilderRole {
    private BuilderRole() {}

    public static final int BUILDER_COUNT = 4;          // builders per village wall
    private static final String NBT_BUILDER = "vgun_builder";
    private static final String NBT_BUILDER_CD = "vgun_builder_cd"; // game time until re-eligible
    private static final String BUILDER_NAME = "builder";
    private static final long STUCK_TICKS = 100;         // 5s without moving -> lose the role
    private static final long DEMOTE_COOLDOWN = 200;     // 10s before a stuck-demoted villager is eligible again

    // Transient stuck tracking: uuid -> { last block position (asLong), game time last seen there }.
    private static final Map<UUID, long[]> LAST_MOVE = new HashMap<>();

    public static boolean isBuilder(Villager v) {
        return v.getPersistentData().getBoolean(NBT_BUILDER);
    }

    public static void promote(Villager v) {
        v.getPersistentData().putBoolean(NBT_BUILDER, true);
        v.setCustomName(Component.literal(BUILDER_NAME));
        v.setCustomNameVisible(true);
    }

    public static void demote(Villager v) {
        if (!isBuilder(v)) return;
        v.getPersistentData().putBoolean(NBT_BUILDER, false);
        if (v.getCustomName() != null && BUILDER_NAME.equals(v.getCustomName().getString())) {
            v.setCustomName(null);
            v.setCustomNameVisible(false);
        }
        LAST_MOVE.remove(v.getUUID());
    }

    /** A free villager that may be promoted: not already a builder and not on the post-stuck cool-down. */
    public static boolean eligible(Villager v, long now) {
        return !isBuilder(v) && now >= v.getPersistentData().getLong(NBT_BUILDER_CD);
    }

    /** True if this builder has sat on the same block for {@value #STUCK_TICKS}+ ticks (records movement too). */
    public static boolean isStuck(Villager v, long now) {
        long packed = v.blockPosition().asLong();
        long[] m = LAST_MOVE.get(v.getUUID());
        if (m == null || m[0] != packed) {
            LAST_MOVE.put(v.getUUID(), new long[]{packed, now});
            return false;
        }
        return now - m[1] >= STUCK_TICKS;
    }

    /** Demote a stuck builder and put it on cool-down so a different villager is picked next. */
    public static void demoteStuck(Villager v, long now) {
        demote(v);
        v.getPersistentData().putLong(NBT_BUILDER_CD, now + DEMOTE_COOLDOWN);
    }
}
