package com.example.villagefightsback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Soft population cap for the EMERALD faction ({@code emerald_mobs_max_number}) -- purely to limit lag, so it is
 * LOCAL: it counts the emerald units (mites included) within the player's active range of a spawn, NOT across the
 * whole world. Emeralds living far from any player aren't ticking and don't lag anyone, so they never count and
 * never block a fresh spawn elsewhere. When a NEW emerald would spawn automatically (conversion, brain summon,
 * death eruption) into an area already at the cap, the spawn is cancelled and that unit's max health is poured into
 * the nearest non-mite emerald instead. A conversion victim then simply dies and its infested path still appears.
 * Manual spawns (spawn egg / {@code /summon}) check {@link #atCap} up front and tell the player. Entities loaded
 * from disk are never capped.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmeraldPopulation {
    private EmeraldPopulation() {}

    /** The player's active entity range: emeralds within this of a spawn count toward the local cap, the rest do not. */
    private static final double RANGE = 128.0;

    /** Every emerald unit counts toward the cap (mites included) -- the cap is about loaded/ticking-entity lag. */
    private static boolean isCapped(Entity e) {
        return Factions.isEmerald(e);
    }

    /** A valid HP-absorption recipient: a live, NON-mite emerald (mites are too transient to bank HP into). */
    private static boolean isAbsorbTarget(Entity e) {
        return e.isAlive() && Factions.isEmerald(e) && !(e instanceof EmeraldEndermite);
    }

    /** True if the emerald population WITHIN {@link #RANGE} of {@code pos} is at/over the cap (0 = unlimited). */
    public static boolean atCap(ServerLevel level, Vec3 pos) {
        int max = Config.emeraldMobsMaxNumber();
        if (max <= 0) return false;
        AABB box = new AABB(pos, pos).inflate(RANGE);
        return level.getEntitiesOfClass(LivingEntity.class, box, EmeraldPopulation::isCapped).size() >= max;
    }

    @SubscribeEvent(priority = EventPriority.LOW) // after the enable_mob_infection gate, which may cancel first
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide() || event.loadedFromDisk()) return;
        if (!(event.getLevel() instanceof ServerLevel level) || !isCapped(event.getEntity())) return;
        if (atCap(level, event.getEntity().position())) {
            if (event.getEntity() instanceof LivingEntity wouldBe) absorbInto(level, wouldBe);
            event.setCanceled(true);
        }
    }

    /** A plain "/summon <emerald entity>" into a capped area is blocked with a message, not silently absorbed. */
    @SubscribeEvent
    public static void onSummonCommand(CommandEvent event) {
        String input = event.getParseResults().getReader().getString();
        if (!input.startsWith("summon ")) return;
        if (!input.contains("thevillagefightsback:emerald") && !input.contains("thevillagefightsback:rich_sheep")) return;
        CommandSourceStack src = event.getParseResults().getContext().getSource();
        if (src.getLevel() != null && atCap(src.getLevel(), src.getPosition())) {
            src.sendFailure(Component.literal("Too many emerald units nearby (cap " + Config.emeraldMobsMaxNumber()
                    + "). No emerald unit was summoned."));
            event.setCanceled(true);
        }
    }

    /** Pour {@code wouldBe}'s max health into the nearest non-mite emerald unit (which heals up to its new max). */
    private static void absorbInto(ServerLevel level, LivingEntity wouldBe) {
        double addHp = wouldBe.getMaxHealth();
        AABB box = new AABB(wouldBe.blockPosition()).inflate(RANGE);
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
