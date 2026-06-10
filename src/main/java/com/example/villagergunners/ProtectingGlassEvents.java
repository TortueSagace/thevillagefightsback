package com.example.villagergunners;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Makes {@link ProtectingGlassBlock} one-way for projectiles: a projectile moving roughly along the block's
 * FACING (outward) is let through (the impact is cancelled and the projectile is nudged past the pane);
 * a projectile moving the other way is left to hit the solid block. So the wall-defender's shots leave the
 * shell while skeleton arrows fired from outside are stopped.
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProtectingGlassEvents {
    private ProtectingGlassEvents() {}

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof BlockHitResult hit)) return;
        Projectile projectile = event.getProjectile();
        Level level = projectile.level();
        if (level.isClientSide) return;

        BlockState state = level.getBlockState(hit.getBlockPos());
        if (!state.is(ModBlocks.PROTECTING_GLASS.get())) return;

        Direction facing = state.getValue(ProtectingGlassBlock.FACING);
        Vec3 v = projectile.getDeltaMovement();
        double dot = v.x * facing.getStepX() + v.y * facing.getStepY() + v.z * facing.getStepZ();
        if (dot <= 0) return; // moving inward -> let it hit the solid pane (blocked)

        // Moving outward -> pass through: skip the hit and slide the projectile just past the pane so it
        // doesn't immediately re-collide. 1.7 > sqrt(3) clears any diagonal crossing of a 1-block pane.
        Vec3 step = v.normalize().scale(1.7);
        Vec3 to = hit.getLocation().add(step);
        projectile.setPos(to.x, to.y, to.z);
        event.setCanceled(true);
    }
}
