package com.example.villagefightsback.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.level.pathfinder.BlockPathTypes;

/**
 * Reaches the compiler-generated enum constructor of {@link BlockPathTypes}, so the mod can add a constant of its
 * own -- which Forge supports outright and Fabric has no mechanism for.
 */
@Mixin(BlockPathTypes.class)
public interface BlockPathTypesInvoker {
    @Invoker("<init>")
    static BlockPathTypes tvfb$create(String internalName, int internalId, float malus) {
        throw new AssertionError("mixin did not apply");
    }
}
