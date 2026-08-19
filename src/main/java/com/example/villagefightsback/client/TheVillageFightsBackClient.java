package com.example.villagefightsback.client;

import com.example.villagefightsback.ModBlocks;
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
import com.example.villagefightsback.network.ModNetwork;
import com.example.villagefightsback.platform.ModEventHandlers;
import com.example.villagefightsback.platform.event.EntityRenderersEvent;
import com.example.villagefightsback.platform.event.ModEventSourcesClient;
import com.example.villagefightsback.platform.event.RegisterColorHandlersEvent;
import com.example.villagefightsback.platform.event.RegisterDimensionSpecialEffectsEvent;
import com.example.villagefightsback.platform.event.RegisterParticleProvidersEvent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import com.example.villagefightsback.platform.registry.RegistryObject;

/**
 * The client entry point: renderers, model layers, tint handlers, particles, the Limbo sky, the HUDs, the
 * keybind, and the client half of the networking.
 *
 * <p>Forge fires each of these as a registration event on the mod bus. Fabric calls the initializer once and
 * expects the mod to register into the right registry itself, so {@link #onInitializeClient} drives the same
 * bodies in the same order (see the shim classes in platform.event, which take the identical calls).
 */
public final class TheVillageFightsBackClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        registerLayerDefinitions(new EntityRenderersEvent.RegisterLayerDefinitions());
        registerRenderers(new EntityRenderersEvent.RegisterRenderers());
        registerItemColors(new RegisterColorHandlersEvent.Item());
        registerBlockColors(new RegisterColorHandlersEvent.Block());
        registerParticles(new RegisterParticleProvidersEvent());
        registerDimensionEffects(new RegisterDimensionSpecialEffectsEvent());
        clientSetup();
        addLayers();

        // The spinning wall-defender icon on its spawn eggs. On Forge this hangs off the item itself, through
        // initializeClient; Fabric attaches it from here instead, which keeps the item class client-free.
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.WALL_DEFENDER_SPAWN.get(),
                (stack, mode, pose, buffers, light, overlay) ->
                        WallDefenderItemRenderer.instance().renderByItem(stack, mode, pose, buffers, light, overlay));
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.WALL_DEFENDER_STAGE4_SPAWN.get(),
                (stack, mode, pose, buffers, light, overlay) ->
                        WallDefenderItemRenderer.instance().renderByItem(stack, mode, pose, buffers, light, overlay));

        LimboAbilityHud.register();
        VillageDiscoveryHud.register();
        LimboAbilityKeybinds.register();

        ModNetwork.registerClientReceivers();
        ModEventHandlers.registerClient();
        ModEventSourcesClient.init();
    }

    public static final ModelLayerLocation WALL_DEFENDER_LAYER =
            new ModelLayerLocation(new ResourceLocation(TheVillageFightsBack.MODID, "wall_defender"), "main");

    /** Three spikes baked at the top of a humanoid head, added to wearers of the Limbo Helmet. */
    public static final ModelLayerLocation LIMBO_HELMET_SPIKES =
            new ModelLayerLocation(new ResourceLocation(TheVillageFightsBack.MODID, "limbo_helmet_spikes"), "main");

    /** General Darius's body: a humanoid model with the villager nose (see {@link DariusVillagerModel}). */
    public static final ModelLayerLocation DARIUS_LAYER =
            new ModelLayerLocation(new ResourceLocation(TheVillageFightsBack.MODID, "general_darius"), "main");

    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WALL_DEFENDER_LAYER, WallDefenderModel::createBodyLayer);
        event.registerLayerDefinition(LIMBO_HELMET_SPIKES, LimboHelmetSpikesLayer::createSpikesLayer);
        event.registerLayerDefinition(DARIUS_LAYER, DariusVillagerModel::createBodyLayer);
    }

    /**
     * Item model properties (the rocket launcher's "aiming" predicate). Forge defers this to a work queue because
     * its setup event runs off-thread; the Fabric client initializer is already on the right thread.
     */
    private static void clientSetup() {
        net.minecraft.client.renderer.item.ItemProperties.register(
                ModItems.ROCKET_LAUNCHER.get(),
                new ResourceLocation(TheVillageFightsBack.MODID, "aiming"),
                (stack, level, entity, seed) ->
                        (entity != null && entity.isUsingItem() && entity.getUseItem() == stack) ? 1.0F : 0.0F);
    }

    /** The Limbo's dark-green void sky (used by worlds whose limbo dimension_type points at this effects key). */
    private static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(new ResourceLocation(TheVillageFightsBack.MODID, "limbo"), new LimboDimensionEffects());
    }

    /** Two-tone tint for the staged-defender spawn eggs (template_spawn_egg model, layers 0=base, 1=spots). */
    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
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
    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (level != null && pos != null
                    && level.getBlockEntity(pos) instanceof com.example.villagefightsback.LimboGravelBlockEntity be) {
                return LimboGravelPalette.colorFor(be.typeId());
            }
            return 0xFFFFFF; // unlinked / plain gravel -> untinted
        }, com.example.villagefightsback.ModBlocks.LIMBO_GRAVEL.get());
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
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

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
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
    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");

    /**
     * Attaches this mod's feature layers to other people's renderers.
     *
     * <p>Forge hands out a map of every renderer at once and lets a mod walk it. Fabric instead calls back once
     * per living renderer as it is built, which is the same reach one entity at a time -- and it covers the
     * player skins without the separate pass Forge needs.
     *
     * <p>What gets attached, and why nothing else does:
     * <ul>
     *   <li>Villagers need no gun layer: an upgraded villager holds the revolver and vanilla's CrossedArmsItemLayer
     *       draws it, so a villager only ever shows what it is actually holding.</li>
     *   <li>The gold re-skin and held rocket launcher live on GigachadGolemRenderer, so the vanilla iron golem
     *       renderer stays untouched.</li>
     *   <li>The angry face goes on vanilla villagers and Guard Villagers guards, but never on MCA Reborn's
     *       villagers -- see the note below.</li>
     * </ul>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLayers() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, entityRenderer, registrationHelper, context) -> {
                    if (entityType == EntityType.VILLAGER) {
                        registrationHelper.register(new AngryVillagerFaceLayer(
                                (net.minecraft.client.renderer.entity.RenderLayerParent) entityRenderer));
                    }
                    EntityType<?> guardType = com.example.villagefightsback.platform.ModRegistries
                            .ENTITY_TYPES.getValue(GUARD_ID);
                    if (guardType != null && entityType == guardType
                            && entityRenderer.getModel() instanceof net.minecraft.client.model.HumanoidModel<?>) {
                        registrationHelper.register(new AngryGuardFaceLayer(
                                (net.minecraft.client.renderer.entity.RenderLayerParent) entityRenderer));
                    }
                    // The Limbo Helmet spikes go on anything humanoid, so ANYONE wearing the helmet shows them.
                    // The layer only draws when the head slot really holds it, so this is a no-op otherwise.
                    // General Darius is skipped because his own renderer already adds the layer.
                    if (entityType == ModEntities.GENERAL_DARIUS.get()) return;
                    if (entityType == EntityType.PLAYER
                            || entityRenderer.getModel() instanceof net.minecraft.client.model.HumanoidModel<?>) {
                        registrationHelper.register(new LimboHelmetSpikesLayer(
                                (LivingEntityRenderer) entityRenderer, context.getModelSet()));
                    }
                });
    }

    /**
     * DELIBERATELY ABSENT: the angry face is never attached to MCA Reborn's villagers.
     *
     * <p>It used to be. The layer draws a scowl over the head while a defender is swinging, which works on a
     * vanilla villager because a vanilla villager has one face. An MCA human does not: MCA gives every villager
     * its own face, and painting our scowl on top replaced the thing that makes each of them a person. A
     * fighting village turned into a crowd of identical angry masks, and the identity MCA is installed FOR
     * disappeared for the whole fight.
     *
     * <p>On an MCA pack the anger simply is not drawn. The vanilla villager and Guard Villagers layers are
     * untouched -- those entities have no MCA face to cover, so nothing is lost by keeping the scowl there.
     */
    private static void angryFaceIsNotForMcaVillagers() {
        // documentation only
    }
}
