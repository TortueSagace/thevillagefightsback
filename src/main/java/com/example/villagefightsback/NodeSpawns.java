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

    /**
     * A feet position where {@code type} fits: the gap just BELOW the node, else just ABOVE it, else BESIDE it.
     *
     * <p>The old version measured a fixed number of cells in a 1x1 COLUMN, which is wrong twice over for the
     * golems this mostly spawns: an iron/Defender/Gigachad golem is 2.7 tall (so 3 cells is right) but also 1.4
     * WIDE, so it does not fit a 1-block shaft at all and was being forged into the walls. Testing the real
     * hitbox fixes both, and the beside-the-node fallback means a tight factory still produces its golem instead
     * of silently producing nothing.
     */
    public static BlockPos spawnSpotFor(ServerLevel level, BlockPos node, EntityType<?> type) {
        int height = net.minecraft.util.Mth.ceil(type.getHeight());
        BlockPos below = node.below(height);     // the mob's TOP block ends just under the node
        if (BrainNodeEvents.fitsFreely(level, below, type)) return below;
        BlockPos above = node.above();           // feet directly above the node
        if (BrainNodeEvents.fitsFreely(level, above, type)) return above;
        return BrainNodeEvents.nearestFit(level, node, type, 4); // beside it, nearest first
    }

    /** A golem below/above the node, in the stage-appropriate form: iron (stage 1), Defender Golem (stage 2-3),
     *  or Gigachad Golem (stage 4+). The upgraded forms are spawned directly so the result is immediate. */
    /** The golem form this stage forges, so the spot search can measure the RIGHT hitbox before spawning it. */
    private static EntityType<? extends net.minecraft.world.entity.Mob> golemType(int stage) {
        if (stage >= 4) return ModEntities.GIGACHAD_GOLEM.get();
        if (stage >= 2) return ModEntities.DEFENDER_GOLEM.get();
        return EntityType.IRON_GOLEM;
    }

    public static void spawnGolem(ServerLevel level, BlockPos node, int stage) {
        // Halt the Iron Factory once the zone is saturated: stop spawning when 20+ golems (iron / defender /
        // gigachad, village or player) are already alive within the income radius of the factory node.
        AABB zone = new AABB(node).inflate(GOLEM_ZONE_RADIUS);
        if (level.getEntitiesOfClass(IronGolem.class, zone, g -> g.isAlive()).size() >= GOLEM_CAP) return;
        BlockPos spot = spawnSpotFor(level, node, golemType(stage));
        if (spot == null) return;
        float yaw = level.getRandom().nextFloat() * 360.0F;
        // 10% chance to also forge a stray EMERALD golem alongside the normal one, in the SAME stage-appropriate
        // form: emerald iron golem (stage 1), emerald Defender Golem (stage 2-3), emerald Gigachad Golem (stage 4+).
        if (Config.emeraldMobsEnabled() && level.getRandom().nextFloat() < 0.10F) {
            EntityType<? extends net.minecraft.world.entity.Mob> etype = stage >= 4 ? ModEntities.EMERALD_GIGACHAD_GOLEM.get()
                    : stage >= 2 ? ModEntities.EMERALD_DEFENDER_GOLEM.get()
                    : ModEntities.EMERALD_IRON_GOLEM.get();
            BlockPos espot = spawnSpotFor(level, node, etype);
            if (espot != null) {
                net.minecraft.world.entity.Mob eg = etype.create(level);
                if (eg != null) {
                    eg.moveTo(espot.getX() + 0.5, espot.getY(), espot.getZ() + 0.5,
                            level.getRandom().nextFloat() * 360.0F, 0.0F);
                    eg.finalizeSpawn(level, level.getCurrentDifficultyAt(espot), MobSpawnType.EVENT, null, null);
                    if (eg instanceof EmeraldGigachadGolemEntity) UpgradeState.setUpgraded(eg); // born fully upgraded
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
            EntityType.IRON_GOLEM.spawn(level, (net.minecraft.world.item.ItemStack) null, (net.minecraft.world.entity.player.Player) null, spot, MobSpawnType.EVENT, false, false); // stage 1: plain golem
        }
    }

    /** One guard below/above the node, armed to the brain node's stage (full kit at stage 3+). */
    public static void spawnGuard(ServerLevel level, BlockPos node, int stage) {
        // An MCA human wearing the mca:guard profession when MCA is installed, else the Guard Villagers guard.
        EntityType<?> guardType = com.example.villagefightsback.compat.MCACompat.guardType(level.getRandom());
        if (guardType == null) return;
        BlockPos spot = spawnSpotFor(level, node, guardType);
        if (spot == null) return;
        // 1.19.2 keeps the short spawn(level, pos, type) overload, but the nulls in the long form are ambiguous
        // between the ItemStack and CompoundTag overloads, so this branch spells the long form out everywhere.
        Entity guard = guardType.spawn(level, (net.minecraft.world.item.ItemStack) null, (net.minecraft.world.entity.player.Player) null, spot, MobSpawnType.EVENT, false, false);
        com.example.villagefightsback.compat.MCACompat.makeGuard(guard);
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
        if (!BrainNodeEvents.fitsFreely(level, feet, ModEntities.WALL_DEFENDER.get())) return;
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
