package com.example.villagefightsback.platform.event;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

/**
 * Where a living entity type's default attributes are declared.
 *
 * <p>Forge posts this as an event during setup. Fabric has a registry instead, so this is not an event at all --
 * it is a thin handle over {@link FabricDefaultAttributeRegistry}, kept in this shape so the fifty-odd lines that
 * fill it in read the same on both branches. It has to run after the entity types are registered, which is why
 * ModEntities calls it from its own register().
 */
public final class EntityAttributeCreationEvent {
    public void put(EntityType<? extends LivingEntity> type, AttributeSupplier attributes) {
        FabricDefaultAttributeRegistry.register(type, attributes);
    }
}
