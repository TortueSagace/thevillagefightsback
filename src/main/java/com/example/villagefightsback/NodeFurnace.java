package com.example.villagefightsback;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The village's own Obliteration Furnace: raised when General Darius musters, fuelled out of the brain node's
 * emeralds for as long as the village can afford it.
 *
 * <p>It is the third side of the Obliteration triangle, and it only makes sense with the other two. Darius
 * marks everything he strikes (see {@link GeneralDariusEntity#doHurtTarget}); a marked creature that dies is
 * struck off the spawn tables by the nearest BURNING furnace (see {@link ObliterationEvents}); and until now
 * the only furnace was one the player had built and fuelled themselves. So a village that fielded its champion
 * had no way to keep what he killed dead. Now it buys that with the same emeralds it buys everything else,
 * which puts a price on it and makes the village's reach something the player can starve.
 *
 * <p>ONE furnace per node, recorded in {@link BrainNodeData#getNodeFurnace}. Not "the nearest furnace", which
 * would have the village topping up a furnace the player built and paid for; and not one per Darius, which
 * would leave a field of them behind a village that has lost a few champions.
 *
 * <p>Config-gated on {@code darius_inflicts_obliteration}. With the mark switched off nothing the village kills
 * would ever be struck off, so a furnace would burn the village's emeralds for no effect at all: the node does
 * not raise one, and stops paying for the one it has.
 */
public final class NodeFurnace {
    private NodeFurnace() {}

    /**
     * How far from the node the furnace may be set down.
     *
     * <p>At least three cells out, so it is clear of the eight the node summons into and of the ring of guards
     * that usually stands there. Near enough to read as the village's, rather than as something that happened
     * to be lying in a field.
     */
    private static final int MIN_AWAY = 3;

    /** Top up once the furnace has under a minute left, so it never actually goes dark while the node can pay. */
    private static final int LOW_WATER = ObliterationFurnaceBlock.TICKS_PER_EMERALD;

    /**
     * The soonest the node may put another emerald in: twenty seconds.
     *
     * <p>Both halves of the rule are needed, and neither is enough alone. {@link #LOW_WATER} alone tops up on
     * EVERY pass once the furnace dips under a minute, which is an emerald every brain tick -- a village's whole
     * treasury into one furnace in seconds. This cooldown alone would buy a minute of fuel every twenty seconds,
     * three times faster than it burns, and the furnace would climb to a fuel level nothing could ever spend.
     *
     * <p>Together they settle exactly where the feature is supposed to sit: the node buys when the flame is
     * inside its last minute and it has not bought recently, which works out at one emerald a minute -- the
     * price the tooltip and the config both quote.
     */
    private static final int FUEL_COOLDOWN_TICKS = 20 * 20;

    /** Node -> the game time it last paid. In memory only; a reload simply lets the next pass buy at once. */
    private static final java.util.Map<GlobalPos, Long> LAST_FUEL = new java.util.HashMap<>();

    /**
     * Raise this node's furnace, if it is due one. Called when General Darius musters.
     *
     * <p>Silent about failure on purpose: no room right now is not an error, and the reflex that spawns Darius
     * runs again. The village gets its furnace the next time there is somewhere to put it.
     */
    public static void ensureFor(ServerLevel level, BrainNodeData data, GlobalPos node, BlockPos brain) {
        if (!Config.dariusInflictsObliteration()) return;
        if (data.getNodeFurnace(node) != null) return;              // it already has one: only ever one at a time

        BlockPos spot = BrainNodeEvents.placeableAsideSpot(level, brain, MIN_AWAY);
        if (spot == null) return;

        BlockState state = ModBlocks.OBLITERATION_FURNACE.get().defaultBlockState()
                .setValue(ObliterationFurnaceBlock.FACING, facingFrom(spot, brain));
        level.setBlock(spot, state, 3);
        data.setNodeFurnace(node, GlobalPos.of(level.dimension(), spot.immutable()));

        // Light it now rather than waiting for the upkeep pass, so Darius never walks out to a cold furnace. No
        // cooldown stands in the way of this one: the node has never paid for this furnace before.
        LAST_FUEL.remove(node);
        fuel(level.getServer(), data, node, GlobalPos.of(level.dimension(), spot.immutable()),
                level.getGameTime());

        level.playSound(null, spot, net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_SET_SPAWN,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.4F);
        // Told, not discovered. The furnace changes what dying near this village MEANS, and a rule that
        // silently starts applying is one the player can only learn by losing something to it.
        level.getServer().getPlayerList().broadcastSystemMessage(
                net.minecraft.network.chat.Component.literal("An Obliteration Furnace appeared...")
                        .withStyle(net.minecraft.ChatFormatting.RED), false);
    }

    /** Face the furnace toward the node, so it reads as the village's rather than as dropped at random. */
    private static Direction facingFrom(BlockPos furnace, BlockPos brain) {
        int dx = brain.getX() - furnace.getX();
        int dz = brain.getZ() - furnace.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Keep every linked furnace burning, one emerald at a time.
     *
     * <p>Driven from the brain node's existing server tick, so it runs on the same throttle as the rest of the
     * village's housekeeping rather than needing a clock of its own.
     */
    public static void tick(MinecraftServer server, BrainNodeData data) {
        // Switched off mid-world: stop paying, and leave the block standing for whoever wants to fuel it by hand.
        if (!Config.dariusInflictsObliteration() || !data.hasNodeFurnaces()) return;

        for (java.util.Map.Entry<GlobalPos, GlobalPos> link : data.nodeFurnaces()) {
            GlobalPos node = link.getKey();
            GlobalPos furnace = link.getValue();
            ServerLevel level = server.getLevel(furnace.dimension());
            if (level == null || !level.isLoaded(furnace.pos())) continue;   // out of sight: judge it when it is back
            if (!level.getBlockState(furnace.pos()).is(ModBlocks.OBLITERATION_FURNACE.get())) {
                data.removeNodeFurnace(node);      // mined, blown up, or replaced: the village stops funding a hole
                LAST_FUEL.remove(node);
                continue;
            }
            fuel(server, data, node, furnace, level.getGameTime());
        }
    }

    /**
     * Buy one more minute, if the village can spare it.
     *
     * <p>The stopping rule is the whole design of the feature: one emerald is one minute, and the node spends
     * only while it has one to spend. {@code addEmeralds(-1)} devolves a village whose balance goes BELOW zero,
     * so refusing at zero is exactly "as long as it does not cost the village stage 4". A village that runs dry
     * lets its furnace go dark and keeps its stage; the bans lapse with the flame, and the next day's income
     * lights it again.
     */
    private static void fuel(MinecraftServer server, BrainNodeData data, GlobalPos node, GlobalPos furnace,
                             long now) {
        BrainNodeData.Econ econ = data.econ(node);
        if (econ.getLevel() < BrainNodeData.MAX_LEVEL) return;      // stage 4 only: this is Darius's furnace
        Long last = LAST_FUEL.get(node);
        // The >= 0 guard is for a clock that has gone backwards: this map outlives a single-player world, so a
        // later world with a younger game time would otherwise look like a purchase far in the future and lock
        // the node out of paying at all.
        if (last != null && now - last >= 0 && now - last < FUEL_COOLDOWN_TICKS) return;
        ObliterationData od = ObliterationData.get(server);
        if (od.burnTicks(furnace) >= LOW_WATER) return;             // still burning: nothing to buy yet
        if (econ.getEmeralds() < 1) return;                         // one more would cost the village a stage
        econ.addEmeralds(-1);
        od.addFuel(furnace, ObliterationFurnaceBlock.TICKS_PER_EMERALD);
        LAST_FUEL.put(node, now);
    }
}
