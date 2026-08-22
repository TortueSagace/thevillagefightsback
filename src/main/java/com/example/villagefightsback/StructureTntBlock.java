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
 *
 * <p>Like the house charges, a refusal MOVES the build rather than cancelling it. {@link HousePlacer} turns
 * down anywhere that would overlap a claimed building or a "do not build here" sign, and for a charge the
 * player put down by hand that read as the block simply doing nothing. It now walks outward and builds at
 * the nearest spot that will take it, which is what BlueTntBlock has always done.
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

    /** How far out to look when the spot the player chose is refused, and how coarsely. */
    private static final int[] RELOCATE_RADII = {6, 10, 14, 20, 28};
    private static final int RELOCATE_MAX_STEP = 6;   // ...but never up or down a cliff

    /**
     * Build at the nearest spot that will take it.
     *
     * <p>Retrying {@code place} is safe: it makes both of its refusals -- the claimed-building test and the
     * marker-sign test -- BEFORE it carves anything, so a rejected attempt leaves the world untouched.
     */
    private BlockPos relocate(ServerLevel sl, BlockPos from) {
        for (int r : RELOCATE_RADII) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4.0;
                int x = from.getX() + (int) Math.round(Math.cos(a) * r);
                int z = from.getZ() + (int) Math.round(Math.sin(a) * r);
                int y = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(y - from.getY()) > RELOCATE_MAX_STEP) continue;
                BlockPos cand = new BlockPos(x, y, z);
                if (HousePlacer.place(sl, cand, Rotation.NONE, structure, loot, floorLayer)) {
                    sl.sendParticles(ParticleTypes.CLOUD, cand.getX() + 0.5, cand.getY() + 1.0, cand.getZ() + 0.5,
                            12, 0.4, 0.3, 0.4, 0.02);
                    return cand;
                }
            }
        }
        return null;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!(level instanceof ServerLevel sl)) return;
        BlockPos at = pos.immutable();
        HouseBuildManager.detonateNextTick(sl, at, () -> {
            sl.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0F, 0.9F);
            sl.sendParticles(ParticleTypes.EXPLOSION, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    8, 1.5, 1.5, 1.5, 0.0);
            // loot -> chests; floorLayer sets the depth
            if (HousePlacer.place(sl, at, Rotation.NONE, structure, loot, floorLayer)) return;
            BlockPos moved = relocate(sl, at);
            if (moved == null) {
                TheVillageFightsBack.LOGGER.info(
                        "[The Village Fights Back] nowhere within {} blocks of {} will take {}; the charge is spent.",
                        RELOCATE_RADII[RELOCATE_RADII.length - 1], at, structure);
            }
        });
    }
}
