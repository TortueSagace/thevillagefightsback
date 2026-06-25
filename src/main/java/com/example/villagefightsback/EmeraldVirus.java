package com.example.villagefightsback;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * State + rules for the Emerald Virus. The infection lives in the host's persistent NBT so it survives
 * saves and -- crucially -- the "ever infected" flag and the green tint are NEVER cleared, so a host can
 * be infected only once and stays green forever (even after the timed effect wears off).
 */
public final class EmeraldVirus {
    private EmeraldVirus() {}

    /** Full virus length: 1 minute. */
    public static final int DURATION_TICKS = 60 * 20; // 1200

    /** Contact infection length: a fertile plant (grass / kelp) brushed against gives Emerald Virus I for 20s. */
    public static final int CONTACT_TICKS = 20 * 20; // 400

    private static final String NBT_EVER = "emerald_virus_ever";     // has this host EVER been infected?
    private static final String NBT_PROGRESS = "emerald_virus_tint"; // green-tint progress 0..1 (never reverts)

    private static final ResourceLocation GUARD_ID = ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard");

    /** Villagers, guards, iron/snow golems, players and the emerald faction are naturally immune and can never
     *  catch the virus (an emerald unit is already the faction, so it has nothing to catch). */
    public static boolean isNaturallyImmune(LivingEntity e) {
        return e instanceof Player
                || e instanceof Villager
                || e instanceof IronGolem
                || e instanceof SnowGolem
                || e instanceof VindictiveGuardEntity   // the boss's own guards are immune to the virus
                || EmeraldMobs.isPlayerCompanion(e)     // the player's pets/mounts (cats, dogs/wolves, horses) never catch it
                || Factions.isEmerald(e)                // emerald units never catch the virus (they ARE it)
                || GUARD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
    }

    /** True if the host has ever carried the virus (used both for tint persistence and one-shot immunity). */
    public static boolean everInfected(LivingEntity e) {
        return e.getPersistentData().getBoolean(NBT_EVER);
    }

    /** A host can be infected only if it is alive, not naturally immune, and has never been infected before. */
    public static boolean canInfect(LivingEntity e) {
        return e.isAlive() && !isNaturallyImmune(e) && !everInfected(e);
    }

    /** True while the timed effect is currently on the host. */
    public static boolean isActive(LivingEntity e) {
        return e.hasEffect(ModEffects.EMERALD_VIRUS);
    }

    /** Green-tint progress, 0..1. Grows while active and is never reduced. */
    public static float progress(LivingEntity e) {
        return e.getPersistentData().getFloat(NBT_PROGRESS);
    }

    public static void setProgress(LivingEntity e, float p) {
        e.getPersistentData().putFloat(NBT_PROGRESS, Math.max(progress(e), Math.min(1.0F, p)));
    }

    /**
     * Infect a host: stamp the permanent "ever" flag and apply the 1-minute, particle-free virus effect.
     * No-op if the host cannot be infected. Returns true if a fresh infection was started.
     */
    public static boolean infect(LivingEntity e) {
        return infect(e, 0);
    }

    /** Infect with a given strength: amplifier 0 = Emerald Virus I, amplifier 1 = Emerald Virus II (stage-4 hit). */
    public static boolean infect(LivingEntity e, int amplifier) {
        return infect(e, amplifier, DURATION_TICKS);
    }

    /** Infect with a given strength AND length. amplifier 0 = Emerald Virus I, 1 = II; {@code durationTicks} is the
     *  active-effect length (the permanent "ever infected" flag + green tint are stamped regardless of length). */
    public static boolean infect(LivingEntity e, int amplifier, int durationTicks) {
        if (!canInfect(e)) return false;
        CompoundTag data = e.getPersistentData();
        data.putBoolean(NBT_EVER, true);
        // ambient=false, showParticles=false, showIcon=true -> no swirl particles, but a HUD icon for players.
        e.addEffect(new MobEffectInstance(ModEffects.EMERALD_VIRUS, durationTicks, amplifier, false, false, true));
        // Seed a faint tint immediately so the colour shift is visible from the first second.
        if (progress(e) < 0.05F) setProgress(e, 0.05F);
        return true;
    }

    /** A mob brushes against a fertile plant (grass / kelp): give it Emerald Virus I for 20s if it is eligible
     *  ({@link #canInfect}). Server-side only; harmlessly no-ops for immune / already-infected hosts, so it is
     *  safe to call every tick from a block's {@code entityInside}. */
    public static void touch(LivingEntity e) {
        if (e.level().isClientSide()) return;
        infect(e, 0, CONTACT_TICKS);
    }

    /** True if the host should currently look green (any accumulated tint). */
    public static boolean isTinted(Entity e) {
        return e instanceof LivingEntity le && progress(le) > 0.0F;
    }

    /** Highest level reached by any placed Village Brain Node (the global virus "stage"); 0 if none. */
    public static int maxBrainNodeStage(MinecraftServer server) {
        if (server == null) return 0;
        BrainNodeData data = BrainNodeData.get(server);
        int max = 0;
        for (GlobalPos gp : data.blocks()) {
            max = Math.max(max, data.econ(gp).getLevel());
        }
        return max;
    }

    /** The virus has evolved (Wither II, spawns Emeraldgers) once any brainNode hits level 4. */
    public static boolean isEvolved(MinecraftServer server) {
        return maxBrainNodeStage(server) >= 4;
    }
}
