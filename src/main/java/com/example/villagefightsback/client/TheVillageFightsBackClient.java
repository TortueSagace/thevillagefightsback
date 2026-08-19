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
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Client-only: attaches the decorative gun render layer to villager and iron golem renderers. */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TheVillageFightsBackClient {
    private TheVillageFightsBackClient() {}

    public static final ModelLayerLocation WALL_DEFENDER_LAYER =
            new ModelLayerLocation(TheVillageFightsBack.id("wall_defender"), "main");

    /** Three spikes baked at the top of a humanoid head, added to wearers of the Limbo Helmet. */
    public static final ModelLayerLocation LIMBO_HELMET_SPIKES =
            new ModelLayerLocation(TheVillageFightsBack.id("limbo_helmet_spikes"), "main");

    /** General Darius's body: a humanoid model with the villager nose (see {@link DariusVillagerModel}). */
    public static final ModelLayerLocation DARIUS_LAYER =
            new ModelLayerLocation(TheVillageFightsBack.id("general_darius"), "main");

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WALL_DEFENDER_LAYER, WallDefenderModel::createBodyLayer);
        event.registerLayerDefinition(LIMBO_HELMET_SPIKES, LimboHelmetSpikesLayer::createSpikesLayer);
        event.registerLayerDefinition(DARIUS_LAYER, DariusVillagerModel::createBodyLayer);
    }

    /** Client setup: register item model properties (the rocket launcher's "aiming" predicate). */
    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                ModItems.ROCKET_LAUNCHER.get(),
                TheVillageFightsBack.id("aiming"),
                (stack, level, entity, seed) ->
                        (entity != null && entity.isUsingItem() && entity.getUseItem() == stack) ? 1.0F : 0.0F));
    }

    /** The Limbo's dark-green void sky (used by worlds whose limbo dimension_type points at this effects key). */
    @SubscribeEvent
    public static void onRegisterDimensionEffects(
            net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
        event.register(TheVillageFightsBack.id("limbo"), new LimboDimensionEffects());
    }

    /** Two-tone tint for the staged-defender spawn eggs (template_spawn_egg model, layers 0=base, 1=spots). */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        net.minecraft.client.color.item.ItemColor handler =
                (stack, layer) -> ((StagedDefenderEggItem) stack.getItem()).getColor(layer);
        for (var ro : ModItems.STAGED_EGGS) {
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
    public static void onRegisterParticles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        // Our green sonic-boom reuses the vanilla SonicBoomParticle renderer with green sprites.
        event.registerSpriteSet(com.example.villagefightsback.ModParticles.GREEN_SONIC_BOOM.get(),
                net.minecraft.client.particle.SonicBoomParticle.Provider::new);
        // JEG-style big explosion animation (scaled-up ExplodeParticle with our fireball sprites).
        event.registerSpriteSet(com.example.villagefightsback.ModParticles.GIGACHAD_EXPLOSION.get(),
                GigachadExplosionParticle.Provider::new);
        // The same animation in green, for the emerald gigachad golem's rockets.
        event.registerSpriteSet(com.example.villagefightsback.ModParticles.GIGACHAD_EXPLOSION_GREEN.get(),
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
        // The Village Spirit IS an Allay as far as rendering is concerned -- it extends the vanilla mob, so the
        // vanilla renderer binds straight to it and no model or texture has to be shipped.
        event.registerEntityRenderer(ModEntities.VILLAGE_SPIRIT.get(), VillageSpiritRenderer::new);
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
    private static final ResourceLocation GUARD_ID = ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard");

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
            // entityTypeOrNull, not ENTITY_TYPE.get: the entity registry is DEFAULTED and get() returns a PIG,
            // not null, for an unregistered id (see MCACompat#present).
            EntityType<?> type = com.example.villagefightsback.compat.MCACompat.entityTypeOrNull(id);
            if (type == null) continue; // MCA not installed -- nothing to decorate
            // 1.21's AddLayers.getRenderer is generic in the renderer type, and the raw EntityType argument erases
            // the call to plain EntityRenderer, so it no longer implicitly casts (and no longer throws the
            // ClassCastException main relied on). The pattern test below does the same job explicitly: a renderer
            // some other mod swapped for a non-living one simply gets skipped, exactly as before.
            Object raw = event.getRenderer((EntityType) type);
            if (!(raw instanceof LivingEntityRenderer<?, ?> renderer)) continue;
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
        for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
                pr.addLayer(new LimboHelmetSpikesLayer<>(pr, event.getEntityModels()));
            }
        }
        // Every other humanoid mob (zombies, skeletons, piglins, armour stands, the Guard Villagers guard, ...).
        // Skip the player (handled above) and General Darius (his renderer already adds the layer) so neither
        // gets a doubled layer.
        for (EntityType<?> type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
            if (type == EntityType.PLAYER || type == ModEntities.GENERAL_DARIUS.get()) continue;
            // getRenderer now returns EntityRenderer<?>; narrow to a living renderer with a HumanoidModel.
            net.minecraft.client.renderer.entity.EntityRenderer<?> renderer = event.getRenderer((EntityType) type);
            if (!(renderer instanceof LivingEntityRenderer<?, ?> living)
                    || !(living.getModel() instanceof net.minecraft.client.model.HumanoidModel<?>)) {
                continue;
            }
            ((LivingEntityRenderer) living).addLayer(
                    new LimboHelmetSpikesLayer((LivingEntityRenderer) living, event.getEntityModels()));
        }
    }

    /** Attach the angry-face layer to the vanilla villager renderer. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addAngryVillagerFace(EntityRenderersEvent.AddLayers event) {
        net.minecraft.client.renderer.entity.EntityRenderer<?> renderer = event.getRenderer(EntityType.VILLAGER);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) return; // defensive: some setups replace the villager renderer
        // The villager renderer's model is a VillagerModel; the wildcard erases that, so cast through raw.
        ((LivingEntityRenderer) living).addLayer(new AngryVillagerFaceLayer(
                (net.minecraft.client.renderer.entity.RenderLayerParent) living));
    }

    /**
     * Attach the angry-face layer to the Guard Villagers guard renderer. Guard Villagers is a runtime
     * soft-dep -- if it is absent the entity type lookup is null and we simply skip (null-guard). The guard
     * renderer is only decorated when it is a LivingEntityRenderer whose model is a HumanoidModel.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addAngryGuardFace(EntityRenderersEvent.AddLayers event) {
        // entityTypeOrNull, not ENTITY_TYPE.get: the entity registry is DEFAULTED and get() returns a PIG,
        // not null, for an unregistered id (see MCACompat#present) -- which would send us off to decorate the
        // pig renderer, saved from doing so only by the HumanoidModel check below.
        EntityType<?> guardType = com.example.villagefightsback.compat.MCACompat.entityTypeOrNull(GUARD_ID);
        if (guardType == null) return; // Guard Villagers not installed -- nothing to decorate
        // getRenderer needs EntityType<? extends T extends LivingEntity>; the registry hands us EntityType<?>,
        // so go through a raw EntityType (we re-check the model type below before attaching the layer).
        net.minecraft.client.renderer.entity.EntityRenderer<?> renderer = event.getRenderer((EntityType) guardType);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) return;
        if (!(living.getModel() instanceof net.minecraft.client.model.HumanoidModel<?>)) return; // wrong model -> skip
        ((LivingEntityRenderer) living).addLayer(new AngryGuardFaceLayer(
                (net.minecraft.client.renderer.entity.RenderLayerParent) living));
    }
}
