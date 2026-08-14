package com.example.villagefightsback;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * How the Limbo Scythe charges: it takes a soul from anything substantial it kills. See
 * {@link LimboScytheItem}. (It is crafted rather than dropped, so there is no drop hook here.)
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboScytheEvents {
    private LimboScytheEvents() {}


    /**
     * Reaping a creature of at least {@link LimboScytheItem#SOUL_HEALTH_MIN} maximum health stores its soul in
     * the scythe. Keyed on MAX health, not on the damage dealt, so a soul is worth what the creature was, and a
     * swarm of small things can never charge it.
     */
    @SubscribeEvent
    public static void onReap(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (victim.getMaxHealth() < LimboScytheItem.SOUL_HEALTH_MIN) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)) return;

        ItemStack held = killer.getMainHandItem();
        if (!held.is(ModItems.LIMBO_SCYTHE.get())) return;
        if (!LimboScytheItem.addSoul(held)) return;   // already full

        if (killer instanceof net.minecraft.world.entity.player.Player player) {
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .literal("The scythe takes a soul (" + LimboScytheItem.souls(held) + "/"
                            + LimboScytheItem.MAX_SOULS + ").")
                    .withStyle(net.minecraft.ChatFormatting.AQUA), true);
        }
    }

    /**
     * A charged scythe ANYWHERE in the player's inventory buys back their death, spending one soul.
     *
     * <p>Anywhere, not just in hand: you rarely die holding the tool you meant to be saved by, and a safety net
     * that only works when you happen to be gripping it is not a safety net. The scythe does not have to be
     * equipped, only carried, which is how a totem behaves in spirit if not in slot.
     *
     * <p>EVERY charged scythe pays, not just the one that saves you. Carrying a second scythe therefore buys no
     * extra lives at all, it only doubles what each death costs -- which is the point: souls are the currency
     * for raising the village's fallen, so cheating your own death has to compete with bringing a guard back,
     * and hoarding scythes makes that trade worse rather than better.
     *
     * <p>Runs at LOWEST priority so every other death handler has already had its say (the reaping above, the
     * emerald economy, the brain's bookkeeping) before the death is called off.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onScytheSave(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;

        java.util.List<ItemStack> charged = new java.util.ArrayList<>();
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.LIMBO_SCYTHE.get()) && LimboScytheItem.souls(s) > 0) charged.add(s);
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(ModItems.LIMBO_SCYTHE.get()) && LimboScytheItem.souls(s) > 0) charged.add(s);
        }
        if (charged.isEmpty()) return;

        event.setCanceled(true);
        charged.forEach(LimboScytheItem::spendSoul);

        // Back on your feet, not on your knees. A totem leaves you at half a heart, which is survivable only
        // because a totem is cheap; a soul costs a 20-HP kill and is drained from EVERY scythe carried, so
        // being handed back a sliver of health that the very next hit takes away made the price absurd. Half
        // of max, and the Regeneration below closes the rest within seconds.
        player.setHealth(player.getMaxHealth() * 0.5F);
        player.removeAllEffects();
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.REGENERATION, 900, 1));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.ABSORPTION, 200, 1));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 800, 0));

        // Soul burst for everyone nearby to see, since the totem animation below is the saved player's alone.
        net.minecraft.server.level.ServerLevel sl = player.serverLevel();
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.8, 0.5, 0.05);
        // The totem-of-undying moment, with the scythe as the item that sweeps up the screen. Sent as a packet
        // rather than entity event 35, which hard-codes the totem stack (see ScytheSavePacket).
        com.example.villagefightsback.network.ModNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new com.example.villagefightsback.network.ScytheSavePacket());
        player.displayClientMessage(net.minecraft.network.chat.Component
                .literal(charged.size() == 1
                        ? "The scythe spends a soul to keep yours."
                        : "Every scythe you carry spends a soul to keep yours.")
                .withStyle(net.minecraft.ChatFormatting.AQUA), true);
        ModTriggers.SCYTHE_SAVE.trigger(player);
    }
}
