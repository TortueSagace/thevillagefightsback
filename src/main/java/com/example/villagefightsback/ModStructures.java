package com.example.villagefightsback;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's worldgen structures. There is exactly one, and it is the first the mod has ever had.
 *
 * <p>Everything the village builds is placed at RUNTIME by {@link HousePlacer} from an {@code .nbt} template,
 * because it is built in reaction to something -- a settler arriving, a stage being reached. The Limbo portal
 * is the opposite: it is part of the world before anybody arrives, so it belongs to worldgen proper.
 */
public final class ModStructures {
    private ModStructures() {}

    public static final DeferredRegister<StructureType<?>> STRUCTURES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, TheVillageFightsBack.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, TheVillageFightsBack.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<LimboPortalStructure>> LIMBO_PORTAL =
            STRUCTURES.register("limbo_portal", () -> () -> LimboPortalStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> LIMBO_PORTAL_PIECE =
            PIECES.register("limbo_portal", () -> (StructurePieceType.ContextlessType)
                    LimboPortalStructure.Piece::new);

    public static void register(IEventBus modBus) {
        STRUCTURES.register(modBus);
        PIECES.register(modBus);
    }
}
