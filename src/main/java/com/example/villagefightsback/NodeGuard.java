package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Try to mine the brain node and the village throws you off it. Every difficulty, Peaceful included.
 *
 * <p>No damage, no hostility, no grudge, just a shove: you are thrown UP and AWAY, and whatever the landing
 * costs you is the landing's doing, not the village's. It started as Peaceful's ONLY defence, on the reasoning
 * that every other difficulty answers a pickaxe on the node with defenders and did not need help. That was
 * true and beside the point -- the node is the village's heart on every difficulty, and a village that stands
 * there and watches its heart be dug out reads as furniture wherever it happens.
 *
 * <p>It does NOT stop the mining. It used to, on Peaceful, and that made the node simply unbreakable there;
 * applied everywhere it would have taken the Unleashed Brain Node with it, since summoning that boss means
 * breaking the node. So the swing lands, the progress counts, and the shove is what you have to fight through
 * rather than a wall you cannot pass: get thrown, walk back, swing again. Peaceful loses its invulnerable node
 * in the trade, which is the honest outcome -- Peaceful means the village will not hurt you, not that it owns
 * an indestructible block.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NodeGuard {
    private NodeGuard() {}

    /** Roughly four blocks of rise. Vanilla's jump is 0.42 for ~1.25 blocks, and height goes as the square. */
    private static final double LAUNCH_UP = 0.78;
    /** Roughly four blocks of travel away from the node, after drag. */
    private static final double LAUNCH_OUT = 0.62;

    /** A held left-click fires this event every tick; one shove per second is a shove rather than a machine gun. */
    private static final int COOLDOWN_TICKS = 20;

    private static final java.util.Map<java.util.UUID, Long> LAST = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onMineNode(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;                                 // creative breaks it instantly anyway
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(ModBlocks.VILLAGE_BRAIN_NODE.get())) return;

        // The event is NOT cancelled: see the class note. The swing lands and the node can be broken.
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
        // 1.21 DOES ship one, but this branch deliberately leaves the mod's wind charge on SNOWBALL_THROW (see
        // WindChargeItem / WindChargeEntity here), so the node's shove stays matched to it rather than becoming
        // the one place in the mod that sounds different.
        level.playSound(null, pos, SoundEvents.SNOWBALL_THROW, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.5, player.getZ(), 18, 0.3, 0.2, 0.3, 0.05);
    }
}
