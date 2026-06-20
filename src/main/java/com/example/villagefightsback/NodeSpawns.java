package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Shared spawn helpers for the three node blocks: find a clear spot below (or above) a node, then spawn the
 * stage-appropriate golem / guard / wall defender. Plain golems/guards are spawned via the same path the
 * village's sunset reinforcements use, so the existing upgrade system adapts them to the brain node's stage;
 * at stage 3+ we spawn the upgraded form directly so the result is immediate.
 */
public final class NodeSpawns {
    private NodeSpawns() {}

    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");

    private static final int GOLEM_CAP = 20;               // the Iron Factory halts at 20+ golems in the zone
    static double GOLEM_ZONE_RADIUS = 100.0;  // brain influence radius (set from Config.apply(); base 100)

    /** A clear feet position with {@code height} non-colliding blocks: prefer the gap just BELOW the node, else
     *  just ABOVE it. Null if neither side has room. */
    public static BlockPos belowOrAbove(ServerLevel level, BlockPos node, int height) {
        BlockPos below = node.below(height);     // the mob's TOP block ends just under the node
        if (clear(level, below, height)) return below;
        BlockPos above = node.above();           // feet directly above the node
        if (clear(level, above, height)) return above;
        return null;
    }

    public static boolean clear(ServerLevel level, BlockPos feet, int height) {
        for (int i = 0; i < height; i++) {
            BlockPos p = feet.above(i);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
        }
        return true;
    }

    /** A golem below/above the node, in the stage-appropriate form: iron (stage 1), Defender Golem (stage 2-3),
     *  or Gigachad Golem (stage 4+). The upgraded forms are spawned directly so the result is immediate. */
    public static void spawnGolem(ServerLevel level, BlockPos node, int stage) {
        // Halt the Iron Factory once the zone is saturated: stop spawning when 20+ golems (iron / defender /
        // gigachad, village or player) are already alive within the income radius of the factory node.
        AABB zone = new AABB(node).inflate(GOLEM_ZONE_RADIUS);
        if (level.getEntitiesOfClass(IronGolem.class, zone, g -> g.isAlive()).size() >= GOLEM_CAP) return;
        BlockPos spot = belowOrAbove(level, node, 3);
        if (spot == null) return;
        float yaw = level.getRandom().nextFloat() * 360.0F;
        // At ANY stage, a 10% chance to also forge a stray EMERALD iron golem alongside the normal one.
        if (level.getRandom().nextFloat() < 0.10F) {
            EmeraldIronGolem eg = ModEntities.EMERALD_IRON_GOLEM.get().create(level);
            if (eg != null) {
                BlockPos espot = belowOrAbove(level, node, 3);
                if (espot != null) {
                    eg.moveTo(espot.getX() + 0.5, espot.getY(), espot.getZ() + 0.5,
                            level.getRandom().nextFloat() * 360.0F, 0.0F);
                    eg.finalizeSpawn(level, level.getCurrentDifficultyAt(espot), MobSpawnType.EVENT, null, null);
                    level.addFreshEntity(eg);
                }
            }
        }
        if (stage >= 4) {
            GigachadGolemEntity g = ModEntities.GIGACHAD_GOLEM.get().create(level);
            if (g == null) return;
            g.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0F);
            g.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null, null);
            UpgradeState.setUpgraded(g);                 // gigachad is born fully upgraded
            level.addFreshEntity(g);
        } else if (stage >= 2) {
            DefenderGolemEntity d = ModEntities.DEFENDER_GOLEM.get().create(level);
            if (d == null) return;
            d.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0F);
            d.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null, null);
            level.addFreshEntity(d);                     // defender golem carries its own kit by construction
        } else {
            EntityType.IRON_GOLEM.spawn(level, spot, MobSpawnType.EVENT); // stage 1: plain golem
        }
    }

    /** One guard below/above the node, armed to the brain node's stage (full kit at stage 3+). */
    public static void spawnGuard(ServerLevel level, BlockPos node, int stage) {
        EntityType<?> guardType = ForgeRegistries.ENTITY_TYPES.getValue(GUARD_ID);
        if (guardType == null) return;
        BlockPos spot = belowOrAbove(level, node, 2);
        if (spot == null) return;
        Entity guard = guardType.spawn(level, spot, MobSpawnType.EVENT);
        if (stage >= 3 && guard instanceof PathfinderMob pm) {
            UpgradeState.setUpgraded(pm);
            VillageEvents.applyUpgrade(pm);              // give it the full stage-3 guard kit now
        }
    }

    /** A wall defender directly ABOVE the node -- unless one already stands above it (no stacking). A stage-4
     *  brain spawns the stage-4 (rocket) defender; otherwise the normal one (which auto-upgrades anyway). */
    public static void spawnWallDefenderAbove(ServerLevel level, BlockPos node, int stage) {
        BlockPos feet = node.above();
        AABB column = new AABB(feet.getX(), feet.getY(), feet.getZ(), feet.getX() + 1, feet.getY() + 4, feet.getZ() + 1);
        if (!level.getEntitiesOfClass(WallDefenderEntity.class, column).isEmpty()) return; // already one above
        if (!clear(level, feet, 2)) return;
        // ALWAYS the base (gun) wall defender here, NEVER the stage-4 rocket one -- regardless of village stage.
        // A rocket turret's explosions would destroy the powder-snow trap under the lure tower, and the lure
        // only works while enemies keep walking onto that snow.
        WallDefenderEntity d = ModEntities.WALL_DEFENDER.get().create(level);
        if (d == null) return;
        d.setGunOnly(true); // never upgrade to rockets, even at stage 4 -- rockets would wreck the powder-snow trap
        d.moveTo(feet.getX() + 0.5, feet.getY() + 0.1, feet.getZ() + 0.5, 0.0F, 0.0F);
        // Finalize like the golem/guard (and spawn-egg) paths so the turret initialises properly and appears.
        d.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(d);
    }
}
