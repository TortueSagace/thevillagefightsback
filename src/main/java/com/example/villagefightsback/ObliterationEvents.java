package com.example.villagefightsback;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.example.villagefightsback.platform.event.LivingDeathEvent;
import com.example.villagefightsback.platform.event.MobSpawnEvent;
import com.example.villagefightsback.platform.event.TickEvent;
import com.example.villagefightsback.platform.EventPriority;
import com.example.villagefightsback.platform.SubscribeEvent;
import com.example.villagefightsback.platform.ModRegistries;

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

        ResourceLocation type = ModRegistries.ENTITY_TYPES.getKey(dead.getType());
        if (type == null) return;
        ObliterationData data = ObliterationData.get(level.getServer());
        GlobalPos furnace = data.nearestBurning(level.dimension(), dead.blockPosition());
        if (furnace == null) return;
        if (!data.obliterate(furnace, type)) return;

        // Tell whoever swung. This is a permanent, world-wide change and it must never happen silently.
        if (event.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            ModTriggers.OBLITERATED.trigger(sp);
        }
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
     * Refuse the spawn of anything currently struck off, at the natural-spawn choke point.
     *
     * <p>Cheapest place to say no: the creature is turned away before it ever enters the world. Covers caves,
     * spawners, breeding and structure spawns. It does NOT cover everything, which is what
     * {@link #onEntityJoin} is for.
     */
    @SubscribeEvent
    public static void onSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ResourceLocation type = ModRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (type == null) return;
        if (!ObliterationData.get(level.getServer()).isObliterated(type)) return;
        event.setSpawnCancelled(true);
    }

    /**
     * The catch-all, and the reason MODDED creatures are banned as reliably as vanilla ones.
     *
     * <p>{@code FinalizeSpawn} only fires for spawns that go through vanilla's spawner machinery. Plenty of
     * mods put creatures into the world by calling {@code addFreshEntity} directly -- summoned minions, ritual
     * spawns, boss adds, custom spawners -- and every one of those would have walked straight past the handler
     * above. Since the whole promise of the furnace is that an obliterated creature cannot appear ANYWHERE,
     * a ban that quietly held only for vanilla mobs would be worse than no ban at all: you would not find out
     * until the thing you paid an Obliterator to erase wandered back into view.
     *
     * <p>Every join passes through here, so this catches them all. Two exclusions:
     * <ul>
     *   <li>{@code loadedFromDisk} -- a chunk loading its existing residents. Obliteration erases a species
     *       from the FUTURE, not from the world, so creatures already alive keep living.</li>
     *   <li>Non-living entities, which are never obliterated and would otherwise pay for this lookup on every
     *       arrow, item and particle-carrier that enters a level.</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onEntityJoin(com.example.villagefightsback.platform.event.EntityJoinLevelEvent event) {
        if (event.loadedFromDisk()) return;                       // already alive: let it be
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (living instanceof Player) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // Ask the cheap question first. Every creature that enters the world reaches this line, and in a save
        // where no Obliteration Furnace is lit -- which is nearly every save, nearly all of the time -- there is
        // no reason to pay for a registry reverse-lookup per spawn to find that out.
        ObliterationData data = ObliterationData.get(level.getServer());
        if (!data.bansAnything()) return;
        ResourceLocation type = ModRegistries.ENTITY_TYPES.getKey(living.getType());
        if (type == null) return;
        if (!data.isObliterated(type)) return;
        event.setCanceled(true);
    }

    /** Burn fuel once a second, server-wide, so a furnace runs down whether or not its chunk is loaded. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % 20 != 0) return;
        ObliterationData.get(event.getServer()).tickSecond();
    }
}
