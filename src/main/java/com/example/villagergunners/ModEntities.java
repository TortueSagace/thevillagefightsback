package com.example.villagergunners;

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
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, VillagerGunners.MODID);

    public static final RegistryObject<EntityType<WallDefenderEntity>> WALL_DEFENDER = ENTITIES.register(
            "wall_defender",
            () -> EntityType.Builder.<WallDefenderEntity>of(WallDefenderEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F)
                    .clientTrackingRange(10)
                    .build("wall_defender"));

    /** The Emeraldger: a villager-based, virus-spawned combatant ({@link EmeraldgerEntity}). Summoned only. */
    public static final RegistryObject<EntityType<EmeraldgerEntity>> EMERALDGER = ENTITIES.register(
            "emeraldger",
            () -> EntityType.Builder.<EmeraldgerEntity>of(EmeraldgerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)            // base villager box; actual box is scaled per-entity by HP
                    .clientTrackingRange(10)
                    .build("emeraldger"));

    /** The Gravemind Warden: a green Warden with a Village Gravemind block for a head ({@link GravemindWardenEntity}). */
    public static final RegistryObject<EntityType<GravemindWardenEntity>> GRAVEMIND_WARDEN = ENTITIES.register(
            "gravemind_warden",
            () -> EntityType.Builder.<GravemindWardenEntity>of(GravemindWardenEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.9F)             // same hitbox as the vanilla Warden
                    .clientTrackingRange(16)
                    .fireImmune()
                    .build("gravemind_warden"));

    /** The Village Gravemind, Unleashed: the boss that erupts when a gravemind block is destroyed ({@link UnleashedGravemindEntity}). */
    public static final RegistryObject<EntityType<UnleashedGravemindEntity>> UNLEASHED_GRAVEMIND = ENTITIES.register(
            "unleashed_gravemind",
            () -> EntityType.Builder.<UnleashedGravemindEntity>of(UnleashedGravemindEntity::new, MobCategory.MONSTER)
                    .sized(1.35F, 4.35F)            // ~1.5x a warden (taller body)
                    .clientTrackingRange(20)
                    .fireImmune()
                    .build("unleashed_gravemind"));

    /** A "vindictive guard": a zombie-villager-looking stage-3 guard the boss makes ({@link VindictiveGuardEntity}). */
    public static final RegistryObject<EntityType<VindictiveGuardEntity>> VINDICTIVE_GUARD = ENTITIES.register(
            "vindictive_guard",
            () -> EntityType.Builder.<VindictiveGuardEntity>of(VindictiveGuardEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .build("vindictive_guard"));

    /** Native rocket projectile (the golem's JEG-free rocket launcher round). See {@link RocketEntity}. */
    public static final RegistryObject<EntityType<RocketEntity>> ROCKET = ENTITIES.register(
            "rocket",
            () -> EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("rocket"));

    /** Limbo Arrow projectile -- banishes whatever it hits to Limbo. See {@link LimboArrowEntity}. */
    public static final RegistryObject<EntityType<LimboArrowEntity>> LIMBO_ARROW = ENTITIES.register(
            "limbo_arrow",
            () -> EntityType.Builder.<LimboArrowEntity>of(LimboArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("limbo_arrow"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(WALL_DEFENDER.get(), WallDefenderEntity.createAttributes().build());
        event.put(EMERALDGER.get(), Villager.createAttributes().build());
        event.put(GRAVEMIND_WARDEN.get(), Warden.createAttributes().build());
        event.put(UNLEASHED_GRAVEMIND.get(), UnleashedGravemindEntity.createAttributes().build());
        event.put(VINDICTIVE_GUARD.get(), VindictiveGuardEntity.createAttributes().build());
    }
}
