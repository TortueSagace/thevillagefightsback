package com.example.villagefightsback;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Obliteration rule, in three parts: marking, erasing, and refusing.
 *
 * <p>MARKING -- an {@link ObliteratorItem} hit applies {@link ModEffects#OBLITERATION}, but only while some
 * furnace is burning. With none lit the weapon is just a hard sword, which is what makes the furnace worth
 * building rather than a formality.
 *
 * <p>ERASING -- a creature that dies carrying the mark, OR is killed outright in a single blow by an
 * Obliterator, is struck off at the NEAREST burning furnace. One-shotting counts because the mark can never
 * land otherwise: the effect is applied by the same hit that kills, and a corpse cannot carry a mark it never
 * had time to receive. Without that clause the weapon would punish you for hitting too hard.
 *
 * <p>REFUSING -- a struck-off type cannot spawn, in any dimension, while any furnace holds it. Creatures
 * ALREADY alive are left alone: this erases a species from the future, not from the world, so obliterating
 * zombies does not empty the caves you have already lit.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ObliterationEvents {
    private ObliterationEvents() {}

    /** How long the mark lasts. Generous: it exists to survive the fight, not to be a race. */
    private static final int MARK_TICKS = 20 * 60;

    /** Called from {@link ObliteratorItem#hurtEnemy}: mark the victim if any furnace is lit. */
    public static void onObliteratorHit(LivingEntity target, LivingEntity attacker) {
        if (!(target.level() instanceof ServerLevel level)) return;
        if (target instanceof Player) return;                  // players are never obliterated
        ObliterationData data = ObliterationData.get(level.getServer());
        if (data.nearestBurning(level.dimension(), target.blockPosition()) == null) return;
        target.addEffect(new MobEffectInstance(ModEffects.OBLITERATION.get(), MARK_TICKS, 0, false, true, true));
    }

    /**
     * A marked death, or a one-shot kill, erases the species at the nearest burning furnace.
     *
     * <p>LOWEST priority so nothing here interferes with the death handlers that actually matter to the rest of
     * the mod (loot, the emerald economy, the scythe's reaping, the grave).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof Player) return;
        if (!(dead.level() instanceof ServerLevel level)) return;

        boolean marked = dead.hasEffect(ModEffects.OBLITERATION.get());
        boolean oneShot = killedByObliterator(event) && dead.getHealth() <= 0.0F && !marked;
        if (!marked && !oneShot) return;

        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(dead.getType());
        if (type == null) return;
        ObliterationData data = ObliterationData.get(level.getServer());
        GlobalPos furnace = data.nearestBurning(level.dimension(), dead.blockPosition());
        if (furnace == null) return;
        if (!data.obliterate(furnace, type)) return;

        // Tell whoever swung. This is a permanent, world-wide change and it must never happen silently.
        if (event.getSource().getEntity() instanceof Player p) {
            p.displayClientMessage(net.minecraft.network.chat.Component
                    .literal("Obliterated: " + dead.getType().getDescription().getString()
                            + ". It cannot spawn while that furnace burns.")
                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), false);
        }
    }

    /** Was the killing blow an Obliterator swing? */
    private static boolean killedByObliterator(LivingDeathEvent event) {
        Entity killer = event.getSource().getEntity();
        return killer instanceof LivingEntity le
                && le.getMainHandItem().is(ModItems.OBLITERATOR.get());
    }

    /**
     * Refuse the spawn of anything currently struck off.
     *
     * <p>{@code FinalizeSpawn} is the choke point every natural spawn passes through, so one handler covers
     * caves, spawners, breeding and structure spawns alike.
     */
    @SubscribeEvent
    public static void onSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (type == null) return;
        if (!ObliterationData.get(level.getServer()).isObliterated(type)) return;
        event.setSpawnCancelled(true);
    }

    /** Burn fuel once a second, server-wide, so a furnace runs down whether or not its chunk is loaded. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % 20 != 0) return;
        ObliterationData.get(event.getServer()).tickSecond();
    }
}
