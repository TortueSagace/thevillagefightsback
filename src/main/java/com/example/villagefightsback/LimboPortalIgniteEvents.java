package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lights Limbo portals: right-clicking a {@link ModBlocks#FAKE_OBSIDIAN} frame with flint &amp; steel (or a fire
 * charge) tries to build a green Limbo portal in the enclosed opening ({@link LimboPortalShape}). On success
 * the flame is consumed but no fire is placed -- the portal blocks fill the frame directly.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboPortalIgniteEvents {
    private LimboPortalIgniteEvents() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        boolean igniter = held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE);
        if (!igniter) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(ModBlocks.FAKE_OBSIDIAN.get())) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) {
            event.setCanceled(true); // keep client + server in lockstep; the server does the work
            return;
        }

        BlockPos firePos = event.getPos().relative(event.getFace());
        if (!LimboPortalShape.tryLight(sl, firePos)) return; // not a valid frame -> let vanilla try (fire, etc.)

        event.setCanceled(true); // built a portal -> don't also place fire
        sl.playSound(null, firePos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        player.swing(event.getHand());
        if (held.is(Items.FLINT_AND_STEEL)) {
            held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(event.getHand() == InteractionHand.MAIN_HAND
                    ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                    : net.minecraft.world.entity.EquipmentSlot.OFFHAND));
        } else if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
    }
}
