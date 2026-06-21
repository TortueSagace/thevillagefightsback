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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Soft population cap for the EMERALD faction ({@code emerald_mobs_max_number}) -- purely to limit lag. Tracks the
 * number of emerald units in LOADED CHUNKS per dimension (mites included -- they count too). When the cap is reached
 * and a NEW emerald unit would spawn automatically (conversion, brain summon, death eruption), the spawn is
 * cancelled and that unit's max health is instead poured into the nearest NON-mite emerald (which heals up). A
 * conversion victim therefore simply dies -- and its infested path, placed separately in {@link EmeraldConversion},
 * still appears. Manual spawns (spawn egg / {@code /summon}) check {@link #atCap} up front and tell the player
 * instead. Entities streamed in from disk are never capped, so reloading a populated world never deletes emeralds.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmeraldPopulation {
    private EmeraldPopulation() {}

    private static final Map<ResourceKey<Level>, Integer> COUNT = new HashMap<>();
    private static final double ABSORB_RADIUS = 128.0;

    /** Every emerald unit counts toward the cap (mites included) -- the cap is about loaded-entity lag. */
    private static boolean isCapped(Entity e) {
        return Factions.isEmerald(e);
    }

    /** A valid HP-absorption recipient: a live, NON-mite emerald (mites are too transient to bank HP into). */
    private static boolean isAbsorbTarget(Entity e) {
        return e.isAlive() && Factions.isEmerald(e) && !(e instanceof EmeraldEndermite);
    }

    /** True if the per-dimension loaded emerald count is at/over the configured cap (0 = unlimited). */
    public static boolean atCap(ServerLevel level) {
        int max = Config.emeraldMobsMaxNumber();
        return max > 0 && COUNT.getOrDefault(level.dimension(), 0) >= max;
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

    /** A plain "/summon <emerald entity>" at the cap is blocked with a message, rather than silently absorbed. */
    @SubscribeEvent
    public static void onSummonCommand(CommandEvent event) {
        String input = event.getParseResults().getReader().getString();
        if (!input.startsWith("summon ")) return;
        if (!input.contains("thevillagefightsback:emerald") && !input.contains("thevillagefightsback:rich_sheep")) return;
        CommandSourceStack src = event.getParseResults().getContext().getSource();
        if (src.getLevel() != null && atCap(src.getLevel())) {
            src.sendFailure(Component.literal("Emerald population cap reached (" + Config.emeraldMobsMaxNumber()
                    + ") in loaded chunks -- no emerald unit summoned."));
            event.setCanceled(true);
        }
    }

    /** Pour {@code wouldBe}'s max health into the nearest non-mite emerald unit (which heals up to its new max). */
    private static void absorbInto(ServerLevel level, LivingEntity wouldBe) {
        double addHp = wouldBe.getMaxHealth();
        AABB box = new AABB(wouldBe.blockPosition()).inflate(ABSORB_RADIUS);
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity c : level.getEntitiesOfClass(LivingEntity.class, box, EmeraldPopulation::isAbsorbTarget)) {
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
