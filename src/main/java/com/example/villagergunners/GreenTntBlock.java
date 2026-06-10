package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "Green TNT" -- a tower-spawning TNT. It detonates the instant it is placed: instead of exploding it
 * builds the big square {@code village_tower_fix} structure centered on this block (the block sits one
 * above the tower's base), block-by-block via {@link HouseBuildManager}. Sensitive blocks go in last and
 * chests get village loot. No explosion damage -- only the carved tower footprint is touched.
 */
public class GreenTntBlock extends Block {
    private static final ResourceLocation TOWER = new ResourceLocation(VillagerGunners.MODID, "village_tower_fix");
    private static final ResourceLocation LOOT = new ResourceLocation(VillagerGunners.MODID, "chests/basic_house");

    public GreenTntBlock(Properties properties) {
        super(properties);
    }

    /** Detonate the moment it is placed -- schedule the build for next tick (off the placement call). */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 0.8F);
        level.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                10, 2.0, 2.0, 2.0, 0.0);
        try {
            HousePlacer.place(level, pos, Rotation.NONE, TOWER, LOOT);
        } catch (Throwable t) {
            VillagerGunners.LOGGER.error("[Go Back To Your World] Green TNT tower placement failed at {}", pos, t);
        }
    }
}
