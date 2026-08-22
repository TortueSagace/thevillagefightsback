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
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * A stage-3 (upgraded) Guard Villagers guard that is killed BY a hostile mob goes out with a bang: a
 * TNT-strength explosion that also sets the whole blast range on fire. Only fires for upgraded guards
 * killed by an {@link Enemy} (not fall damage, drowning, friendly fire, etc.).
 *
 * <h2>It cannot set itself off</h2>
 * The blast is raised with no owner, so a guard killed by it has a null killer and fails the threat
 * test -- which is what has always stopped the chain. That is a subtle thing to be relying on, and it
 * stopped being true for one release when the threat test started dereferencing its argument: a guard
 * caught in another guard's blast crashed the server from inside the explosion's own damage loop.
 *
 * <p>So the rule is now stated rather than inferred. While a blast is being resolved, no death it
 * causes raises another one. Even with a hostile somehow credited for the kill, a line of guards
 * cannot detonate down its own length.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class GuardDeathExplosionEvents {
    private GuardDeathExplosionEvents() {}

    private static final ResourceLocation GUARD_ID = ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard");
    private static final float BLAST_RADIUS = 4.0F;   // TNT-strength
    private static final int FIRE_RADIUS = 4;         // ignite the whole blast range

    /** True while one of these blasts is resolving. Not thread-shared: the server ticks on one thread. */
    private static boolean exploding;

    @SubscribeEvent
    public static void onGuardDeath(LivingDeathEvent event) {
        if (!Config.guardsDropFireGrenadeUponDeath()) return;                            // config: off = guards die quietly
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level)) return;
        if (!GUARD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()))) return; // Guard Villagers guard only
        if (!UpgradeState.isUpgraded(e)) return;                                         // stage-3 only
        if (exploding) return;                           // caught in another guard's blast; see the class doc
        Entity killer = event.getSource().getEntity();
        if (!Factions.isVillageThreat(killer)) return;   // killed by a hostile / boss-minion / MCA raider only
        explodeWithFire(level, e.getX(), e.getY() + 0.5, e.getZ());
    }

    /** TNT-strength explosion that also leaves the whole blast range on fire. */
    private static void explodeWithFire(ServerLevel level, double x, double y, double z) {
        exploding = true;
        try {
            blast(level, x, y, z);
        } finally {
            exploding = false;   // a throw inside the blast must not leave the guards permanently inert
        }
    }

    private static void blast(ServerLevel level, double x, double y, double z) {
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
