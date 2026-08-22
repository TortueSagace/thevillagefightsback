package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Limbo portals in a SUPERFLAT world, where the real structure cannot reach.
 *
 * <h2>Why this exists</h2>
 * A superflat world does not generate modded structures, and no data pack can change that. The flat
 * generator's structure list is fixed in code when the world is created:
 * <pre>
 *   // FlatLevelGeneratorSettings.getDefault()
 *   HolderSet&lt;StructureSet&gt; holderset = HolderSet.direct(STRONGHOLDS, VILLAGES);
 *   new FlatLevelGeneratorSettings(Optional.of(holderset), ...)
 *
 *   // FlatLevelSource.createState()
 *   settings.structureOverrides().map(HolderSet::stream)      // present -&gt; ONLY those two
 *           .orElseGet(() -&gt; lookup.listElements()...)         // absent  -&gt; everything
 * </pre>
 * The override is present and it names exactly two vanilla sets, so ours is filtered out before any of our
 * code runs. It has nothing to do with when the world was made, and it applies to every mod equally.
 *
 * <h2>What this does instead</h2>
 * Places the same frame from a chunk-load hook, and <b>only in a level whose generator is actually flat</b>.
 * Normal worlds are untouched and keep using the real {@link LimboPortalStructure}, so {@code /locate} still
 * works there -- which it cannot do for anything placed this way, since there is no structure to locate.
 *
 * <p>The roll is derived from the seed and the chunk coordinates rather than stored, the same trick
 * {@link LimboDrift} uses to decide which chunks glitch: the same chunk always gives the same answer, so
 * nothing has to be remembered and nothing can drift. One cheap block read then stops a portal being laid
 * twice over the same spot.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LimboPortalFlatgen {
    private LimboPortalFlatgen() {}

    /**
     * One chunk in this many carries a portal.
     *
     * <p>The structure set asks for a 32-chunk spacing, which works out at about one per 1024 chunks, so
     * this is the same rarity expressed the only way a per-chunk roll can express it.
     */
    private static final int ONE_IN = 1024;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // Flat worlds only. In a normal world the structure does this properly, and doing both would put
        // two portals in some chunks and make the rarity a lie.
        if (!(level.getChunkSource().getGenerator() instanceof FlatLevelSource)) return;

        ChunkPos cp = event.getChunk().getPos();
        if (!rolls(level.getSeed(), cp.x, cp.z)) return;

        int x = cp.getMiddleBlockX();
        int z = cp.getMiddleBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() + 1) return;
        BlockPos base = new BlockPos(x, y, z);

        // Already standing (this chunk has been loaded before): nothing to do.
        if (level.getBlockState(base).is(ModBlocks.FAKE_OBSIDIAN.get())) return;

        boolean alongX = ((x * 31 + z) & 1) == 0;
        LimboPortalStructure.layFrame(level, base, alongX);
        TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] a cold Limbo portal stands at {} (flat world).", base);
    }

    /**
     * The per-chunk roll: stable for a given seed and chunk, and stored nowhere.
     *
     * <p>The two large odd multipliers are the ones vanilla uses to decorrelate chunk coordinates before
     * hashing them; without them a diagonal of chunks all roll the same number.
     */
    private static boolean rolls(long seed, int cx, int cz) {
        RandomSource rng = RandomSource.create(seed ^ (cx * 341873128712L + cz * 132897987541L));
        return rng.nextInt(ONE_IN) == 0;
    }
}
