package com.example.villagefightsback;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * How the Limbo Scythe charges: it takes a soul from anything substantial it kills. See
 * {@link LimboScytheItem}. (It is crafted rather than dropped, so there is no drop hook here.)
 */
// GAME bus: onReap is a gameplay event. (1.21 throws for an @EventBusSubscriber class with no handler at all.)
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
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
}
