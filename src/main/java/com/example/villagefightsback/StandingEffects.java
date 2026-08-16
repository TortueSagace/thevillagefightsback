package com.example.villagefightsback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps a player's Hero/Enemy status matching their reputation, and keeps everything else from touching it.
 *
 * <p>This used to be {@code DifficultyEffects}, and it forced a status by difficulty: Peaceful pinned Hero on,
 * Hostile and Nightmare pinned Enemy on, Neutral did nothing. The status is now the shadow of a number (see
 * {@link ReputationData}), so this enforces THAT instead -- on every difficulty, including Neutral, because
 * Neutral is exactly where the number actually moves.
 *
 * <p>Three jobs, and each is the answer to a way the old version leaked:
 *
 * <ol>
 *   <li><b>Re-derive on a slow timer.</b> 1.20.1 has no infinite duration, and even with one, "permanent" has
 *       to survive milk, a totem, a death and {@code LimboScytheEvents}' {@code removeAllEffects}. Rather than
 *       teach every one of those about standing, the status is simply re-asserted a few times a minute.</li>
 *   <li><b>Refuse the status from anywhere else.</b> Reputation is the only legitimate source now, so any
 *       other grant is a bug or another mod, and either way it is denied at the door.</li>
 *   <li><b>Gate the OUTCOME, not the causes.</b> No village unit may take a player as its target unless that
 *       player is an Enemy of the Village. See {@link #onVillageTargetsPlayer}.</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StandingEffects {
    private StandingEffects() {}

    /** How often standing is re-derived. Cheap: a handful of players, once a second. */
    private static final int REFRESH_INTERVAL = 20;

    /**
     * Put every online player's effects back in step with their reputation.
     *
     * <p>Runs on every difficulty. Hostile and Nightmare never move the number, but they still have to APPLY
     * it -- their whole meaning is that you are permanently an Enemy, and that is an effect somebody has to
     * keep on you.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % REFRESH_INTERVAL != 0) return;

        ReputationData rep = ReputationData.get(event.getServer());
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            // Creative counts, spectator does not. A creative player is standing there and the village has no
            // way of knowing otherwise; a spectator is not present in any sense the village could react to.
            if (p.isSpectator()) continue;
            rep.refresh(p, rep.get(p.getUUID()));
        }
    }

    /**
     * NOBODY from the village takes a swing at a player who is not an Enemy of the Village.
     *
     * <p>This was Peaceful-only, and the reason it existed at all is the reason it now covers everything:
     * hostility in this mod has never had one route. Hitting a villager used to stamp a personal grudge
     * straight onto that villager; hitting the node aggroed every defender in range; and two paths never
     * consulted the Enemy status at all -- {@code GeneralDariusEntity.isHuntable} returned true for every
     * non-creative player, and {@code StageThreeGuardGoal.refreshTarget} honours any target somebody else
     * set. Chasing each route was always going to miss one.
     *
     * <p>So the OUTCOME is gated instead. Forge fires this from {@code Mob.setTarget}, so every route runs
     * through here, including routes added later.
     *
     * <p>{@code Factions.isVillage} is the test, and it deliberately excludes the infected: a Vindictive
     * Guard is not the village, it is what rises when the village dies, and it goes on hunting you whatever
     * your reputation says.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onVillageTargetsPlayer(net.minecraftforge.event.entity.living.LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof net.minecraft.world.entity.player.Player p)) return;
        if (!Factions.isVillage(event.getEntity())) return;
        if (p.hasEffect(ModEffects.ENEMY_OF_THE_VILLAGE.get())) return;   // earned it: the village may answer
        event.setCanceled(true);
    }

    /**
     * Refuse Hero, Enemy and Nightmare from any source but reputation.
     *
     * <p>HIGHEST priority so it beats anything that might grant one. {@link ReputationData} raises a flag
     * around its own applications so its derive pass is let through; everything else -- a vanilla raid win
     * handing out Hero, another mod, an operator with a command -- is denied, because a status that could be
     * granted behind the number's back would drift out of step with it and never come back.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        if (ReputationData.isDeriving()) return;                          // our own re-derive
        var effect = event.getEffectInstance().getEffect();
        if (effect == net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE
                || effect == ModEffects.ENEMY_OF_THE_VILLAGE.get()
                || effect == ModEffects.NIGHTMARE_OF_THE_VILLAGE.get()) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }
}
