package com.example.villagefightsback.platform;

import java.util.List;

import net.minecraft.world.entity.item.ItemEntity;

/**
 * An entity whose death drops are being collected rather than dropped, so the LivingDropsEvent can be fired with
 * the real list before anything reaches the world.
 *
 * <p>Implemented on LivingEntity by a mixin; the capture window is open only for the length of one
 * {@code dropAllDeathLoot} call.
 */
public interface DropCapture {
    /** The list drops are being diverted into, or null when nothing is being captured. */
    List<ItemEntity> tvfbCapturedDrops();
}
