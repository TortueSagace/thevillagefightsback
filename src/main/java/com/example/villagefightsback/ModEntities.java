package com.example.villagefightsback;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entity-type registration + attributes. */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TheVillageFightsBack.MODID);

    public static final RegistryObject<EntityType<WallDefenderEntity>> WALL_DEFENDER = ENTITIES.register(
            "wall_defender",
            () -> EntityType.Builder.<WallDefenderEntity>of(WallDefenderEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F)
                    .clientTrackingRange(10)
                    .build("wall_defender"));

    /** Stage-4 (rocket) wall defender -- same class, but its own type so a separate spawn egg can force it
     *  into permanent rocket mode + the stage-4 texture. */
    public static final RegistryObject<EntityType<WallDefenderEntity>> WALL_DEFENDER_STAGE4 = ENTITIES.register(
            "wall_defender_stage4",
            () -> EntityType.Builder.<WallDefenderEntity>of(WallDefenderEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F)
                    .clientTrackingRange(10)
                    .build("wall_defender_stage4"));

    /** The Emeraldger: a villager-based, virus-spawned combatant ({@link EmeraldgerEntity}). Summoned only. */
    public static final RegistryObject<EntityType<EmeraldgerEntity>> EMERALDGER = ENTITIES.register(
            "emeraldger",
            () -> EntityType.Builder.<EmeraldgerEntity>of(EmeraldgerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)            // base villager box; actual box is scaled per-entity by HP
                    .clientTrackingRange(10)
                    .build("emeraldger"));

    /** The Brain Node Warden: a green Warden with a Village Brain Node block for a head ({@link BrainNodeWardenEntity}). */
    public static final RegistryObject<EntityType<BrainNodeWardenEntity>> BRAIN_NODE_WARDEN = ENTITIES.register(
            "brain_node_warden",
            () -> EntityType.Builder.<BrainNodeWardenEntity>of(BrainNodeWardenEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.9F)             // same hitbox as the vanilla Warden
                    .clientTrackingRange(16)
                    .fireImmune()
                    .build("brain_node_warden"));

    /** The Village Brain Node, Unleashed: the boss that erupts when a brainNode block is destroyed ({@link UnleashedBrainNodeEntity}). */
    public static final RegistryObject<EntityType<UnleashedBrainNodeEntity>> UNLEASHED_BRAIN_NODE = ENTITIES.register(
            "unleashed_brain_node",
            () -> EntityType.Builder.<UnleashedBrainNodeEntity>of(UnleashedBrainNodeEntity::new, MobCategory.MONSTER)
                    .sized(1.35F, 4.35F)            // ~1.5x a warden (taller body)
                    .clientTrackingRange(20)
                    .fireImmune()
                    .build("unleashed_brain_node"));

    /** A "vindictive guard": a zombie-villager-looking stage-3 guard the boss makes ({@link VindictiveGuardEntity}). */
    public static final RegistryObject<EntityType<VindictiveGuardEntity>> VINDICTIVE_GUARD = ENTITIES.register(
            "vindictive_guard",
            () -> EntityType.Builder.<VindictiveGuardEntity>of(VindictiveGuardEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .build("vindictive_guard"));

    /** The "Gigachad Golem": an upgraded village iron golem, its own creature type ({@link GigachadGolemEntity}). */
    public static final RegistryObject<EntityType<GigachadGolemEntity>> GIGACHAD_GOLEM = ENTITIES.register(
            "gigachad_golem",
            () -> EntityType.Builder.<GigachadGolemEntity>of(GigachadGolemEntity::new, MobCategory.MISC)
                    .sized(1.4F, 2.7F)             // same hitbox as a vanilla iron golem
                    .clientTrackingRange(12)
                    .build("gigachad_golem"));

    /** The "Vindictive Gigachad": a boss-minion gigachad golem the unleashed brainNode summons ({@link VindictiveGigachadEntity}). */
    public static final RegistryObject<EntityType<VindictiveGigachadEntity>> VINDICTIVE_GIGACHAD = ENTITIES.register(
            "vindictive_gigachad",
            () -> EntityType.Builder.<VindictiveGigachadEntity>of(VindictiveGigachadEntity::new, MobCategory.MONSTER)
                    .sized(1.4F, 2.7F)             // same hitbox as a vanilla iron golem
                    .clientTrackingRange(12)
                    .fireImmune()
                    .build("vindictive_gigachad"));

    /** Native rocket projectile (the golem's JEG-free rocket launcher round). See {@link RocketEntity}. */
    public static final RegistryObject<EntityType<RocketEntity>> ROCKET = ENTITIES.register(
            "rocket",
            () -> EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("rocket"));

    /** Native bullet projectile -- a JEG-free clone of the revolver round. See {@link BulletEntity}. */
    public static final RegistryObject<EntityType<BulletEntity>> BULLET = ENTITIES.register(
            "bullet",
            () -> EntityType.Builder.<BulletEntity>of(BulletEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)             // tiny, like jeg's projectile size 0.05
                    .clientTrackingRange(6)
                    .updateInterval(1)
                    .setShouldReceiveVelocityUpdates(true)
                    .fireImmune()
                    .build("bullet"));

    /** Volatile Limbo Potion projectile -- thrown; bursts and banishes nearby creatures to Limbo. See
     *  {@link VolatileLimboPotionEntity}. */
    public static final RegistryObject<EntityType<VolatileLimboPotionEntity>> VOLATILE_LIMBO_POTION = ENTITIES.register(
            "volatile_limbo_potion",
            () -> EntityType.Builder.<VolatileLimboPotionEntity>of(VolatileLimboPotionEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("volatile_limbo_potion"));

    /** Fire Grenade projectile -- a throwable fire bomb (native molotov replacement). See {@link FireGrenadeEntity}. */
    public static final RegistryObject<EntityType<FireGrenadeEntity>> FIRE_GRENADE = ENTITIES.register(
            "fire_grenade",
            () -> EntityType.Builder.<FireGrenadeEntity>of(FireGrenadeEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("fire_grenade"));

    /** The Limbo Phantom: a 3x black-and-white phantom that phases through bedrock. See {@link LimboPhantomEntity}. */
    public static final RegistryObject<EntityType<LimboPhantomEntity>> LIMBO_PHANTOM = ENTITIES.register(
            "limbo_phantom",
            () -> EntityType.Builder.<LimboPhantomEntity>of(LimboPhantomEntity::new, MobCategory.MONSTER)
                    .sized(2.7F, 1.5F)                  // 3x the vanilla phantom box (0.9 x 0.5), baked to match the model
                    .clientTrackingRange(16)            // glows from far -> track from far
                    .fireImmune()
                    .build("limbo_phantom"));

    // --- Limbo raiders: ghostly 2x versions of the vanilla raid roster (see LimboRaider). Boxes are baked at
    //     2x the vanilla mob's box so the hitbox always matches the 2x-scaled model (no getDimensions reliance).
    public static final RegistryObject<EntityType<LimboPillagerEntity>> LIMBO_PILLAGER = ENTITIES.register(
            "limbo_pillager",
            () -> EntityType.Builder.<LimboPillagerEntity>of(LimboPillagerEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.9F)                  // 2x vanilla pillager (0.6 x 1.95)
                    .clientTrackingRange(16)
                    .build("limbo_pillager"));
    public static final RegistryObject<EntityType<LimboVindicatorEntity>> LIMBO_VINDICATOR = ENTITIES.register(
            "limbo_vindicator",
            () -> EntityType.Builder.<LimboVindicatorEntity>of(LimboVindicatorEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.9F)                  // 2x vanilla vindicator
                    .clientTrackingRange(16)
                    .build("limbo_vindicator"));
    public static final RegistryObject<EntityType<LimboEvokerEntity>> LIMBO_EVOKER = ENTITIES.register(
            "limbo_evoker",
            () -> EntityType.Builder.<LimboEvokerEntity>of(LimboEvokerEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.9F)                  // 2x vanilla evoker
                    .clientTrackingRange(16)
                    .build("limbo_evoker"));
    public static final RegistryObject<EntityType<LimboRavagerEntity>> LIMBO_RAVAGER = ENTITIES.register(
            "limbo_ravager",
            () -> EntityType.Builder.<LimboRavagerEntity>of(LimboRavagerEntity::new, MobCategory.MONSTER)
                    .sized(3.9F, 4.4F)                  // 2x vanilla ravager (1.95 x 2.2)
                    .clientTrackingRange(20)
                    .build("limbo_ravager"));
    public static final RegistryObject<EntityType<LimboVexEntity>> LIMBO_VEX = ENTITIES.register(
            "limbo_vex",
            () -> EntityType.Builder.<LimboVexEntity>of(LimboVexEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.6F)                  // 2x vanilla vex (0.4 x 0.8)
                    .clientTrackingRange(8)
                    .fireImmune()
                    .build("limbo_vex"));

    /** Emerald Virus arrow projectile -- infects whatever it hits. See {@link EmeraldVirusArrowEntity}. */
    public static final RegistryObject<EntityType<EmeraldVirusArrowEntity>> EMERALD_VIRUS_ARROW = ENTITIES.register(
            "emerald_virus_arrow",
            () -> EntityType.Builder.<EmeraldVirusArrowEntity>of(EmeraldVirusArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("emerald_virus_arrow"));

    /** The Rich Sheep: a fertile/rich-looking sheep ({@link RichSheepEntity}) that drops lime wool + an emerald. */
    public static final RegistryObject<EntityType<RichSheepEntity>> RICH_SHEEP = ENTITIES.register(
            "rich_sheep",
            () -> EntityType.Builder.<RichSheepEntity>of(RichSheepEntity::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.3F)
                    .clientTrackingRange(10)
                    .build("rich_sheep"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(WALL_DEFENDER.get(), WallDefenderEntity.createAttributes().build());
        event.put(WALL_DEFENDER_STAGE4.get(), WallDefenderEntity.createAttributes().build());
        event.put(EMERALDGER.get(), Villager.createAttributes().build());
        event.put(BRAIN_NODE_WARDEN.get(), Warden.createAttributes().build());
        event.put(UNLEASHED_BRAIN_NODE.get(), UnleashedBrainNodeEntity.createAttributes().build());
        event.put(VINDICTIVE_GUARD.get(), VindictiveGuardEntity.createAttributes().build());
        event.put(GIGACHAD_GOLEM.get(), net.minecraft.world.entity.animal.IronGolem.createAttributes().build());
        event.put(VINDICTIVE_GIGACHAD.get(), net.minecraft.world.entity.animal.IronGolem.createAttributes().build());
        event.put(LIMBO_PHANTOM.get(), LimboPhantomEntity.createAttributes().build());
        event.put(LIMBO_PILLAGER.get(), LimboPillagerEntity.createAttributes().build());
        event.put(LIMBO_VINDICATOR.get(), LimboVindicatorEntity.createAttributes().build());
        event.put(LIMBO_EVOKER.get(), LimboEvokerEntity.createAttributes().build());
        event.put(LIMBO_RAVAGER.get(), LimboRavagerEntity.createAttributes().build());
        event.put(LIMBO_VEX.get(), LimboVexEntity.createAttributes().build());
        event.put(RICH_SHEEP.get(), net.minecraft.world.entity.animal.Sheep.createAttributes().build());
    }
}
