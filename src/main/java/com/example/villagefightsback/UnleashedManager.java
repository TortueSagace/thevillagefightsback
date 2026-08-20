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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Lifecycle + faction rules for the unleashed brainNode boss ({@link UnleashedBrainNodeEntity}) and its
 * vindictive guards ({@link VindictiveGuardEntity}):
 *  - detects a village's villager count FALLING to 0 (it was &gt;0 before) and kills the brain node block,
 *    which (being a non-silk removal) summons the boss;
 *  - {@link #summonBoss} emerges the boss with a screen shake + purple "Prepare..." title;
 *  - infected faction never harm/target each other; they absorb 90% of explosion damage;
 *  - a creature slain by a DIRECT boss hit becomes a vindictive guard instead of dying.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class UnleashedManager {
    private UnleashedManager() {}

    private static final double VILLAGER_RADIUS = 64.0;
    private static final double SHAKE_RADIUS = 48.0;

    /** brainNode -> last villager count seen (transient: edge detection only). */
    private static final Map<GlobalPos, Integer> LAST_COUNT = new HashMap<>();

    /**
     * brainNode -> how many CONSECUTIVE samples have reported zero villagers.
     *
     * <p>A single zero reading does not mean the village is dead, and acting on one summoned the boss over a
     * village that was perfectly alive. Loading is the problem: the guard below only proves the node's OWN
     * block is loaded, while the villager scan reaches {@value #VILLAGER_RADIUS} blocks across nine chunks by
     * nine, and entities are restored a little after the chunk they live in. Teleport out of a village and
     * back and there is a window where the node is loaded, its villagers are not yet in the entity index, and
     * the count is a false zero.
     *
     * <p>So a village has to read empty {@value #ZERO_SAMPLES_REQUIRED} times running before it is declared
     * dead. At one sample every 40 ticks that is six seconds of genuine silence, far longer than any
     * chunk-load gap, and a real massacre stays at zero and still triggers.
     */
    private static final Map<GlobalPos, Integer> ZERO_STREAK = new HashMap<>();
    private static final int ZERO_SAMPLES_REQUIRED = 3;

    // --- trigger: villager count falls from >0 to 0 ---------------------------------------------
    // HIGH priority so this runs BEFORE BrainNodeEvents.onServerTick (NORMAL) on the same END tick: a village
    // losing its last villager removes the brain node and summons the boss FIRST, so the brain-node auto-placer
    // (which scans the same tick) already sees the boss and won't race a duplicate brain node into existence.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 40 != 0) return;
        long _p = ModProfiler.now();
        BrainNodeData data = BrainNodeData.get(server);
        for (GlobalPos gp : new ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) {
                ZERO_STREAK.remove(gp);   // an unload breaks the run: the streak must be consecutive LOADED samples
                continue;                 // only count when loaded (else a false 0)
            }
            int count = level.getEntitiesOfClass(Villager.class, new AABB(gp.pos()).inflate(VILLAGER_RADIUS),
                    v -> v.isAlive() && !(v instanceof EmeraldgerEntity)).size();
            Integer prev = LAST_COUNT.get(gp);
            if (count > 0) {
                ZERO_STREAK.remove(gp);
            } else if (prev != null && prev > 0) {
                // Empty, and it was not empty before. Do NOT act yet, and deliberately do NOT write the zero
                // into LAST_COUNT: leaving prev above zero is what lets the streak keep counting.
                if (ZERO_STREAK.merge(gp, 1, Integer::sum) < ZERO_SAMPLES_REQUIRED) continue;
                ZERO_STREAK.remove(gp);
                // The last villager really is gone -> the brain node dies. Removing the block (a non-silk
                // removal) makes VillageBrainNodeBlock#onRemove summon the boss.
                level.removeBlock(gp.pos(), false);
            }
            LAST_COUNT.put(gp, count);
        }
        ModProfiler.add("boss.manager", _p);
    }

    // --- the summon -----------------------------------------------------------------------------

    /**
     * A brain node has been destroyed. Decide what rises from it.
     *
     * <p>Every node death used to raise the boss, whatever the world was set to. That is the right answer for a
     * player who went looking for a fight and much too heavy for everyone else, so the boss is now reserved for
     * the two settings that ask for one -- Mega Brutal mode, or Nightmare difficulty. Either is enough.
     *
     * <p>Below that the village does not fight back, it MOVES: a harmless {@link VillageSpiritEntity} rises
     * instead and flies off to found the village elsewhere, and the dead node is left as a block of Debris to
     * mark what happened there.
     *
     * <p>{@code defeated_brain_node_spawn_spirit} keeps the meaning it always had: with it off, nothing rises
     * at all and a node is simply destroyed.
     */
    public static void onNodeDestroyed(ServerLevel sl, BlockPos pos) {
        if (!Config.spawnSpiritOnDefeat()) return;
        crumbleVillageNodes(sl, pos);   // whichever way it goes, the village's outbuildings die with it
        if (Config.megaBrutalMode() || Difficulty.current() == Difficulty.NIGHTMARE) {
            summonBoss(sl, pos);
            return;
        }
        leaveDeadNode(sl, pos);
    }

    /**
     * Every Golem Factory / Barracks / Lure Tower that belonged to this village becomes Debris.
     *
     * <p>Those blocks are the village's organs: each adds 10% to its daily harvest and each spawns its own
     * reinforcement while a brain is in range. With the brain dead they do nothing at all, and leaving a field
     * of intact, ancient-debris-tough node blocks around a corpse reads as loot rather than as a ruin. They are
     * turned to Debris, which is exactly what the block is for.
     *
     * <p>Membership is the same {@code INCOME_RADIUS} test the harvest uses, read off the tracked node set
     * rather than scanned out of the world, so this costs a walk over a short list. Each one is untracked as it
     * goes, so a node cannot be counted for a village that no longer exists.
     */
    private static void crumbleVillageNodes(ServerLevel sl, BlockPos brain) {
        BrainNodeData data = BrainNodeData.get(sl.getServer());
        double r2 = (double) BrainNodeEvents.INCOME_RADIUS * BrainNodeEvents.INCOME_RADIUS;
        int crumbled = 0;
        for (GlobalPos node : new java.util.ArrayList<>(data.nodes())) {
            if (!node.dimension().equals(sl.dimension())) continue;
            if (node.pos().distSqr(brain) > r2) continue;
            BlockPos at = node.pos();
            if (sl.isLoaded(at) && sl.getBlockState(at).getBlock() instanceof AbstractNodeBlock) {
                sl.setBlock(at, ModBlocks.DEBRIS.get().defaultBlockState(), 3);
                crumbled++;
            }
            data.removeNode(node);      // untrack it either way; its village is gone
        }
        if (crumbled > 0) {
            TheVillageFightsBack.LOGGER.info(
                    "[The Village Fights Back] the brain node at {} fell; {} village node(s) crumbled to debris.",
                    brain, crumbled);
        }
    }

    /**
     * The peaceful path. The village is NOT saved here -- it is left savable.
     *
     * <p>Rather than raise the spirit itself, the node leaves a cold husk behind and waits. The resurrection is
     * the player's to perform: feed the husk a golden apple and the spirit rises. That keeps a lost village a
     * thing that happened TO the player, and a recovered one a thing they chose to do, instead of the world
     * quietly repairing itself while they walk away.
     */
    private static void leaveDeadNode(ServerLevel sl, BlockPos pos) {
        // onRemove has already run for the node block, so writing here is safe and does not re-enter it.
        sl.setBlock(pos, ModBlocks.DEAD_BRAIN_NODE.get().defaultBlockState(), 3);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 24, 0.4, 0.6, 0.4, 0.01);
        sl.playSound(null, pos, net.minecraft.sounds.SoundEvents.SCULK_CATALYST_BREAK,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.2F, 0.6F);
        sl.players().forEach(pl -> pl.displayClientMessage(Component.literal(
                "§cThe Village Brain node at §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + "§c is defeated. You might still save something if you feed it a golden apple..."), false));
    }

    /**
     * Raise the scout that will carry this village somewhere new, and crumble the husk it came from.
     *
     * <p>Called from {@link DeadBrainNodeBlock} when a player feeds the husk a golden apple -- never
     * automatically.
     */
    /**
     * Raise the scout, and remember who paid for it.
     *
     * <p>{@code reviver} is the player who fed the husk its golden apple. They are credited only when the
     * spirit actually founds the new village, not here -- the apple buys an attempt, and the reward is for
     * the village standing again. The spirit carries the UUID so the credit survives its flight.
     */
    public static void spawnVillageSpirit(ServerLevel sl, BlockPos pos, net.minecraft.world.entity.player.Player reviver,
                                          boolean inPlace) {
        VillageSpiritEntity spirit = ModEntities.VILLAGE_SPIRIT.get().create(sl);
        if (spirit == null) return;
        spirit.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, sl.random.nextFloat() * 360.0F, 0.0F);
        spirit.setOrigin(pos);
        if (inPlace) spirit.setRestore(pos);       // a found village: it comes straight back to this block
        if (reviver != null) spirit.setReviver(reviver.getUUID());
        spirit.finalizeSpawn(sl, sl.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        sl.addFreshEntity(spirit);

        // The husk is spent -- unless the spirit is coming back to it. A found village's brain is
        // re-lit in place, so crumbling it here would destroy the very block the spirit is flying home to.
        if (!inPlace) sl.setBlock(pos, ModBlocks.DEBRIS.get().defaultBlockState(), 3);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.5, 0.7, 0.5, 0.02);
        sl.playSound(null, pos, net.minecraft.sounds.SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM,
                net.minecraft.sounds.SoundSource.NEUTRAL, 1.2F, 0.8F);
        sl.players().forEach(pl -> pl.displayClientMessage(Component.literal(
                inPlace
                ? "§bA Village Spirit rises from the dead brain, and circles back to wake it."
                : "§bA Village Spirit rises, and drifts away to look for a new home."), false));
    }

    /** Emerge the boss at {@code pos} with the dramatic intro. Called from VillageBrainNodeBlock#onRemove. */
    public static void summonBoss(ServerLevel sl, BlockPos pos) {
        if (!Config.spawnSpiritOnDefeat()) return; // boss + its emerge animation gated off by config
        UnleashedBrainNodeEntity boss = ModEntities.UNLEASHED_BRAIN_NODE.get().create(sl);
        if (boss == null) return;
        boss.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, sl.random.nextFloat() * 360.0F, 0.0F);
        boss.finalizeSpawn(sl, sl.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null); // triggers emerge
        sl.addFreshEntity(boss);

        Component title = Component.literal("Prepare...").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > SHAKE_RADIUS * SHAKE_RADIUS) continue;
            ModNetwork.sendTo(p, new ScreenShakePacket(1.3F, 70));
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
        }
        sl.players().forEach(pl -> pl.displayClientMessage(Component.literal(
                "§5A Vindictive Village Spirit has risen. You failed to protect the village."), false));
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
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && Factions.isInfected(self) && Factions.isInfected(target)) {
            event.setCanceled(true); // boss <-> vindictive guards never target each other
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
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
        if (Factions.isEmerald(victim)) return;      // emerald + vindictive spare each other -> not recruited
        if (Factions.isLimbo(victim)) return;        // Limbo raiders/phantoms die normally (not recruited)
        if (!boss.canMakeGuard()) { boss.registerKill(); return; } // 20-guard cap full: victim dies, still counts
        event.setCanceled(true);                      // do not actually die -- transform instead
        boss.onDirectKill(victim);
        victim.discard();
    }
}
