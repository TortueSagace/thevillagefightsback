package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Emerald Hammer: an emerald-tier pickaxe that breaks a 3x3 area (the plane facing the player) in a
 * single swing. The directly-mined block obeys the tool's own (iron-equivalent) tier; the eight SURROUNDING
 * blocks are only broken if an iron pickaxe could mine them too -- anything tougher (needs a diamond tool,
 * unbreakable, or an obsidian-grade blast resistance like our brain node) is left intact.
 */
public class EmeraldHammerItem extends PickaxeItem {
    /** Re-entrancy guard: while we AOE-break the ring, the per-neighbour breaks must not AOE again. */
    private static final ThreadLocal<Boolean> BREAKING = ThreadLocal.withInitial(() -> false);

    public EmeraldHammerItem(Tier tier, int attackDamage, float attackSpeed, Properties props) {
        super(tier, attackDamage, attackSpeed, props);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        // Only fan out from a real, server-side, survival mine of a non-instant block (and never re-enter).
        if (!level.isClientSide && !BREAKING.get()
                && level instanceof ServerLevel sl && entity instanceof ServerPlayer player
                && !player.isCreative() && state.getDestroySpeed(level, pos) > 0.0F) {
            breakArea(stack, sl, pos, player);
        }
        return super.mineBlock(stack, level, state, pos, entity);
    }

    /** Break the 8 blocks around {@code center} in the plane perpendicular to the player's view axis. */
    private void breakArea(ItemStack stack, ServerLevel level, BlockPos center, ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);
        Direction.Axis axis = Direction.getNearest(look.x, look.y, look.z).getAxis();
        BREAKING.set(true);
        try {
            for (BlockPos p : plane(center, axis)) {
                if (p.equals(center)) continue;
                if (stack.isEmpty()) break;            // the hammer broke partway through the ring
                tryBreak(level, player, p);
            }
        } finally {
            BREAKING.set(false);
        }
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

    /** Break {@code pos} via the player's game mode (drops + tool damage + stats) only if an iron pickaxe could. */
    private void tryBreak(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return;
        if (s.getDestroySpeed(level, pos) < 0.0F) return;                 // unbreakable (bedrock / barrier)
        if (s.is(BlockTags.NEEDS_DIAMOND_TOOL)) return;                   // beyond an iron pickaxe's tier
        if (s.getBlock().getExplosionResistance() >= 1200.0F) return;     // brain node / fake obsidian etc.
        player.gameMode.destroyBlock(pos);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.thevillagefightsback.emerald_hammer.desc").withStyle(ChatFormatting.GRAY));
    }
}
