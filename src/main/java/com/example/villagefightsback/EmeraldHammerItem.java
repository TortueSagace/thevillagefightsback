package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.example.villagefightsback.platform.event.PlayerInteractEvent;
import com.example.villagefightsback.platform.event.BlockEvent;
import com.example.villagefightsback.platform.SubscribeEvent;

/**
 * The Emerald Hammer: an emerald-tier pickaxe that breaks a 3x3 area (the plane of the MINED FACE) in a
 * single swing. The directly-mined block obeys the tool's own (iron-equivalent) tier; the eight SURROUNDING
 * blocks are only broken if an iron pickaxe could mine them too -- anything tougher (needs a diamond tool,
 * unbreakable, or an obsidian-grade blast resistance like our brain node) is left intact.
 *
 * <p>The 3x3 sits in the plane perpendicular to the face the player actually mined (so digging up/down gives
 * a horizontal 3x3, and digging a wall gives a vertical 3x3 aligned to that wall). The real mined face is
 * captured by the Forge-bus {@link FaceTracker}; if none was recorded we fall back to the player's view axis.
 *
 * <p>It fans out in BOTH survival and creative. Vanilla only calls {@link #mineBlock} in survival, so the
 * creative 3x3 is driven from {@link FaceTracker#onBreak}. The whole 3x3 costs the durability of a SINGLE
 * block (charged on the centre); the re-entrant neighbour breaks spend no extra durability (so the hammer
 * does not wear out 9x faster). Creative spends no durability at all.
 */
public class EmeraldHammerItem extends PickaxeItem {
    /** Re-entrancy guard: while we AOE-break the ring, the per-neighbour breaks must not AOE again. */
    private static final ThreadLocal<Boolean> BREAKING = ThreadLocal.withInitial(() -> false);

    /** Last block FACE each player hit (for the AOE plane). Consumed (removed) on use; best-effort cache. */
    private static final Map<UUID, Direction> LAST_FACE = new ConcurrentHashMap<>();

    public EmeraldHammerItem(Tier tier, int attackDamage, float attackSpeed, Properties props) {
        super(tier, attackDamage, attackSpeed, props);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        // Re-entrant neighbour break during our own AOE: do NOT fan out again, and do NOT spend extra
        // durability -- the whole 3x3 costs the durability of one block, charged on the centre below.
        if (BREAKING.get()) return true;
        // mineBlock is only called in SURVIVAL (vanilla skips it in creative; creative is handled by
        // FaceTracker.onBreak). Fan the 3x3 out from this centre, then super charges the single durability hit.
        if (!level.isClientSide && level instanceof ServerLevel sl && entity instanceof ServerPlayer player
                && state.getDestroySpeed(level, pos) > 0.0F) {
            breakArea(sl, pos, player, axisFor(player));
        }
        return super.mineBlock(stack, level, state, pos, entity); // centre block: the single durability hit
    }

    /** Break the 8 blocks around {@code center} in the plane perpendicular to {@code axis}. */
    private static void breakArea(ServerLevel level, BlockPos center, ServerPlayer player, Direction.Axis axis) {
        BREAKING.set(true);
        try {
            for (BlockPos p : plane(center, axis)) {
                if (p.equals(center)) continue;
                tryBreak(level, player, p);
            }
        } finally {
            BREAKING.set(false);
        }
    }

    /** The axis of the player's last mined FACE (consumed); falls back to the view axis if none was recorded. */
    private static Direction.Axis axisFor(Player player) {
        Direction face = LAST_FACE.remove(player.getUUID());
        if (face != null) return face.getAxis();
        Vec3 look = player.getViewVector(1.0F);
        return Direction.getNearest(look.x, look.y, look.z).getAxis();
    }

    /** The 3x3 cells in the plane perpendicular to {@code axis} (the face the player is mining). */
    private static List<BlockPos> plane(BlockPos c, Direction.Axis axis) {
        List<BlockPos> out = new ArrayList<>(9);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                switch (axis) {
                    case Y -> out.add(c.offset(a, 0, b)); // mining floor/ceiling -> horizontal 3x3
                    case X -> out.add(c.offset(0, a, b)); // mining an east/west wall -> y,z plane
                    case Z -> out.add(c.offset(a, b, 0)); // mining a north/south wall -> x,y plane
                }
            }
        }
        return out;
    }

    /** Break {@code pos} via the player's game mode (drops + stats) only if an iron pickaxe could. */
    private static void tryBreak(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return;
        if (s.getDestroySpeed(level, pos) < 0.0F) return;                 // unbreakable (bedrock / barrier)
        if (s.is(BlockTags.NEEDS_DIAMOND_TOOL)) return;                   // beyond an iron pickaxe's tier
        if (s.getBlock().getExplosionResistance() >= 1200.0F) return;     // brain node / fake obsidian etc.
        player.gameMode.destroyBlock(pos);
    }

    // The inventory tooltip (.desc) is added centrally by ModTooltips; no per-item override (it would double).

    /**
     * Records the face each player most recently hit (so {@link #breakArea} can orient the 3x3 to the actual
     * mined face instead of the coarser view axis), and drives the 3x3 in CREATIVE (where vanilla never calls
     * {@link #mineBlock}). Survival is handled by {@code mineBlock}; this only does the creative fan-out.
     */
    public static final class FaceTracker {
        private static final double REACH = 6.0; // a touch beyond survival reach (4.5), covers creative too

        private FaceTracker() {}

        @SubscribeEvent
        public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
            Player player = event.getEntity();
            if (player == null || event.getFace() == null) return;
            if (player.getMainHandItem().getItem() instanceof EmeraldHammerItem) {
                LAST_FACE.put(player.getUUID(), event.getFace());
            }
        }

        @SubscribeEvent
        public static void onBreak(BlockEvent.BreakEvent event) {
            if (BREAKING.get()) return; // re-entrant neighbour break during our own AOE
            Player player = event.getPlayer();
            if (player == null) return;
            if (!(player.getMainHandItem().getItem() instanceof EmeraldHammerItem)) return;
            // Refine to the precise face the player is looking at as the block breaks (most accurate moment).
            Vec3 eye = player.getEyePosition(1.0F);
            Vec3 end = eye.add(player.getViewVector(1.0F).scale(REACH));
            net.minecraft.world.phys.HitResult hr = player.level().clip(new net.minecraft.world.level.ClipContext(
                    eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            if (hr instanceof net.minecraft.world.phys.BlockHitResult bhr
                    && bhr.getBlockPos().equals(event.getPos())) {
                LAST_FACE.put(player.getUUID(), bhr.getDirection());
            }
            // CREATIVE: vanilla never calls Item.mineBlock in creative, so drive the 3x3 from here. Survival is
            // handled by mineBlock (which also charges the single durability hit); creative spends none.
            if (player.isCreative() && player.level() instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
                breakArea(sl, event.getPos(), sp, axisFor(sp));
            }
        }
    }
}
