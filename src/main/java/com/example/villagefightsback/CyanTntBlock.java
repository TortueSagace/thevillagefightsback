package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * "Cyan TNT" -- the grand-house charge a village raises once its brain node reaches stage 2.
 *
 * <p>Mechanically it is a Blue TNT with a different template pool: it builds one of
 * {@link HousePlacer#BIG_HOUSES} instead of one of the eight small {@link HousePlacer#HOUSES}. Everything
 * else -- detonate-on-placement, the relocate search, the refusal to carve through an existing building --
 * is inherited unchanged.
 *
 * <p>Two things do differ, both because the big houses are up to {@value HousePlacer#BIG_HOUSE_MAX_SIDE}
 * blocks across rather than seven:
 *
 * <ul>
 *   <li>It asks for {@value #CLEARANCE} blocks of clear ground rather than 8, and searches further out
 *       when the spot it lands on is taken. {@value #CLEARANCE} is half the longest side rounded up, so the
 *       largest of the three cannot overlap a neighbour while still packing tightly enough to find spots.</li>
 *   <li>If nothing within reach fits, it falls back to a SMALL house rather than giving up. A settler that
 *       walked out to found a building should come home with one; refusing to build at all is the right
 *       answer for a charge that has nowhere to go, not for one that merely has nowhere big enough.</li>
 * </ul>
 *
 * <p>It extends {@link BlueTntBlock} deliberately: {@code HousePlacer} skips any captured Blue/Green TNT
 * when transcribing a template, so that guard covers this block too and a Cyan charge caught inside a
 * schematic can never chain into another house.
 */
public class CyanTntBlock extends BlueTntBlock {
    /** Clear ground demanded around the spot: half the longest big-house side, rounded up. */
    public static final int CLEARANCE = (HousePlacer.BIG_HOUSE_MAX_SIDE + 1) / 2;

    /** Scaled from Blue's {6,10,14,18} by the same factor the footprint grew. */
    private static final int[] RADII = {12, 20, 28, 36};

    public CyanTntBlock(Properties properties) {
        super(properties, () -> HousePlacer.BIG_HOUSES, CLEARANCE, RADII);
    }

    /**
     * No room for a grand house anywhere in reach -- build a small one instead of wasting the settler.
     *
     * <p>Runs the same search over the small pool at the small clearance, starting where the charge landed.
     */
    @Override
    protected BlockPos fallback(ServerLevel sl, BlockPos at, Direction doorFacing) {
        ResourceLocation small = HousePlacer.HOUSES[sl.random.nextInt(HousePlacer.HOUSES.length)];
        BlockPos spot = HousePlacer.place(sl, at, doorFacing, small)
                ? at
                : relocate(sl, at, doorFacing, small, new int[]{6, 10, 14, 18}, 8);
        if (spot != null) {
            TheVillageFightsBack.LOGGER.info(
                    "[The Village Fights Back] Cyan TNT at {} found no room for a grand house; built {} instead.",
                    at, small.getPath());
        }
        return spot;
    }

    @Override
    protected String label() {
        return "Cyan TNT";
    }
}
