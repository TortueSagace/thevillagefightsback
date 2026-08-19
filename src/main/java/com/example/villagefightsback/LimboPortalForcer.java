package com.example.villagefightsback;

import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Moves a creature through a Limbo Portal, applying the dimension's 16x space compression (twice the nether's)
 * and finding-or-building a destination portal at the scaled coordinates. Portals are never built inside the
 * original Limbo cube -- if the scaled target lands on the cube, the portal is raised to just above it.
 *
 *   overworld (x,z)  ->  Limbo (x/16, z/16)
 *   Limbo (x,z)      ->  overworld (x*16, z*16)
 */
public final class LimboPortalForcer {
    private LimboPortalForcer() {}

    private static final int SEARCH_RADIUS = 24;     // reuse a nearby existing portal instead of stacking new
    private static final int FLOAT_Y = 128;          // default altitude for a fresh Limbo portal in open void
    private static final int CUBE_MIN = -11, CUBE_MAX = 10; // the original cube spans x/z -11..10
    private static final int ABOVE_CUBE_Y = 83;      // cube top is 81 -> sit just above it

    /**
     * Send {@code entity} through the portal it is standing in. A DRIFT chunk mirrors a real twin chunk, so a
     * portal there links 1:1 to that exact twin spot (overworld-drift -> overworld at the same x,z; nether-drift
     * -> the nether twin). A non-drift Limbo location uses the 16x compression overworld<->Limbo.
     */
    public static void teleport(Entity entity, ServerLevel from) {
        MinecraftServer server = from.getServer();
        if (server == null) return;
        long seed = from.getSeed(); // shared across dimensions; the Limbo drift roll uses it
        boolean inLimbo = LimboDimension.LIMBO.equals(from.dimension());
        boolean destIsLimbo = !inLimbo;
        int ex = entity.blockPosition().getX(), ez = entity.blockPosition().getZ();

        ServerLevel dest;
        int tx, tz;
        boolean driftDest = false; // destination is a drift chunk in the Limbo (has terrain, not void)

        if (!inLimbo) {
            // Entering Limbo.
            dest = server.getLevel(LimboDimension.LIMBO);
            int cx = ex >> 4, cz = ez >> 4;
            if (Level.OVERWORLD.equals(from.dimension()) && LimboDrift.type(seed, cx, cz) == LimboDrift.Type.OVERWORLD) {
                tx = ex; tz = ez; driftDest = true;              // this spot mirrors a drift chunk 1:1
            } else {
                tx = Math.floorDiv(ex, 16); tz = Math.floorDiv(ez, 16); // compress 16x
            }
        } else {
            // Leaving Limbo.
            int cx = ex >> 4, cz = ez >> 4;
            LimboDrift.Type dt = LimboDrift.type(seed, cx, cz);
            if (dt == LimboDrift.Type.OVERWORLD) {
                dest = server.getLevel(Level.OVERWORLD); tx = ex; tz = ez;          // 1:1 to the overworld twin
            } else if (dt == LimboDrift.Type.NETHER) {
                dest = server.getLevel(Level.NETHER);
                tx = ((cx >> 3) << 4) + (ex & 15); tz = ((cz >> 3) << 4) + (ez & 15); // the nether twin spot
            } else {
                dest = server.getLevel(Level.OVERWORLD);
                tx = ex * 16; tz = ez * 16;                                          // de-compress 16x
            }
        }
        if (dest == null) return;

        BlockPos stand = findOrBuildPortal(dest, tx, tz, destIsLimbo, driftDest);
        double x = stand.getX() + 0.5, y = stand.getY(), z = stand.getZ() + 0.5;

        if (entity instanceof ServerPlayer player) {
            player.teleportTo(dest, x, y, z, player.getYRot(), player.getXRot());
            player.setPortalCooldown();
        } else {
            Entity moved = com.example.villagefightsback.platform.DimensionTravel.teleport(entity, dest, x, y, z);
            if (moved != null) moved.setPortalCooldown();
        }
    }

    /** Find an existing Limbo portal near the target, else build one; return the spot to stand on. */
    private static BlockPos findOrBuildPortal(ServerLevel dest, int tx, int tz, boolean inLimbo, boolean driftDest) {
        int baseY = chooseBaseY(dest, tx, tz, inLimbo, driftDest);

        // 1. Reuse a nearby existing portal (avoid stacking duplicates on repeated trips).
        BlockPos existing = findNearbyPortal(dest, tx, baseY, tz);
        if (existing != null) return existing;

        // 2. Build a fresh portal (5x5 obsidian platform + fake-obsidian frame + green portal interior).
        return buildPortal(dest, new BlockPos(tx, baseY, tz));
    }

    /** Where the portal's interior-bottom sits. The void Limbo gets a floating platform (raised above the
     *  original cube); a drift chunk and any natural world land on the actual surface. */
    private static int chooseBaseY(ServerLevel dest, int tx, int tz, boolean inLimbo, boolean driftDest) {
        if (inLimbo && !driftDest) {
            boolean onCube = tx >= CUBE_MIN && tx <= CUBE_MAX && tz >= CUBE_MIN && tz <= CUBE_MAX;
            return onCube ? ABOVE_CUBE_Y : FLOAT_Y;
        }
        // A drift chunk (has copied terrain) or a natural world: land on the surface.
        int surface = dest.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz);
        return Math.max(dest.getMinBuildHeight() + 2, surface + 1);
    }

    private static BlockPos findNearbyPortal(ServerLevel level, int cx, int cy, int cz) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        int bestD = Integer.MAX_VALUE;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                    m.set(cx + dx, cy + dy, cz + dz);
                    if (!level.isLoaded(m)) continue;
                    if (level.getBlockState(m).is(ModBlocks.LIMBO_PORTAL.get())) {
                        // stand on the block just below the lowest portal cell in this column
                        BlockPos.MutableBlockPos b = m.mutable();
                        while (level.getBlockState(b.below()).is(ModBlocks.LIMBO_PORTAL.get())) b.move(Direction.DOWN);
                        int d = dx * dx + dy * dy + dz * dz;
                        if (d < bestD) { bestD = d; best = b.immutable(); }
                    }
                }
            }
        }
        return best;
    }

    /** Build a 2x3 Limbo portal (axis X) with a solid obsidian frame and a 5x5 obsidian platform under it.
     *  {@code interiorBase} is the bottom-centre-left interior cell; returns the spot to stand on. */
    private static BlockPos buildPortal(ServerLevel level, BlockPos interiorBase) {
        int bx = interiorBase.getX(), by = interiorBase.getY(), bz = interiorBase.getZ();
        // Real (solid, impassable) obsidian frame: a portal's frame loses its "fake"/passable nature so nobody can
        // slip out through it -- the portal itself is the only way through.
        BlockState frame = Blocks.OBSIDIAN.defaultBlockState();
        BlockState platform = Blocks.OBSIDIAN.defaultBlockState();
        BlockState portalX = ModBlocks.LIMBO_PORTAL.get().defaultBlockState()
                .setValue(LimboPortalBlock.AXIS, Direction.Axis.X);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        // 5x5 standing platform one block below the frame base.
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                m.set(bx + dx, by - 1, bz + dz);
                level.setBlock(m, platform, 3);
            }
        }
        // Clear the air column where the frame + interior go (2 wide along X, 3 tall, the portal plane at z=bz).
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                m.set(bx + dx, by + dy, bz);
                level.setBlock(m, air, 3);
            }
        }
        // Frame: bottom & top rows (x -1..2), left & right columns (y 0..2).
        for (int dx = -1; dx <= 2; dx++) {
            level.setBlock(m.set(bx + dx, by - 1, bz), frame, 3);
            level.setBlock(m.set(bx + dx, by + 3, bz), frame, 3);
        }
        for (int dy = 0; dy <= 2; dy++) {
            level.setBlock(m.set(bx - 1, by + dy, bz), frame, 3);
            level.setBlock(m.set(bx + 2, by + dy, bz), frame, 3);
        }
        // Interior: the 2x3 green portal.
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                level.setBlock(m.set(bx + dx, by + dy, bz), portalX, 3);
            }
        }
        return new BlockPos(bx, by, bz); // stand in the interior, on the bottom frame row
    }
}
