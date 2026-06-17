package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * "Limbo": a void dimension (data-driven: {@code data/thevillagefightsback/dimension[_type]/limbo.json}) whose
 * only feature is a hollow 22x22x22 bedrock cube centred on spawn.
 *
 * Limbo runs ALWAYS-LOADED, in parallel with the overworld: at server start the cube's chunks are
 * force-loaded (so the room exists and ticks even when no player is there) and the cube is built once.
 * Force-loading uses {@link ServerLevel#setChunkForced} (the /forceload mechanism) -- it persists across
 * restarts, keeps the chunks fully ticking, and has no spawn/compass side effects.
 *
 * Enter it with e.g. {@code /execute in thevillagefightsback:limbo run tp @s 0 61 0}.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboDimension {
    private LimboDimension() {}

    public static final ResourceKey<Level> LIMBO =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(TheVillageFightsBack.MODID, "limbo"));

    // Outer cube bounds (inclusive) -> 22 blocks on every axis, centred on the (0,0) column.
    private static final int MIN_X = -11, MAX_X = 10;
    private static final int MIN_Z = -11, MAX_Z = 10;
    private static final int MIN_Y = 60, MAX_Y = 81;
    // Spawn stands on the bedrock floor in the centre of the hollow interior.
    private static final BlockPos SPAWN = new BlockPos(0, MIN_Y + 1, 0);

    /**
     * Banish any entity to Limbo's spawn. Players are re-seated with {@code teleportTo}; mobs cross dimensions
     * via the Forge {@code changeDimension(ServerLevel, ITeleporter)} overload (the returned entity is the new
     * instance in Limbo). Server-side only; no-op on the client or if Limbo isn't loaded.
     */
    public static Entity sendToLimbo(Entity e) {
        if (e.level().isClientSide()) return e;
        // The Vindictive Village Spirit and General Darius cannot be banished -- they ARE of the Limbo. Tell
        // nearby players it shrugged off the attempt (e.g. a Volatile Limbo Potion), then leave it standing.
        if (e instanceof UnleashedBrainNodeEntity || e instanceof GeneralDariusEntity) { notifyBanishImmune(e); return e; }
        // Multipart bosses can't change dimensions; banishing one leaves a ghost marker (and duplicates). Skip.
        if (e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                || e instanceof net.minecraft.world.entity.boss.EnderDragonPart
                || !e.canChangeDimensions()) return e;
        MinecraftServer server = e.getServer();
        if (server == null) return e;
        ServerLevel limbo = server.getLevel(LIMBO);
        if (limbo == null) return e;
        LimboBanishFx.start(e); // black spirals + soul + teleport swirl where its hitbox was, BEFORE it leaves
        forceLoadCube(limbo);
        ensureCube(limbo);
        double x = SPAWN.getX() + 0.5, y = SPAWN.getY(), z = SPAWN.getZ() + 0.5;
        if (e instanceof ServerPlayer player) {
            player.teleportTo(limbo, x, y, z, player.getYRot(), player.getXRot());
            return player;
        }
        return e.changeDimension(limbo, new net.minecraftforge.common.util.ITeleporter() {
            @Override
            public Entity placeEntity(Entity entity, ServerLevel from, ServerLevel to, float yaw,
                    java.util.function.Function<Boolean, Entity> repositionEntity) {
                Entity moved = repositionEntity.apply(false); // recreate in Limbo, no portal logic
                moved.moveTo(x, y, z, moved.getYRot(), moved.getXRot());
                return moved;
            }
        });
    }

    private static long lastBanishImmuneMsg = Long.MIN_VALUE;

    /** Briefly tell nearby players the boss is immune to banishment (throttled so repeats don't spam the chat). */
    private static void notifyBanishImmune(Entity boss) {
        if (!(boss.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (now - lastBanishImmuneMsg < 40L) return; // at most once per ~2s
        lastBanishImmuneMsg = now;
        net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal(
                "§5The Vindictive Village Spirit is immune to banishment.");
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(boss.getX(), boss.getY(), boss.getZ()) <= 64.0 * 64.0) p.sendSystemMessage(msg);
        }
    }

    /** Keep Limbo loaded in parallel with the overworld: force-load the cube chunks and build the room. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel limbo = event.getServer().getLevel(LIMBO);
        if (limbo == null) return;
        forceLoadCube(limbo); // keep the room loaded + ticking even with no player present
        ensureCube(limbo);
        ensureCentralNest(limbo);
    }

    /** The central cube is the only one with a nest on its TOP, in a corner. Idempotent (covers old worlds). */
    private static void ensureCentralNest(ServerLevel limbo) {
        int nx = MIN_X + 3, nz = MIN_Z + 3;   // a top corner of the cube
        int topY = MAX_Y + 1;                  // one above the cube's top shell
        if (!limbo.getBlockState(new BlockPos(nx, topY, nz)).is(Blocks.ANCIENT_DEBRIS)) {
            LimboNest.placeOnTop(limbo, nx, topY, nz);
        }
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

    /** Build the hollow cube around spawn if it isn't there yet. Returns true if it was built now. The shell is
     *  fake bedrock (looks like bedrock, anything walks through); existing real-bedrock cubes are converted in
     *  place by the chunk-load migration in {@link LimboWorldgenEvents}, so we treat either as "already built". */
    private static boolean ensureCube(ServerLevel level) {
        BlockPos corner = new BlockPos(MIN_X, MIN_Y, MIN_Z);
        BlockState atCorner = level.getBlockState(corner);
        if (atCorner.is(Blocks.BEDROCK) || atCorner.is(ModBlocks.FAKE_BEDROCK.get())) return false; // already built

        BlockState bedrock = ModBlocks.FAKE_BEDROCK.get().defaultBlockState();
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

        // Drop the indestructible two-part Limbo Bed at a random interior floor cell (foot + head toward
        // north, both kept inside the room, never on the spawn column).
        int bx, bz;
        do {
            bx = MIN_X + 1 + level.random.nextInt(MAX_X - MIN_X - 1);   // -10..9
            bz = MIN_Z + 2 + level.random.nextInt(MAX_Z - MIN_Z - 2);   // -9..9 (head at bz-1 stays interior)
        } while (bx == SPAWN.getX() && (bz == SPAWN.getZ() || bz - 1 == SPAWN.getZ()));
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
        BlockPos footPos = new BlockPos(bx, MIN_Y + 1, bz);
        BlockState foot = ModBlocks.LIMBO_BED.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT)
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, facing);
        BlockState head = ModBlocks.LIMBO_BED.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD)
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, facing);
        level.setBlock(footPos, foot, 3);
        level.setBlock(footPos.relative(facing), head, 3);
        return true;
    }
}
