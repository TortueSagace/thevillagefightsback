package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The opening move on the hostile difficulties: no bell in your hand, a village already watching you.
 *
 * <p>On Hostile and Nightmare you are an Enemy of the Village from the first morning, and handing an Enemy the
 * bell that FOUNDS a village reads backwards. Instead a brain node is placed for you, close enough to find on
 * the first day and far enough that you do not spawn inside its garrison.
 *
 * <p>TIMING is the whole difficulty here. On a brand-new world the player is ALREADY IN IT when the difficulty
 * screen appears, so at login the answer does not exist yet and the config still reads Normal. Hooking this to
 * login alone meant a Nightmare world handed out a bell and placed nothing, which is the exact opposite of the
 * intent. So it runs from BOTH ends: from the difficulty answer, which is the fresh-world path, and from login,
 * which is every session after that. The once-per-player flag makes the overlap harmless.
 *
 * <p>Because the bell has usually already been given by the time the answer arrives, this also takes it back.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HostileStart {
    private HostileStart() {}

    /** Once per player: a second node on every relog would carpet the spawn in villages. */
    private static final String NBT_NODE_PLACED = "vgun_hostile_start_node";

    private static final int MIN_DIST = 18;
    private static final int MAX_DIST = 22;
    /** How many angles to try around the ring before giving up for this login. */
    private static final int TRIES = 24;

    /** Every session after the first: the difficulty is already known by the time the player logs in. */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) apply(player);
    }

    /**
     * Give this player the hostile opening, once: take back the starter bell and place their brain node.
     *
     * <p>Called from the difficulty answer AND from login, because on a fresh world only the first exists and
     * on every later session only the second does.
     */
    public static void apply(ServerPlayer player) {
        // Not while the creation screens are still open. At login on a brand-new world the difficulty is
        // whatever the LAST world left in the config, and acting on it here is what put a brain node and no
        // bell into a Peaceful world. The answer calls this again a moment later, with the real value.
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server != null) {
            WorldModeData data = WorldModeData.getExisting(server);
            if (data != null && data.isSetupPending()) return;
        }
        if (!Difficulty.current().placesBrainNodeAtSpawn()) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;

        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(NBT_NODE_PLACED)) return;
        persisted.putBoolean(NBT_NODE_PLACED, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);

        takeBackStarterBell(player);

        BlockPos spot = findRingSpot(level, player.blockPosition());
        if (spot == null) {
            // Nowhere usable in the ring: leave the flag SET regardless. Retrying on every login would mean a
            // player who spawned on an ocean gets a node the first time they log in somewhere buildable, which
            // could be a thousand blocks from the spawn this was meant to be about.
            TheVillageFightsBack.LOGGER.info(
                    "[The Village Fights Back] no usable brain node spot {}-{} blocks from {}'s spawn; none placed.",
                    MIN_DIST, MAX_DIST, player.getGameProfile().getName());
            return;
        }
        level.setBlock(spot, ModBlocks.VILLAGE_BRAIN_NODE.get().defaultBlockState(), 3); // onPlace tracks it
        TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] {} difficulty: brain node placed at {} for {}.",
                Difficulty.current().title(), spot, player.getGameProfile().getName());
    }

    /**
     * Remove the starter bell the login handout already gave, since the answer arrives after it.
     *
     * <p>Only ONE, and only a bell. On a world this new the single bell in the inventory is certainly the one
     * that was just handed over, but taking every bell would also confiscate any the player had legitimately
     * carried in from a previous life on a server.
     */
    private static void takeBackStarterBell(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack st = player.getInventory().getItem(i);
            if (st.is(net.minecraft.world.item.Items.BELL)) {
                st.shrink(1);
                return;
            }
        }
    }

    /**
     * A spot on the {@value #MIN_DIST}-{@value #MAX_DIST} ring around {@code around} that a node can stand on
     * and summon from.
     *
     * <p>Angles are tried in a shuffled order rather than swept, so two worlds with the same spawn do not put
     * the node in the same compass direction. The spot itself is vetted by the same
     * {@code BrainNodeEvents.isPlaceableNodeSpot} the village uses when it settles a node of its own, so a
     * hostile start cannot produce a node wedged in a cave roof or floating over water that the ordinary
     * placement rules would have rejected.
     */
    private static BlockPos findRingSpot(ServerLevel level, BlockPos around) {
        java.util.List<Integer> angles = new java.util.ArrayList<>();
        for (int i = 0; i < TRIES; i++) angles.add(i * (360 / TRIES));
        java.util.Collections.shuffle(angles, new java.util.Random(level.getSeed() ^ around.asLong()));

        for (int deg : angles) {
            double rad = Math.toRadians(deg);
            int dist = MIN_DIST + level.random.nextInt(MAX_DIST - MIN_DIST + 1);
            int x = around.getX() + (int) Math.round(Math.cos(rad) * dist);
            int z = around.getZ() + (int) Math.round(Math.sin(rad) * dist);
            BlockPos surface = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));
            BlockPos candidate = BrainNodeEvents.placeableNodeSpot(level, surface);
            if (candidate != null) return candidate;
        }
        return null;
    }
}
