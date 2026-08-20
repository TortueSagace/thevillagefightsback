package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The Survival opening: a map instead of a bell.
 *
 * <p>Creative hands the player a bell and lets them found a village wherever they like. Survival makes the
 * village something to go and FIND: the means to build one is buried a few hundred blocks away, and a
 * treasure map is the only thing in the player's hands at spawn. The Reputation Book goes in the chest with
 * it, so the rules and the tool arrive together at the end of the walk rather than one at each end.
 *
 * <p>The chest is generated on demand rather than looked for. Relying on a naturally generated structure
 * being nearby would mean the opening silently failing in the wrong biome, and a mod that hands out a map to
 * nothing is worse than one that hands out no map.
 */
public final class SurvivalStart {
    private SurvivalStart() {}

    /** How far out the cache is buried. Far enough to be a journey, close enough to be today's journey. */
    private static final int MIN_DIST = 300;
    private static final int MAX_DIST = 800;

    /** How deep below the surface the chest sits, so it reads as buried treasure rather than a gift box. */
    private static final int BURY_DEPTH = 4;

    /**
     * Bury the cache and hand {@code player} the map to it.
     *
     * <p>Returns false if nowhere workable turned up, in which case the caller should fall back to the normal
     * bell so the player is never left with nothing.
     */
    public static boolean give(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos spot = findSpot(level, player.blockPosition());
        if (spot == null) return false;

        // Writing here loads and generates that one chunk, once. That is NOT the force-loading the mod
        // otherwise forbids: no ticket is taken and nothing is kept ticking -- it is a single synchronous
        // generation at world start, the same cost the player pays when they walk out to dig it up.
        level.setBlock(spot, Blocks.CHEST.defaultBlockState(), 3);
        if (!(level.getBlockEntity(spot) instanceof ChestBlockEntity chest)) return false;
        // A DEAD node, not a living one: Survival never hands the player a working village. The apple to wake
        // it is theirs to craft, and both books are here so the rules arrive with the thing they describe.
        chest.setItem(12, new ItemStack(ModBlocks.DEAD_BRAIN_NODE.get()));
        chest.setItem(13, new ItemStack(ModItems.REPUTATION_BOOK.get()));
        chest.setItem(14, new ItemStack(ModItems.LIMBO_BOOK.get()));
        chest.setChanged();

        ItemStack map = MapItem.create(level, spot.getX(), spot.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, spot, "+", MapDecoration.Type.RED_X);
        map.setHoverName(Component.literal("Map to a Buried Brain Node"));
        if (!player.getInventory().add(map)) player.drop(map, false);

        player.sendSystemMessage(Component.literal(
                "§6Survival: §fthe means to found a village is buried out there. Follow the map."));
        TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] buried the Survival starter cache at {} for {}.",
                spot, player.getGameProfile().getName());
        return true;
    }

    /**
     * Somewhere to bury it: a random bearing, a random distance, and solid ground under the surface.
     *
     * <p>The height comes from the chunk GENERATOR, not from {@code level.getHeight}. That distinction is the
     * whole reason this works: the cache is hundreds of blocks away, those chunks are not loaded, and
     * {@code Level.getHeight} answers {@code getMinBuildHeight()} for an unloaded chunk rather than admitting
     * it does not know. Every candidate was therefore rejected as "no room under it", all eight attempts, and
     * the opening silently fell through to the bell. {@code getBaseHeight} computes the terrain height from
     * the generator's own noise without generating anything, which is how vanilla locates structures and how
     * a real treasure map picks its X.
     */
    private static BlockPos findSpot(ServerLevel level, BlockPos from) {
        net.minecraft.world.level.chunk.ChunkGenerator gen = level.getChunkSource().getGenerator();
        net.minecraft.world.level.levelgen.RandomState rs = level.getChunkSource().randomState();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            int dist = MIN_DIST + level.random.nextInt(MAX_DIST - MIN_DIST);
            int x = from.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = from.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int surface = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
            int y = surface - BURY_DEPTH;
            if (y <= level.getMinBuildHeight() + 2) continue;      // genuinely no room under it
            if (surface <= level.getSeaLevel()) continue;          // under water: keep looking for dry land
            return new BlockPos(x, y, z);
        }
        return null;
    }
}
