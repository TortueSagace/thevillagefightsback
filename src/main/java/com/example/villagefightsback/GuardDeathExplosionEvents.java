package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import com.example.villagefightsback.platform.event.LivingDeathEvent;
import com.example.villagefightsback.platform.SubscribeEvent;
import com.example.villagefightsback.platform.ModRegistries;

/**
 * A stage-3 (upgraded) Guard Villagers guard that is killed BY a hostile mob goes out with a bang: a
 * TNT-strength explosion that also sets the whole blast range on fire. Only fires for upgraded guards
 * killed by an {@link Enemy} (not fall damage, drowning, friendly fire, etc.).
 */
public final class GuardDeathExplosionEvents {
    private GuardDeathExplosionEvents() {}

    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");
    private static final float BLAST_RADIUS = 4.0F;   // TNT-strength
    private static final int FIRE_RADIUS = 4;         // ignite the whole blast range

    @SubscribeEvent
    public static void onGuardDeath(LivingDeathEvent event) {
        if (!Config.guardsDropFireGrenadeUponDeath()) return;                            // config: off = guards die quietly
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level)) return;
        if (!GUARD_ID.equals(ModRegistries.ENTITY_TYPES.getKey(e.getType()))) return; // Guard Villagers guard only
        if (!UpgradeState.isUpgraded(e)) return;                                         // stage-3 only
        Entity killer = event.getSource().getEntity();
        if (!Factions.isVillageThreat(killer)) return;   // killed by a hostile / boss-minion / MCA raider only
        explodeWithFire(level, e.getX(), e.getY() + 0.5, e.getZ());
    }

    /** TNT-strength explosion that also leaves the whole blast range on fire. */
    private static void explodeWithFire(ServerLevel level, double x, double y, double z) {
        level.explode(null, x, y, z, BLAST_RADIUS, true, Level.ExplosionInteraction.TNT);
        BlockPos center = BlockPos.containing(x, y, z);
        int r = FIRE_RADIUS;
        for (BlockPos bp : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (center.distSqr(bp) > (long) r * r) continue;
            if (!level.getBlockState(bp).isAir()) continue;
            BlockPos below = bp.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                level.setBlockAndUpdate(bp.immutable(), Blocks.FIRE.defaultBlockState());
            }
        }
    }
}
