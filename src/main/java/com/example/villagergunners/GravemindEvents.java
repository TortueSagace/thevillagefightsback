package com.example.villagergunners;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Drives the Village Gravemind: spawns one at the centre of each loaded village, grants sunset emerald
 * income, and keeps every gravemind's visual level in sync with the global {@link GravemindData}.
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GravemindEvents {
    private GravemindEvents() {}

    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");
    private static final int INCOME_RADIUS = 100;     // count defenders within this of a gravemind
    private static final int VILLAGE_SCAN_RADIUS = 64; // look for village centres this far from players
    private static final int TICK_INTERVAL = 40;       // run all of this every 2 seconds
    private static final long SUNSET_TIME = 12000L;     // day-time at dusk

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % TICK_INTERVAL != 0) return;

        GravemindData data = GravemindData.get(server);
        spawnInVillages(server, data);
        handleSunsetIncome(server, data);
        syncVisuals(server, data);
    }

    /** Place a gravemind at the centre (meeting point / bell) of each loaded village, once. */
    private static void spawnInVillages(MinecraftServer server, GravemindData data) {
        for (ServerLevel level : server.getAllLevels()) {
            PoiManager poi = level.getPoiManager();
            for (ServerPlayer player : level.players()) {
                poi.getInSquare(holder -> holder.is(PoiTypes.MEETING), player.blockPosition(),
                                VILLAGE_SCAN_RADIUS, PoiManager.Occupancy.ANY)
                        .forEach(record -> tryPlaceGravemind(level, data, record.getPos()));
            }
        }
    }

    private static void tryPlaceGravemind(ServerLevel level, GravemindData data, BlockPos center) {
        GlobalPos key = GlobalPos.of(level.dimension(), center.immutable());
        if (data.isServiced(key)) return;
        // Place on the surface directly above the village centre column.
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center);
        if (!level.getBlockState(surface).canBeReplaced()) return;
        if (!level.getBlockState(surface.below()).isFaceSturdy(level, surface.below(), Direction.UP)) return;
        level.setBlock(surface, ModBlocks.VILLAGE_GRAVEMIND.get().defaultBlockState(), 3); // onPlace tracks it
        data.addServiced(key);
    }

    /** Once per day at sunset: +1 emerald per villager/guard alive within {@value #INCOME_RADIUS} blocks. */
    private static void handleSunsetIncome(MinecraftServer server, GravemindData data) {
        long dayTime = server.overworld().getDayTime();
        long day = Math.floorDiv(dayTime, 24000L);
        long timeOfDay = Math.floorMod(dayTime, 24000L);
        if (timeOfDay < SUNSET_TIME || day == data.getLastIncomeDay()) return;

        data.setLastIncomeDay(day);
        int income = countDefendersNearGraveminds(server, data);
        if (income <= 0) return;
        int gained = data.addEmeralds(income);
        broadcast(server, "§7The Village Gravemind drew §a" + income + "§7 emeralds from its defenders at sunset.");
        if (gained > 0) {
            broadcast(server, "§6The village evolved to level §e" + data.getLevel()
                    + (data.isMaxLevel() ? " §6(max)!" : "§6!"));
        }
    }

    private static int countDefendersNearGraveminds(MinecraftServer server, GravemindData data) {
        Set<UUID> counted = new HashSet<>();
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            AABB box = new AABB(gp.pos()).inflate(INCOME_RADIUS);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                    x -> x.isAlive() && isDefender(x))) {
                counted.add(e.getUUID());
            }
        }
        return counted.size();
    }

    /** Keep loaded gravemind blocks showing the current level; prune positions whose block is gone. */
    private static void syncVisuals(MinecraftServer server, GravemindData data) {
        int target = VillageGravemindBlock.chargesForLevel(data.getLevel());
        List<GlobalPos> stale = new ArrayList<>();
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            BlockState state = level.getBlockState(gp.pos());
            if (state.getBlock() instanceof VillageGravemindBlock) {
                if (state.getValue(VillageGravemindBlock.CHARGES) != target) {
                    level.setBlock(gp.pos(), state.setValue(VillageGravemindBlock.CHARGES, target), 3);
                }
            } else {
                stale.add(gp);
            }
        }
        for (GlobalPos gp : stale) data.removeBlock(gp);
    }

    private static boolean isDefender(Entity e) {
        return e instanceof Villager || GUARD_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
    }

    private static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }
}
