package com.example.villagergunners;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lets a {@link WallDefenderBlockEntity} take damage from projectiles that actually reach it (i.e. once a
 * skeleton has broken the protecting-glass shell). Each projectile hit knocks {@value #DAMAGE_PER_HIT} off
 * its 100 HP; at 0 the block breaks. The defender's own outward shots spawn outside the block, so they
 * never hit it.
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WallDefenderEvents {
    private WallDefenderEvents() {}

    private static final int DAMAGE_PER_HIT = 5; // 100 HP -> ~20 projectile hits to destroy

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof BlockHitResult hit)) return;
        Projectile projectile = event.getProjectile();
        Level level = projectile.level();
        if (level.isClientSide) return;
        BlockEntity be = level.getBlockEntity(hit.getBlockPos());
        if (be instanceof WallDefenderBlockEntity defender) {
            defender.hurt(DAMAGE_PER_HIT);
        }
    }
}
