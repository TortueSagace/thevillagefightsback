package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * "Blue TNT" -- a house-spawning TNT. It detonates the instant it is placed (by a player or the settler
 * AI): instead of a real explosion it builds one of the four basic houses, centered on this block, with
 * the fake-cobblestone door facing {@link #FACING}. No explosion damage -- only the house pocket is carved.
 * The detonation runs on a 1-tick scheduled block tick (so it happens off the placement call, not synchronously).
 */
public class BlueTntBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** Ticks the founding flow budgets for a settler's house to finish building after it's dropped. */
    public static final int FUSE_TICKS = 80;

    /** Which templates this charge builds, how much clear ground it wants, and how far it will look. */
    private final java.util.function.Supplier<ResourceLocation[]> houses;
    private final int clearance;
    private final int[] relocateRadii;

    /** The Blue charge: the eight small houses. */
    public BlueTntBlock(Properties properties) {
        this(properties, () -> HousePlacer.HOUSES, 8, new int[]{6, 10, 14, 18});
    }

    protected BlueTntBlock(Properties properties, java.util.function.Supplier<ResourceLocation[]> houses,
                           int clearance, int[] relocateRadii) {
        super(properties);
        this.houses = houses;
        this.clearance = clearance;
        this.relocateRadii = relocateRadii;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** The templates this charge picks from. */
    protected ResourceLocation[] houses() {
        return houses.get();
    }

    /** How this charge names itself in the log. */
    protected String label() {
        return "Blue TNT";
    }

    /**
     * Last resort when nothing within reach fits this charge's templates. Blue simply gives up; the Cyan
     * charge overrides this to fall back to a small house rather than waste the settler that carried it.
     *
     * @return where it built, or null if it built nothing.
     */
    protected BlockPos fallback(ServerLevel sl, BlockPos at, Direction doorFacing) {
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Face the placer, so a player-placed house ends up with its door toward where they stood.
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /**
     * Detonate the moment it is placed. The build is captured here (door facing + position) and run on the
     * next SERVER tick via {@link HouseBuildManager}, so it fires reliably -- even if a neighbouring TNT's
     * footprint carves this block away first (a scheduled block tick would have been silently dropped).
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!(level instanceof ServerLevel sl)) return;
        Direction doorFacing = state.getValue(FACING);
        BlockPos at = pos.immutable();
        HouseBuildManager.detonateNextTick(sl, at, () -> detonate(sl, at, doorFacing));
    }

    /** Refuse a relocation that would climb a cliff or drop into a hole rather than move along the ground. */
    private static final int RELOCATE_MAX_STEP = 6;

    /**
     * Go off, and build.
     *
     * <p>If the spot is refused because a building is already standing there, the TNT MOVES and goes off
     * somewhere that works. It used to simply do nothing and stay put -- a live-looking blue block lying in the
     * village forever, which reads as a bug whatever the reason for it.
     */
    private void detonate(ServerLevel sl, BlockPos at, Direction doorFacing) {
        ResourceLocation[] pool = houses();
        ResourceLocation house = pool[sl.random.nextInt(pool.length)];
        BlockPos spot = HousePlacer.place(sl, at, doorFacing, house) ? at : relocate(sl, at, doorFacing, house);
        if (spot == null) spot = fallback(sl, at, doorFacing);

        // Wherever it ended up, this charge is spent. The carve removes the block when the house lands on it;
        // when the house lands elsewhere -- or nowhere -- nothing else will, so clear it by hand.
        if (spot == null || !spot.equals(at)) {
            // `this`, not BLUE_TNT: the Cyan charge is a subclass, and naming Blue here left every relocated
            // cyan charge lying in the world as a live-looking block -- the exact thing the relocation exists
            // to avoid. Any future charge inherits the right behaviour for free.
            if (sl.getBlockState(at).is(this)) sl.removeBlock(at, false);
        }
        effect(sl, spot == null ? at : spot);
        if (spot == null) {
            TheVillageFightsBack.LOGGER.info(
                    "[The Village Fights Back] {} at {} found nowhere clear within {} blocks; spent "
                    + "without a house rather than left lying there.",
                    label(), at, relocateRadii[relocateRadii.length - 1]);
        }
    }

    /**
     * Look outward in rings for a spot the house is actually allowed to occupy, and build at the first one.
     *
     * <p>{@code HousePlacer.place} refuses BEFORE it touches the world, so attempting a candidate costs nothing
     * when it fails. The cheap free-footprint test still runs first, to keep the number of attempts down.
     */
    protected BlockPos relocate(ServerLevel sl, BlockPos from, Direction doorFacing, ResourceLocation house) {
        return relocate(sl, from, doorFacing, house, relocateRadii, clearance);
    }

    protected static BlockPos relocate(ServerLevel sl, BlockPos from, Direction doorFacing, ResourceLocation house,
                                       int[] radii, int clearance) {
        for (int r : radii) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4.0;
                int x = from.getX() + (int) Math.round(Math.cos(a) * r);
                int z = from.getZ() + (int) Math.round(Math.sin(a) * r);
                int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(y - from.getY()) > RELOCATE_MAX_STEP) continue;
                BlockPos cand = new BlockPos(x, y, z);
                if (!ExpansionManager.footprintFree(sl, cand, clearance)) continue;
                if (HousePlacer.place(sl, cand, doorFacing, house)) return cand;
            }
        }
        return null;
    }

    private static void effect(ServerLevel sl, BlockPos at) {
        sl.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0F, 1.0F);
        sl.sendParticles(ParticleTypes.EXPLOSION, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                8, 1.5, 1.5, 1.5, 0.0);
    }
}
