package com.example.villagefightsback.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Reads MCA Reborn's tombstones, so the Limbo Scythe can raise anyone MCA itself buried.
 *
 * <p>This mod buries its OWN dead in its own memorial (see {@link com.example.villagefightsback.Graves}),
 * MCA installed or not. What is left here is the other direction: recognising a grave MCA dug and giving up its
 * occupant, so a player with a scythe is not told that MCA's graves are somehow not graves.
 *
 * <h2>Why this is reflection</h2>
 * MCA is a runtime soft dep with no compile-time access, and its classes are Forgix-relocated
 * ({@code forge.net.mca.*}), so there is nothing to import. Every lookup here is therefore by name and every
 * call is guarded: if any piece is missing the whole helper turns itself off after logging ONCE and MCA graves
 * are simply not recognised, which costs nothing else.
 *
 */
public final class MCATombstones {
    private MCATombstones() {}

    /** Any MCA tombstone id, used only to confirm MCA's grave blocks are actually registered. */
    private static final ResourceLocation HEADSTONE = new ResourceLocation("mca", "gravelling_headstone");

    /** MCA's classes carry a loader prefix from its merged jar; try the Forge one first, then the bare name. */
    private static final String[] PREFIXES = {"forge.net.mca.", "net.mca."};

    private static boolean resolved;
    private static boolean usable;
    private static Class<?> dataClass;
    private static Field entityDataField;
    private static Field computedNameField;
    private static Field nbtField;

    /** True when MCA is present AND every reflective handle we need resolved. */
    public static synchronized boolean available() {
        if (!resolved) {
            resolved = true;
            usable = resolve();
            if (!usable) {
                TheVillageFightsBack.LOGGER.info(
                        "[The Village Fights Back] MCA tombstones unavailable; falling back to our own memorial block.");
            }
        }
        return usable && ForgeRegistries.BLOCKS.containsKey(HEADSTONE);
    }

    private static boolean resolve() {
        if (!MCACompat.present()) return false;
        for (String p : PREFIXES) {
            try {
                dataClass = Class.forName(p + "block.TombstoneBlock$Data");
                Class<?> entityData = Class.forName(p + "block.TombstoneBlock$Data$EntityData");
                nbtField = entityData.getDeclaredField("nbt");
                nbtField.setAccessible(true);

                entityDataField = dataClass.getDeclaredField("entityData");
                entityDataField.setAccessible(true);
                computedNameField = dataClass.getDeclaredField("computedName");
                computedNameField.setAccessible(true);

                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // wrong prefix, or MCA changed shape: try the next prefix, then give up quietly
            }
        }
        return false;
    }


    /** True if the block at {@code pos} is an MCA tombstone holding a buried entity we could resurrect. */
    public static boolean isOccupiedGrave(ServerLevel level, BlockPos pos) {
        if (!available()) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !dataClass.isInstance(be)) return false;
        try {
            return ((Optional<?>) entityDataField.get(be)).isPresent();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Rebuild and release the entity buried in an MCA tombstone at {@code pos}, clearing the grave.
     *
     * <p>This deliberately does NOT use MCA's own animated resurrection (which its Scythe drives over several
     * ticks with lightning): we only need the stored entity back, and driving our own item through MCA's private
     * tick state would be far more fragile than reading the snapshot and spawning it.
     *
     * @return true if something was raised
     */
    public static boolean raise(ServerLevel level, BlockPos pos) {
        if (!available()) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !dataClass.isInstance(be)) return false;
        try {
            Object stored = ((Optional<?>) entityDataField.get(be)).orElse(null);
            if (stored == null) return false;
            CompoundTag nbt = ((CompoundTag) nbtField.get(stored)).copy();
            if (!com.example.villagefightsback.Graves.spawnFromNbt(level, pos, nbt)) return false;
            entityDataField.set(be, Optional.empty());
            computedNameField.set(be, null);
            be.setChanged();
            level.destroyBlock(pos, false);   // the grave has given up its occupant
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            TheVillageFightsBack.LOGGER.warn("[The Village Fights Back] MCA tombstone resurrection failed: {}",
                    e.toString());
            return false;
        }
    }

    /** True if the grave at {@code pos} holds General Darius, so his old memorial can be retired. */
    public static boolean holdsDarius(ServerLevel level, BlockPos pos) {
        if (!available()) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !dataClass.isInstance(be)) return false;
        try {
            Object stored = ((Optional<?>) entityDataField.get(be)).orElse(null);
            if (stored == null) return false;
            return com.example.villagefightsback.Graves.isDariusNbt((CompoundTag) nbtField.get(stored));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Is the block at {@code pos} an MCA tombstone at all (occupied or not)? Used to find Darius's old grave. */
    public static boolean isTombstone(ServerLevel level, BlockPos pos) {
        if (!available()) return false;
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && dataClass.isInstance(be);
    }

    // --- our memorial, seen by MCA -------------------------------------------------------------------
    // Our stone is in MCA's mca:tombstones block tag (see data/mca/tags/blocks/tombstones.json), which is what
    // makes a cluster of them register as an MCA GRAVEYARD building. Telling MCA's GraveyardManager about each
    // individual grave is the other half: that index is what its mourning tasks search to find somewhere to
    // grieve, so without it a graveyard would be recognised but never visited.

    private static Method graveyardGet;
    private static Method setTombstoneState;
    private static Method removeTombstoneState;
    private static Object stateFilled;
    private static boolean graveyardResolved;
    private static boolean graveyardUsable;

    private static synchronized boolean graveyardAvailable() {
        if (graveyardResolved) return graveyardUsable;
        graveyardResolved = true;
        if (MCACompat.present()) {
            for (String p : PREFIXES) {
                try {
                    Class<?> mgr = Class.forName(p + "server.world.data.GraveyardManager");
                    Class<?> state = Class.forName(p + "server.world.data.GraveyardManager$TombstoneState");
                    graveyardGet = mgr.getMethod("get", ServerLevel.class);
                    setTombstoneState = mgr.getMethod("setTombstoneState", BlockPos.class, state);
                    removeTombstoneState = mgr.getMethod("removeTombstoneState", BlockPos.class);
                    stateFilled = enumValue(state, "FILLED");
                    graveyardUsable = true;
                    break;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // wrong prefix or MCA changed shape: fall through and simply do not register graves
                }
            }
        }
        return graveyardUsable;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<Enum>) type.asSubclass(Enum.class), name);
    }

    /** Tell MCA a grave stands here, so its villagers can find it and mourn. No-op without MCA. */
    public static void registerGrave(ServerLevel level, BlockPos pos) {
        if (!graveyardAvailable()) return;
        try {
            setTombstoneState.invoke(graveyardGet.invoke(null, level), pos, stateFilled);
        } catch (ReflectiveOperationException | RuntimeException e) {
            graveyardUsable = false;
            TheVillageFightsBack.LOGGER.warn("[The Village Fights Back] could not register a grave with MCA: {}",
                    e.toString());
        }
    }

    /** Tell MCA the grave here is gone (its occupant was raised, or the stone was broken). No-op without MCA. */
    public static void unregisterGrave(ServerLevel level, BlockPos pos) {
        if (!graveyardAvailable()) return;
        try {
            removeTombstoneState.invoke(graveyardGet.invoke(null, level), pos);
        } catch (ReflectiveOperationException | RuntimeException e) {
            graveyardUsable = false;
        }
    }
}
