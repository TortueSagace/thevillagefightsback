package com.example.villagergunners;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Block-entity type registration. */
public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VillagerGunners.MODID);

    public static final RegistryObject<BlockEntityType<WallDefenderBlockEntity>> WALL_DEFENDER =
            BLOCK_ENTITIES.register("wall_defender",
                    () -> BlockEntityType.Builder.of(WallDefenderBlockEntity::new, ModBlocks.WALL_DEFENDER.get())
                            .build(null));

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
