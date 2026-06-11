package com.example.villagergunners;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Runs the Limbo "holding cell" rules:
 *  - Every creature teleported INTO Limbo gets a {@link LimboGravelBlock} marker dropped where it stood, and
 *    is remembered ({@link LimboData}) until it leaves or dies.
 *  - Destroying a creature's gravel marker (by any means, including sleeping in the {@link LimboBedBlock})
 *    returns that creature to the marker's exact spot.
 *  - A creature that dies in Limbo has its marker instantly destroyed (no return).
 *  - If Limbo ever holds more than {@value #MAX_CREATURES} creatures, the most ancient are released until the
 *    count is back to the limit.
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboManager {
    private LimboManager() {}

    public static final int MAX_CREATURES = 100;
    private static final int TICK_INTERVAL = 20;     // register/cap upkeep ~1/s
    private static final int PENDING_STALE_TICKS = 200;
    // Generous box around the cube (cube is y 60..81, x/z -11..10) -- everything in Limbo is in here.
    private static final AABB SCAN = new AABB(-48, 40, -48, 48, 100, 48);

    /** Pre-teleport origin (dimension + block) captured the instant a creature heads to Limbo. */
    private record PendingOrigin(ResourceKey<Level> dim, BlockPos pos, long tick) {}
    private static final Map<UUID, PendingOrigin> PENDING = new ConcurrentHashMap<>();
    private static final Set<UUID> LIMBO_PLAYERS = ConcurrentHashMap.newKeySet(); // players currently in Limbo

    // --- capture where a MOB came from, just before it enters Limbo (covers changeDimension/sendToLimbo) ---
    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!LimboDimension.LIMBO.equals(event.getDimension())) return;
        Entity e = event.getEntity();
        if (e.level().isClientSide() || !(e instanceof LivingEntity)) return;
        if (LimboDimension.LIMBO.equals(e.level().dimension())) return; // already in Limbo
        PENDING.put(e.getUUID(), new PendingOrigin(e.level().dimension(), e.blockPosition(), e.level().getGameTime()));
    }

    /**
     * Players reach Limbo via teleportTo (command / sendToLimbo), which fires NO travel event -- so track them
     * here: remember each player's position while it is OUTSIDE Limbo (its return point), and greet it on entry.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        UUID id = sp.getUUID();
        if (LimboDimension.LIMBO.equals(sp.level().dimension())) {
            if (LIMBO_PLAYERS.add(id)) { // first tick inside Limbo
                sp.sendSystemMessage(Component.literal("Get out. Now.").withStyle(ChatFormatting.DARK_RED));
            }
        } else {
            LIMBO_PLAYERS.remove(id);
            PENDING.put(id, new PendingOrigin(sp.level().dimension(), sp.blockPosition(), sp.level().getGameTime()));
        }
    }

    // --- a creature that dies IN Limbo: instantly destroy its marker, no return ---
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel sl)) return;
        if (!LimboDimension.LIMBO.equals(sl.dimension())) return;
        MinecraftServer server = sl.getServer();
        LimboData.Entry entry = LimboData.get(server).removeByCreature(event.getEntity().getUUID());
        if (entry == null) return;
        ServerLevel origin = server.getLevel(entry.originDim);
        if (origin != null) origin.removeBlock(entry.gravelPos, false); // entry already gone -> onRemove won't return
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % TICK_INTERVAL != 0) return;
        ServerLevel limbo = server.getLevel(LimboDimension.LIMBO);
        if (limbo == null) return;
        LimboData data = LimboData.get(server);
        long now = limbo.getGameTime();

        // 1. Register newly-arrived tracked creatures: drop their gravel marker where they came from.
        for (LivingEntity e : limbo.getEntitiesOfClass(LivingEntity.class, SCAN, LivingEntity::isAlive)) {
            UUID id = e.getUUID();
            if (data.getByCreature(id) != null) continue;
            PendingOrigin po = PENDING.remove(id);
            if (po == null) continue; // wasn't tracked-in via a dimension change (e.g. /summon) -> ignore
            BlockPos gravelPos = placeGravel(server, po.dim(), po.pos(), data);
            data.add(new LimboData.Entry(id, po.dim(), gravelPos, now));
        }

        // 2. Forget creatures that left Limbo some other way; clear their (now-meaningless) marker.
        for (LimboData.Entry entry : data.entries()) {
            if (limbo.getEntity(entry.creature) == null) {
                data.removeByCreature(entry.creature);
                ServerLevel origin = server.getLevel(entry.originDim);
                if (origin != null && origin.getBlockState(entry.gravelPos).is(ModBlocks.LIMBO_GRAVEL.get())) {
                    origin.removeBlock(entry.gravelPos, false);
                }
            }
        }

        // 3. Over-population: release the most ancient creatures until back at the limit.
        if (data.size() > MAX_CREATURES) {
            List<LimboData.Entry> all = data.entries();
            all.sort(Comparator.comparingLong(en -> en.entryTime));
            int excess = data.size() - MAX_CREATURES;
            for (int i = 0; i < excess && i < all.size(); i++) {
                LimboData.Entry old = all.get(i);
                ServerLevel origin = server.getLevel(old.originDim);
                if (origin != null) origin.removeBlock(old.gravelPos, false); // -> onRemove -> return
                else data.removeByCreature(old.creature);                     // origin unloaded/gone -> just forget
            }
        }

        // 4. Drop stale pending-origins (a travel that never completed).
        PENDING.values().removeIf(po -> now - po.tick() > PENDING_STALE_TICKS);
    }

    /** Place a gravel marker at (or just above) the creature's origin position; returns the actual spot used. */
    private static BlockPos placeGravel(MinecraftServer server, ResourceKey<Level> dim, BlockPos pos, LimboData data) {
        ServerLevel origin = server.getLevel(dim);
        if (origin == null) return pos;
        BlockPos p = pos;
        int guard = 0;
        while (data.hasGravelAt(dim, p) && guard++ < 8) p = p.above(); // don't stack two markers on one block
        origin.setBlock(p, ModBlocks.LIMBO_GRAVEL.get().defaultBlockState(), 3);
        return p;
    }

    /** Called from {@link LimboGravelBlock#onRemove}: a marker vanished -> return its creature here. */
    public static void onGravelRemoved(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        LimboData.Entry entry = LimboData.get(server).removeByGravel(level.dimension(), pos);
        if (entry != null) teleportOut(server, entry);
    }

    /** Sleeping in the Limbo bed = destroy your own marker = return. */
    public static void requestReturn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        LimboData data = LimboData.get(server);
        LimboData.Entry entry = data.getByCreature(player.getUUID());
        if (entry == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("There is no way back from here.")
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED));
            return;
        }
        ServerLevel origin = server.getLevel(entry.originDim);
        boolean removed = origin != null
                && origin.getBlockState(entry.gravelPos).is(ModBlocks.LIMBO_GRAVEL.get())
                && origin.removeBlock(entry.gravelPos, false); // -> onRemove -> teleportOut
        if (!removed) { // marker already gone -> return directly
            data.removeByCreature(player.getUUID());
            teleportOut(server, entry);
        }
    }

    /** Move the entry's creature out of Limbo, back to its marker's exact location. */
    private static void teleportOut(MinecraftServer server, LimboData.Entry entry) {
        ServerLevel limbo = server.getLevel(LimboDimension.LIMBO);
        ServerLevel dest = server.getLevel(entry.originDim);
        if (limbo == null || dest == null) return;
        Entity e = limbo.getEntity(entry.creature);
        if (e == null || !e.isAlive()) return;
        double x = entry.gravelPos.getX() + 0.5, y = entry.gravelPos.getY(), z = entry.gravelPos.getZ() + 0.5;
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(dest, x, y, z, sp.getYRot(), sp.getXRot());
        } else {
            e.changeDimension(dest, new ITeleporter() {
                @Override
                public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld,
                                          float yaw, Function<Boolean, Entity> repositionEntity) {
                    Entity moved = repositionEntity.apply(false);
                    moved.moveTo(x, y, z, moved.getYRot(), moved.getXRot());
                    return moved;
                }
            });
        }
    }
}
