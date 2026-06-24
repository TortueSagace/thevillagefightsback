package com.example.villagefightsback;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One pane of a CHUNK PORTAL: the four walls around a 16x16 chunk are built from these (see
 * {@link ChunkPortalManager}). It looks and swirls exactly like the {@link LimboPortalBlock} -- the model,
 * collision-less shape, particles and render shape are all inherited -- but a PLAYER who dwells in it is moved
 * 1:1 (NO 16x compression) between the overworld and the Limbo at the same x/z.
 *
 * <p>Only players cross by walking through. Mobs come OUT via the per-portal timed emergence in
 * {@link ChunkPortalManager}; they deliberately do NOT cross by walking into a pane, which would route them
 * through {@code changeDimension} and trip the {@link LimboManager} containment auto-leash meant for banished
 * creatures.
 */
public class ChunkPortalBlock extends LimboPortalBlock {
    /** Per-player dwell tracking: UUID -> {lastGameTime, consecutiveTicksInside}. */
    private static final Map<UUID, long[]> DWELL = new ConcurrentHashMap<>();

    public ChunkPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl)) return;
        if (!(entity instanceof Player player)) return; // only players walk through; mobs emerge instead
        if (entity.isPassenger() || entity.isVehicle() || !entity.canChangeDimensions()) return;
        if (entity.isOnPortalCooldown()) { entity.setPortalCooldown(); return; }

        long now = sl.getGameTime();
        UUID id = entity.getUUID();
        long[] d = DWELL.get(id);
        if (d != null && d[0] == now) return; // already counted this tick (overlapping panes)
        long count = (d != null && d[0] == now - 1) ? d[1] + 1 : 1;
        if (DWELL.size() > 128) DWELL.values().removeIf(e -> now - e[0] > 100); // prune wanderers
        DWELL.put(id, new long[]{now, count});

        // Survival players must dwell ~2s; creative players go through at once.
        int needed = player.getAbilities().instabuild ? 1 : 40;
        if (count >= needed) {
            DWELL.remove(id);
            entity.setPortalCooldown();
            ChunkPortalManager.crossPlayer(player, sl, pos);
        }
    }
}
