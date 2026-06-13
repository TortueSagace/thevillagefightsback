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
    public static void onRegisterParticles(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        // Our green sonic-boom reuses the vanilla SonicBoomParticle renderer with green sprites.
        event.registerSpriteSet(com.example.villagergunners.ModParticles.GREEN_SONIC_BOOM.get(),
                net.minecraft.client.particle.SonicBoomParticle.Provider::new);
        // JEG-style big explosion animation (scaled-up ExplodeParticle with our fireball sprites).
        event.registerSpriteSet(com.example.villagergunners.ModParticles.GIGACHAD_EXPLOSION.get(),
                GigachadExplosionParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WALL_DEFENDER.get(), WallDefenderRenderer::new);
        event.registerEntityRenderer(ModEntities.WALL_DEFENDER_STAGE4.get(), WallDefenderRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALDGER.get(), EmeraldgerRenderer::new);
        event.registerEntityRenderer(ModEntities.GRAVEMIND_WARDEN.get(), GravemindWardenRenderer::new);
        // The boss reuses the green-warden + gravemind-head renderer (it extends GravemindWardenEntity).
        event.registerEntityRenderer(ModEntities.UNLEASHED_GRAVEMIND.get(), GravemindWardenRenderer::new);
        // Vindictive guards render as plain zombie villagers (vanilla renderer).
        event.registerEntityRenderer(ModEntities.VINDICTIVE_GUARD.get(),
                net.minecraft.client.renderer.entity.ZombieVillagerRenderer::new);
        // Gigachad golem: gold-skinned golem + rocket launcher. The vindictive subclass adds green vines on top.
        event.registerEntityRenderer(ModEntities.GIGACHAD_GOLEM.get(), GigachadGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.VINDICTIVE_GIGACHAD.get(), VindictiveGigachadRenderer::new);
        // Native rocket projectile: billboard its item sprite (RocketEntity implements ItemSupplier).
        event.registerEntityRenderer(ModEntities.ROCKET.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx));
        // Thrown volatile limbo potion: billboard its item sprite as it flies.
        event.registerEntityRenderer(ModEntities.VOLATILE_LIMBO_POTION.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx));
        event.registerEntityRenderer(ModEntities.EMERALD_VIRUS_ARROW.get(), EmeraldVirusArrowRenderer::new);
        event.registerEntityRenderer(ModEntities.LIMBO_PHANTOM.get(), LimboPhantomRenderer::new);
        // Rich Sheep: vanilla sheep model/geometry + our green rich skin and (un-tinted) rich fur layer.
        event.registerEntityRenderer(ModEntities.RICH_SHEEP.get(), RichSheepRenderer::new);
        // The bullet is invisible; its yellow particle tracer is the visual.
        event.registerEntityRenderer(ModEntities.BULLET.get(), BulletRenderer::new);
        // Fire grenade: billboard its item sprite as it flies.
        event.registerEntityRenderer(ModEntities.FIRE_GRENADE.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx));
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        // Villagers need NO custom layer: upgraded villagers hold the revolver and the vanilla
        // CrossedArmsItemLayer renders it (nerfed villagers hold a stone axe, also rendered by it). A
        // villager therefore only ever shows whatever it actually holds -- no gun before the upgrade.

        // Gold re-skin + held rocket launcher now live on GigachadGolemRenderer (gigachads are their own
        // creature type), so the VANILLA iron golem renderer is left untouched.
    }
}
