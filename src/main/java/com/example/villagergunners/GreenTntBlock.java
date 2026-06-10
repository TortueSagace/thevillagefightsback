package com.example.villagergunners;

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

    /**
     * Detonate the moment it is placed. The tower build is captured here and run on the next SERVER tick via
     * {@link HouseBuildManager}, so it fires reliably even if a neighbouring TNT's footprint carves this
     * block away first (a scheduled block tick would have been silently dropped).
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!(level instanceof ServerLevel sl)) return;
        BlockPos at = pos.immutable();
        HouseBuildManager.detonateNextTick(sl, at, () -> {
            sl.playSound(null, at, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 0.8F);
            sl.sendParticles(ParticleTypes.EXPLOSION, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    10, 2.0, 2.0, 2.0, 0.0);
            HousePlacer.place(sl, at, Rotation.NONE, TOWER, LOOT);
        });
    }
}
