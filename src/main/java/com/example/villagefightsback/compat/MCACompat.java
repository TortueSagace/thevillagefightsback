package com.example.villagefightsback.compat;

import com.example.villagefightsback.Config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Minecraft Comes Alive (MCA Reborn, mod id {@code mca}) integration. MCA is a pure RUNTIME soft dep: nothing
 * here imports an MCA class, everything goes through entity ids, exactly like the Guard Villagers integration.
 * With MCA absent every method below degrades to plain vanilla behaviour, so a modpack without MCA behaves
 * byte-for-byte as it did before this class existed.
 *
 * <h2>Why this is needed</h2>
 * MCA's {@code VillagerEntityMCA} EXTENDS {@link Villager}, so the whole mod (every {@code instanceof Villager}
 * check, every {@code getEntitiesOfClass(Villager.class, ...)} sweep, the join-event goal injection, the
 * factions, the Emerald Virus, the Limbo Sage profession) already works on MCA villagers with no changes.
 *
 * <p>The one thing that does NOT work is SPAWNING. MCA replaces vanilla villagers through a spawn queue: it
 * intercepts the entity being added to the level, discards it, and rebuilds it as an MCA villager. That queue
 * is gated on the spawn reason ({@code allowedSpawnReasons}, default {@code ["natural", "structure"]}) and on
 * an EXACT class check ({@code getClass() == Villager.class}). Every villager the brain node spawns uses
 * {@code MOB_SUMMONED} / {@code EVENT} / {@code SPAWN_EGG}, so none of them are converted: the village would
 * fill up with vanilla "squidward" villagers standing next to MCA humans.
 *
 * <p>Worse, a player who "fixes" that by adding {@code mob_summoned} to MCA's {@code allowedSpawnReasons}
 * breaks the village instead: MCA's replacement copies only the name, gender, age, villager data, trades and
 * SCOREBOARD TAGS, and specifically NOT {@link Entity#getPersistentData()} -- which is where every settler /
 * builder / miner / upgrade flag in this mod lives. The settlers would spawn with their role erased.
 *
 * <p>The fix is to sidestep the queue entirely: when MCA is installed we build the MCA villager OURSELVES via
 * its registered entity type. MCA's spawn queue then never sees a vanilla {@code Villager} to convert, so the
 * persistent data we stamp on the settler survives untouched, and the village is uniformly MCA.
 *
 * @see VillagerRoleTags for the belt-and-braces mirror that survives a conversion we did not initiate
 */
public final class MCACompat {
    private MCACompat() {}

    /** MCA's mod id, and the two ids its villagers are registered under (gender is baked into the type). */
    public static final String MODID = "mca";
    public static final ResourceLocation MALE_VILLAGER = new ResourceLocation(MODID, "male_villager");
    public static final ResourceLocation FEMALE_VILLAGER = new ResourceLocation(MODID, "female_villager");

    /** Both MCA villager ids, for callers that must decorate/inspect every one of them (e.g. the render layers). */
    public static final ResourceLocation[] VILLAGER_IDS = { MALE_VILLAGER, FEMALE_VILLAGER };

    /**
     * True when MCA's villagers are registered. Resolved from the registry on every call rather than cached:
     * the entity registry is swapped when a client connects to a server, and a stale cache would be wrong on
     * exactly the setup this integration exists for.
     */
    public static boolean present() {
        return ForgeRegistries.ENTITY_TYPES.containsKey(MALE_VILLAGER);
    }

    /** True when MCA is installed AND the player has left {@code mca_compat} on (the default). */
    public static boolean enabled() {
        return present() && Config.MCA_COMPAT.get();
    }

    /**
     * The entity type this mod should spawn when it wants "a villager": a randomly gendered MCA human when the
     * integration is live, otherwise {@link EntityType#VILLAGER}. Never returns null, so no caller has to add a
     * new failure branch. Call it once PER VILLAGER (not once per batch) so a group comes out mixed-gender.
     */
    public static EntityType<?> villagerType(RandomSource rng) {
        if (!enabled()) return EntityType.VILLAGER;
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                rng.nextBoolean() ? MALE_VILLAGER : FEMALE_VILLAGER);
        return type != null ? type : EntityType.VILLAGER;
    }

    /**
     * Create (but do not add) a villager of the right kind for this modpack. Falls back to a vanilla villager
     * if MCA's type ever fails to produce a {@link Villager}, so the village can never stall on a bad spawn.
     * The caller still does its own {@code moveTo} / {@code finalizeSpawn} / {@code addFreshEntity} as before;
     * MCA's own init (name, genetics, family tree) runs inside {@code finalizeSpawn}.
     */
    public static Villager createVillager(Level level) {
        if (enabled() && villagerType(level.getRandom()).create(level) instanceof Villager mca) return mca;
        return EntityType.VILLAGER.create(level);
    }

    /** True for an MCA villager specifically (either gender). Vanilla villagers and Emeraldgers return false. */
    public static boolean isMcaVillager(Entity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return MALE_VILLAGER.equals(id) || FEMALE_VILLAGER.equals(id);
    }
}
