package com.example.villagergunners.client;

import com.example.villagergunners.ModEntities;
import com.example.villagergunners.ModItems;
import com.example.villagergunners.StagedDefenderEggItem;
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
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

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

    /** Two-tone tint for the staged-defender spawn eggs (template_spawn_egg model, layers 0=base, 1=spots). */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        net.minecraft.client.color.item.ItemColor handler =
                (stack, layer) -> ((StagedDefenderEggItem) stack.getItem()).getColor(layer);
        for (RegistryObject<net.minecraft.world.item.Item> ro : ModItems.STAGED_EGGS) {
            event.register(handler, ro.get());
        }
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WALL_DEFENDER.get(), WallDefenderRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALDGER.get(), EmeraldgerRenderer::new);
        event.registerEntityRenderer(ModEntities.GRAVEMIND_WARDEN.get(), GravemindWardenRenderer::new);
        // The boss reuses the green-warden + gravemind-head renderer (it extends GravemindWardenEntity).
        event.registerEntityRenderer(ModEntities.UNLEASHED_GRAVEMIND.get(), GravemindWardenRenderer::new);
        // Vindictive guards render as plain zombie villagers (vanilla renderer).
        event.registerEntityRenderer(ModEntities.VINDICTIVE_GUARD.get(),
                net.minecraft.client.renderer.entity.ZombieVillagerRenderer::new);
        // Native rocket projectile: billboard its item sprite (RocketEntity implements ItemSupplier).
        event.registerEntityRenderer(ModEntities.ROCKET.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx));
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
            // Stage-3 (upgraded) golems get a fancy metallic-gold re-skin. Added first so it draws under the
            // held gun.
            golem.addLayer(new GoldGolemLayer(golem));
            // Upgraded golems hold our NATIVE rocket launcher (nerfed ones have an empty hand).
            golem.addLayer(new HeldGunLayer<>(golem,
                    new ResourceLocation(VillagerGunners.MODID, "rocket_launcher"), "right_arm",
                    -0.72F, 1.55F, -0.30F, -90.0F, 0.0F, 0.0F, 1.6F, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    HeldGunLayer.Gate.HELD_GUN));
        }
    }
}
