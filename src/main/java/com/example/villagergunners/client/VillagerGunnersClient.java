package com.example.villagergunners.client;

import com.example.villagergunners.GunUtil;
import com.example.villagergunners.ModEntities;
import com.example.villagergunners.VillagerGunners;

import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only: attaches the decorative gun render layer to villager and iron golem renderers. */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VillagerGunnersClient {
    private VillagerGunnersClient() {}

    public static final ModelLayerLocation WALL_DEFENDER_LAYER =
            new ModelLayerLocation(new ResourceLocation(VillagerGunners.MODID, "wall_defender"), "main");

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WALL_DEFENDER_LAYER, WallDefenderModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WALL_DEFENDER.get(), WallDefenderRenderer::new);
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        // Villagers need NO custom layer: upgraded villagers hold the revolver and the vanilla
        // CrossedArmsItemLayer renders it (nerfed villagers hold a stone axe, also rendered by it). A
        // villager therefore only ever shows whatever it actually holds -- no gun before the upgrade.

        // Iron golem: no vanilla item layer. Anchor to right_arm. NOTE: the right_arm PIVOT is at
        // x=0 (centre); the visible arm box is at x -13..-9 and 30px long. So we offset to the arm
        // box and down to the hand. Big golem -> scale the gun up. (Orientation may need tuning.)
        LivingEntityRenderer<IronGolem, IronGolemModel<IronGolem>> golem = event.getRenderer(EntityType.IRON_GOLEM);
        if (golem != null) {
            // Upgraded golems hold the rocket launcher (nerfed ones have an empty hand).
            golem.addLayer(new HeldGunLayer<>(golem, GunUtil.ROCKET_LAUNCHER, "right_arm",
                    -0.72F, 1.55F, -0.30F, -90.0F, 0.0F, 0.0F, 1.6F, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    HeldGunLayer.Gate.HELD_GUN));
        }
    }
}
