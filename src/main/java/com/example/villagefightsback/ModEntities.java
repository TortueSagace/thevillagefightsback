package com.example.villagefightsback;
import com.example.villagefightsback.modules.ModuleRegistry;
import com.example.villagefightsback.modules.Modules;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Entity-type registration + attributes. */
public final class ModEntities {
    private ModEntities() {}

    public static final ModuleRegistry<EntityType<?>> ENTITIES =
            ModuleRegistry.create(BuiltInRegistries.ENTITY_TYPE, TheVillageFightsBack.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<WallDefenderEntity>> WALL_DEFENDER = ENTITIES.register(
            "wall_defender",
            () -> EntityType.Builder.<WallDefenderEntity>of(WallDefenderEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F)
                    .clientTrackingRange(10)
                    .build("wall_defender"));

    /** Stage-4 (rocket) wall defender -- same class, but its own type so a separate spawn egg can force it
     *  into permanent rocket mode + the stage-4 texture. */
    public static final DeferredHolder<EntityType<?>, EntityType<WallDefenderEntity>> WALL_DEFENDER_STAGE4 = ENTITIES.register(
            "wall_defender_stage4",
            () -> EntityType.Builder.<WallDefenderEntity>of(WallDefenderEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F)
                    .clientTrackingRange(10)
                    .build("wall_defender_stage4"));

    /** The Emeraldger: a villager-based, virus-spawned combatant ({@link EmeraldgerEntity}). Summoned only. */
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldgerEntity>> EMERALDGER = ENTITIES.register(
            "emeraldger",
            () -> EntityType.Builder.<EmeraldgerEntity>of(EmeraldgerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)            // base villager box; actual box is scaled per-entity by HP
                    .clientTrackingRange(10)
                    .build("emeraldger"));

    /** The Brain Node Warden: a green Warden with a Village Brain Node block for a head ({@link BrainNodeWardenEntity}). */
    public static final DeferredHolder<EntityType<?>, EntityType<BrainNodeWardenEntity>> BRAIN_NODE_WARDEN = ENTITIES.register(
            "brain_node_warden",
            () -> EntityType.Builder.<BrainNodeWardenEntity>of(BrainNodeWardenEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.9F)             // same hitbox as the vanilla Warden
                    .clientTrackingRange(16)
                    .fireImmune()
                    .build("brain_node_warden"));

    /**
     * The Village Spirit: the harmless scout that relocates a village when a node falls below Nightmare /
     * Mega Brutal. MISC so nothing ever natural-spawns it -- it exists only where a node died.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<VillageSpiritEntity>> VILLAGE_SPIRIT =
            ENTITIES.register("village_spirit",
            () -> EntityType.Builder.<VillageSpiritEntity>of(VillageSpiritEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.6F)             // the vanilla Allay's size, since it wears its model
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("village_spirit"));

    /** The Village Brain Node, Unleashed: the boss that erupts when a brainNode block is destroyed ({@link UnleashedBrainNodeEntity}). */
    public static final DeferredHolder<EntityType<?>, EntityType<UnleashedBrainNodeEntity>> UNLEASHED_BRAIN_NODE = ENTITIES.register(
            "unleashed_brain_node",
            () -> EntityType.Builder.<UnleashedBrainNodeEntity>of(UnleashedBrainNodeEntity::new, MobCategory.MONSTER)
                    .sized(1.35F, 4.35F)            // ~1.5x a warden (taller body)
                    .clientTrackingRange(20)
                    .fireImmune()
                    .build("unleashed_brain_node"));

    /** A "vindictive guard": a zombie-villager-looking stage-3 guard the boss makes ({@link VindictiveGuardEntity}). */
    public static final DeferredHolder<EntityType<?>, EntityType<VindictiveGuardEntity>> VINDICTIVE_GUARD = ENTITIES.register(
            "vindictive_guard",
            () -> EntityType.Builder.<VindictiveGuardEntity>of(VindictiveGuardEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .build("vindictive_guard"));

    /** General Darius: a boss-grade upgrade of the stage-4 guard, summoned only by a stage-4 brain-node reflex
     *  ({@link BrainReflexes}). Wields the Alien Smasher + full Limbo set ({@link GeneralDariusEntity}). */
    public static final DeferredHolder<EntityType<?>, EntityType<GeneralDariusEntity>> GENERAL_DARIUS = ENTITIES.register(
            "general_darius",
            () -> EntityType.Builder.<GeneralDariusEntity>of(GeneralDariusEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)            // base villager box
                    .clientTrackingRange(16)
                    .fireImmune()
                    .build("general_darius"));

    /** The "Gigachad Golem": an upgraded village iron golem, its own creature type ({@link GigachadGolemEntity}). */
    public static final DeferredHolder<EntityType<?>, EntityType<GigachadGolemEntity>> GIGACHAD_GOLEM = ENTITIES.register(
            "gigachad_golem",
            () -> EntityType.Builder.<GigachadGolemEntity>of(GigachadGolemEntity::new, MobCategory.MISC)
                    .sized(1.4F, 2.7F)             // same hitbox as a vanilla iron golem
                    .clientTrackingRange(12)
                    .build("gigachad_golem"));

    /** The "Defender Golem": the stage-2 evolution of the village iron golem -- an iron golem with a wall
     *  defender (in a protecting-glass cocoon) for a head, 150 HP, melee + a turret ({@link DefenderGolemEntity}). */
    public static final DeferredHolder<EntityType<?>, EntityType<DefenderGolemEntity>> DEFENDER_GOLEM = ENTITIES.register(
            "defender_golem",
            () -> EntityType.Builder.<DefenderGolemEntity>of(DefenderGolemEntity::new, MobCategory.MISC)
                    .sized(1.4F, 2.7F)             // same hitbox as a vanilla iron golem
                    .clientTrackingRange(12)
                    .build("defender_golem"));

    /** The "Vindictive Gigachad": a boss-minion gigachad golem the unleashed brainNode summons ({@link VindictiveGigachadEntity}). */
    public static final DeferredHolder<EntityType<?>, EntityType<VindictiveGigachadEntity>> VINDICTIVE_GIGACHAD = ENTITIES.register(
            "vindictive_gigachad",
            () -> EntityType.Builder.<VindictiveGigachadEntity>of(VindictiveGigachadEntity::new, MobCategory.MONSTER)
                    .sized(1.4F, 2.7F)             // same hitbox as a vanilla iron golem
                    .clientTrackingRange(12)
                    .fireImmune()
                    .build("vindictive_gigachad"));

    /** Native rocket projectile (the golem's JEG-free rocket launcher round). See {@link RocketEntity}. */
    public static final DeferredHolder<EntityType<?>, EntityType<RocketEntity>> ROCKET = ENTITIES.register(
            "rocket",
            () -> EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("rocket"));

    /** Native wind charge projectile -- a clone of the vanilla 1.21 wind charge (knockback-only gust). See
     *  {@link WindChargeEntity}. */
    public static final DeferredHolder<EntityType<?>, EntityType<WindChargeEntity>> WIND_CHARGE = ENTITIES.register(
            "wind_charge",
            () -> EntityType.Builder.<WindChargeEntity>of(WindChargeEntity::new, MobCategory.MISC)
                    .sized(0.3125F, 0.3125F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("wind_charge"));

    /** Native bullet projectile -- a JEG-free clone of the revolver round. See {@link BulletEntity}. */
    public static final DeferredHolder<EntityType<?>, EntityType<BulletEntity>> BULLET = ENTITIES.register(
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
    public static final DeferredHolder<EntityType<?>, EntityType<VolatileLimboPotionEntity>> VOLATILE_LIMBO_POTION = ENTITIES.register(
            "volatile_limbo_potion",
            () -> EntityType.Builder.<VolatileLimboPotionEntity>of(VolatileLimboPotionEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("volatile_limbo_potion"));

    /** Fire Grenade projectile -- a throwable fire bomb (native molotov replacement). See {@link FireGrenadeEntity}. */
    public static final DeferredHolder<EntityType<?>, EntityType<FireGrenadeEntity>> FIRE_GRENADE = ENTITIES.register(
            "fire_grenade",
            () -> EntityType.Builder.<FireGrenadeEntity>of(FireGrenadeEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("fire_grenade"));

    /** The Emerald Egg's thrown projectile (inflicts Emerald Virus on hit). See {@link EmeraldEggEntity}. */
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldEggEntity>> EMERALD_EGG = ENTITIES.register(
            "emerald_egg",
            () -> EntityType.Builder.<EmeraldEggEntity>of(EmeraldEggEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("emerald_egg"));

    /** The Limbo Phantom: a 3x black-and-white phantom that phases through bedrock. See {@link LimboPhantomEntity}. */
    public static final DeferredHolder<EntityType<?>, EntityType<LimboPhantomEntity>> LIMBO_PHANTOM = ENTITIES.register(
            "limbo_phantom",
            () -> EntityType.Builder.<LimboPhantomEntity>of(LimboPhantomEntity::new, MobCategory.MONSTER)
                    .sized(2.7F, 1.5F)                  // 3x the vanilla phantom box (0.9 x 0.5), baked to match the model
                    .clientTrackingRange(16)            // glows from far -> track from far
                    .fireImmune()
                    .build("limbo_phantom"));

    // --- Limbo raiders: ghostly 2x versions of the vanilla raid roster (see LimboRaider). Boxes are baked at
    //     2x the vanilla mob's box so the hitbox always matches the 2x-scaled model (no getDimensions reliance).
    public static final DeferredHolder<EntityType<?>, EntityType<LimboPillagerEntity>> LIMBO_PILLAGER = ENTITIES.register(
            "limbo_pillager",
            () -> EntityType.Builder.<LimboPillagerEntity>of(LimboPillagerEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.9F)                  // 2x vanilla pillager (0.6 x 1.95)
                    .clientTrackingRange(16)
                    .build("limbo_pillager"));
    public static final DeferredHolder<EntityType<?>, EntityType<LimboVindicatorEntity>> LIMBO_VINDICATOR = ENTITIES.register(
            "limbo_vindicator",
            () -> EntityType.Builder.<LimboVindicatorEntity>of(LimboVindicatorEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.9F)                  // 2x vanilla vindicator
                    .clientTrackingRange(16)
                    .build("limbo_vindicator"));
    public static final DeferredHolder<EntityType<?>, EntityType<LimboEvokerEntity>> LIMBO_EVOKER = ENTITIES.register(
            "limbo_evoker",
            () -> EntityType.Builder.<LimboEvokerEntity>of(LimboEvokerEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.9F)                  // 2x vanilla evoker
                    .clientTrackingRange(16)
                    .build("limbo_evoker"));
    public static final DeferredHolder<EntityType<?>, EntityType<LimboRavagerEntity>> LIMBO_RAVAGER = ENTITIES.register(
            "limbo_ravager",
            () -> EntityType.Builder.<LimboRavagerEntity>of(LimboRavagerEntity::new, MobCategory.MONSTER)
                    .sized(3.9F, 4.4F)                  // 2x vanilla ravager (1.95 x 2.2)
                    .clientTrackingRange(20)
                    .build("limbo_ravager"));
    public static final DeferredHolder<EntityType<?>, EntityType<LimboVexEntity>> LIMBO_VEX = ENTITIES.register(
            "limbo_vex",
            () -> EntityType.Builder.<LimboVexEntity>of(LimboVexEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.6F)                  // 2x vanilla vex (0.4 x 0.8)
                    .clientTrackingRange(8)
                    .fireImmune()
                    .build("limbo_vex"));
    public static final DeferredHolder<EntityType<?>, EntityType<LimboWitchEntity>> LIMBO_WITCH = ENTITIES.register(
            "limbo_witch",
            () -> EntityType.Builder.<LimboWitchEntity>of(LimboWitchEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.9F)                  // 2x vanilla witch (0.6 x 1.95)
                    .clientTrackingRange(16)
                    .build("limbo_witch"));

    /** Emerald Virus arrow projectile -- infects whatever it hits. See {@link EmeraldVirusArrowEntity}. */
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldVirusArrowEntity>> EMERALD_VIRUS_ARROW = ENTITIES.register(
            "emerald_virus_arrow",
            () -> EntityType.Builder.<EmeraldVirusArrowEntity>of(EmeraldVirusArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("emerald_virus_arrow"));

    /** The Rich Sheep: a fertile/rich-looking sheep ({@link RichSheepEntity}) that drops lime wool + an emerald. */
    public static final DeferredHolder<EntityType<?>, EntityType<RichSheepEntity>> RICH_SHEEP = ENTITIES.register(
            "rich_sheep",
            () -> EntityType.Builder.<RichSheepEntity>of(RichSheepEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 1.3F)
                    .clientTrackingRange(10)
                    .build("rich_sheep"));

    // --- v1.0.3 EMERALD faction ("mob infection"): vanilla mobs reskinned + emerald-faction AI ---
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldCow>> EMERALD_COW = ENTITIES.register("emerald_cow",
            () -> EntityType.Builder.<EmeraldCow>of(EmeraldCow::new, MobCategory.MONSTER).sized(0.9F, 1.4F).clientTrackingRange(10).build("emerald_cow"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldChicken>> EMERALD_CHICKEN = ENTITIES.register("emerald_chicken",
            () -> EntityType.Builder.<EmeraldChicken>of(EmeraldChicken::new, MobCategory.MONSTER).sized(0.4F, 0.7F).clientTrackingRange(10).build("emerald_chicken"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldCat>> EMERALD_CAT = ENTITIES.register("emerald_cat",
            () -> EntityType.Builder.<EmeraldCat>of(EmeraldCat::new, MobCategory.MONSTER).sized(0.6F, 0.7F).clientTrackingRange(10).build("emerald_cat"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldEndermite>> EMERALD_ENDERMITE = ENTITIES.register("emerald_endermite",
            () -> EntityType.Builder.<EmeraldEndermite>of(EmeraldEndermite::new, MobCategory.MONSTER).sized(0.4F, 0.3F).clientTrackingRange(8).build("emerald_endermite"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldWolf>> EMERALD_WOLF = ENTITIES.register("emerald_wolf",
            () -> EntityType.Builder.<EmeraldWolf>of(EmeraldWolf::new, MobCategory.MONSTER).sized(0.6F, 0.85F).clientTrackingRange(10).build("emerald_wolf"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldHorse>> EMERALD_HORSE = ENTITIES.register("emerald_horse",
            () -> EntityType.Builder.<EmeraldHorse>of(EmeraldHorse::new, MobCategory.MONSTER).sized(1.396F, 1.6F).clientTrackingRange(10).build("emerald_horse"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldPig>> EMERALD_PIG = ENTITIES.register("emerald_pig",
            () -> EntityType.Builder.<EmeraldPig>of(EmeraldPig::new, MobCategory.MONSTER).sized(0.9F, 0.9F).clientTrackingRange(10).build("emerald_pig"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldSalmon>> EMERALD_SALMON = ENTITIES.register("emerald_salmon",
            () -> EntityType.Builder.<EmeraldSalmon>of(EmeraldSalmon::new, MobCategory.MONSTER).sized(0.7F, 0.4F).clientTrackingRange(8).build("emerald_salmon"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldBat>> EMERALD_BAT = ENTITIES.register("emerald_bat",
            () -> EntityType.Builder.<EmeraldBat>of(EmeraldBat::new, MobCategory.MONSTER).sized(0.5F, 0.9F).clientTrackingRange(8).build("emerald_bat"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldZombie>> EMERALD_ZOMBIE = ENTITIES.register("emerald_zombie",
            () -> EntityType.Builder.<EmeraldZombie>of(EmeraldZombie::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8).build("emerald_zombie"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldSkeleton>> EMERALD_SKELETON = ENTITIES.register("emerald_skeleton",
            () -> EntityType.Builder.<EmeraldSkeleton>of(EmeraldSkeleton::new, MobCategory.MONSTER).sized(0.6F, 1.99F).clientTrackingRange(8).build("emerald_skeleton"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldCreeper>> EMERALD_CREEPER = ENTITIES.register("emerald_creeper",
            () -> EntityType.Builder.<EmeraldCreeper>of(EmeraldCreeper::new, MobCategory.MONSTER).sized(0.6F, 1.7F).clientTrackingRange(8).build("emerald_creeper"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldEnderman>> EMERALD_ENDERMAN = ENTITIES.register("emerald_enderman",
            () -> EntityType.Builder.<EmeraldEnderman>of(EmeraldEnderman::new, MobCategory.MONSTER).sized(0.6F, 2.9F).clientTrackingRange(8).build("emerald_enderman"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldIronGolem>> EMERALD_IRON_GOLEM = ENTITIES.register("emerald_iron_golem",
            () -> EntityType.Builder.<EmeraldIronGolem>of(EmeraldIronGolem::new, MobCategory.MISC).sized(1.4F, 2.7F).clientTrackingRange(10).build("emerald_iron_golem"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldDefenderGolemEntity>> EMERALD_DEFENDER_GOLEM = ENTITIES.register("emerald_defender_golem",
            () -> EntityType.Builder.<EmeraldDefenderGolemEntity>of(EmeraldDefenderGolemEntity::new, MobCategory.MISC).sized(1.4F, 2.7F).clientTrackingRange(12).build("emerald_defender_golem"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldGigachadGolemEntity>> EMERALD_GIGACHAD_GOLEM = ENTITIES.register("emerald_gigachad_golem",
            () -> EntityType.Builder.<EmeraldGigachadGolemEntity>of(EmeraldGigachadGolemEntity::new, MobCategory.MISC).sized(1.4F, 2.7F).clientTrackingRange(12).build("emerald_gigachad_golem"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldPhantom>> EMERALD_PHANTOM = ENTITIES.register("emerald_phantom",
            () -> EntityType.Builder.<EmeraldPhantom>of(EmeraldPhantom::new, MobCategory.MONSTER).sized(0.9F, 0.5F).clientTrackingRange(8).build("emerald_phantom"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldWither>> EMERALD_WITHER = ENTITIES.register("emerald_wither",
            () -> EntityType.Builder.<EmeraldWither>of(EmeraldWither::new, MobCategory.MONSTER).sized(0.9F, 3.5F).fireImmune().clientTrackingRange(10).build("emerald_wither"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldGuardian>> EMERALD_GUARDIAN = ENTITIES.register("emerald_guardian",
            () -> EntityType.Builder.<EmeraldGuardian>of(EmeraldGuardian::new, MobCategory.MONSTER).sized(0.85F, 0.85F).clientTrackingRange(10).build("emerald_guardian"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmeraldElderGuardian>> EMERALD_ELDER_GUARDIAN = ENTITIES.register("emerald_elder_guardian",
            () -> EntityType.Builder.<EmeraldElderGuardian>of(EmeraldElderGuardian::new, MobCategory.MONSTER).sized(1.9975F, 1.9975F).clientTrackingRange(10).build("emerald_elder_guardian"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    /**
     * Registers attributes only for entity types whose module is enabled: a disabled module's holder is unbound
     * on purpose, and reading it here would be the one place a "removed" creature still crashed the game.
     */
    private static void putIfEnabled(EntityAttributeCreationEvent event,
            net.neoforged.neoforge.registries.DeferredHolder<EntityType<?>, ? extends EntityType<? extends net.minecraft.world.entity.LivingEntity>> holder,
            net.minecraft.world.entity.ai.attributes.AttributeSupplier attributes) {
        if (Modules.contentEnabled("entity_type", holder.getKey().location().getPath())) {
            event.put(holder.get(), attributes);
        }
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        putIfEnabled(event, WALL_DEFENDER, WallDefenderEntity.createAttributes().build());
        putIfEnabled(event, WALL_DEFENDER_STAGE4, WallDefenderEntity.createAttributes().build());
        putIfEnabled(event, EMERALDGER, Villager.createAttributes().build());
        putIfEnabled(event, BRAIN_NODE_WARDEN, Warden.createAttributes().build());
        putIfEnabled(event, VILLAGE_SPIRIT, VillageSpiritEntity.createAttributes().build());
        putIfEnabled(event, UNLEASHED_BRAIN_NODE, UnleashedBrainNodeEntity.createAttributes().build());
        putIfEnabled(event, VINDICTIVE_GUARD, VindictiveGuardEntity.createAttributes().build());
        putIfEnabled(event, GENERAL_DARIUS, GeneralDariusEntity.createAttributes().build());
        putIfEnabled(event, GIGACHAD_GOLEM, net.minecraft.world.entity.animal.IronGolem.createAttributes().build());
        putIfEnabled(event, DEFENDER_GOLEM, DefenderGolemEntity.createAttributes().build());
        putIfEnabled(event, VINDICTIVE_GIGACHAD, net.minecraft.world.entity.animal.IronGolem.createAttributes().build());
        putIfEnabled(event, LIMBO_PHANTOM, LimboPhantomEntity.createAttributes().build());
        putIfEnabled(event, LIMBO_PILLAGER, LimboPillagerEntity.createAttributes().build());
        putIfEnabled(event, LIMBO_VINDICATOR, LimboVindicatorEntity.createAttributes().build());
        putIfEnabled(event, LIMBO_EVOKER, LimboEvokerEntity.createAttributes().build());
        putIfEnabled(event, LIMBO_RAVAGER, LimboRavagerEntity.createAttributes().build());
        putIfEnabled(event, LIMBO_VEX, LimboVexEntity.createAttributes().build());
        putIfEnabled(event, LIMBO_WITCH, LimboWitchEntity.createAttributes().build());
        putIfEnabled(event, RICH_SHEEP, RichSheepEntity.createAttributes().build());
        // --- emerald faction ---
        putIfEnabled(event, EMERALD_COW, EmeraldCow.createAttributes().build());
        putIfEnabled(event, EMERALD_CHICKEN, EmeraldChicken.createAttributes().build());
        putIfEnabled(event, EMERALD_CAT, EmeraldCat.createAttributes().build());
        putIfEnabled(event, EMERALD_ENDERMITE, EmeraldEndermite.createAttributes().build());
        putIfEnabled(event, EMERALD_WOLF, EmeraldWolf.createAttributes().build());
        putIfEnabled(event, EMERALD_HORSE, EmeraldHorse.createAttributes().build());
        putIfEnabled(event, EMERALD_PIG, EmeraldPig.createAttributes().build());
        putIfEnabled(event, EMERALD_SALMON, EmeraldSalmon.createAttributes().build());
        putIfEnabled(event, EMERALD_BAT, EmeraldBat.createAttributes().build());
        putIfEnabled(event, EMERALD_ZOMBIE, EmeraldZombie.createAttributes().build());
        putIfEnabled(event, EMERALD_SKELETON, EmeraldSkeleton.createAttributes().build());
        putIfEnabled(event, EMERALD_CREEPER, EmeraldCreeper.createAttributes().build());
        putIfEnabled(event, EMERALD_ENDERMAN, EmeraldEnderman.createAttributes().build());
        putIfEnabled(event, EMERALD_IRON_GOLEM, EmeraldIronGolem.createAttributes().build());
        putIfEnabled(event, EMERALD_DEFENDER_GOLEM, DefenderGolemEntity.createAttributes().build());      // 150 HP + turret
        putIfEnabled(event, EMERALD_GIGACHAD_GOLEM, net.minecraft.world.entity.animal.IronGolem.createAttributes().build()); // 200 HP applied at spawn (armGolemFull)
        putIfEnabled(event, EMERALD_PHANTOM, EmeraldPhantom.createAttributes().build());
        putIfEnabled(event, EMERALD_WITHER, EmeraldWither.createAttributes().build());
        putIfEnabled(event, EMERALD_GUARDIAN, EmeraldGuardian.createAttributes().build());
        putIfEnabled(event, EMERALD_ELDER_GUARDIAN, EmeraldElderGuardian.createAttributes().build());
    }
}
