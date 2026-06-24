package com.example.villagefightsback.goal;

import java.util.EnumSet;

import com.example.villagefightsback.infection.BlockMatcher;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * An emerald unit that cannot PATH to its prey digs through the soft block in its way -- the block(s) directly in
 * FRONT of it (one per body level: 2 blocks for a 2-tall unit, 3 for a 3-tall one), the one directly ABOVE its head,
 * or straight DOWN (always allowed, and preferred when the prey is below). It digs at a bare-hand player's speed
 * (no tool) and shows the cracking animation.
 *
 * <p>It may ONLY break blocks the fertile infection would itself convert ({@link BlockMatcher#isCandidate}) --
 * dirt, sand, stone, logs, etc. -- plus, as an exception, the faction's own already-fertile GROUND blocks (so it can
 * tunnel through infection it created). It is STRICTLY forbidden from breaking anything else: cobblestone, the
 * fake/wall blocks, chests, ores, bricks, glass, the brain node, bedrock, etc. (all excluded by isCandidate).
 */
public class EmeraldDigGoal extends Goal {
    private final Mob mob;
    private BlockPos dig;
    private float progress;     // 0..1 accumulated break progress
    private int shownStage = -1; // last cracking stage broadcast (0..9), -1 = none

    public EmeraldDigGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)); // stand and break while digging
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (!mob.onGround()) return false;                 // only ground units dig (a stuck walker, not mid-fall)
        if (!mob.getNavigation().isDone()) return false;   // still has a path -> let it walk, don't dig
        dig = findDiggable(target);
        return dig != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && dig != null && isDiggable(mob.level(), dig);
    }

    /** The first block to dig, toward the prey: straight DOWN (if the prey is below), then the front column (one per
     *  body level), then the block above the head, then DOWN as a fallback (digging down is always allowed). */
    private BlockPos findDiggable(LivingEntity target) {
        Level level = mob.level();
        BlockPos feet = mob.blockPosition();
        int h = Math.max(1, Mth.ceil(mob.getBbHeight())); // 2 for a 2-tall unit, 3 for a 3-tall one
        Direction dir = Direction.getNearest(
                (float) (target.getX() - mob.getX()), 0.0F, (float) (target.getZ() - mob.getZ()));
        // Prey below us -> dig straight DOWN toward it first.
        if (target.getY() < mob.getY() - 1.0 && isDiggable(level, feet.below())) return feet.below();
        for (int y = 0; y < h; y++) {                      // blocks directly in FRONT, foot to head
            BlockPos p = feet.above(y).relative(dir);
            if (isDiggable(level, p)) return p;
        }
        BlockPos above = feet.above(h);                    // the block directly ABOVE the head
        if (isDiggable(level, above)) return above;
        // DOWN is always an option -- fall back to digging straight down if front and up are blocked.
        return isDiggable(level, feet.below()) ? feet.below() : null;
    }

    /** A block this unit is allowed to dig: a solid, breakable block that the fertile infection would convert, or
     *  the faction's own fertile/rich GROUND terrain (NOT the wall-shaped fertile variants). */
    private static boolean isDiggable(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.blocksMotion()) return false;          // only solid obstructions
        if (s.getDestroySpeed(level, pos) < 0.0F) return false;    // unbreakable (bedrock / fake bedrock)
        if (BlockMatcher.isCandidate(level, pos, s)) return true;  // raw infectable terrain (excludes walls/cobble/etc.)
        if (BlockMatcher.isInfection(s)) {                         // exception: dig through our OWN fertile terrain
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(s.getBlock());
            String path = id == null ? "" : id.getPath();
            // ...but never the wall-shaped fertile variants (fertile cobblestone/planks/brick walls).
            return !(path.contains("cobblestone") || path.contains("planks") || path.contains("brick"));
        }
        return false;
    }

    @Override
    public void tick() {
        if (dig == null) return;
        Level level = mob.level();
        mob.getLookControl().setLookAt(dig.getX() + 0.5, dig.getY() + 0.5, dig.getZ() + 0.5);
        mob.getNavigation().stop();

        BlockState s = level.getBlockState(dig);
        float hardness = s.getDestroySpeed(level, dig);
        // Bare-hand player break speed: damage/tick = 1 / hardness / (30 if hand can harvest it, else 100).
        float perTick = hardness <= 0.0F ? 1.0F
                : 1.0F / hardness / (s.requiresCorrectToolForDrops() ? 100.0F : 30.0F);
        progress += perTick;
        mob.swing(InteractionHand.MAIN_HAND);

        int stage = Math.min(9, (int) (progress * 10.0F));
        if (stage != shownStage) {
            level.destroyBlockProgress(mob.getId(), dig, stage); // cracking overlay
            shownStage = stage;
        }
        if (progress >= 1.0F) {
            level.destroyBlock(dig, false); // break it (no drops, so it never litters)
            clearProgress();
            dig = null;
        }
    }

    @Override
    public void stop() {
        clearProgress();
        dig = null;
    }

    private void clearProgress() {
        if (dig != null) mob.level().destroyBlockProgress(mob.getId(), dig, -1); // clear the cracking overlay
        progress = 0.0F;
        shownStage = -1;
    }
}
