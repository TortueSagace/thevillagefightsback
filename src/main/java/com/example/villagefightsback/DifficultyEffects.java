package com.example.villagefightsback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Enforces the standing the chosen {@link Difficulty} gives you with the village.
 *
 * <p>Peaceful pins Hero of the Village on and Enemy off; Hostile and Nightmare do the reverse. Neutral is not
 * anywhere below, which is the point: on Normal this class does nothing at all and the village's ordinary rules
 * decide where you stand.
 *
 * <p>It works by REFRESHING rather than by granting once. There is no infinite duration in 1.20.1, and even if
 * there were, "permanent" has to survive a death, a milk bucket, a totem, and every place in this mod that
 * removes the opposite status -- {@code VillageEvents} strips Hero when it brands you an Enemy, and the brain
 * node strips Enemy when you buy Hero with emeralds. Rather than teach all of those about difficulty, the
 * status is simply re-asserted a few times a minute, so anything that removes it has it back within a second.
 *
 * <p>The other half is {@link #onApplicable}: the status you are not supposed to have is refused at the door.
 * Without that the refresh loop and a grant elsewhere would fight, and you would flicker between the two.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DifficultyEffects {
    private DifficultyEffects() {}

    /** How often the forced status is re-asserted. Cheap: a handful of players, once a second. */
    private static final int REFRESH_INTERVAL = 20;
    /** Long enough that the gap between refreshes never shows, short enough to lapse if difficulty changes. */
    private static final int DURATION = 20 * 30;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % REFRESH_INTERVAL != 0) return;

        Difficulty d = Difficulty.current();
        if (d == Difficulty.NEUTRAL) return;                    // the village judges you itself

        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            // Creative counts, spectator does not. A creative player is standing there and the village has no
            // way of knowing otherwise; excluding them also made the hostile difficulties impossible to test
            // without dying to them. A spectator is not present in any sense the village could react to.
            if (p.isSpectator()) continue;
            if (d.forcesHero()) {
                force(p, new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, DURATION, 0, false, false, true));
                p.removeEffect(ModEffects.ENEMY_OF_THE_VILLAGE.get());
            } else if (d.forcesEnemy()) {
                force(p, VillageEvents.enemyInstance(DURATION));
                p.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
            }
        }
    }

    /**
     * Re-assert {@code inst}, and make it uncurable.
     *
     * <p>The cure set is emptied on the instance itself rather than on the effect, because one of the two is
     * vanilla's Hero of the Village and this mod does not own it. Emptying the set is what makes milk and a
     * totem powerless against a status the difficulty says you have permanently.
     *
     * <p>Only re-applied once the existing one is running low, so the icon does not visibly reset every second.
     */
    private static void force(ServerPlayer p, MobEffectInstance inst) {
        MobEffectInstance held = p.getEffect(inst.getEffect());
        if (held != null && held.getDuration() > DURATION / 2) return;
        inst.setCurativeItems(new java.util.ArrayList<>()); // milk and totems cannot lift a forced status
        p.addEffect(inst);
    }

    /**
     * On Peaceful, the village never takes a swing at you, whatever you do to it.
     *
     * <p>Forcing Hero and refusing Enemy was not enough, and the report proved it: the player kept Hero, never
     * became an Enemy, and was beaten to death anyway. Retaliation does not consult the Enemy status at all --
     * hitting a villager stamps a personal grudge straight onto that villager, and hitting the node aggroes
     * every defender in range directly. Both are separate paths, and there is no reason to believe those are
     * the only two.
     *
     * <p>So this gates the OUTCOME rather than the causes: a village unit may not take a player as its target
     * while Peaceful is on. One rule at the door beats hunting down every route to the door, and any future
     * route is covered by it automatically.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onVillageTargetsPlayer(net.minecraftforge.event.entity.living.LivingChangeTargetEvent event) {
        if (!Difficulty.current().forcesHero()) return;
        if (!(event.getNewTarget() instanceof net.minecraft.world.entity.player.Player)) return;
        if (!Factions.isVillage(event.getEntity())) return;
        event.setCanceled(true);
    }

    /**
     * Refuse the status the difficulty forbids, before it can land.
     *
     * <p>HIGHEST priority so it beats anything that might grant it, and it fires for every source -- the brain
     * node's emerald donation, a raid win, this mod's own branding -- rather than needing each of those to
     * check the difficulty itself. On Peaceful you cannot become an Enemy however hard you try; on Hostile and
     * Nightmare no amount of donating buys you Hero.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        Difficulty d = Difficulty.current();
        if (d == Difficulty.NEUTRAL) return;

        var effect = event.getEffectInstance().getEffect();
        boolean forbidden = d.forcesHero()
                ? effect == ModEffects.ENEMY_OF_THE_VILLAGE.get()
                : effect == MobEffects.HERO_OF_THE_VILLAGE;
        if (forbidden) event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }
}
