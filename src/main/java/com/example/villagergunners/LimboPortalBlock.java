package com.example.villagergunners;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Limbo Portal: a green re-skin of the nether portal. It has no collision, glows, swirls green, and -- when
 * a creature dwells in it -- teleports it between the overworld and Limbo with TWICE the nether's space
 * compression (the dimension_type's coordinate_scale is 16). Destination portals (with a standing platform)
 * are found-or-built by {@link LimboPortalForcer}, which also keeps portals OUT of the original Limbo cube
 * (they spawn just above it instead).
 */
public class LimboPortalBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    protected static final VoxelShape X_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    protected static final VoxelShape Z_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    /** Per-entity dwell tracking: UUID -> {lastGameTime, consecutiveTicksInside}. */
    private static final Map<UUID, long[]> DWELL = new ConcurrentHashMap<>();

    public LimboPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl)) return;
        if (entity.isPassenger() || entity.isVehicle() || !entity.canChangeDimensions()) return;
        if (entity.isOnPortalCooldown()) { entity.setPortalCooldown(); return; }

        long now = sl.getGameTime();
        UUID id = entity.getUUID();
        long[] d = DWELL.get(id);
        long count = (d != null && d[0] == now - 1) ? d[1] + 1 : 1;
        if (d != null && d[0] == now) return; // already counted this tick (overlapping portal blocks)
        if (DWELL.size() > 128) DWELL.values().removeIf(e -> now - e[0] > 100); // prune entities that wandered off
        DWELL.put(id, new long[]{now, count});

        // Survival players must dwell ~2s; creative players + mobs/items go through at once.
        int needed = (entity instanceof Player p && !p.getAbilities().instabuild) ? 40 : 1;
        if (count >= needed) {
            DWELL.remove(id);
            entity.setPortalCooldown();
            LimboPortalForcer.teleport(entity, sl);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Green swirl: a few classic portal motes for movement + green sparkles for the colour.
        for (int i = 0; i < 4; i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            double vx = (random.nextFloat() - 0.5) * 0.5;
            double vy = (random.nextFloat() - 0.5) * 0.5;
            double vz = (random.nextFloat() - 0.5) * 0.5;
            if (random.nextBoolean()) {
                level.addParticle(ParticleTypes.PORTAL, x, y, z, vx, vy, vz);
            } else {
                level.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y, z, vx * 0.2, vy * 0.2, vz * 0.2);
            }
        }
    }

    @Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }
}
