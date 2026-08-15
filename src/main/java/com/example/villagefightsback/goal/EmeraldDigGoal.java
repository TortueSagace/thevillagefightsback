package com.example.villagefightsback.goal;

import java.util.EnumSet;
import java.util.List;

import com.example.villagefightsback.infection.BlockMatcher;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A unit that cannot PATH to its prey digs through the soft block in its way -- the block(s) directly in
 * FRONT of it (one per body level: 2 blocks for a 2-tall unit, 3 for a 3-tall one), the one directly ABOVE its head,
 * or straight DOWN (preferred when the prey is below). It digs at a bare-hand player's speed (no tool) and shows the
 * cracking animation. In EVERY direction -- down included -- it only breaks blocks {@link #isDiggable} permits;
 * forbidden blocks (cobblestone, fake/wall blocks, chests, ore, glass, the brain node, bedrock, ...) are never mined.
 *
 * <p>Has NO goal flags, so it runs ALONGSIDE the combat/movement goals instead of competing with them for the MOVE
 * flag (which the attack goal always holds while a target exists). It only kicks in when a real pathfind to the
 * target fails ({@link Path#canReach()} is false) -- i.e. there is no clear path -- then breaks the obstacle and the
 * normal pursuit walks through. The reach check is staggered per-entity to a few times a second to keep it cheap.
 *
 * <p>NAME: this began as an emerald-faction ability and kept the name, but VILLAGE GUARDS carry it too now (see
 * {@code VillageEvents.ensureGuardDigGoal}), at every stage rather than only when upgraded. The digging rules are
 * identical for both sides; what differs is only who is holding the pick. The village's own walls are safe either
 * way, because {@link #isDiggable} refuses cobblestone, planks, brick, the fake gates and this mod's own glass.
 */
public class EmeraldDigGoal extends Goal {
    private static final int RECHECK_TICKS = 20; // how often to (re)test reachability with a real pathfind

    /**
     * Digging is only considered this close to the prey.
     *
     * <p>The whole goal turns on "a pathfind to the target failed, so something is in the way". That inference
     * is only sound within the pathfinder's reach. Ask it for a path to something a thousand blocks off and it
     * fails for the mundane reason that it gave up, not because anything is blocking -- and the digger, reading
     * that as an obstruction, starts tunnelling through open ground. On a superflat that is exactly as absurd
     * as it sounds: a clear horizon, and the raider digs into the dirt.
     *
     * <p>32 blocks is comfortably inside what the pathfinder will actually search, so inside it a failure means
     * what the goal thinks it means. Further out, walking is the answer, and for a Nightmare raider the hunt
     * goal is already doing exactly that.
     */
    private static final double DIG_RANGE = 32.0;

    private final Mob mob;
    /** Whether this digger may draw an emerald tool. Guards do; emerald units keep clawing bare-handed. */
    private final boolean toolUser;
    private BlockPos dig;
    private float progress;       // 0..1 accumulated break progress on `dig`
    private int shownStage = -1;  // last cracking stage broadcast (0..9), -1 = none
    private ItemStack stashedWeapon = ItemStack.EMPTY; // what was in the hand before a tool was drawn
    private boolean holdingTool;

    /** Bare-handed digger (the emerald faction). */
    public EmeraldDigGoal(Mob mob) {
        this(mob, false);
    }

    public EmeraldDigGoal(Mob mob, boolean toolUser) {
        this.mob = mob;
        this.toolUser = toolUser;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class)); // no flags -> runs concurrently with combat/movement goals
    }

    /**
     * The emerald tool that bites into {@code state} hardest, or null if none beats bare hands.
     *
     * <p>Chosen by ASKING each tool rather than by matching block tags: {@code getDestroySpeed} is the same
     * question the game asks for a player, so the pickaxe wins on stone, the shovel on dirt, the axe on wood
     * and the sword on cobweb, without this goal keeping its own copy of vanilla's tag tables to fall out of
     * date. A block no tool is better at (a mob-placed oddity, a modded block in no mineable tag) returns null
     * and the digger just uses its fists, which is what it did for everything before.
     */
    private static net.minecraft.world.item.Item bestTool(BlockState state) {
        net.minecraft.world.item.Item best = null;
        float bestSpeed = 1.0F; // bare hand; a tool has to actually beat it to be worth drawing
        for (net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.Item> ro : List.of(
                com.example.villagefightsback.ModItems.EMERALD_PICKAXE,
                com.example.villagefightsback.ModItems.EMERALD_SHOVEL,
                com.example.villagefightsback.ModItems.EMERALD_AXE,
                com.example.villagefightsback.ModItems.EMERALD_SWORD)) {
            net.minecraft.world.item.Item item = ro.get();
            float speed = new ItemStack(item).getDestroySpeed(state);
            if (speed > bestSpeed) { bestSpeed = speed; best = item; }
        }
        return best;
    }

    /** Draw the right tool for {@code state}, stashing whatever the digger was holding the first time. */
    private void equipFor(BlockState state) {
        if (!toolUser) return;
        net.minecraft.world.item.Item want = bestTool(state);
        if (want == null) { restoreWeapon(); return; }
        if (mob.getMainHandItem().is(want)) return;
        if (!holdingTool) {
            stashedWeapon = mob.getMainHandItem().copy();
            holdingTool = true;
        }
        mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(want));
        mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
    }

    /** Put the weapon back. A guard that stops digging must not wander off holding a shovel. */
    private void restoreWeapon() {
        if (!holdingTool) return;
        mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stashedWeapon);
        mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
        stashedWeapon = ItemStack.EMPTY;
        holdingTool = false;
    }

    /**
     * The prey to dig toward.
     *
     * <p>{@link Mob#getTarget()} first, then the brain's ATTACK_TARGET. The fallback exists because this goal is
     * no longer emerald-only: village guards carry it too, and an MCA Reborn guard is a brain-driven Villager that
     * picks its prey through memory rather than through {@code setTarget}. Without the fallback such a guard would
     * carry the goal and never once use it, since {@code getTarget()} stays null for its whole life.
     *
     * <p>The REGISTERED check is not optional. {@link net.minecraft.world.entity.ai.Brain#getMemory} THROWS for a
     * memory the entity's brain never declared, and most of the mobs carrying this goal (golems, emerald mobs)
     * have no ATTACK_TARGET memory at all, so probing unguarded would crash the server on the first stuck digger.
     */
    private LivingEntity currentTarget() {
        LivingEntity t = mob.getTarget();
        if (t != null) return t;
        net.minecraft.world.entity.ai.Brain<?> brain = mob.getBrain();
        net.minecraft.world.entity.ai.memory.MemoryModuleType<LivingEntity> mem =
                net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET;
        if (!brain.checkMemory(mem, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) return null;
        return brain.getMemory(mem).orElse(null);
    }

    @Override
    public boolean canUse() {
        LivingEntity target = currentTarget();
        if (target == null || !target.isAlive() || !mob.onGround()) return false;
        // Only dig once the mob has actually WALKED to the end of its path (it is stuck), never mid-stride -- this is
        // what stops it gouging random holes in the floor on the way to reachable prey.
        if (!mob.getNavigation().isDone()) return false;
        // Too far for "no path" to mean "blocked": see DIG_RANGE.
        if (mob.distanceToSqr(target) > DIG_RANGE * DIG_RANGE) return false;
        if (!stuckCheckDue()) return false;            // only run the (costly) reach test a few times a second
        if (canReachTarget(target)) return false;      // a clear path exists -> walk, don't dig
        dig = findDiggable(target);
        return dig != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = currentTarget();
        if (target == null || !target.isAlive() || dig == null || !isDiggable(mob.level(), dig)) return false;
        // The prey walked away: stop tunnelling after it and follow instead.
        if (mob.distanceToSqr(target) > DIG_RANGE * DIG_RANGE) return false;
        if (stuckCheckDue() && canReachTarget(target)) return false; // a path opened up -> stop digging and walk it
        return true;
    }

    /** Fires a few times a second, staggered across entities so not every digger pathfinds on the same tick. */
    private boolean stuckCheckDue() {
        return (mob.tickCount + mob.getId()) % RECHECK_TICKS == 0;
    }

    /** True if the mob can actually path to {@code target} (so it should walk/jump, not dig). Accuracy 1 means
     *  reaching ADJACENT counts as reachable -- a melee mob never stands on its target's exact block, and the
     *  pathfinder already steps/jumps 1-block rises, so a straight path with a single step-up is "reachable"
     *  (accuracy 0 wrongly reported every such target as unreachable, which made it dig everywhere). */
    private boolean canReachTarget(LivingEntity target) {
        Path p = mob.getNavigation().createPath(target, 1);
        return p != null && p.canReach();
    }

    /** The first block to dig, toward the prey: straight DOWN (if the prey is below), then the front column (one per
     *  body level), then the block above the head, then DOWN as a fallback (only if that block is itself diggable). */
    private BlockPos findDiggable(LivingEntity target) {
        Level level = mob.level();
        BlockPos feet = mob.blockPosition();
        int h = Math.max(1, Mth.ceil(mob.getBbHeight())); // 2 for a 2-tall unit, 3 for a 3-tall one
        Direction dir = Direction.getNearest(
                (float) (target.getX() - mob.getX()), 0.0F, (float) (target.getZ() - mob.getZ()));
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // Prey below us -> dig straight DOWN toward it first.
        if (target.getY() < mob.getY() - 1.0) {
            p.set(feet.getX(), feet.getY() - 1, feet.getZ());
            if (isDiggable(level, p)) return p.immutable();
        }
        // Blocks directly in FRONT, foot to head. WIDE units (golems, width > 1) scan up to 2 blocks ahead so they
        // reach PAST their own body to the actual obstruction (a 1-block check sits inside a golem's own footprint).
        int reach = mob.getBbWidth() > 1.0F ? 2 : 1;
        for (int dist = 1; dist <= reach; dist++) {
            for (int y = 0; y < h; y++) {
                p.set(feet.getX() + dir.getStepX() * dist, feet.getY() + y, feet.getZ() + dir.getStepZ() * dist);
                if (isDiggable(level, p)) return p.immutable();
            }
        }
        // The block directly ABOVE the head.
        p.set(feet.getX(), feet.getY() + h, feet.getZ());
        if (isDiggable(level, p)) return p.immutable();
        // Fallback: straight DOWN (only if that block is itself diggable).
        p.set(feet.getX(), feet.getY() - 1, feet.getZ());
        return isDiggable(level, p) ? p.immutable() : null;
    }

    /** A block this unit is allowed to dig: a solid, breakable block that the fertile infection would convert
     *  ({@link BlockMatcher#isCandidate} -- a blacklist, so foreign soft blocks like the Spore mod's DO qualify),
     *  or the faction's own fertile/rich GROUND terrain (NOT the wall-shaped fertile variants). Forbidden blocks
     *  (cobblestone, fake/wall blocks, chests, ore, glass, brain node, bedrock, ...) are excluded by isCandidate. */
    private boolean isDiggable(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        // COBWEB is an obstruction that does not block motion, so the solidity test below throws it out. For a
        // tool user it is worth cutting anyway: walking into it is what a web is FOR, and a guard wading through
        // at a crawl while something shoots at it is the trap working exactly as the player intended. Only tool
        // users get this, so a player's web still slows the emerald faction the way it always has.
        if (toolUser && s.is(net.minecraft.world.level.block.Blocks.COBWEB)) return true;
        if (s.isAir() || !s.blocksMotion()) return false;          // only solid obstructions
        if (s.getDestroySpeed(level, pos) < 0.0F) return false;    // unbreakable (bedrock / fake bedrock)
        if (BlockMatcher.isCandidate(level, pos, s)) return true;  // any infectable obstruction (incl. foreign soft blocks)
        if (BlockMatcher.isInfection(s)) {                         // exception: dig through our OWN fertile terrain
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(s.getBlock());
            String path = id == null ? "" : id.getPath();
            return !(path.contains("cobblestone") || path.contains("planks") || path.contains("brick"));
        }
        // Flimsy glass barriers (a player's glass dome, etc.) ARE smashable to reach enclosed prey -- but NOT the
        // mod's own reinforced/protecting glass (village defence), which lives in our namespace.
        if (s.is(net.minecraftforge.common.Tags.Blocks.GLASS) || s.is(net.minecraftforge.common.Tags.Blocks.GLASS_PANES)) {
            ResourceLocation gid = ForgeRegistries.BLOCKS.getKey(s.getBlock());
            return gid == null || !gid.getNamespace().equals(com.example.villagefightsback.TheVillageFightsBack.MODID);
        }
        return false;
    }

    @Override
    public void tick() {
        if (dig == null) return;
        Level level = mob.level();
        mob.getLookControl().setLookAt(dig.getX() + 0.5, dig.getY() + 0.5, dig.getZ() + 0.5);

        BlockState s = level.getBlockState(dig);
        equipFor(s);
        float hardness = s.getDestroySpeed(level, dig);
        // Vanilla's own mining formula: damage/tick = toolSpeed / hardness / (30 if it can harvest, else 100).
        // Held tool included, so drawing the right one actually pays -- an emerald pickaxe chews stone about as
        // fast as a player's would, where the old hard-coded 1.0F meant a fully equipped guard scratched at a
        // wall at exactly the speed of a mob with no hands.
        ItemStack held = mob.getMainHandItem();
        float speed = held.isEmpty() ? 1.0F : held.getDestroySpeed(s);
        boolean canHarvest = !s.requiresCorrectToolForDrops() || (!held.isEmpty() && held.isCorrectToolForDrops(s));
        float perTick = hardness <= 0.0F ? 1.0F
                : speed / hardness / (canHarvest ? 30.0F : 100.0F);
        progress += perTick;
        mob.swing(InteractionHand.MAIN_HAND);

        int stage = Math.min(9, (int) (progress * 10.0F));
        if (stage != shownStage) {
            level.destroyBlockProgress(mob.getId(), dig, stage); // cracking overlay
            shownStage = stage;
        }
        if (progress >= 1.0F) {
            level.destroyBlock(dig, false);    // break it (no drops, so it never litters)
            clearProgress();
            LivingEntity target = currentTarget();
            dig = target != null ? findDiggable(target) : null; // keep tunnelling: line up the next block in the way
        }
    }

    @Override
    public void stop() {
        clearProgress();
        dig = null;
        restoreWeapon();
    }

    private void clearProgress() {
        if (dig != null) mob.level().destroyBlockProgress(mob.getId(), dig, -1); // clear the cracking overlay
        progress = 0.0F;
        shownStage = -1;
    }
}
