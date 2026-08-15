package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Peaceful's one and only defence: try to mine the brain node and the village throws you off it.
 *
 * <p>On Peaceful nothing in the village will raise a hand to you -- that is the whole difficulty. But a village
 * that cannot stop its own heart being dug out is not defended at all, it is furniture, so the node keeps this:
 * no damage, no hostility, no grudge, just a shove. You are thrown UP and AWAY, and whatever the landing costs
 * you is the landing's doing, not the village's.
 *
 * <p>Peaceful only. Every other difficulty answers a pickaxe on the node with defenders, which is a real answer
 * and does not need help.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PeacefulNodeGuard {
    private PeacefulNodeGuard() {}

    /** Roughly four blocks of rise. Vanilla's jump is 0.42 for ~1.25 blocks, and height goes as the square. */
    private static final double LAUNCH_UP = 0.78;
    /** Roughly four blocks of travel away from the node, after drag. */
    private static final double LAUNCH_OUT = 0.62;

    /** A held left-click fires this event every tick; one shove per second is a shove rather than a machine gun. */
    private static final int COOLDOWN_TICKS = 20;

    private static final java.util.Map<java.util.UUID, Long> LAST = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onMineNode(PlayerInteractEvent.LeftClickBlock event) {
        if (!Difficulty.current().forcesHero()) return;                  // Peaceful only
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;                                 // creative breaks it instantly anyway
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(ModBlocks.VILLAGE_BRAIN_NODE.get())) return;

        event.setCanceled(true);   // the swing does not land: no progress, no damage to the node

        long now = level.getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) return;
        LAST.put(player.getUUID(), now);

        // Straight out from the node in plan, so the shove always reads as coming FROM the thing you hit. A
        // player standing exactly on the node's column has no direction to be pushed in, so they just go up.
        Vec3 away = player.position().subtract(Vec3.atCenterOf(pos));
        double hl = Math.sqrt(away.x * away.x + away.z * away.z);
        double hx = hl > 1.0E-4 ? away.x / hl : 0.0;
        double hz = hl > 1.0E-4 ? away.z / hl : 0.0;

        player.setDeltaMovement(hx * LAUNCH_OUT, LAUNCH_UP, hz * LAUNCH_OUT);
        player.hasImpulse = true;
        player.hurtMarked = true;   // without this the server's velocity change never reaches the client

        // The mod's own wind charge uses SNOWBALL_THROW; 1.20.1 has no wind-charge sound of its own.
        level.playSound(null, pos, SoundEvents.SNOWBALL_THROW, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.5, player.getZ(), 18, 0.3, 0.2, 0.3, 0.05);
    }
}
