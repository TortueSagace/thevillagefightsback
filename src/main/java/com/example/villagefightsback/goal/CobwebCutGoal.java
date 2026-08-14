package com.example.villagefightsback.goal;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A guard standing IN a cobweb cuts itself free with an emerald sword.
 *
 * <p>Not the same problem as {@link EmeraldDigGoal}, which only wakes up when a guard has prey it cannot path
 * to. A web needs no enemy and no failed path: the guard is already caught, crawling, and helpless, and the
 * point is to get out. So this runs off nothing but "am I in a web".
 *
 * <p>NOT WHILE FIGHTING. A guard mid-swing that stops to do groundskeeping is a dead guard, and a web that
 * merely slows a defender who is still hitting back is the trap working as intended. It fires the moment the
 * fight ends, so a guard never stays stuck once there is nothing left to hit.
 *
 * <p>The sword is the tool for it -- vanilla gives swords a destroy speed of 15 against cobweb, against a
 * hardness of 4 -- so the cut takes well under a second, which is what "immediately" has to mean for something
 * meant to free you from a trap.
 */
public class CobwebCutGoal extends Goal {
    /** How far up to look for the web: feet and head, since either can be the one holding the guard. */
    private static final int BODY_CELLS = 2;

    private final Mob guard;
    private BlockPos web;
    private float progress;
    private int shownStage = -1;
    private ItemStack stashedWeapon = ItemStack.EMPTY;
    private boolean holdingSword;

    public CobwebCutGoal(Mob guard) {
        this.guard = guard;
        // No flags: cutting is done standing still with the hands, so it must not wrestle the movement or look
        // goals for control the way a flagged goal would.
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        if (guard.getTarget() != null && guard.getTarget().isAlive()) return false; // busy fighting
        web = findWeb();
        return web != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (guard.getTarget() != null && guard.getTarget().isAlive()) return false;
        return web != null && guard.level().getBlockState(web).is(Blocks.COBWEB);
    }

    /** The cobweb holding this guard, feet first then head, or null if it is standing clear. */
    private BlockPos findWeb() {
        BlockPos feet = guard.blockPosition();
        for (int dy = 0; dy < BODY_CELLS; dy++) {
            BlockPos p = feet.above(dy);
            if (guard.level().getBlockState(p).is(Blocks.COBWEB)) return p.immutable();
        }
        return null;
    }

    @Override
    public void tick() {
        if (web == null) return;
        Level level = guard.level();
        guard.getLookControl().setLookAt(web.getX() + 0.5, web.getY() + 0.5, web.getZ() + 0.5);

        if (!holdingSword) {
            stashedWeapon = guard.getMainHandItem().copy();
            holdingSword = true;
            guard.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(com.example.villagefightsback.ModItems.EMERALD_SWORD.get()));
            guard.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }

        BlockState s = level.getBlockState(web);
        float hardness = s.getDestroySpeed(level, web);
        ItemStack held = guard.getMainHandItem();
        float speed = held.isEmpty() ? 1.0F : held.getDestroySpeed(s);
        progress += hardness <= 0.0F ? 1.0F : speed / hardness / 30.0F;
        guard.swing(InteractionHand.MAIN_HAND);

        int stage = Math.min(9, (int) (progress * 10.0F));
        if (stage != shownStage) {
            level.destroyBlockProgress(guard.getId(), web, stage);
            shownStage = stage;
        }
        if (progress >= 1.0F) {
            level.destroyBlock(web, false); // no drops: a cut web is destroyed, not harvested
            clearProgress();
            web = findWeb();                // a guard can be in two web cells at once; keep cutting
        }
    }

    @Override
    public void stop() {
        clearProgress();
        web = null;
        if (holdingSword) {
            guard.setItemSlot(EquipmentSlot.MAINHAND, stashedWeapon);
            guard.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
            stashedWeapon = ItemStack.EMPTY;
            holdingSword = false;
        }
    }

    private void clearProgress() {
        if (web != null) guard.level().destroyBlockProgress(guard.getId(), web, -1);
        progress = 0.0F;
        shownStage = -1;
    }
}
