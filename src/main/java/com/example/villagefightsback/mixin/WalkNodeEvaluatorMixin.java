package com.example.villagefightsback.mixin;

import com.example.villagefightsback.platform.PathTypeProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Lets a block name its own path type, which is what Forge's Block.getBlockPathType hook does.
 *
 * <p>This is the fake-cobblestone smart door: it reports a type that is BLOCKED by default, and the village's own
 * units opt in by setting their malus for that type to zero. Without this the door is just a wall.
 */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin {
    @Inject(method = "getBlockPathTypeRaw", at = @At("HEAD"), cancellable = true)
    private static void tvfb$blockDecides(BlockGetter level, BlockPos pos,
                                          CallbackInfoReturnable<BlockPathTypes> cir) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PathTypeProvider provider) {
            BlockPathTypes type = provider.getBlockPathType(state, level, pos, null);
            if (type != null) {
                cir.setReturnValue(type);
            }
        }
    }
}
