package com.example.villagefightsback.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Buries a fallen defender in one of MCA Reborn's OWN tombstones, so the grave is a real MCA grave: it counts
 * toward an MCA graveyard, and MCA's Scythe can resurrect from it exactly as if MCA had buried the villager
 * itself.
 *
 * <h2>Why this is reflection</h2>
 * MCA is a runtime soft dep with no compile-time access, and its classes are Forgix-relocated
 * ({@code forge.net.mca.*}), so there is nothing to import. Every lookup here is therefore by name and every
 * call is guarded: if any piece is missing the whole helper turns itself off after logging ONCE, and the caller
 * silently falls back to this mod's own memorial block. That is the accepted cost of the interop -- it is the
 * only way to put a villager into a grave MCA's own Scythe will accept.
 *
 * <h2>The trick that makes the epitaph possible</h2>
 * MCA's tombstone renders exactly one line of its own between "Here Lies" and "May he/she rest in peace", and
 * that line comes from the buried entity's stored NAME. Crucially {@code EntityData} keeps that name SEPARATE
 * from the entity's NBT snapshot, so substituting an epitaph for the name changes what is carved on the stone
 * without touching the villager that the Scythe would bring back.
 */
public final class MCATombstones {
    private MCATombstones() {}

    /** Flat, ground-level headstone: the right look for a grave dug where a defender fell. */
    private static final ResourceLocation HEADSTONE = new ResourceLocation("mca", "gravelling_headstone");

    /** MCA's classes carry a loader prefix from its merged jar; try the Forge one first, then the bare name. */
    private static final String[] PREFIXES = {"forge.net.mca.", "net.mca."};

    private static boolean resolved;
    private static boolean usable;
    private static Class<?> dataClass;
    private static Constructor<?> entityDataCtor;
    private static Field entityDataField;
    private static Field computedNameField;
    private static Field nbtField;
    private static Method setEntity;
    private static Method getGender;

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
                Class<?> gender = Class.forName(p + "entity.ai.relationship.Gender");

                entityDataCtor = entityData.getDeclaredConstructor(CompoundTag.class, Component.class, gender);
                entityDataCtor.setAccessible(true);
                nbtField = entityData.getDeclaredField("nbt");
                nbtField.setAccessible(true);

                entityDataField = dataClass.getDeclaredField("entityData");
                entityDataField.setAccessible(true);
                computedNameField = dataClass.getDeclaredField("computedName");
                computedNameField.setAccessible(true);

                setEntity = dataClass.getMethod("setEntity", net.minecraft.world.entity.Entity.class);
                getGender = dataClass.getMethod("getGender");
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // wrong prefix, or MCA changed shape: try the next prefix, then give up quietly
            }
        }
        return false;
    }

    /**
     * Place an MCA headstone at {@code grave} burying {@code deceased}, carved with {@code epitaph}.
     *
     * @return true if the grave was actually created
     */
    public static boolean bury(ServerLevel level, BlockPos grave, LivingEntity deceased, Component epitaph) {
        if (!available()) return false;
        Block headstone = ForgeRegistries.BLOCKS.getValue(HEADSTONE);
        if (headstone == null) return false;

        BlockState previous = level.getBlockState(grave);
        level.setBlock(grave, headstone.defaultBlockState(), 3);
        BlockEntity be = level.getBlockEntity(grave);
        if (be == null || !dataClass.isInstance(be)) {
            level.setBlock(grave, previous, 3);   // not MCA's block entity after all: undo and let the caller fall back
            return false;
        }
        try {
            // Let MCA build the snapshot itself, so the NBT is exactly what its own burial would have stored.
            setEntity.invoke(be, deceased);
            Object gender = getGender.invoke(be);

            // Then swap ONLY the displayed name for the epitaph, keeping MCA's nbt and gender untouched, so the
            // stone reads as a memorial while the villager it can resurrect is unchanged.
            Object stored = ((Optional<?>) entityDataField.get(be)).orElse(null);
            if (stored != null) {
                CompoundTag nbt = (CompoundTag) nbtField.get(stored);
                entityDataField.set(be, Optional.of(entityDataCtor.newInstance(nbt, epitaph, gender)));
                computedNameField.set(be, null);  // force the rendered text to be rebuilt from the new name
            }
            be.setChanged();
            level.sendBlockUpdated(grave, level.getBlockState(grave), level.getBlockState(grave), 3);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            TheVillageFightsBack.LOGGER.warn(
                    "[The Village Fights Back] could not fill an MCA tombstone ({}); using our own memorial instead.",
                    e.toString());
            level.setBlock(grave, previous, 3);
            usable = false;   // stop trying for the rest of the session
            return false;
        }
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

    /** A free, supported cell at or near {@code around} where a headstone can stand. Null if the ground is full. */
    public static BlockPos findGraveSpot(ServerLevel level, BlockPos around) {
        for (int r = 0; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = 1; dy >= -2; dy--) {
                        BlockPos p = around.offset(dx, dy, dz);
                        if (!level.isLoaded(p)) continue;
                        if (!level.getBlockState(p).canBeReplaced()) continue;
                        BlockPos below = p.below();
                        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;
                        return p;
                    }
                }
            }
        }
        return null;
    }
}
