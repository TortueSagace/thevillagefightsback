package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Combat glue for the Limbo raiders: every attack a {@link LimboRaider} causes deals 3x damage (uniformly --
 * melee, crossbow bolts, evoker fangs, vex charges), a Limbo Evoker's summoned vexes are swapped for
 * {@link LimboVexEntity Limbo Vexes}, and the raiders smash through weak blocks that wall them off from prey.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboRaidEvents {
    private LimboRaidEvents() {}

    private static final float TOUGH_RESISTANCE = 1200.0F; // obsidian-tough or harder is spared (and bedrock)

    /** Limbo raiders break blocks: when one is pressed against a wall on the way to a target, it smashes the
     *  weak blocks at body height (never the floor under its feet, and never bedrock / obsidian-tough blocks),
     *  so a walled village is no defence against the Limbo. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof LimboRaider) || !(event.getEntity() instanceof Mob mob)) return;
        if (mob.level().isClientSide() || (mob.tickCount & 3) != 0) return;          // throttle: every 4 ticks
        if (mob.getTarget() == null || !mob.horizontalCollision) return;             // only dig when blocked en route
        ServerLevel level = (ServerLevel) mob.level();
        AABB box = mob.getBoundingBox().inflate(0.2);
        int feetY = Mth.floor(mob.getY());                                            // keep the floor it stands on
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            if (p.getY() < feetY) continue;                                           // never dig out the floor
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.getDestroySpeed(level, p) < 0) continue;               // skip air + unbreakable (bedrock)
            if (s.getBlock().getExplosionResistance() >= TOUGH_RESISTANCE) continue;  // obsidian-tough / reinforced: spared
            level.destroyBlock(p, true);                                              // (protecting glass is now weak -> smashed here)
        }

        // Reach an elevated target (a defender on the wall, a villager up high) by pillaring with cheap scaffolds.
        pillarTowardTarget(mob, level);
    }

    /** When a ground raider is walled off from a target ABOVE it, place a cheap scaffold under its feet and step
     *  up -- the same trick the village builders/guards use -- so the wall's height is no defence either. */
    private static void pillarTowardTarget(Mob mob, ServerLevel level) {
        if (mob instanceof LimboVexEntity || mob instanceof LimboRavagerEntity) return; // flyers / too big to pillar
        var target = mob.getTarget();
        if (target == null || target.getY() - mob.getY() <= 2.0) return;             // only climb to a clearly higher target
        BlockPos feet = mob.blockPosition();
        if (isSolid(level, feet.above()) || isSolid(level, feet.above(2))) return;   // no headroom to rise
        BlockState at = level.getBlockState(feet);
        if (at.canBeReplaced()) {
            level.setBlock(feet, ModBlocks.CHEAP_SCAFFOLD.get().defaultBlockState(), 3); // auto-expires (~30s)
        } else if (!at.is(ModBlocks.CHEAP_SCAFFOLD.get())) {
            return;                                                                   // feet space blocked by something solid
        }
        mob.setPos(feet.getX() + 0.5, feet.getY() + 1, feet.getZ() + 0.5);           // step up onto the scaffold
        mob.fallDistance = 0.0F;
    }

    private static boolean isSolid(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).blocksMotion();
    }

    /** 3x strength: triple any damage whose ultimate cause is a Limbo raider (covers projectiles + spells).
     *  Also spare the giant raiders from suffocation/cramming, since a 3x ravager's box can clip the village. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof LimboRaider
                && (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                    || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.CRAMMING))) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof LimboRaider) {
            event.setAmount(event.getAmount() * LimboRaider.DAMAGE_MULT);
        }
    }

    /** A Limbo Evoker summons Limbo Vexes: intercept each vanilla vex it spawns and replace it with ours. */
    @SubscribeEvent
    public static void onVexSummon(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Vex vex) || vex instanceof LimboVexEntity) return;
        if (!(vex.getOwner() instanceof LimboEvokerEntity evoker)) return;
        event.setCanceled(true); // drop the vanilla vex...
        ServerLevel level = (ServerLevel) event.getLevel();
        LimboVexEntity lv = ModEntities.LIMBO_VEX.get().create(level); // ...and summon a Limbo Vex in its place
        if (lv == null) return;
        lv.moveTo(vex.getX(), vex.getY(), vex.getZ(), vex.getYRot(), vex.getXRot());
        lv.setOwner(evoker);
        lv.setBoundOrigin(vex.blockPosition());
        lv.finalizeSpawn(level, level.getCurrentDifficultyAt(vex.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null, null);
        lv.setLimitedLife(20 * (30 + level.getRandom().nextInt(90))); // 30-120s, like a vanilla summoned vex
        level.addFreshEntity(lv);
    }
}
