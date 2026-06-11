package com.example.villagergunners;

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
import net.minecraftforge.registries.ForgeRegistries;

/**
 * State + rules for the Emerald Virus. The infection lives in the host's persistent NBT so it survives
 * saves and -- crucially -- the "ever infected" flag and the green tint are NEVER cleared, so a host can
 * be infected only once and stays green forever (even after the timed effect wears off).
 */
public final class EmeraldVirus {
    private EmeraldVirus() {}

    /** Full virus length: 1 minute. */
    public static final int DURATION_TICKS = 60 * 20; // 1200

    private static final String NBT_EVER = "emerald_virus_ever";     // has this host EVER been infected?
    private static final String NBT_PROGRESS = "emerald_virus_tint"; // green-tint progress 0..1 (never reverts)

    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");

    /** Villagers, guards, iron/snow golems and players are naturally immune and can never catch the virus. */
    public static boolean isNaturallyImmune(LivingEntity e) {
        return e instanceof Player
                || e instanceof Villager
                || e instanceof IronGolem
                || e instanceof SnowGolem
                || GUARD_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
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
        return e.hasEffect(ModEffects.EMERALD_VIRUS.get());
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
        if (!canInfect(e)) return false;
        CompoundTag data = e.getPersistentData();
        data.putBoolean(NBT_EVER, true);
        // ambient=false, showParticles=false, showIcon=true -> no swirl particles, but a HUD icon for players.
        e.addEffect(new MobEffectInstance(ModEffects.EMERALD_VIRUS.get(), DURATION_TICKS, 0, false, false, true));
        // Seed a faint tint immediately so the colour shift is visible from the first second.
        if (progress(e) < 0.05F) setProgress(e, 0.05F);
        return true;
    }

    /** True if the host should currently look green (any accumulated tint). */
    public static boolean isTinted(Entity e) {
        return e instanceof LivingEntity le && progress(le) > 0.0F;
    }

    /** Highest level reached by any placed Village Gravemind (the global virus "stage"); 0 if none. */
    public static int maxGravemindStage(MinecraftServer server) {
        if (server == null) return 0;
        GravemindData data = GravemindData.get(server);
        int max = 0;
        for (GlobalPos gp : data.blocks()) {
            max = Math.max(max, data.econ(gp).getLevel());
        }
        return max;
    }

    /** The virus has evolved (Wither II, spawns Emeraldgers) once any gravemind hits level 4. */
    public static boolean isEvolved(MinecraftServer server) {
        return maxGravemindStage(server) >= 4;
    }
}
