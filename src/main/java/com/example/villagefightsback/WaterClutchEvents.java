package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Every villager and guard knows the MLG water bucket: when a fall would hurt one, it drops a water source
 * under its own feet, lands in it, and picks the water back up a moment later.
 *
 * <p>Stage-3 guards already did this, but only inside {@code StageThreeGuardGoal.maybeWaterClutch()} -- a
 * PROACTIVE trick the combat goal plays mid-fight, which needs a mace, a live target and stage 3. That goal
 * is untouched and still runs (clutching before the landing looks better than clutching during it). This is
 * the REACTIVE floor under it: it costs nothing until a villager is actually about to take fall damage, and
 * it covers every villager and guard from stage 1 rather than the stage-3 knights only.
 *
 * <p>The damage is cancelled whether or not the water goes down, so a village never loses anybody to a fall.
 * The water is the visible flourish; a villager whose feet are inside a block it cannot replace still lands
 * safely, it just does not get to show off. Note the consequence: villagers and guards are now immune to
 * fall damage outright, which is deliberate -- it is what keeps a village intact when the Special Sauce
 * flips gravity and drops everything back down at once.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WaterClutchEvents {
    private WaterClutchEvents() {}

    /** Matches StageThreeGuardGoal's own clutch lifetime, so both tricks look identical in game. */
    private static final int REMOVE_TICKS = 25;
    /** Bounds the block churn when a whole village lands at once (a reversed-gravity drop does exactly that). */
    private static final int MAX_ACTIVE = 64;

    private record Clutch(ServerLevel level, BlockPos pos, long end) {}
    private static final List<Clutch> ACTIVE = new ArrayList<>();

    @SubscribeEvent
    public static void onFall(LivingAttackEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_FALL)) return;
        LivingEntity e = event.getEntity();
        if (!(e instanceof Villager) && !Factions.isGuard(e)) return;
        event.setCanceled(true);          // the village never loses anybody to a fall...
        placeClutch(e);                   // ...and shows the trick off when it can
    }

    /** Drop a brief water source at the feet, MLG style. Silently does nothing if there is no room. */
    private static void placeClutch(LivingEntity e) {
        if (!(e.level() instanceof ServerLevel sl) || ACTIVE.size() >= MAX_ACTIVE) return;
        BlockPos feet = e.blockPosition();
        // Never into water that is already there. canBeReplaced() is true for a water source, so a
        // villager landing in a pond stamped a FRESH source into it -- and the cleanup, which cannot
        // tell its own water from the pond's, punched an air hole in somebody's lake 25 ticks later.
        // Nothing needs saving in the first place: it landed in water.
        if (!sl.getBlockState(feet).getFluidState().isEmpty()) return;
        if (!sl.getBlockState(feet).canBeReplaced()) return;
        sl.setBlock(feet, Blocks.WATER.defaultBlockState(), 3);
        ACTIVE.add(new Clutch(sl, feet.immutable(), sl.getGameTime() + REMOVE_TICKS));
        e.fallDistance = 0.0F;
        sl.playSound(null, feet.getX() + 0.5, feet.getY() + 0.5, feet.getZ() + 0.5,
                SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 0.6F, 1.2F);
    }

    /** Pick every expired clutch back up. Only touches the block if it is still the water we placed. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Clutch> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Clutch c = it.next();
            if (c.level.getGameTime() < c.end) continue;
            if (c.level.getBlockState(c.pos).is(Blocks.WATER)) {
                c.level.setBlock(c.pos, Blocks.AIR.defaultBlockState(), 3);
            }
            it.remove();
        }
    }

    /** Drop every pending clutch on shutdown so nothing is left flooded across a restart. */
    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        for (Clutch c : ACTIVE) {
            if (c.level.getBlockState(c.pos).is(Blocks.WATER)) {
                c.level.setBlock(c.pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        ACTIVE.clear();
    }
}
