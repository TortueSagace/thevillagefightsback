package com.example.villagefightsback;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * The Limbo kills what stays in it, and keeps what it kills.
 *
 * <h2>The clock</h2>
 * Anything alive in the Limbo accumulates time; at {@value #ONSET_TICKS} it takes
 * {@link ModEffects#SLOW_ANNIHILATION}, and the effect is renewed every {@value #RENEW_TICKS} for as long as
 * it is still there. Leaving resets the clock, so the Limbo is survivable in short visits and lethal as a
 * residence -- which is the point.
 *
 * <p>Everything here runs on the {@value #TICK_INTERVAL}-tick cadence
 * {@link LimboRaidManager} already uses for its omen, and only ever walks the LIMBO level's entity list --
 * never a world-sized query, and nothing at all in any other dimension. The bookkeeping map is empty in
 * every world where nobody is in the Limbo, which is almost all of them.
 *
 * <h2>The exit</h2>
 * Levitation would normally pin you to the ceiling until the Wither finishes. Crouching mirrors the rise
 * into a fall of the same speed, so a player who works that out can still reach the bed. A Gas Mask refuses
 * the effect outright.
 *
 * <h2>What is left behind</h2>
 * Dying with it turns you into one of the Limbo's own: the black banish swirl, then a random member of the
 * roster where you fell. A player still respawns normally -- the unit is what is left of them, not them.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SlowAnnihilationEvents {
    private SlowAnnihilationEvents() {}

    /** Ten seconds in the Limbo before it starts. */
    private static final int ONSET_TICKS = 200;
    /** ...and it lasts ten, renewed every two, so it never lapses while you are still down there. */
    private static final int DURATION_TICKS = 200;
    private static final int RENEW_TICKS = 40;
    /** The sweep cadence. Per-tick would be an entity walk every tick for a cosmetic countdown. */
    private static final int TICK_INTERVAL = 20;

    /** How long each living thing has been in the Limbo, in ticks. Empty unless somebody is down there. */
    private static final Map<UUID, Integer> TIME = new HashMap<>();

    // --- the clock ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % TICK_INTERVAL != 0) return;
        ServerLevel limbo = server.getLevel(LimboDimension.LIMBO);
        if (limbo == null) {
            TIME.clear();
            return;
        }

        java.util.Set<UUID> present = new java.util.HashSet<>();
        for (net.minecraft.world.entity.Entity e : limbo.getAllEntities()) {
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
            present.add(le.getUUID());
            int t = TIME.merge(le.getUUID(), TICK_INTERVAL, Integer::sum);
            if (t < ONSET_TICKS) continue;
            MobEffectInstance cur = le.getEffect(ModEffects.SLOW_ANNIHILATION);
            if (cur == null || cur.getDuration() <= DURATION_TICKS - RENEW_TICKS) {
                le.addEffect(new MobEffectInstance(ModEffects.SLOW_ANNIHILATION,
                        DURATION_TICKS, 0, false, true, true));
            }
        }
        // Anything that left (or died) starts again from zero next time it comes down.
        TIME.keySet().retainAll(present);
    }

    /**
     * Crouch to go down.
     *
     * <p>Levitation is applied by the effect itself and there is no way to make vanilla levitation
     * directional, so the descent is a velocity write on the ticks the player is crouching. The speed is
     * the mirror of the rise rather than a free fall, so it reads as fighting the effect rather than as
     * switching it off: {@code ReversedGravity} works out the same arithmetic from the other direction.
     */
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!player.isShiftKeyDown()) return;
        if (!player.hasEffect(ModEffects.SLOW_ANNIHILATION)) return;
        MobEffectInstance lev = player.getEffect(MobEffects.LEVITATION);
        if (lev == null) return;
        // Vanilla levitation settles at 0.9 * 0.05 * (amp + 1) per tick upward; mirror exactly that.
        double rise = 0.9 * 0.05 * (lev.getAmplifier() + 1);
        Vec3 m = player.getDeltaMovement();
        player.setDeltaMovement(m.x, -rise, m.z);
        player.hurtMarked = true;
        player.resetFallDistance();
    }

    // --- the mask ----------------------------------------------------------------------------------

    /**
     * A Gas Mask refuses it outright.
     *
     * <p>Denied rather than stripped afterwards, the way {@code VillageEvents.onImmunityEffect} denies, so
     * the wearer never sees a frame of Wither or a flicker of the icon.
     */
    @SubscribeEvent
    public static void onApplicable(MobEffectEvent.Applicable event) {
        if (!event.getEffectInstance().is(ModEffects.SLOW_ANNIHILATION)) return;
        if (event.getEntity().getItemBySlot(EquipmentSlot.HEAD).is(ModArmor.GAS_MASK.get())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    // --- what it leaves behind ---------------------------------------------------------------------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!dead.hasEffect(ModEffects.SLOW_ANNIHILATION)) return;
        if (!(dead.level() instanceof ServerLevel sl)) return;

        LimboBanishFx.start(dead);      // the black swirl; the lime one is the emerald faction's
        Mob risen = ChunkPortalManager.randomRosterMob(sl, sl.getRandom());
        if (risen == null) return;
        risen.moveTo(dead.getX(), dead.getY(), dead.getZ(), sl.getRandom().nextFloat() * 360.0F, 0.0F);
        risen.finalizeSpawn(sl, sl.getCurrentDifficultyAt(risen.blockPosition()), MobSpawnType.EVENT, null);
        sl.addFreshEntity(risen);
        TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] {} was annihilated in the Limbo; a {} rose in its place.",
                dead.getName().getString(), risen.getType().getDescription().getString());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TIME.clear();
    }
}
