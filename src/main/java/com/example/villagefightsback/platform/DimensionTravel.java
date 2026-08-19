package com.example.villagefightsback.platform;

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;

/**
 * Moving a non-player entity to an exact spot in another dimension.
 *
 * <p>On Forge this is {@code entity.changeDimension(level, ITeleporter)} with an anonymous teleporter that skips
 * the portal search and just repositions. Fabric's answer is {@link FabricDimensions}, which takes the target
 * outright. Both end up inside {@code Entity.changeDimension}, which is where the mod's
 * {@link com.example.villagefightsback.platform.event.EntityTravelToDimensionEvent} is fired from -- so the Limbo
 * containment leash still hears about every crossing.
 */
public final class DimensionTravel {
    private DimensionTravel() {}

    /**
     * Puts {@code entity} at the given spot in {@code dest}, keeping its facing and killing its momentum.
     *
     * @return the entity in its new level, which for a non-player is a NEW instance, or null if the move failed
     */
    public static Entity teleport(Entity entity, ServerLevel dest, double x, double y, double z) {
        PortalInfo target = new PortalInfo(new Vec3(x, y, z), Vec3.ZERO, entity.getYRot(), entity.getXRot());
        return FabricDimensions.teleport(entity, dest, target);
    }
}
