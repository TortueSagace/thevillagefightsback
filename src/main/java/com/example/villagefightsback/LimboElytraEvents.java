package com.example.villagefightsback;

import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Makes the Limbo Elytra Chestplate glide.
 *
 * <p>Vanilla only lets {@code minecraft:elytra} fly, and only damages that. Forge asks the item; Fabric asks
 * these two events, so both halves of the Forge hooks live here: permission to glide, and the durability cost
 * of one point per second of flight, which mirrors what vanilla charges a real elytra.
 */
public final class LimboElytraEvents {
    private LimboElytraEvents() {}

    public static void register() {
        EntityElytraEvents.ALLOW.register(LimboElytraEvents::wearingLimboElytra);

        EntityElytraEvents.CUSTOM.register((entity, tickElytra) -> {
            if (!wearingLimboElytra(entity)) return false;
            if (tickElytra && !entity.level().isClientSide()) {
                ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
                if ((entity.getFallFlyingTicks() + 1) % 20 == 0) {
                    chest.hurtAndBreak(1, entity, e -> e.broadcastBreakEvent(EquipmentSlot.CHEST));
                }
            }
            return true; // handled: vanilla must not also charge its own elytra durability
        });
    }

    private static boolean wearingLimboElytra(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof LimboElytraChestplateItem;
    }
}
