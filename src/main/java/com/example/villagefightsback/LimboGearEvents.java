package com.example.villagefightsback;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
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
 *  - FULL SET bonuses: fire immunity, fall immunity, no harmful status effects, Limbo Phantoms turn neutral,
 *    and the keybound green banish beam (every 30s) with its HUD cooldown ({@link LimboAbility}).
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
            // Break any block softer than ancient debris on contact (sides + ceiling on a jump, never the
            // floor, never the fake-block doors). The armour itself grants this; Darius reuses the same helper.
            LimboGear.breakSoftBlocksOnContact(sp);
        }

        // Bedrock phasing (server side): a full-set wearer passes through bedrock like the Limbo Phantom. Must
        // be mirrored on the client (LimboBedrockPhaseClient) or the move rubber-bands. Not for spectators.
        if (!sp.isSpectator()) {
            sp.noPhysics = full && LimboGear.touchesBedrock(sp);
        }
    }

    private static void clearHarmfulEffects(LivingEntity e) {
        List<net.minecraft.world.effect.MobEffect> harmful = e.getActiveEffects().stream()
                .map(MobEffectInstance::getEffect)
                .filter(eff -> eff.getCategory() == MobEffectCategory.HARMFUL)
                .toList();
        for (var eff : harmful) e.removeEffect(eff);
    }

    /** Full-set immunity: ONLY fire and fall damage are cancelled (harmful status effects are cleared each tick
     *  in {@link #onPlayerTick}). All other damage -- mobs, the Limbo raid, drowning, etc. -- applies normally. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide() || !LimboGear.hasFullSet(player)) return;
        if (event.getSource().is(DamageTypeTags.IS_FIRE) || event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true); // fire + fall immunity only
        }
    }

    /** Only Limbo PHANTOMS turn neutral toward a full-set wearer (the original gear bonus). The Limbo raiders
     *  do NOT -- a raid is a real threat that hunts the player even in full Limbo armour. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof LimboPhantomEntity
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
