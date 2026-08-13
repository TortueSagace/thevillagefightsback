package com.example.villagefightsback.client;

import com.example.villagefightsback.ModEntities;
import com.example.villagefightsback.ModItems;
import com.example.villagefightsback.StagedDefenderEggItem;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/** Client-only: attaches the decorative gun render layer to villager and iron golem renderers. */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TheVillageFightsBackClient {
    private TheVillageFightsBackClient() {}

    public static final ModelLayerLocation WALL_DEFENDER_LAYER =
            new ModelLayerLocation(new ResourceLocation(TheVillageFightsBack.MODID, "wall_defender"), "main");

    /** Three spikes baked at the top of a humanoid head, added to wearers of the Limbo Helmet. */
    public static final ModelLayerLocation LIMBO_HELMET_SPIKES =
            new ModelLayerLocation(new ResourceLocation(TheVillageFightsBack.MODID, "limbo_helmet_spikes"), "main");

    /** General Darius's body: a humanoid model with the villager nose (see {@link DariusVillagerModel}). */
    public static final ModelLayerLocation DARIUS_LAYER =
            new ModelLayerLocation(new ResourceLocation(TheVillageFightsBack.MODID, "general_darius"), "main");

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WALL_DEFENDER_LAYER, WallDefenderModel::createBodyLayer);
        event.registerLayerDefinition(LIMBO_HELMET_SPIKES, LimboHelmetSpikesLayer::createSpikesLayer);
        event.registerLayerDefinition(DARIUS_LAYER, DariusVillagerModel::createBodyLayer);
    }

    /** Client setup: register item model properties (the rocket launcher's "aiming" predicate). */
    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.renderer.item.ItemProperties.register(
                    ModItems.ROCKET_LAUNCHER.get(),
                    new ResourceLocation(TheVillageFightsBack.MODID, "aiming"),
                    (stack, level, entity, seed) ->
                            (entity != null && entity.isUsingItem() && entity.getUseItem() == stack) ? 1.0F : 0.0F);
        });
    }

    /** 1.19.2 GUI overlays: the overlay package + RegisterGuiOverlaysEvent exist in 1.19.2; only the render
     *  lambda takes a PoseStack instead of 1.20.1's GuiGraphics. */
    @SubscribeEvent
    public static void onRegisterOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        LimboAbilityHud.register(event);
        VillageDiscoveryHud.register(event);
    }

    /** The Limbo's dark-green void sky (used by worlds whose limbo dimension_type points at this effects key). */
    @SubscribeEvent
    public static void onRegisterDimensionEffects(
            net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
        event.register(new ResourceLocation(TheVillageFightsBack.MODID, "limbo"), new LimboDimensionEffects());
    }

    /** Two-tone tint for the staged-defender spawn eggs (template_spawn_egg model, layers 0=base, 1=spots). */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        net.minecraft.client.color.item.ItemColor handler =
                (stack, layer) -> ((StagedDefenderEggItem) stack.getItem()).getColor(layer);
        for (RegistryObject<net.minecraft.world.item.Item> ro : ModItems.STAGED_EGGS) {
            event.register(handler, ro.get());
        }
        // Soul gravel: tint toward the held creature's colour (its spawn egg, else a sampled texture colour).
        event.register((stack, layer) -> {
            String type = com.example.villagefightsback.LimboSoulGravelItem.typeId(stack);
            return type.isEmpty() ? 0xFFFFFF : LimboGravelPalette.colorFor(type);
        }, com.example.villagefightsback.ModBlocks.LIMBO_SOUL_GRAVEL.get());
    }

    /** Tint a placed Limbo gravel toward the colour of the creature it holds (read from its block entity). */
    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (level != null && pos != null
                    && level.getBlockEntity(pos) instanceof com.example.villagefightsback.LimboGravelBlockEntity be) {
                return LimboGravelPalette.colorFor(be.typeId());
            }
            return 0xFFFFFF; // unlinked / plain gravel -> untinted
        }, com.example.villagefightsback.ModBlocks.LIMBO_GRAVEL.get());
    }

    @SubscribeEvent
    public static void onRegisterParticles(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        // Our green sonic-boom reuses the vanilla SonicBoomParticle renderer with green sprites.
        event.register(com.example.villagefightsback.ModParticles.GREEN_SONIC_BOOM.get(),
                net.minecraft.client.particle.SonicBoomParticle.Provider::new);
        // JEG-style big explosion animation (scaled-up ExplodeParticle with our fireball sprites).
        event.register(com.example.villagefightsback.ModParticles.GIGACHAD_EXPLOSION.get(),
                GigachadExplosionParticle.Provider::new);
        // The same animation in green, for the emerald gigachad golem's rockets.
        event.register(com.example.villagefightsback.ModParticles.GIGACHAD_EXPLOSION_GREEN.get(),
                GigachadExplosionParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.example.villagefightsback.ModBlocks.MEMORIAL_BE.get(),
                MemorialRenderer::new);
        // The Limbo gravel's purple instability glow (a block-entity renderer).
        event.registerBlockEntityRenderer(com.example.villagefightsback.ModBlocks.LIMBO_GRAVEL_BE.get(),
                LimboGravelRenderer::new);
        event.registerEntityRenderer(ModEntities.WALL_DEFENDER.get(), WallDefenderRenderer::new);
        event.registerEntityRenderer(ModEntities.WALL_DEFENDER_STAGE4.get(), WallDefenderRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALDGER.get(), EmeraldgerRenderer::new);
        // --- emerald faction (v1.0.3): vanilla mob shapes, emerald textures + emissive eyes ---
        event.registerEntityRenderer(ModEntities.EMERALD_COW.get(), EmeraldCowRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_CHICKEN.get(), EmeraldChickenRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_CAT.get(), EmeraldCatRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_ENDERMITE.get(), EmeraldEndermiteRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_WOLF.get(), EmeraldWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_HORSE.get(), EmeraldHorseRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_PIG.get(), EmeraldPigRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_SALMON.get(), EmeraldSalmonRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_BAT.get(), EmeraldBatRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_ZOMBIE.get(), EmeraldZombieRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_SKELETON.get(), EmeraldSkeletonRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_CREEPER.get(), EmeraldCreeperRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_ENDERMAN.get(), EmeraldEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_IRON_GOLEM.get(), EmeraldIronGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_DEFENDER_GOLEM.get(), EmeraldDefenderGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_GIGACHAD_GOLEM.get(), EmeraldGigachadGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_PHANTOM.get(), EmeraldPhantomRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_WITHER.get(), EmeraldWitherRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_GUARDIAN.get(), EmeraldGuardianRenderer::new);
        event.registerEntityRenderer(ModEntities.EMERALD_ELDER_GUARDIAN.get(), EmeraldElderGuardianRenderer::new);
        event.registerEntityRenderer(ModEntities.BRAIN_NODE_WARDEN.get(), BrainNodeWardenRenderer::new);
        // The boss reuses the green-warden + brainNode-head renderer (it extends BrainNodeWardenEntity).
        event.registerEntityRenderer(ModEntities.UNLEASHED_BRAIN_NODE.get(), BrainNodeWardenRenderer::new);
        // Vindictive guards render as plain zombie villagers (vanilla renderer).
        event.registerEntityRenderer(ModEntities.VINDICTIVE_GUARD.get(),
                net.minecraft.client.renderer.entity.ZombieVillagerRenderer::new);
        // General Darius: a HUMAN guard (player-model body, armour via the HumanoidArmorLayer) + helmet spikes.
        event.registerEntityRenderer(ModEntities.GENERAL_DARIUS.get(), GeneralDariusRenderer::new);
        // Gigachad golem: gold-skinned golem + rocket launcher. The vindictive subclass adds green vines on top.
        event.registerEntityRenderer(ModEntities.GIGACHAD_GOLEM.get(), GigachadGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.VINDICTIVE_GIGACHAD.get(), VindictiveGigachadRenderer::new);
        // Defender golem: vanilla iron golem body with its iron head replaced by a wall-defender-in-a-cocoon.
        event.registerEntityRenderer(ModEntities.DEFENDER_GOLEM.get(), DefenderGolemRenderer::new);
        // Native rocket projectile: billboard its item sprite (RocketEntity implements ItemSupplier).
        event.registerEntityRenderer(ModEntities.ROCKET.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx));
        // Thrown volatile limbo potion: billboard its item sprite as it flies.
        event.registerEntityRenderer(ModEntities.VOLATILE_LIMBO_POTION.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx));
        event.registerEntityRenderer(ModEntities.EMERALD_VIRUS_ARROW.get(), EmeraldVirusArrowRenderer::new);
        event.registerEntityRenderer(ModEntities.LIMBO_PHANTOM.get(), LimboPhantomRenderer::new);
        // Limbo raiders: vanilla illager/ravager/vex/witch renderers, re-skinned + scaled 2x + emissive glow.
        event.registerEntityRenderer(ModEntities.LIMBO_PILLAGER.get(), LimboPillagerRenderer::new);
        event.registerEntityRenderer(ModEntities.LIMBO_VINDICATOR.get(), LimboVindicatorRenderer::new);
        event.registerEntityRenderer(ModEntities.LIMBO_EVOKER.get(), LimboEvokerRenderer::new);
        event.registerEntityRenderer(ModEntities.LIMBO_RAVAGER.get(), LimboRavagerRenderer::new);
        event.registerEntityRenderer(ModEntities.LIMBO_VEX.get(), LimboVexRenderer::new);
        event.registerEntityRenderer(ModEntities.LIMBO_WITCH.get(), LimboWitchRenderer::new);
        // Rich Sheep: vanilla sheep model/geometry + our green rich skin and (un-tinted) rich fur layer.
        event.registerEntityRenderer(ModEntities.RICH_SHEEP.get(), RichSheepRenderer::new);
        // The bullet is invisible; its yellow particle tracer is the visual.
        event.registerEntityRenderer(ModEntities.BULLET.get(), BulletRenderer::new);
        // Fire grenade: billboard its item sprite as it flies.
        event.registerEntityRenderer(ModEntities.FIRE_GRENADE.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx));
        // Wind charge: billboard its item sprite as it flies.
        event.registerEntityRenderer(ModEntities.WIND_CHARGE.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx));
        // Emerald egg: billboard its item sprite as it flies.
        event.registerEntityRenderer(ModEntities.EMERALD_EGG.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx));
    }

    /** The Guard Villagers guard entity type id. The mod is a RUNTIME soft-dep, so this may resolve to null. */
    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        // Villagers need NO custom gun layer: upgraded villagers hold the revolver and the vanilla
        // CrossedArmsItemLayer renders it (nerfed villagers hold a stone axe, also rendered by it). A
        // villager therefore only ever shows whatever it actually holds -- no gun before the upgrade.
        //
        // Gold re-skin + held rocket launcher now live on GigachadGolemRenderer (gigachads are their own
        // creature type), so the VANILLA iron golem renderer is left untouched.

        // Angry-face overlays: while a villager / guard is FIGHTING (detected client-side via the synced
        // arm-swing animation -- see AngryFaceClient), scowl the angry villager face onto its head.
        addAngryVillagerFace(event);
        addAngryGuardFace(event);
        addAngryMcaFace(event);
        addLimboHelmetSpikes(event);
    }

    /**
     * Attach the angry-face layer to MCA Reborn's villagers ({@code mca:male_villager} / {@code mca:female_villager}).
     * MCA is a runtime soft-dep, so the types are looked up by id and we simply skip when they are absent.
     *
     * <p>The GUARD variant of the layer is the right one here, not the villager variant: MCA renders its humans
     * with a {@code HumanoidMobRenderer} over a player-shaped head, the same geometry the Guard Villagers guard
     * uses, whereas {@link AngryVillagerFaceLayer} is laid out for the vanilla villager's oversized head.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addAngryMcaFace(EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.resources.ResourceLocation id
                : com.example.villagefightsback.compat.MCACompat.VILLAGER_IDS) {
            EntityType<?> type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(id);
            if (type == null) continue; // MCA not installed -- nothing to decorate
            LivingEntityRenderer<?, ?> renderer;
            try {
                renderer = event.getRenderer((EntityType) type);
            } catch (ClassCastException notLiving) {
                continue; // another mod swapped in a non-living renderer
            }
            if (renderer == null) continue;
            if (!(renderer.getModel() instanceof net.minecraft.client.model.HumanoidModel<?>)) continue;
            ((LivingEntityRenderer) renderer).addLayer(new AngryGuardFaceLayer(
                    (net.minecraft.client.renderer.entity.RenderLayerParent) renderer));
        }
    }

    /**
     * Attach the Limbo Helmet spikes layer to every player skin renderer (default + slim) AND to every other
     * humanoid mob renderer, so ANYONE wearing the Limbo Helmet shows the spikes -- not just the player. The
     * layer itself only draws when the head slot actually holds the Limbo Helmet, so adding it everywhere is a
     * no-op for mobs that aren't wearing it.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLimboHelmetSpikes(EntityRenderersEvent.AddLayers event) {
        // Players (default + slim skins).
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
                pr.addLayer(new LimboHelmetSpikesLayer<>(pr, event.getEntityModels()));
            }
        }
        // Every other humanoid mob (zombies, skeletons, piglins, armour stands, the Guard Villagers guard, ...).
        // Skip the player (handled above) and General Darius (his renderer already adds the layer) so neither
        // gets a doubled layer.
        for (EntityType<?> type : net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValues()) {
            if (type == EntityType.PLAYER || type == ModEntities.GENERAL_DARIUS.get()) continue;
            LivingEntityRenderer<?, ?> renderer;
            try {
                renderer = event.getRenderer((EntityType) type); // throws CCE for non-living renderers
            } catch (ClassCastException notLiving) {
                continue;
            }
            if (renderer == null || !(renderer.getModel() instanceof net.minecraft.client.model.HumanoidModel<?>)) {
                continue;
            }
            ((LivingEntityRenderer) renderer).addLayer(
                    new LimboHelmetSpikesLayer((LivingEntityRenderer) renderer, event.getEntityModels()));
        }
    }

    /** Attach the angry-face layer to the vanilla villager renderer. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addAngryVillagerFace(EntityRenderersEvent.AddLayers event) {
        LivingEntityRenderer<?, ?> renderer = event.getRenderer(EntityType.VILLAGER);
        if (renderer == null) return; // defensive: some setups replace the villager renderer
        // The villager renderer's model is a VillagerModel; the wildcard erases that, so cast through raw.
        ((LivingEntityRenderer) renderer).addLayer(new AngryVillagerFaceLayer(
                (net.minecraft.client.renderer.entity.RenderLayerParent) renderer));
    }

    /**
     * Attach the angry-face layer to the Guard Villagers guard renderer. Guard Villagers is a runtime
     * soft-dep -- if it is absent the entity type lookup is null and we simply skip (null-guard). The guard
     * renderer is only decorated when it is a LivingEntityRenderer whose model is a HumanoidModel.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addAngryGuardFace(EntityRenderersEvent.AddLayers event) {
        EntityType<?> guardType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(GUARD_ID);
        if (guardType == null) return; // Guard Villagers not installed -- nothing to decorate
        // getRenderer needs EntityType<? extends T extends LivingEntity>; the registry hands us EntityType<?>,
        // so go through a raw EntityType (we re-check the model type below before attaching the layer).
        LivingEntityRenderer<?, ?> renderer = event.getRenderer((EntityType) guardType);
        if (renderer == null) return;
        if (!(renderer.getModel() instanceof net.minecraft.client.model.HumanoidModel<?>)) return; // wrong model -> skip
        ((LivingEntityRenderer) renderer).addLayer(new AngryGuardFaceLayer(
                (net.minecraft.client.renderer.entity.RenderLayerParent) renderer));
    }
}
