package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The environmental half of "Darius' Special Sauce" (registry id {@code darius_special_effect}): the half that has no
 * attacker to deflect onto. {@link DariusSpecialEffectEvents} already bounces any attack that HAS a living attacker;
 * everything the WORLD does to the holder -- drowning, cactus, fire, falling, suffocation, lava, the void,
 * an ownerless explosion -- used to pass straight through it. Now each of those turns around and happens to
 * whatever was doing it.
 *
 * <p>Cost: one {@code hasEffect} call per damage event. The potion lasts 10-20 seconds and is rare, so in
 * the overwhelmingly common case nothing below the second line ever runs.
 *
 * <p>Hooked at the earliest incoming-damage hook like the deflection handler: the holder takes no damage, no
 * knockback and no invulnerability flicker. HIGHEST priority so the reversal wins over anything that would
 * otherwise consume the same event (notably {@link VoidFallEvents}, which relays a void fall into the next
 * dimension down).
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DariusSpecialEffectReversals {
    private DariusSpecialEffectReversals() {}

    /** Radius of the "5x5 zone" the fluid / fire reversals clear (2 -> a 5x5x5 box). */
    private static final int ZONE = 2;
    /** Fall damage shatters this sphere, by distance, not a cube. */
    private static final double FALL_RADIUS = 3.0;
    /** Obsidian's hardness. Anything softer than obsidian shatters; obsidian and up hold. */
    private static final float OBSIDIAN_HARDNESS = 50.0F;
    /** Matches AlienSmasherEvents: keeps the brain node, the obliteration furnace and friends intact. */
    private static final float RESISTANCE_CEILING = 600.0F;
    /** Fraction of shattered blocks that drop, for the DESTROY_WITH_DECAY feel AlienSmasherEvents uses. */
    private static final float DROP_CHANCE = 0.35F;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEnvironmentalDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel sl)) return;
        if (!victim.hasEffect(ModEffects.DARIUS_SPECIAL_EFFECT)) return;

        DamageSource src = event.getSource();
        BlockPos at = victim.blockPosition();

        // Checked first: out_of_world is not in the IS_FALL tag, but this is the branch carrying a dimension
        // rule, so it reads better up top.
        if (src.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            // Limbo's void is a doorway, not a death -- falling out of it relays you to the End on purpose,
            // and flipping gravity there would break that route.
            if (LimboDimension.LIMBO.equals(sl.dimension())) return;
            event.setCanceled(true);
            ReversedGravity.trigger(sl, victim);
            grant(victim, "darius_special_effect/void");
            return;
        }

        // Water is drowning.
        if (src.is(DamageTypes.DROWN)) {
            event.setCanceled(true);
            clearFluid(sl, at, false);
            victim.setAirSupply(victim.getMaxAirSupply());
            grant(victim, "darius_special_effect/drowning");
            return;
        }

        // Lava burns itself. MUST be tested before the fire types: lava is inside the IS_FIRE tag.
        if (src.is(DamageTypes.LAVA)) {
            event.setCanceled(true);
            clearFluid(sl, at, true);
            victim.clearFire();
            grant(victim, "darius_special_effect/lava");
            return;
        }

        // Fire burns itself. ON_FIRE can tick with no fire block anywhere in reach, hence the clearFire.
        if (src.is(DamageTypes.IN_FIRE) || src.is(DamageTypes.ON_FIRE) || src.is(DamageTypes.HOT_FLOOR)) {
            event.setCanceled(true);
            clearFire(sl, at);
            victim.clearFire();
            grant(victim, "darius_special_effect/fire");
            return;
        }

        // Cactus pricks itself. The damage source does not say WHICH cactus, so break whatever is touching.
        if (src.is(DamageTypes.CACTUS)) {
            event.setCanceled(true);
            breakTouching(sl, victim, Blocks.CACTUS);
            grant(victim, "darius_special_effect/cactus");
            return;
        }

        // Blocks self-suffocate.
        if (src.is(DamageTypes.IN_WALL)) {
            event.setCanceled(true);
            breakSuffocating(sl, victim);
            grant(victim, "darius_special_effect/suffocation");
            return;
        }

        // An anvil or a stalactite dropped on the holder destroys ITSELF. The falling block is the damage's
        // direct entity, so discarding it stops the fall dead and it never lands.
        if (src.is(DamageTypes.FALLING_ANVIL) || src.is(DamageTypes.FALLING_STALACTITE)) {
            event.setCanceled(true);
            if (src.getDirectEntity() != null) src.getDirectEntity().discard();
            grant(victim, "darius_special_effect/fall");
            return;
        }

        // Landing ON a pointed dripstone shatters the dripstone instead. MUST be tested before IS_FALL:
        // vanilla puts STALAGMITE inside that tag, so otherwise this falls through to the generic terrain
        // shatter and the spike that did the damage is left standing.
        if (src.is(DamageTypes.STALAGMITE)) {
            event.setCanceled(true);
            breakTouching(sl, victim, Blocks.POINTED_DRIPSTONE);
            victim.fallDistance = 0.0F;
            grant(victim, "darius_special_effect/fall");
            return;
        }

        // Fall damage hurts gravity.
        if (src.is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
            shatter(sl, at);
            victim.fallDistance = 0.0F;
            grant(victim, "darius_special_effect/fall");
            return;
        }

        // Explosions: one already lands on whoever caused it, because DariusSpecialEffectEvents.onAttack sees a living
        // attacker for a creeper or for TNT a player lit. The gap is a blast with NOBODY behind it -- redstone
        // TNT, a bed in the Nether, a respawn anchor in the Overworld -- which today just kills the holder.
        if (src.is(DamageTypeTags.IS_EXPLOSION)) {
            if (src.getEntity() instanceof LivingEntity) return;   // onAttack redirects it in full
            // Pre-armour amount, so this over-protects slightly rather than under-protecting. Good enough:
            // the alternative is a second handler on the post-armour hook for a prank potion.
            if (event.getAmount() < victim.getHealth()) return;    // only a LETHAL ownerless blast
            event.setCanceled(true);
            grant(victim, "darius_special_effect/explosion");
        }
    }

    // --- the reversals -------------------------------------------------------------------------------

    /** Clear every water (or lava) cell in the 5x5x5 around {@code centre}, including waterlogged blocks. */
    private static void clearFluid(ServerLevel sl, BlockPos centre, boolean lava) {
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-ZONE, -ZONE, -ZONE),
                                                 centre.offset(ZONE, ZONE, ZONE))) {
            BlockState s = sl.getBlockState(p);
            FluidState f = s.getFluidState();
            if (f.isEmpty() || !f.is(lava ? FluidTags.LAVA : FluidTags.WATER)) continue;
            if (s.getBlock() instanceof LiquidBlock) {
                sl.setBlock(p.immutable(), Blocks.AIR.defaultBlockState(), 3);
            } else if (!lava && s.hasProperty(BlockStateProperties.WATERLOGGED)) {
                sl.setBlock(p.immutable(), s.setValue(BlockStateProperties.WATERLOGGED, Boolean.FALSE), 3);
            }
        }
    }

    /** Put out every fire in the 5x5x5 around {@code centre}. */
    private static void clearFire(ServerLevel sl, BlockPos centre) {
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-ZONE, -ZONE, -ZONE),
                                                 centre.offset(ZONE, ZONE, ZONE))) {
            BlockState s = sl.getBlockState(p);
            if (s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE)) {
                sl.removeBlock(p.immutable(), false);
            }
        }
    }

    /** Break every {@code block} in contact with the holder's hitbox. */
    private static void breakTouching(ServerLevel sl, LivingEntity e, Block block) {
        AABB box = e.getBoundingBox().inflate(0.35);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                                                 BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (sl.getBlockState(p).is(block)) sl.destroyBlock(p.immutable(), true);
        }
    }

    /** Break whichever block is actually squeezing the holder. */
    private static void breakSuffocating(ServerLevel sl, LivingEntity e) {
        AABB box = e.getBoundingBox().deflate(0.001);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                                                 BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState s = sl.getBlockState(p);
            if (!s.isAir() && s.isSuffocating(sl, p) && breakable(sl, s, p)) {
                sl.destroyBlock(p.immutable(), true);
            }
        }
    }

    /** Fall damage shatters everything softer than obsidian in a 3-block SPHERE. */
    private static void shatter(ServerLevel sl, BlockPos centre) {
        int r = (int) Math.ceil(FALL_RADIUS);
        double r2 = FALL_RADIUS * FALL_RADIUS;
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            if (centre.distSqr(p) > r2) continue;                          // spherical, by distance
            BlockState s = sl.getBlockState(p);
            if (s.isAir() || !breakable(sl, s, p)) continue;
            if (s.getDestroySpeed(sl, p) >= OBSIDIAN_HARDNESS) continue;   // obsidian and up hold
            sl.destroyBlock(p.immutable(), sl.random.nextFloat() < DROP_CHANCE);
        }
    }

    /** Unbreakable (bedrock, barrier) and the mod's hardened blocks are never touched. */
    private static boolean breakable(ServerLevel sl, BlockState s, BlockPos p) {
        return s.getDestroySpeed(sl, p) >= 0.0F
                && s.getBlock().getExplosionResistance() < RESISTANCE_CEILING;
    }

    /** Advancements are a player thing -- Darius reversing something grants nobody anything. */
    private static void grant(LivingEntity e, String id) {
        if (e instanceof ServerPlayer p) Achievements.award(p, id);
    }
}
