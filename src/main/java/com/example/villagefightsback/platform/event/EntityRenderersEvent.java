package com.example.villagefightsback.platform.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/**
 * Renderer and model-layer registration, in Forge's shape. Backed by Fabric's registries, which take the same
 * arguments; only the moment differs, since these are called from the client initializer rather than posted.
 */
@Environment(EnvType.CLIENT)
public final class EntityRenderersEvent {
    private EntityRenderersEvent() {}

    /** Model layer definitions -- the baked geometry a custom model is built from. */
    @Environment(EnvType.CLIENT)
    public static final class RegisterLayerDefinitions {
        public void registerLayerDefinition(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
            EntityModelLayerRegistry.registerModelLayer(location, definition::get);
        }
    }

    /** Entity and block-entity renderers. */
    @Environment(EnvType.CLIENT)
    public static final class RegisterRenderers {
        public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type,
                                                              EntityRendererProvider<T> provider) {
            EntityRendererRegistry.register(type, provider);
        }

        public <T extends BlockEntity> void registerBlockEntityRenderer(BlockEntityType<? extends T> type,
                                                                        BlockEntityRendererProvider<T> provider) {
            net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(type, provider);
        }
    }
}
