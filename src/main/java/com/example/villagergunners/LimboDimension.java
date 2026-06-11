package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * "Limbo": a void dimension (data-driven: {@code data/gobacktoyourworld/dimension[_type]/limbo.json}) whose
 * only feature is a hollow 22x22x22 bedrock cube centred on spawn.
 *
 * Limbo runs ALWAYS-LOADED, in parallel with the overworld: at server start the cube's chunks are
 * force-loaded (so the room exists and ticks even when no player is there) and the cube is built once.
 * Force-loading uses {@link ServerLevel#setChunkForced} (the /forceload mechanism) -- it persists across
 * restarts, keeps the chunks fully ticking, and has no spawn/compass side effects.
 *
 * Enter it with e.g. {@code /execute in gobacktoyourworld:limbo run tp @s 0 61 0}.
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboDimension {
    private LimboDimension() {}

    public static final ResourceKey<Level> LIMBO =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(VillagerGunners.MODID, "limbo"));

    // Outer cube bounds (inclusive) -> 22 blocks on every axis, centred on the (0,0) column.
    private static final int MIN_X = -11, MAX_X = 10;
    private static final int MIN_Z = -11, MAX_Z = 10;
    private static final int MIN_Y = 60, MAX_Y = 81;
    // Spawn stands on the bedrock floor in the centre of the hollow interior.
    private static final BlockPos SPAWN = new BlockPos(0, MIN_Y + 1, 0);

    /** Keep Limbo loaded in parallel with the overworld: force-load the cube chunks and build the room. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel limbo = event.getServer().getLevel(LIMBO);
        if (limbo == null) return;
        forceLoadCube(limbo); // keep the room loaded + ticking even with no player present
        ensureCube(limbo);
    }

    /** Safety net: if a player somehow enters before the room exists, build it and drop them inside. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!LIMBO.equals(event.getTo())) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel limbo = player.getServer() == null ? null : player.getServer().getLevel(LIMBO);
        if (limbo == null) return;
        forceLoadCube(limbo);
        if (ensureCube(limbo)) {
            player.teleportTo(limbo, SPAWN.getX() + 0.5, SPAWN.getY(), SPAWN.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
        }
    }

    /** Force-load every chunk the cube occupies (cube spans block -11..10 on X/Z -> chunks -1..0 on each). */
    private static void forceLoadCube(ServerLevel level) {
        for (int cx = MIN_X >> 4; cx <= (MAX_X >> 4); cx++) {
            for (int cz = MIN_Z >> 4; cz <= (MAX_Z >> 4); cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
    }

    /** Build the hollow bedrock cube around spawn if it isn't there yet. Returns true if it was built now. */
    private static boolean ensureCube(ServerLevel level) {
        BlockPos corner = new BlockPos(MIN_X, MIN_Y, MIN_Z);
        if (level.getBlockState(corner).is(Blocks.BEDROCK)) return false; // already built

        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int y = MIN_Y; y <= MAX_Y; y++) {
                for (int z = MIN_Z; z <= MAX_Z; z++) {
                    boolean shell = x == MIN_X || x == MAX_X || y == MIN_Y || y == MAX_Y || z == MIN_Z || z == MAX_Z;
                    m.set(x, y, z);
                    level.setBlock(m, shell ? bedrock : air, 2); // flag 2: sync to clients, no neighbour updates
                }
            }
        }
        return true;
    }
}
