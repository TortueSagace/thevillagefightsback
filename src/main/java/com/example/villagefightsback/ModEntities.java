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

    /** General Darius: a boss-grade upgrade of the stage-4 guard, summoned only by a stage-4 brain-node reflex
     *  ({@link BrainReflexes}). Wields the Alien Smasher + full Limbo set ({@link GeneralDariusEntity}). */
    public static final RegistryObject<EntityType<GeneralDariusEntity>> GENERAL_DARIUS = ENTITIES.register(
            "general_darius",
            () -> EntityType.Builder.<GeneralDariusEntity>of(GeneralDariusEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)            // base villager box
                    .clientTrackingRange(16)
                    .fireImmune()
                    .build("general_darius"));

    /** The "Gigachad Golem": an upgraded village iron golem, its own creature type ({@link GigachadGolemEntity}). */
    public static final RegistryObject<EntityType<GigachadGolemEntity>> GIGACHAD_GOLEM = ENTITIES.register(
            "gigachad_golem",
            () -> EntityType.Builder.<GigachadGolemEntity>of(GigachadGolemEntity::new, MobCategory.MISC)
                    .sized(1.4F, 2.7F)             // same hitbox as a vanilla iron golem
                    .clientTrackingRange(12)
                    .build("gigachad_golem"));

    /** The "Defender Golem": the stage-2 evolution of the village iron golem -- an iron golem with a wall
     *  defender (in a protecting-glass cocoon) for a head, 150 HP, melee + a turret ({@link DefenderGolemEntity}). */
    public static final RegistryObject<EntityType<DefenderGolemEntity>> DEFENDER_GOLEM = ENTITIES.register(
            "defender_golem",
            () -> EntityType.Builder.<DefenderGolemEntity>of(DefenderGolemEntity::new, MobCategory.MISC)
                    .sized(1.4F, 2.7F)             // same hitbox as a vanilla iron golem
                    .clientTrackingRange(12)
                    .build("defender_golem"));

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

    /** Native wind charge projectile -- a clone of the vanilla 1.21 wind charge (knockback-only gust). See
     *  {@link WindChargeEntity}. */
    public static final RegistryObject<EntityType<WindChargeEntity>> WIND_CHARGE = ENTITIES.register(
            "wind_charge",
            () -> EntityType.Builder.<WindChargeEntity>of(WindChargeEntity::new, MobCategory.MISC)
                    .sized(0.3125F, 0.3125F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("wind_charge"));

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

    /** The Emerald Egg's thrown projectile (inflicts Emerald Virus on hit). See {@link EmeraldEggEntity}. */
    public static final RegistryObject<EntityType<EmeraldEggEntity>> EMERALD_EGG = ENTITIES.register(
            "emerald_egg",
            () -> EntityType.Builder.<EmeraldEggEntity>of(EmeraldEggEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("emerald_egg"));

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
    public static final RegistryObject<EntityType<LimboWitchEntity>> LIMBO_WITCH = ENTITIES.register(
            "limbo_witch",
            () -> EntityType.Builder.<LimboWitchEntity>of(LimboWitchEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.9F)                  // 2x vanilla witch (0.6 x 1.95)
                    .clientTrackingRange(16)
                    .build("limbo_witch"));

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
            () -> EntityType.Builder.<RichSheepEntity>of(RichSheepEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 1.3F)
                    .clientTrackingRange(10)
                    .build("rich_sheep"));

    // --- v1.0.3 EMERALD faction ("mob infection"): vanilla mobs reskinned + emerald-faction AI ---
    public static final RegistryObject<EntityType<EmeraldCow>> EMERALD_COW = ENTITIES.register("emerald_cow",
            () -> EntityType.Builder.<EmeraldCow>of(EmeraldCow::new, MobCategory.MONSTER).sized(0.9F, 1.4F).clientTrackingRange(10).build("emerald_cow"));
    public static final RegistryObject<EntityType<EmeraldChicken>> EMERALD_CHICKEN = ENTITIES.register("emerald_chicken",
            () -> EntityType.Builder.<EmeraldChicken>of(EmeraldChicken::new, MobCategory.MONSTER).sized(0.4F, 0.7F).clientTrackingRange(10).build("emerald_chicken"));
    public static final RegistryObject<EntityType<EmeraldCat>> EMERALD_CAT = ENTITIES.register("emerald_cat",
            () -> EntityType.Builder.<EmeraldCat>of(EmeraldCat::new, MobCategory.MONSTER).sized(0.6F, 0.7F).clientTrackingRange(10).build("emerald_cat"));
    public static final RegistryObject<EntityType<EmeraldEndermite>> EMERALD_ENDERMITE = ENTITIES.register("emerald_endermite",
            () -> EntityType.Builder.<EmeraldEndermite>of(EmeraldEndermite::new, MobCategory.MONSTER).sized(0.4F, 0.3F).clientTrackingRange(8).build("emerald_endermite"));
    public static final RegistryObject<EntityType<EmeraldWolf>> EMERALD_WOLF = ENTITIES.register("emerald_wolf",
            () -> EntityType.Builder.<EmeraldWolf>of(EmeraldWolf::new, MobCategory.MONSTER).sized(0.6F, 0.85F).clientTrackingRange(10).build("emerald_wolf"));
    public static final RegistryObject<EntityType<EmeraldHorse>> EMERALD_HORSE = ENTITIES.register("emerald_horse",
            () -> EntityType.Builder.<EmeraldHorse>of(EmeraldHorse::new, MobCategory.MONSTER).sized(1.396F, 1.6F).clientTrackingRange(10).build("emerald_horse"));
    public static final RegistryObject<EntityType<EmeraldPig>> EMERALD_PIG = ENTITIES.register("emerald_pig",
            () -> EntityType.Builder.<EmeraldPig>of(EmeraldPig::new, MobCategory.MONSTER).sized(0.9F, 0.9F).clientTrackingRange(10).build("emerald_pig"));
    public static final RegistryObject<EntityType<EmeraldSalmon>> EMERALD_SALMON = ENTITIES.register("emerald_salmon",
            () -> EntityType.Builder.<EmeraldSalmon>of(EmeraldSalmon::new, MobCategory.MONSTER).sized(0.7F, 0.4F).clientTrackingRange(8).build("emerald_salmon"));
    public static final RegistryObject<EntityType<EmeraldBat>> EMERALD_BAT = ENTITIES.register("emerald_bat",
            () -> EntityType.Builder.<EmeraldBat>of(EmeraldBat::new, MobCategory.MONSTER).sized(0.5F, 0.9F).clientTrackingRange(8).build("emerald_bat"));
    public static final RegistryObject<EntityType<EmeraldZombie>> EMERALD_ZOMBIE = ENTITIES.register("emerald_zombie",
            () -> EntityType.Builder.<EmeraldZombie>of(EmeraldZombie::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8).build("emerald_zombie"));
    public static final RegistryObject<EntityType<EmeraldSkeleton>> EMERALD_SKELETON = ENTITIES.register("emerald_skeleton",
            () -> EntityType.Builder.<EmeraldSkeleton>of(EmeraldSkeleton::new, MobCategory.MONSTER).sized(0.6F, 1.99F).clientTrackingRange(8).build("emerald_skeleton"));
    public static final RegistryObject<EntityType<EmeraldCreeper>> EMERALD_CREEPER = ENTITIES.register("emerald_creeper",
            () -> EntityType.Builder.<EmeraldCreeper>of(EmeraldCreeper::new, MobCategory.MONSTER).sized(0.6F, 1.7F).clientTrackingRange(8).build("emerald_creeper"));
    public static final RegistryObject<EntityType<EmeraldEnderman>> EMERALD_ENDERMAN = ENTITIES.register("emerald_enderman",
            () -> EntityType.Builder.<EmeraldEnderman>of(EmeraldEnderman::new, MobCategory.MONSTER).sized(0.6F, 2.9F).clientTrackingRange(8).build("emerald_enderman"));
    public static final RegistryObject<EntityType<EmeraldIronGolem>> EMERALD_IRON_GOLEM = ENTITIES.register("emerald_iron_golem",
            () -> EntityType.Builder.<EmeraldIronGolem>of(EmeraldIronGolem::new, MobCategory.MISC).sized(1.4F, 2.7F).clientTrackingRange(10).build("emerald_iron_golem"));
    public static final RegistryObject<EntityType<EmeraldDefenderGolemEntity>> EMERALD_DEFENDER_GOLEM = ENTITIES.register("emerald_defender_golem",
            () -> EntityType.Builder.<EmeraldDefenderGolemEntity>of(EmeraldDefenderGolemEntity::new, MobCategory.MISC).sized(1.4F, 2.7F).clientTrackingRange(12).build("emerald_defender_golem"));
    public static final RegistryObject<EntityType<EmeraldGigachadGolemEntity>> EMERALD_GIGACHAD_GOLEM = ENTITIES.register("emerald_gigachad_golem",
            () -> EntityType.Builder.<EmeraldGigachadGolemEntity>of(EmeraldGigachadGolemEntity::new, MobCategory.MISC).sized(1.4F, 2.7F).clientTrackingRange(12).build("emerald_gigachad_golem"));
    public static final RegistryObject<EntityType<EmeraldPhantom>> EMERALD_PHANTOM = ENTITIES.register("emerald_phantom",
            () -> EntityType.Builder.<EmeraldPhantom>of(EmeraldPhantom::new, MobCategory.MONSTER).sized(0.9F, 0.5F).clientTrackingRange(8).build("emerald_phantom"));
    public static final RegistryObject<EntityType<EmeraldWither>> EMERALD_WITHER = ENTITIES.register("emerald_wither",
            () -> EntityType.Builder.<EmeraldWither>of(EmeraldWither::new, MobCategory.MONSTER).sized(0.9F, 3.5F).fireImmune().clientTrackingRange(10).build("emerald_wither"));
    public static final RegistryObject<EntityType<EmeraldGuardian>> EMERALD_GUARDIAN = ENTITIES.register("emerald_guardian",
            () -> EntityType.Builder.<EmeraldGuardian>of(EmeraldGuardian::new, MobCategory.MONSTER).sized(0.85F, 0.85F).clientTrackingRange(10).build("emerald_guardian"));
    public static final RegistryObject<EntityType<EmeraldElderGuardian>> EMERALD_ELDER_GUARDIAN = ENTITIES.register("emerald_elder_guardian",
            () -> EntityType.Builder.<EmeraldElderGuardian>of(EmeraldElderGuardian::new, MobCategory.MONSTER).sized(1.9975F, 1.9975F).clientTrackingRange(10).build("emerald_elder_guardian"));

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
        event.put(GENERAL_DARIUS.get(), GeneralDariusEntity.createAttributes().build());
        event.put(GIGACHAD_GOLEM.get(), net.minecraft.world.entity.animal.IronGolem.createAttributes().build());
        event.put(DEFENDER_GOLEM.get(), DefenderGolemEntity.createAttributes().build());
        event.put(VINDICTIVE_GIGACHAD.get(), net.minecraft.world.entity.animal.IronGolem.createAttributes().build());
        event.put(LIMBO_PHANTOM.get(), LimboPhantomEntity.createAttributes().build());
        event.put(LIMBO_PILLAGER.get(), LimboPillagerEntity.createAttributes().build());
        event.put(LIMBO_VINDICATOR.get(), LimboVindicatorEntity.createAttributes().build());
        event.put(LIMBO_EVOKER.get(), LimboEvokerEntity.createAttributes().build());
        event.put(LIMBO_RAVAGER.get(), LimboRavagerEntity.createAttributes().build());
        event.put(LIMBO_VEX.get(), LimboVexEntity.createAttributes().build());
        event.put(LIMBO_WITCH.get(), LimboWitchEntity.createAttributes().build());
        event.put(RICH_SHEEP.get(), RichSheepEntity.createAttributes().build());
        // --- emerald faction ---
        event.put(EMERALD_COW.get(), EmeraldCow.createAttributes().build());
        event.put(EMERALD_CHICKEN.get(), EmeraldChicken.createAttributes().build());
        event.put(EMERALD_CAT.get(), EmeraldCat.createAttributes().build());
        event.put(EMERALD_ENDERMITE.get(), EmeraldEndermite.createAttributes().build());
        event.put(EMERALD_WOLF.get(), EmeraldWolf.createAttributes().build());
        event.put(EMERALD_HORSE.get(), EmeraldHorse.createAttributes().build());
        event.put(EMERALD_PIG.get(), EmeraldPig.createAttributes().build());
        event.put(EMERALD_SALMON.get(), EmeraldSalmon.createAttributes().build());
        event.put(EMERALD_BAT.get(), EmeraldBat.createAttributes().build());
        event.put(EMERALD_ZOMBIE.get(), EmeraldZombie.createAttributes().build());
        event.put(EMERALD_SKELETON.get(), EmeraldSkeleton.createAttributes().build());
        event.put(EMERALD_CREEPER.get(), EmeraldCreeper.createAttributes().build());
        event.put(EMERALD_ENDERMAN.get(), EmeraldEnderman.createAttributes().build());
        event.put(EMERALD_IRON_GOLEM.get(), EmeraldIronGolem.createAttributes().build());
        event.put(EMERALD_DEFENDER_GOLEM.get(), DefenderGolemEntity.createAttributes().build());      // 150 HP + turret
        event.put(EMERALD_GIGACHAD_GOLEM.get(), net.minecraft.world.entity.animal.IronGolem.createAttributes().build()); // 200 HP applied at spawn (armGolemFull)
        event.put(EMERALD_PHANTOM.get(), EmeraldPhantom.createAttributes().build());
        event.put(EMERALD_WITHER.get(), EmeraldWither.createAttributes().build());
        event.put(EMERALD_GUARDIAN.get(), EmeraldGuardian.createAttributes().build());
        event.put(EMERALD_ELDER_GUARDIAN.get(), EmeraldElderGuardian.createAttributes().build());
    }
}
