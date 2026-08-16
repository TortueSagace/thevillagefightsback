package com.example.villagefightsback.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;

/**
 * The Limbo Scythe's death-save animation: vanilla's totem-of-undying presentation, with the scythe in place of
 * the totem.
 *
 * <p>Copies what {@code ClientLevel#handleEntityEvent} case 35 does -- the swirling totem particles, the totem
 * sound, and the item that sweeps up the screen -- and changes only the item, since that is the one part of it
 * that says WHICH thing just saved you. Reusing the vanilla particle and sound keeps the moment instantly
 * readable as "you should be dead": players already know what that flash means.
 *
 * <p>Client-only, reached through DistExecutor from {@code ScytheSavePacket}, so the class is never loaded on a
 * dedicated server.
 *
 * <p>1.21 note: there is no DistExecutor hop any more. The packet is a {@code CustomPacketPayload} registered
 * with {@code playToClient}, so only a client ever holds a handler for it and this class is still never loaded
 * on a dedicated server -- the guarantee is now structural rather than a runtime guard. The client API this
 * uses is unchanged: {@code ParticleEngine#createTrackingEmitter} and {@code GameRenderer#displayItemActivation}
 * both survive into 1.21 with the same signatures.
 */
public final class ScytheSaveFx {
    private ScytheSaveFx() {}

    public static void play() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        mc.particleEngine.createTrackingEmitter(mc.player, ParticleTypes.TOTEM_OF_UNDYING, 30);
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                net.minecraft.sounds.SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.PLAYERS,
                1.0F, 1.0F, false);
        mc.gameRenderer.displayItemActivation(
                new ItemStack(com.example.villagefightsback.ModItems.LIMBO_SCYTHE.get()));
    }
}
