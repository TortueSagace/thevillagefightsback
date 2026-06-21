package com.example.villagefightsback;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Soft population cap for the EMERALD faction ({@code emerald_mobs_max_number}) -- purely to limit lag. Tracks the
 * number of loaded NON-mite emerald units per dimension (mites are transient and exempt). When the cap is reached
 * and a NEW emerald unit would spawn (conversion, brain summon, death eruption, spawn egg), the spawn is cancelled
 * and that unit's max health is instead poured into the nearest non-mite emerald (which heals up). A conversion
 * victim therefore simply dies -- and its infested path, placed separately in {@link EmeraldConversion}, still
 * appears. Entities streamed in from disk are never capped (only genuinely new spawns are), so reloading a world
 * with many emeralds never deletes them.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmeraldPopulation {
    private EmeraldPopulation() {}

    private static final Map<ResourceKey<Level>, Integer> COUNT = new HashMap<>();
    private static final double ABSORB_RADIUS = 128.0;

    /** A unit that counts toward (and is bounded by) the cap: any emerald unit that is NOT a mite. */
    private static boolean isCapped(Entity e) {
        return Factions.isEmerald(e) && !(e instanceof EmeraldEndermite);
    }

    @SubscribeEvent(priority = EventPriority.LOW) // after the enable_mob_infection gate, which may cancel first
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level) || !isCapped(event.getEntity())) return;
        ResourceKey<Level> key = level.dimension();
        int count = COUNT.getOrDefault(key, 0);
        int max = Config.emeraldMobsMaxNumber();
        // Only NEW spawns are capped; entities loaded from disk are always admitted (and counted).
        if (max > 0 && count >= max && !event.loadedFromDisk()) {
            if (event.getEntity() instanceof LivingEntity wouldBe) absorbInto(level, wouldBe);
            event.setCanceled(true);
            return;
        }
        COUNT.put(key, count + 1);
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!isCapped(event.getEntity())) return;
        ResourceKey<Level> key = level.dimension();
        COUNT.put(key, Math.max(0, COUNT.getOrDefault(key, 0) - 1));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        COUNT.clear(); // reset so a fresh world load recounts from zero as chunks stream back in
    }

    /** Pour {@code wouldBe}'s max health into the nearest non-mite emerald unit (which heals up to its new max). */
    private static void absorbInto(ServerLevel level, LivingEntity wouldBe) {
        double addHp = wouldBe.getMaxHealth();
        AABB box = new AABB(wouldBe.blockPosition()).inflate(ABSORB_RADIUS);
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity c : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && isCapped(e))) {
            double d = c.distanceToSqr(wouldBe);
            if (d < best) { best = d; nearest = c; }
        }
        if (nearest == null) return;
        AttributeInstance attr = nearest.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(attr.getBaseValue() + addHp);
        nearest.setHealth((float) Math.min(nearest.getMaxHealth(), nearest.getHealth() + addHp));
    }
}
