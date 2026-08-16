package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every Obliteration Furnace in the world: how long it has left to burn, and what it has struck off the spawn
 * tables while burning.
 *
 * <p>WHY THIS IS SAVED DATA AND NOT THE BLOCK ENTITY. The obliteration list has to answer one question from
 * anywhere: "may this creature spawn?" -- asked in any dimension, thousands of blocks away, in chunks where the
 * furnace itself is nowhere near loaded. A block entity cannot answer that, because an unloaded furnace does
 * not tick and cannot be read. Keeping the whole picture in one world-level record means the ban holds while
 * you are in the Nether and the furnace is in an overworld basement nobody has visited for a week.
 *
 * <p>Burn time is decremented HERE, once per second by {@link ObliterationEvents}, rather than in the block
 * entity's tick, for exactly the same reason: fuel must run out on schedule whether or not anyone is standing
 * near the furnace. A furnace whose timer reaches zero drops its whole list, and everything it had erased can
 * spawn again -- that is the bargain the feature is built on, and it would be quietly broken if the timer only
 * ran while the chunk was loaded.
 */
public class ObliterationData extends SavedData {
    private static final String NAME = "thevillagefightsback_obliteration";

    /** One furnace's state. */
    public static class Furnace {
        /** Ticks of fuel left. Zero means cold, and a cold furnace bans nothing. */
        public int burnTicks;
        /** Entity type ids this furnace has erased. Cleared the moment it goes cold. */
        public final Set<ResourceLocation> obliterated = new HashSet<>();
    }

    private final Map<GlobalPos, Furnace> furnaces = new HashMap<>();

    // 1.21 note: the same lookup, through the new SavedData.Factory. computeIfAbsent no longer takes the
    // loader and the constructor as two loose arguments -- they are bundled into a Factory, and the loader is
    // handed the registry lookup along with the tag (see load below). Nothing about what is stored changed.
    public static ObliterationData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ObliterationData::new, (tag, registries) -> load(tag, registries)), NAME);
    }

    // --- furnace bookkeeping ---------------------------------------------------------------------------

    public Furnace furnace(GlobalPos at) {
        return furnaces.computeIfAbsent(at, k -> {
            setDirty();
            return new Furnace();
        });
    }

    /** Forget a furnace entirely (it was broken). Its bans lift with it. */
    public void remove(GlobalPos at) {
        if (furnaces.remove(at) != null) setDirty();
    }

    public boolean isBurning(GlobalPos at) {
        Furnace f = furnaces.get(at);
        return f != null && f.burnTicks > 0;
    }

    /** Add fuel. Returns the new total, in ticks. */
    public int addFuel(GlobalPos at, int ticks) {
        Furnace f = furnace(at);
        f.burnTicks += ticks;
        setDirty();
        return f.burnTicks;
    }

    /**
     * Burn one second off every furnace, and empty the list of any that just went out.
     *
     * <p>The clearing is the whole point of the timer: running dry is not a pause, it is an amnesty.
     */
    public void tickSecond() {
        boolean changed = false;
        for (Furnace f : furnaces.values()) {
            if (f.burnTicks <= 0) continue;
            f.burnTicks = Math.max(0, f.burnTicks - 20);
            if (f.burnTicks == 0 && !f.obliterated.isEmpty()) {
                f.obliterated.clear();   // amnesty: everything it erased may spawn again
            }
            changed = true;
        }
        if (changed) setDirty();
    }

    // --- the ban ---------------------------------------------------------------------------------------

    /**
     * True if ANY furnace is currently burning with something struck off.
     *
     * <p>The cheap question the entity-join handler asks before it bothers to look an entity's registry id up.
     * That handler sees every creature that enters the world, and in a save where nobody has ever lit an
     * Obliteration Furnace -- which is nearly all of them -- this answers no without touching a registry.
     */
    public boolean bansAnything() {
        for (Furnace f : furnaces.values()) {
            if (f.burnTicks > 0 && !f.obliterated.isEmpty()) return true;
        }
        return false;
    }

    /** True if ANY burning furnace has struck this type off. Dimension-agnostic on purpose: the ban is global.
     *  Players are answered no before anything is consulted, so a save that somehow recorded the ban still lets
     *  its players in. */
    public boolean isObliterated(ResourceLocation type) {
        if (PLAYER.equals(type)) return false;
        for (Furnace f : furnaces.values()) {
            if (f.burnTicks > 0 && f.obliterated.contains(type)) return true;
        }
        return false;
    }

    /**
     * The burning furnace nearest {@code from}, or null if none is lit.
     *
     * <p>Nearest across ALL dimensions is meaningless, so a furnace in another dimension is only considered
     * when there is nothing lit in this one: a kill made in the Nether should still land somewhere rather than
     * being silently dropped because the only lit furnace is at home.
     */
    public GlobalPos nearestBurning(ResourceKey<Level> dimension, BlockPos from) {
        GlobalPos bestHere = null, bestAnywhere = null;
        double hereSqr = Double.MAX_VALUE, anySqr = Double.MAX_VALUE;
        for (Map.Entry<GlobalPos, Furnace> e : furnaces.entrySet()) {
            if (e.getValue().burnTicks <= 0) continue;
            GlobalPos gp = e.getKey();
            double d = gp.pos().distSqr(from);
            if (gp.dimension().equals(dimension)) {
                if (d < hereSqr) { hereSqr = d; bestHere = gp; }
            } else if (d < anySqr) {
                anySqr = d; bestAnywhere = gp;
            }
        }
        return bestHere != null ? bestHere : bestAnywhere;
    }

    /**
     * The one type that can never be struck off, whatever asks.
     *
     * <p>A banned type cannot enter the world, and a player entering the world is a player logging in or
     * respawning. Obliterating players would not read as a hard difficulty setting, it would read as a save
     * that has stopped letting people in. Every caller already declines to obliterate a player, so this is the
     * backstop rather than the rule -- but it is the one place a future caller cannot get wrong, and a ban
     * written into the save once would outlive whatever bug wrote it.
     */
    private static final ResourceLocation PLAYER = ResourceLocation.fromNamespaceAndPath("minecraft", "player");

    /** Strike {@code type} off, at {@code at}. Returns false if that furnace is cold or already had it. */
    public boolean obliterate(GlobalPos at, ResourceLocation type) {
        if (PLAYER.equals(type)) return false;                     // never, by any route: see above
        Furnace f = furnaces.get(at);
        if (f == null || f.burnTicks <= 0) return false;
        if (!f.obliterated.add(type)) return false;
        setDirty();
        return true;
    }

    public List<ResourceLocation> listFor(GlobalPos at) {
        Furnace f = furnaces.get(at);
        return f == null ? List.of() : new ArrayList<>(f.obliterated);
    }

    public int burnTicks(GlobalPos at) {
        Furnace f = furnaces.get(at);
        return f == null ? 0 : f.burnTicks;
    }

    // --- persistence -----------------------------------------------------------------------------------

    // 1.21 note: load and save both take a HolderLookup.Provider now, so saved data that stores registry
    // CONTENT can resolve it against the connected registries. Nothing here stores anything but plain ids and
    // longs, so the argument is accepted and ignored, and the on-disk format is byte-for-byte main's.

    public static ObliterationData load(CompoundTag tag, HolderLookup.Provider registries) {
        ObliterationData d = new ObliterationData();
        ListTag list = tag.getList("furnaces", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(t.getString("dim")));
            GlobalPos gp = GlobalPos.of(dim, BlockPos.of(t.getLong("pos")));
            Furnace f = new Furnace();
            f.burnTicks = t.getInt("burn");
            for (Tag e : t.getList("types", Tag.TAG_STRING)) {
                f.obliterated.add(ResourceLocation.parse(e.getAsString()));
            }
            d.furnaces.put(gp, f);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<GlobalPos, Furnace> e : furnaces.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("dim", e.getKey().dimension().location().toString());
            t.putLong("pos", e.getKey().pos().asLong());
            t.putInt("burn", e.getValue().burnTicks);
            ListTag types = new ListTag();
            for (ResourceLocation id : e.getValue().obliterated) {
                types.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
            }
            t.put("types", types);
            list.add(t);
        }
        tag.put("furnaces", list);
        return tag;
    }
}
