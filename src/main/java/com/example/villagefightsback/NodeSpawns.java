package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
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

    /** An iron golem below/above the node -- a gigachad if the brain node is stage 3+. */
    public static void spawnGolem(ServerLevel level, BlockPos node, int stage) {
        BlockPos spot = belowOrAbove(level, node, 3);
        if (spot == null) return;
        if (stage >= 3) {
            GigachadGolemEntity g = ModEntities.GIGACHAD_GOLEM.get().create(level);
            if (g == null) return;
            g.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
            g.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null, null);
            UpgradeState.setUpgraded(g);                 // gigachad is born fully upgraded
            level.addFreshEntity(g);
        } else {
            EntityType.IRON_GOLEM.spawn(level, spot, MobSpawnType.EVENT); // plain golem (auto-arms with the village)
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
        EntityType<WallDefenderEntity> type =
                stage >= 4 ? ModEntities.WALL_DEFENDER_STAGE4.get() : ModEntities.WALL_DEFENDER.get();
        WallDefenderEntity d = type.create(level);
        if (d == null) return;
        d.moveTo(feet.getX() + 0.5, feet.getY() + 0.1, feet.getZ() + 0.5, 0.0F, 0.0F);
        // Finalize like the golem/guard (and spawn-egg) paths so the turret initialises properly and appears.
        d.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), MobSpawnType.EVENT, null, null);
        level.addFreshEntity(d);
    }
}
