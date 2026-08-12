package com.example.villagefightsback;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Burial and resurrection for the village's fallen.
 *
 * <p>A villager that has killed at least one hostile has fought for the village, and is buried where it falls
 * with an epitaph naming what it did. A grave holds the whole villager, so the Limbo Scythe can bring it back.
 * General Darius is always buried, kills or not: he is the village's champion and only ever dies in its defence.
 *
 * <p>Which headstone gets used depends on the modpack. With MCA Reborn installed the grave is one of MCA's OWN
 * tombstones (see {@link com.example.villagefightsback.compat.MCATombstones}), so it counts toward an MCA
 * graveyard, MCA's villagers mourn at it, and MCA's own Scythe works on it as well as ours. Without MCA the
 * grave is this mod's memorial block. Either way the Limbo Scythe raises it.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Graves {
    private Graves() {}

    /** Marks a stored snapshot as General Darius, so a later spawn can find and clear his old grave. */
    public static final String NBT_DARIUS_GRAVE = "vgun_darius_grave";

    /**
     * Bury {@code deceased} at its death site. Silently does nothing when there is no room or no grave system
     * available, since failing to bury someone must never interrupt their death.
     *
     * @return true if a grave was raised
     */
    public static boolean bury(ServerLevel level, LivingEntity deceased, Component epitaph) {
        BlockPos spot = com.example.villagefightsback.compat.MCATombstones.findGraveSpot(
                level, deceased.blockPosition());
        if (spot == null) return false;
        // MCA's own tombstone when it is installed: a grave its graveyards, its mourners and its Scythe all
        // understand. Our memorial block is only the fallback for a pack without MCA.
        return com.example.villagefightsback.compat.MCATombstones.bury(level, spot, deceased, epitaph);
    }

    /**
     * Rebuild an entity from a grave's stored snapshot and release it at {@code at}, at full health.
     *
     * <p>A fresh UUID is assigned: the snapshot still carries the dead villager's, and reusing it risks
     * colliding with anything the world still remembers under that id.
     */
    public static boolean spawnFromNbt(ServerLevel level, BlockPos at, CompoundTag nbt) {
        return EntityType.create(nbt, level).map(e -> {
            e.setUUID(UUID.randomUUID());
            e.moveTo(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5,
                    level.getRandom().nextFloat() * 360.0F, 0.0F);
            if (e instanceof LivingEntity le) le.setHealth(le.getMaxHealth());
            return level.addFreshEntity(e);
        }).orElse(false);
    }

    /**
     * Bury the fallen. LOWEST priority so every other death handler (loot, the emerald economy, the brain's
     * bookkeeping) has already run and cannot be disturbed by the grave being placed.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;

        if (dead instanceof GeneralDariusEntity) {
            // The champion always gets a grave, whether or not he took anyone with him.
            bury(level, dead, Component.literal("General Darius, champion of the village"));
            return;
        }
        if (!(dead instanceof Villager)) return;
        if (!VillagerKills.earnedGrave(dead)) return;   // never fought: no headstone, so a siege cannot carpet the village
        bury(level, dead, VillagerKills.epitaph(dead));
    }

    /**
     * Clear the previous General Darius's grave within {@code radius} of {@code around}.
     *
     * <p>Called when the brain node marches a NEW Darius out: the village has its champion back, so the memorial
     * to the last one is retired rather than leaving a graveyard of Dariuses accumulating around the node.
     */
    public static void clearDariusGrave(ServerLevel level, BlockPos around, int radius) {
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-radius, -4, -radius),
                                                 around.offset(radius, 4, radius))) {
            if (!level.isLoaded(p)) continue;
            if (!com.example.villagefightsback.compat.MCATombstones.isTombstone(level, p)) continue;
            if (!com.example.villagefightsback.compat.MCATombstones.holdsDarius(level, p)) continue;
            level.destroyBlock(p.immutable(), false);
            return;   // at most one champion's grave per village
        }
    }

    /** True if this snapshot is General Darius (used to recognise his grave). */
    public static boolean isDariusNbt(CompoundTag nbt) {
        String id = nbt.getString("id");
        return id.equals(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(ModEntities.GENERAL_DARIUS.get()).toString());
    }

    /** Convenience for callers holding an entity rather than a snapshot. */
    public static boolean isDarius(Entity e) {
        return e instanceof GeneralDariusEntity;
    }
}
