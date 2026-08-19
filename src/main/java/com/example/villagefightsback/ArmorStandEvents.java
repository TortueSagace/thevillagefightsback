package com.example.villagefightsback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import com.example.villagefightsback.platform.event.PlayerInteractEvent;
import com.example.villagefightsback.platform.SubscribeEvent;

/**
 * Decoration nicety: a vanilla armor stand HAS no arms, so a weapon you hand it goes into its main-hand slot
 * but never renders (arms are what draw held items). When a player gives a weapon to an ARMLESS stand, we just
 * flip its {@code ShowArms} on -- BEFORE vanilla's interactAt runs -- so the weapon actually shows; vanilla
 * still does the item placement and inventory handling. Armless stands stay armless for armor / display use;
 * only handing one a weapon grows the arms. Purely cosmetic.
 */
public final class ArmorStandEvents {
    private ArmorStandEvents() {}

    @SubscribeEvent
    public static void onGiveWeapon(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getTarget() instanceof ArmorStand stand) || stand.isShowArms()) return;
        if (!isWeapon(event.getEntity().getItemInHand(event.getHand()))) return;
        // Grow arms via a save/load round-trip (setShowArms is private). We do NOT cancel the event: vanilla's
        // ArmorStand#interactAt then drops the weapon into the now-visible main hand and updates the inventory.
        CompoundTag tag = new CompoundTag();
        stand.addAdditionalSaveData(tag);
        tag.putBoolean("ShowArms", true);
        stand.readAdditionalSaveData(tag);
    }

    /** A hand weapon (so we never grow arms for armor/blocks that vanilla equips or ignores). */
    private static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var it = stack.getItem();
        if (it instanceof ProjectileWeaponItem || it instanceof TridentItem         // bow / crossbow / trident
                || it instanceof RocketLauncherItem || it instanceof MaceItem || it instanceof FireGrenadeItem) {
            return true;
        }
        // swords, axes (incl. the Alien Slasher/Eradicator) and any item that adds melee attack damage in hand.
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE);
    }
}
