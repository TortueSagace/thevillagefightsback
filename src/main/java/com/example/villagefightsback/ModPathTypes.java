package com.example.villagefightsback;

import java.lang.reflect.Field;
import java.util.Arrays;

import com.example.villagefightsback.mixin.BlockPathTypesInvoker;

import net.minecraft.world.level.pathfinder.BlockPathTypes;

/**
 * Custom pathfinding type for the fake-cobblestone "smart door".
 *
 * In 1.20.1, WalkNodeEvaluator asks for a block's path type with mob == null, so a block can't tell allies from
 * enemies there. Instead, the fake-cobblestone block returns this single type for everyone, and the per-mob
 * decision is made via {@code Mob.getPathfindingMalus(type)}: this type defaults to malus -1 (BLOCKED), so any
 * mob that hasn't opted in won't route through it. Allied mobs (villagers/iron golems/guards) call
 * {@code setPathfindingMalus(ALLY_PASSABLE, 0)} so it becomes passable for them only.
 *
 * <p>Forge can extend an enum; Fabric cannot, so the constant is built through the compiler-generated enum
 * constructor ({@link BlockPathTypesInvoker}) and spliced into the enum's own values array. Only the splice can
 * fail, and nothing the mod does depends on it: the malus map that decides passability is keyed by the object,
 * not by the ordinal. So a failure is logged and play continues.
 */
public final class ModPathTypes {
    /** Default malus -1.0 => BLOCKED for every mob unless it overrides the malus to >= 0. */
    public static final BlockPathTypes ALLY_PASSABLE = create();

    private ModPathTypes() {}

    /** Force class-load (and thus the create() above) early, from the mod initializer. */
    public static void init() {}

    private static BlockPathTypes create() {
        BlockPathTypes[] existing = BlockPathTypes.values();
        BlockPathTypes type = BlockPathTypesInvoker.tvfb$create(
                "THEVILLAGEFIGHTSBACK_ALLY_PASSABLE", existing.length, -1.0F);
        appendToValues(existing, type);
        return type;
    }

    /**
     * Puts the new constant into {@code BlockPathTypes.values()} so the enum is internally consistent -- anything
     * that walks the values, or looks one up by ordinal, sees it too.
     */
    private static void appendToValues(BlockPathTypes[] existing, BlockPathTypes added) {
        try {
            BlockPathTypes[] grown = Arrays.copyOf(existing, existing.length + 1);
            grown[existing.length] = added;
            // $VALUES is static final, which ordinary reflection refuses to write on Java 17, so it goes through
            // Unsafe -- the same route every enum-extension library takes.
            Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
            Field values = BlockPathTypes.class.getDeclaredField("$VALUES");
            unsafe.putObject(unsafe.staticFieldBase(values), unsafe.staticFieldOffset(values), grown);
        } catch (ReflectiveOperationException | RuntimeException e) {
            TheVillageFightsBack.LOGGER.warn("[The Village Fights Back] Could not add {} to BlockPathTypes.values();"
                    + " the smart door still works, since passability is decided by the per-mob malus map.",
                    added.name(), e);
        }
    }
}
