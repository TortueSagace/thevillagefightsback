package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A generic structure-spawning TNT (the yellow / orange / purple variants). Like {@link GreenTntBlock} it
 * detonates the instant it is placed -- instead of exploding, it builds a fixed structure centred on this
 * block, block-by-block via {@link HouseBuildManager}. The structure id is fixed per block (one per colour),
 * so each colour spawns exactly the node it is named for. (The .nbt templates are supplied separately;
 * {@link HousePlacer#place} logs a warning and no-ops if a template is missing.)
 */
public class StructureTntBlock extends Block {
    private final ResourceLocation structure;
    private final ResourceLocation loot; // chest loot for the structure's containers (null = no loot)
    private final int floorLayer;        // template local y that sits one below the TNT (<0 = anchor on powder snow)

    public StructureTntBlock(Properties properties, ResourceLocation structure) {
        this(properties, structure, null, 0);
    }

    public StructureTntBlock(Properties properties, ResourceLocation structure, ResourceLocation loot) {
        this(properties, structure, loot, 0);
    }

    public StructureTntBlock(Properties properties, ResourceLocation structure, ResourceLocation loot, int floorLayer) {
        super(properties);
        this.structure = structure;
        this.loot = loot;
        this.floorLayer = floorLayer;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!(level instanceof ServerLevel sl)) return;
        BlockPos at = pos.immutable();
        HouseBuildManager.detonateNextTick(sl, at, () -> {
            sl.playSound(null, at, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 0.9F);
            sl.sendParticles(ParticleTypes.EXPLOSION, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    8, 1.5, 1.5, 1.5, 0.0);
            HousePlacer.place(sl, at, Rotation.NONE, structure, loot, floorLayer); // loot -> chests; floorLayer sets the depth
        });
    }
}
