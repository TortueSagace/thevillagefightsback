package com.example.villagefightsback;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Worn-Limbo-gear effects.
 *  - Per worn piece: a glowing green shimmer (also for a held Limbo Pickaxe).
 *  - FULL SET bonuses: total immunity (all damage except a void-fall / {@code /kill}), fire immunity, fall
 *    immunity, no harmful status effects, Limbo Phantoms turn neutral, and the keybound green banish beam
 *    (every 30s) with its HUD cooldown ({@link LimboAbility}).
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboGearEvents {
    private LimboGearEvents() {}

    private static final Set<UUID> FULL_SET = ConcurrentHashMap.newKeySet(); // players currently in the full set

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp) || sp.level().isClientSide()) return;

        boolean full = LimboGear.wornCount(sp) >= 4;

        // Track set completion -> push the cooldown to the client so the HUD is correct the instant it appears.
        // (The "shine" is the gear's own animated/foil look, NOT particles around the player.)
        if (full) {
            if (FULL_SET.add(sp.getUUID())) LimboAbility.syncOnEquip(sp);
        } else {
            FULL_SET.remove(sp.getUUID());
        }

        if (full) {
            // Fire + fall immunity (belt-and-braces with the damage cancel below) and a clean slate of effects.
            sp.clearFire();
            sp.fallDistance = 0.0F;
            LimboAbility.tick(sp);
            if ((sp.tickCount & 7) == 0) clearHarmfulEffects(sp);
        }
    }

    private static void clearHarmfulEffects(LivingEntity e) {
        List<net.minecraft.world.effect.MobEffect> harmful = e.getActiveEffects().stream()
                .map(MobEffectInstance::getEffect)
                .filter(eff -> eff.getCategory() == MobEffectCategory.HARMFUL)
                .toList();
        for (var eff : harmful) e.removeEffect(eff);
    }

    /** Full-set immunity: cancel all incoming damage except a void-fall (so the void chain still works) and /kill. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide() || !LimboGear.hasFullSet(player)) return;
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD) || event.getSource().is(DamageTypes.GENERIC_KILL)) {
            return; // let the void-fall chain and /kill through
        }
        event.setCanceled(true);
    }

    /** Limbo Phantoms turn neutral toward a full-set wearer (they never pick them as a target). */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (Factions.isLimbo(event.getEntity())
                && event.getNewTarget() instanceof Player p && LimboGear.hasFullSet(p)) {
            event.setCanceled(true);
        }
    }

    /** Drop per-player state on logout (no map leak, no stale cooldown/HUD on rejoin). */
    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        FULL_SET.remove(event.getEntity().getUUID());
        LimboAbility.clear(event.getEntity().getUUID());
    }
}
