package com.example.villagefightsback.compat;

import com.example.villagefightsback.Config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import com.example.villagefightsback.platform.ModRegistries;

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
        return ModRegistries.ENTITY_TYPES.containsKey(MALE_VILLAGER);
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
        EntityType<?> type = ModRegistries.ENTITY_TYPES.getValue(
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

    /**
     * MCA's SOLDIER professions. Matches MCA's own {@code VillagerEntityMCA.isGuard()}, which is exactly
     * {@code profession == GUARD || profession == ARCHER} -- so this mod's idea of "that villager is a guard"
     * cannot drift from MCA's.
     */
    private static final java.util.Set<ResourceLocation> GUARD_PROFESSIONS = java.util.Set.of(
            new ResourceLocation(MODID, "guard"),
            new ResourceLocation(MODID, "archer"));

    /**
     * True for an MCA villager doing a guard's job. They are Villagers, so without this they would be armed as
     * CIVILIANS and handed the villager stage kit (the Alien Slasher) while MCA also runs its own crossbow
     * behaviour on them. They are the village's soldiers, so this mod gives them the GUARD kit instead and keeps
     * them out of the civilian roles (settler / builder / miner).
     */
    public static boolean isMcaGuard(Entity e) {
        if (!(e instanceof Villager v)) return false;
        ResourceLocation id = ModRegistries.VILLAGER_PROFESSIONS.getKey(v.getVillagerData().getProfession());
        return id != null && GUARD_PROFESSIONS.contains(id);
    }

    /** The Guard Villagers guard entity, used when MCA is absent. */
    private static final ResourceLocation GUARD_VILLAGERS_GUARD = new ResourceLocation("guardvillagers", "guard");

    /**
     * The entity type to spawn when this mod wants A GUARD: an MCA human when MCA is installed, otherwise the
     * Guard Villagers guard as before.
     *
     * <p>MCA guards are not a separate creature -- they are MCA villagers wearing the {@code mca:guard}
     * profession, which MCA renders with a human face and its own guard clothing. Spawning the Guard Villagers
     * entity into an MCA village left the guards as the only blank-faced villagers in a town of humans. The
     * caller must follow the spawn with {@link #makeGuard} to actually pin the profession on.
     */
    public static EntityType<?> guardType(RandomSource rng) {
        if (enabled()) {
            EntityType<?> mca = ModRegistries.ENTITY_TYPES.getValue(
                    rng.nextBoolean() ? MALE_VILLAGER : FEMALE_VILLAGER);
            if (mca != null) return mca;
        }
        return ModRegistries.ENTITY_TYPES.getValue(GUARD_VILLAGERS_GUARD); // null if Guard Villagers is absent
    }

    /**
     * Turn a freshly spawned MCA villager into a GUARD by giving it the {@code mca:guard} profession. A no-op
     * for anything else, so it is safe to call on whatever {@link #guardType} produced.
     *
     * <p>The profession sticks: MCA registers guard as an "important" profession, so the villager brain will not
     * reset it for want of a job-site POI the way it would an ordinary job. Setting it also switches MCA onto its
     * own guard AI (its enemy sensor, patrol and combat tasks) and its guard clothing, while this mod's
     * {@code isGuard} recognises the same profession and issues the emerald knight / virus archer kit on top.
     */
    public static void makeGuard(net.minecraft.world.entity.Entity e) {
        if (!(e instanceof Villager v) || !isMcaVillager(v)) return;
        net.minecraft.world.entity.npc.VillagerProfession guard =
                ModRegistries.VILLAGER_PROFESSIONS.getValue(new ResourceLocation(MODID, "guard"));
        if (guard == null) return;
        v.setVillagerData(v.getVillagerData().setProfession(guard));
    }

    /** MCA's own "who should this guard attack" memory, resolved by registry id so MCA stays a soft dep. */
    private static final ResourceLocation GUARD_ENEMY_MEMORY = new ResourceLocation(MODID, "nearest_guard_enemy");

    /**
     * Make {@code villager} forget an emerald-faction target.
     *
     * <p>Needed because MCA's combat villagers (guards and archers) do NOT pick targets through
     * {@code Mob.setTarget}: a sensor writes MCA's {@code nearest_guard_enemy} memory and a brain task promotes
     * it to the vanilla {@code ATTACK_TARGET} memory. Forge only fires {@code LivingChangeTargetEvent} from
     * {@code Mob.setTarget}, so the emerald non-aggression cancel in EmeraldConversion never sees them, and an
     * MCA guard would chase emerald units forever (swinging for zero damage, since the faction damage-immunity
     * handler cancels the hits). Clearing both memories is what actually calls the guard off.
     *
     * <p>Cheap and safe with MCA absent: the vanilla memory is always checked, and MCA's is looked up in the
     * registry and simply skipped when it is not there.
     */
    public static void forgetEmeraldTarget(net.minecraft.world.entity.LivingEntity villager) {
        net.minecraft.world.entity.ai.Brain<?> brain = villager.getBrain();
        eraseIfEmerald(brain, net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
        net.minecraft.core.registries.BuiltInRegistries.MEMORY_MODULE_TYPE
                .getOptional(GUARD_ENEMY_MEMORY)
                .ifPresent(type -> {
                    @SuppressWarnings("unchecked")
                    net.minecraft.world.entity.ai.memory.MemoryModuleType<net.minecraft.world.entity.LivingEntity> m =
                            (net.minecraft.world.entity.ai.memory.MemoryModuleType<net.minecraft.world.entity.LivingEntity>) type;
                    eraseIfEmerald(brain, m);
                });
    }

    /**
     * Erase {@code memory} when it holds an emerald unit.
     *
     * <p>The REGISTERED check is not optional: {@code Brain.getMemory} THROWS
     * {@code IllegalStateException("Unregistered memory fetched")} for a memory the brain does not declare, and
     * this is reached for every village member, including iron golems and wall defenders whose default brain
     * declares no memories at all. Probing first is what keeps that from taking the server down.
     */
    private static <U> void eraseIfEmerald(net.minecraft.world.entity.ai.Brain<?> brain,
                                           net.minecraft.world.entity.ai.memory.MemoryModuleType<U> memory) {
        if (!brain.checkMemory(memory, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) return;
        brain.getMemory(memory)
                .filter(v -> v instanceof net.minecraft.world.entity.Entity e
                        && com.example.villagefightsback.Factions.isEmerald(e))
                .ifPresent(v -> brain.eraseMemory(memory));
    }

    /** The radius MCA's own {@code VillagerEntityMCA.hurt} broadcasts an attacker to nearby guards over. */
    private static final double HURT_BROADCAST_RADIUS = 32.0;

    /**
     * Undo MCA's "a villager was hurt" guard call-out when the attacker was an emerald unit.
     *
     * <p>This is the one acquisition path no event can gate. {@code VillagerEntityMCA.hurt} writes the attacker
     * straight into the {@code ATTACK_TARGET} memory of every MCA guard within 32 blocks with a RAW
     * {@code Brain.setMemory} (no {@code LivingChangeTargetEvent}), and it does so BEFORE calling
     * {@code super.hurt} -- so it lands before {@code LivingAttackEvent}, and long before the faction
     * damage-immunity cancel. One emerald creeper blast clipping a villager would otherwise turn every guard in
     * a 32-block radius into a pursuer.
     *
     * <p>Edge-triggered: it runs only when an emerald actually damages an MCA villager, and then does exactly one
     * villager-class box query over MCA's own broadcast box. No periodic sweep, so it costs nothing at rest.
     */
    public static void recallGuardsHurtBy(net.minecraft.world.entity.LivingEntity victim) {
        if (!present()) return;
        for (Villager v : victim.level().getEntitiesOfClass(Villager.class,
                new net.minecraft.world.phys.AABB(victim.blockPosition()).inflate(HURT_BROADCAST_RADIUS))) {
            forgetEmeraldTarget(v);
        }
    }

    /**
     * True for a FEMALE MCA villager. MCA bakes gender into the entity type rather than into a data field, so
     * this is answerable by registry id alone, with no MCA class involved. Anything else (a male MCA villager,
     * a vanilla villager, a guard entity) is treated as male, which is only ever used to pick a pronoun.
     */
    public static boolean isFemale(Entity e) {
        return FEMALE_VILLAGER.equals(ModRegistries.ENTITY_TYPES.getKey(e.getType()));
    }

    /** True for an MCA villager specifically (either gender). Vanilla villagers and Emeraldgers return false. */
    public static boolean isMcaVillager(Entity e) {
        ResourceLocation id = ModRegistries.ENTITY_TYPES.getKey(e.getType());
        return MALE_VILLAGER.equals(id) || FEMALE_VILLAGER.equals(id);
    }

    /**
     * True when the village bell must be LEFT STANDING because MCA Reborn is using it.
     *
     * <p>MCA's "Town Center" building is defined as exactly one block -- {@code #mca:town_center}, whose only
     * member is {@code minecraft:bell} (its own lang string for the tag is literally "Bell"). That building is
     * what MCA's residency, marriage, taxes and guard-spawning systems hang the village off. This mod normally
     * has the brain node REPLACE the bell, which would delete MCA's town centre out from under it, so with MCA
     * installed the node settles beside the bell instead. MCA gives the town centre an 8-block margin, so an
     * adjacent spot still counts as the town hall.
     */
    public static boolean preserveTownCenter() {
        return enabled();
    }

    /** How far from the bell a brain node may settle and still sit inside MCA's town-centre margin (8). */
    public static final int TOWN_CENTER_MARGIN = 8;

    /**
     * MCA's HOSTILE professions. This is the trap in MCA's design as far as this mod is concerned: an outlaw
     * (bandit) and a cultist are not separate entities, they are ordinary {@code VillagerEntityMCA} carrying a
     * profession -- so they are {@link Villager}s, and every village check in this mod keys off exactly that.
     * Left alone, the village would treat raiding cultists as its own citizens: never fight them, spare them from
     * the emerald faction, count them as population, let them take village roles, and -- worst -- ARM them with
     * the village's stage kit, handing a hostile its Alien Slasher and leather set.
     *
     * <p>Deliberately only outlaw + cultist. MCA's other custom professions are NOT hostile: guard and archer are
     * village defenders, mercenaries are hireable by the player, adventurers are neutral travellers. Treating one
     * of those as an enemy would be a worse bug than the one being fixed.
     */
    private static final java.util.Set<ResourceLocation> HOSTILE_PROFESSIONS = java.util.Set.of(
            new ResourceLocation(MODID, "outlaw"),
            new ResourceLocation(MODID, "cultist"));

    /**
     * True for an MCA villager whose profession makes it an ENEMY of the village (outlaw / cultist). Cheap: the
     * {@code instanceof} gate rejects every non-villager before any registry work, and only villagers -- a small
     * minority of the entities a threat scan sees -- pay for the profession lookup.
     */
    public static boolean isHostileVillager(Entity e) {
        if (!(e instanceof Villager v)) return false;
        ResourceLocation id = ModRegistries.VILLAGER_PROFESSIONS.getKey(v.getVillagerData().getProfession());
        return id != null && HOSTILE_PROFESSIONS.contains(id);
    }
}
