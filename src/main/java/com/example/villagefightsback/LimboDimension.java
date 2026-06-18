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
    // Extra force-loaded chunk ring around the cube, so a banished creature that drifts through the (passable)
    // walls stays loaded long enough for the containment sweep to re-centre it before it can be "forgotten".
    private static final int LOAD_PAD = 1;

    /** The Limbo cube's spawn cell (centre floor). */
    public static BlockPos cubeSpawn() { return SPAWN; }

    /** Guarantee the cell directly under {@code feet} (in Limbo) is a solid standing surface. If it has been
     *  destroyed/replaced by something you would fall through (air, fluid, a non-sturdy block), drop a
     *  fake-bedrock block there -- so anything spawned at that spot can never fall into the void. Fake bedrock
     *  is itself solid as a floor (it only goes passable when a block covers it), so the new block holds. */
    public static void ensureFloorUnder(ServerLevel limbo, BlockPos feet) {
        BlockPos below = feet.below();
        if (!limbo.getBlockState(below).isFaceSturdy(limbo, below, net.minecraft.core.Direction.UP)) {
            limbo.setBlock(below, ModBlocks.FAKE_BEDROCK.get().defaultBlockState(), 3);
        }
    }

    /** True if {@code e} has drifted outside the cube interior (so it should be re-centred to keep it loaded). */
    public static boolean outsideCube(Entity e) {
        return e.getX() < MIN_X || e.getX() > MAX_X + 1
                || e.getZ() < MIN_Z || e.getZ() > MAX_Z + 1
                || e.getY() < MIN_Y || e.getY() > MAX_Y + 1;
    }

    /**
     * Banish any entity to Limbo's spawn. Players are re-seated with {@code teleportTo}; mobs cross dimensions
     * via the Forge {@code changeDimension(ServerLevel, ITeleporter)} overload (the returned entity is the new
     * instance in Limbo). Server-side only; no-op on the client or if Limbo isn't loaded.
     */
    public static Entity sendToLimbo(Entity e) {
        if (e.level().isClientSide()) return e;
        // TOGGLE: an entity ALREADY in Limbo, hit by a beam/potion (or a player drinking one), returns to the
        // overworld instead of being banished again.
        if (LIMBO.equals(e.level().dimension())) {
            LimboManager.sendBackToOverworld(e);
            return e;
        }
        // Apex threat: a wither storm can't change dimensions and is multipart, so it gets a bespoke teleport +
        // many "part" gravels rather than the normal single-leash path.
        if (Factions.isWitherStorm(e)) { banishWitherStorm(e); return e; }
        // The Ender Dragon's own part system can't be cleanly relocated -- leave it. (Our bosses ARE now
        // banishable: they return via their gravel like anything else.)
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
        ensureFloorUnder(limbo, SPAWN); // solid ground under the arrival spot, even if the floor cell was destroyed
        double x = SPAWN.getX() + 0.5, y = SPAWN.getY(), z = SPAWN.getZ() + 0.5;
        if (e instanceof ServerPlayer player) {
            player.teleportTo(limbo, x, y, z, player.getYRot(), player.getXRot());
            return player;
        }
        // changeDimension fires EntityTravelToDimensionEvent, which LimboManager uses to auto-place the leash.
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

    /**
     * Banish a wither storm: TELEPORT the core into Limbo (the engine {@code teleportTo}, like {@code /tp},
     * bypasses its can't-change-dimensions flag and keeps it coherent -- no destroy/respawn), discard any
     * detached parts left behind in the origin dimension, then scatter MANY "Wither Storm part" gravels around
     * its origin -- all tethering the one core. Breaking ANY part returns the whole storm there
     * ({@link LimboManager#registerWitherStorm} / {@link LimboManager#onGravelDestroyed}).
     */
    private static void banishWitherStorm(Entity storm) {
        if (!(storm.level() instanceof ServerLevel sl)) return;
        MinecraftServer server = sl.getServer();
        ServerLevel limbo = server == null ? null : server.getLevel(LIMBO);
        if (limbo == null) return;
        ResourceKey<Level> originDim = sl.dimension();
        BlockPos originPos = storm.blockPosition();
        java.util.UUID creature = storm.getUUID();
        // BASE max-health (not getMaxHealth(), which folds in transient modifiers, and not current HP): a storm's
        // base is 400, so it is always unstable regardless of how injured/buffed it is when banished.
        float maxHealth = storm instanceof net.minecraft.world.entity.LivingEntity le
                ? (float) le.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                : 300.0F;
        double r2 = 128.0 * 128.0;
        // How many "part" gravels to scatter: roughly the number of the storm's part entities (8..24).
        int parts = 0;
        for (Entity e : sl.getAllEntities()) {
            if (e != storm && Factions.isWitherStormPart(e) && e.distanceToSqr(storm.position()) <= r2) parts++;
        }
        int partCount = Math.max(8, Math.min(24, parts > 0 ? parts : 12));

        LimboBanishFx.start(storm);
        forceLoadCube(limbo);
        ensureCube(limbo);
        ensureFloorUnder(limbo, SPAWN); // solid ground under the arrival spot, even if the floor cell was destroyed
        double x = SPAWN.getX() + 0.5, y = SPAWN.getY(), z = SPAWN.getZ() + 0.5;
        storm.teleportTo(limbo, x, y, z, java.util.Set.of(), storm.getYRot(), storm.getXRot());
        // Pin the relocated core so it doesn't despawn, and confirm it actually landed in Limbo (a soft-dep boss
        // could refuse the teleport -- if so, the storm was NOT banished and we must not drop phantom gravels).
        Entity core = limbo.getEntity(creature);
        TheVillageFightsBack.LOGGER.info("[Limbo] banish wither storm {}: landed-in-limbo={}, parts={}",
                creature, core != null, partCount);
        if (core instanceof net.minecraft.world.entity.Mob m) m.setPersistenceRequired();
        if (core == null) return; // teleport failed -> abort the banish rather than scatter unanchored gravels
        // Discard any detached parts still in the origin dimension (the old core instance is already removed by
        // the teleport; the relocated core regenerates its own parts in Limbo).
        for (Entity e : sl.getAllEntities()) {
            if (Factions.isWitherStormPart(e)
                    && e.distanceToSqr(originPos.getX() + 0.5, originPos.getY(), originPos.getZ() + 0.5) <= r2) {
                e.discard();
            }
        }
        LimboManager.registerWitherStorm(server, creature, originDim, originPos, maxHealth, partCount);
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
        // Wherever the player ends up standing, never let them drop through a destroyed floor cell.
        ensureFloorUnder(limbo, player.blockPosition());
    }

    /** Force-load every chunk the cube occupies (cube spans block -11..10 on X/Z -> chunks -1..0 on each). */
    private static void forceLoadCube(ServerLevel level) {
        for (int cx = (MIN_X >> 4) - LOAD_PAD; cx <= (MAX_X >> 4) + LOAD_PAD; cx++) {
            for (int cz = (MIN_Z >> 4) - LOAD_PAD; cz <= (MAX_Z >> 4) + LOAD_PAD; cz++) {
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
