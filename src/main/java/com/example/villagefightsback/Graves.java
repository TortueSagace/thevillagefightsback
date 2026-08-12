package com.example.villagefightsback;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

    /**
     * Bury {@code deceased} at its death site. Silently does nothing when there is no room or no grave system
     * available, since failing to bury someone must never interrupt their death.
     *
     * @return true if a grave was raised
     */
    public static boolean bury(ServerLevel level, LivingEntity deceased, Component epitaph) {
        BlockPos spot = findGraveSpot(level, deceased.blockPosition());
        if (spot == null) return false;
        // ALWAYS this mod's own memorial, MCA installed or not. MCA's flat headstone was tried first at one
        // point, but its stone carries MCA's own header and footer around our line and reads far too long, and
        // the village's dead belong under the village's own stone. Our memorial is also immune to the fertile
        // spread by construction (BlockMatcher never converts this mod's namespace), so a grave cannot be eaten.
        // Graves MCA itself dug are still recognised and can still be raised with the Limbo Scythe.
        return buryInMemorial(level, spot, deceased, epitaph);
    }

    /** Bury {@code deceased} in this mod's own memorial, facing away from the body so the words read outward. */
    private static boolean buryInMemorial(ServerLevel level, BlockPos spot, LivingEntity deceased, Component epitaph) {
        net.minecraft.world.level.block.state.BlockState stone = ModBlocks.MEMORIAL.get().defaultBlockState()
                .setValue(MemorialBlock.FACING, deceased.getDirection().getOpposite());
        level.setBlock(spot, stone, 3);
        if (!(level.getBlockEntity(spot) instanceof MemorialBlockEntity grave)) {
            level.removeBlock(spot, false);
            return false;
        }
        CompoundTag snapshot = new CompoundTag();
        // saveWithoutId omits the entity id, which EntityType.create needs to rebuild it, so put it back.
        deceased.saveWithoutId(snapshot);
        net.minecraft.resources.ResourceLocation type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(deceased.getType());
        if (type == null) { level.removeBlock(spot, false); return false; }
        snapshot.putString("id", type.toString());
        grave.bury(snapshot, epitaph.getString());
        // Let MCA index it, so its villagers can find this grave and mourn at it like one of their own.
        com.example.villagefightsback.compat.MCATombstones.registerGrave(level, spot);
        return true;
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
                        if (!level.getBlockState(below).isFaceSturdy(level, below,
                                net.minecraft.core.Direction.UP)) continue;
                        return p;
                    }
                }
            }
        }
        return null;
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
            boolean mcaGrave = com.example.villagefightsback.compat.MCATombstones.isTombstone(level, p)
                    && com.example.villagefightsback.compat.MCATombstones.holdsDarius(level, p);
            boolean ourGrave = level.getBlockEntity(p) instanceof MemorialBlockEntity m
                    && m.hasOccupant() && isDariusNbt(m.peekOccupant());
            if (!mcaGrave && !ourGrave) continue;
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
}
