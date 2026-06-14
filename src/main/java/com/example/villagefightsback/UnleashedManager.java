package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.example.villagefightsback.network.ModNetwork;
import com.example.villagefightsback.network.ScreenShakePacket;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Lifecycle + faction rules for the unleashed brainNode boss ({@link UnleashedBrainNodeEntity}) and its
 * vindictive guards ({@link VindictiveGuardEntity}):
 *  - detects a village's villager count FALLING to 0 (it was &gt;0 before) and kills the brain node block,
 *    which (being a non-silk removal) summons the boss;
 *  - {@link #summonBoss} emerges the boss with a screen shake + purple "Prepare..." title;
 *  - infected faction never harm/target each other; they absorb 90% of explosion damage;
 *  - a creature slain by a DIRECT boss hit becomes a vindictive guard instead of dying.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UnleashedManager {
    private UnleashedManager() {}

    private static final double VILLAGER_RADIUS = 64.0;
    private static final double SHAKE_RADIUS = 48.0;

    /** brainNode -> last villager count seen (transient: edge detection only). */
    private static final Map<GlobalPos, Integer> LAST_COUNT = new HashMap<>();

    // --- trigger: villager count falls from >0 to 0 ---------------------------------------------
    // HIGH priority so this runs BEFORE BrainNodeEvents.onServerTick (NORMAL) on the same END tick: a village
    // losing its last villager removes the brain node and summons the boss FIRST, so the brain-node auto-placer
    // (which scans the same tick) already sees the boss and won't race a duplicate brain node into existence.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 40 != 0) return;
        long _p = ModProfiler.now();
        BrainNodeData data = BrainNodeData.get(server);
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue; // only when loaded (else a false 0)
            int count = level.getEntitiesOfClass(Villager.class, new AABB(gp.pos()).inflate(VILLAGER_RADIUS),
                    v -> v.isAlive() && !(v instanceof EmeraldgerEntity)).size();
            Integer prev = LAST_COUNT.get(gp);
            if (prev != null && prev > 0 && count == 0) {
                // The last villager is gone -> the brain node dies. Removing the block (a non-silk removal)
                // makes VillageBrainNodeBlock#onRemove summon the boss.
                level.removeBlock(gp.pos(), false);
            }
            LAST_COUNT.put(gp, count);
        }
        ModProfiler.add("boss.manager", _p);
    }

    // --- the summon -----------------------------------------------------------------------------
    /** Emerge the boss at {@code pos} with the dramatic intro. Called from VillageBrainNodeBlock#onRemove. */
    public static void summonBoss(ServerLevel sl, BlockPos pos) {
        UnleashedBrainNodeEntity boss = ModEntities.UNLEASHED_BRAIN_NODE.get().create(sl);
        if (boss == null) return;
        boss.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, sl.random.nextFloat() * 360.0F, 0.0F);
        boss.finalizeSpawn(sl, sl.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null); // triggers emerge
        sl.addFreshEntity(boss);

        Component title = Component.literal("Prepare...").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > SHAKE_RADIUS * SHAKE_RADIUS) continue;
            ModNetwork.sendTo(p, new ScreenShakePacket(1.3F, 70));
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
        }
        sl.players().forEach(pl -> pl.displayClientMessage(Component.literal(
                "§5The Village Brain Node has been UNLEASHED — you failed to protect the village."), false));
    }

    /** Send a camera shake to every player near {@code center}. Used by the boss's slam attack, etc. */
    public static void shakeNearby(ServerLevel sl, Entity center, float intensity, int durationTicks) {
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(center.getX(), center.getY(), center.getZ()) <= SHAKE_RADIUS * SHAKE_RADIUS) {
                ModNetwork.sendTo(p, new ScreenShakePacket(intensity, durationTicks));
            }
        }
    }

    // --- faction rules --------------------------------------------------------------------------
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity self = event.getEntity();
        LivingEntity target = event.getNewTarget();
        if (target != null && Factions.isInfected(self) && Factions.isInfected(target)) {
            event.setCanceled(true); // boss <-> vindictive guards never target each other
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!Factions.isInfected(victim)) return;
        // no friendly fire within the infected faction
        Entity src = event.getSource().getEntity();
        if (src != null && Factions.isInfected(src)) { event.setCanceled(true); return; }
        // boss + vindictive guards absorb 90% of explosion damage
        if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)) event.setAmount(event.getAmount() * 0.1F);
    }

    // --- a creature slain by a DIRECT boss hit becomes a vindictive guard ------------------------
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof UnleashedBrainNodeEntity boss)) return;
        if (victim instanceof Player) return;        // players die normally (the boss doesn't recruit players)
        if (Factions.isInfected(victim)) return;     // never convert faction-mates
        if (!boss.canMakeGuard()) { boss.registerKill(); return; } // 20-guard cap full: victim dies, still counts
        event.setCanceled(true);                      // do not actually die -- transform instead
        boss.onDirectKill(victim);
        victim.discard();
    }
}
