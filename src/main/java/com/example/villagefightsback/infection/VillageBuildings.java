package com.example.villagefightsback.infection;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Protects the buildings of a NATURALLY GENERATED village from block infection by reading the village's real
 * structure pieces, rather than trying to recognise houses by eye.
 *
 * <h2>Why this replaced the flood-fill</h2>
 * The first attempt reused {@link com.example.villagefightsback.HouseBedFiller}'s room detector, which finds
 * interior floor cells and keeps the rectangular ones. In practice that protected almost nothing: on a live
 * world it registered TWO rooms for an entire village, because that detector only scans a 32-block radius and
 * an 8-block Y band around the brain node, while a village spans 60 to 80 blocks and the infection reaches 33
 * blocks at stage 2 and 67 at stage 3. Most houses were never even looked at, and stage 2 duly ate them.
 *
 * <p>A generated village already knows exactly where its buildings are: every house is its own
 * {@link StructurePiece} with an exact {@link BoundingBox}. Reading those is both cheaper and complete, and it
 * covers the whole village however far it sprawls.
 *
 * <p>Flat pieces are deliberately LEFT infectable. Streets, paths and farm plots are one or two blocks tall,
 * and the dirt-path spread running along a village's streets is the whole look of the takeover -- protecting
 * them would stop the infection at the village edge. Only pieces tall enough to be a building are claimed.
 */
public final class VillageBuildings {
    private VillageBuildings() {}

    /** A piece this tall or more counts as a building; anything flatter is a street, path or field. */
    private static final int MIN_BUILDING_HEIGHT = 4;

    /**
     * Claim every building of the village containing {@code center}. Cheap and one-shot: a structure start
     * already holds its full piece list, so this reads the village's own data and never scans the world or
     * forces chunks to load. Returns how many buildings were protected.
     */
    public static int protectVillageAt(ServerLevel level, BlockPos center) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(center, StructureTags.VILLAGE);
        if (start == null || !start.isValid()) {
            // The node may sit just outside any piece box (it settles beside the bell under MCA). Fall back to
            // the starts referenced by the node's own chunk, which is loaded by definition.
            start = fromChunk(level, center);
            if (start == null) return 0;
        }

        StructureZones zones = StructureZones.get(level);
        int claimed = 0;
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox b = piece.getBoundingBox();
            if (b.getYSpan() < MIN_BUILDING_HEIGHT) continue; // a street or a field, not a building
            zones.addBox(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
            claimed++;
        }
        return claimed;
    }

    /** The first village start referenced by {@code center}'s chunk, or null. */
    private static StructureStart fromChunk(ServerLevel level, BlockPos center) {
        // 1.19.2 has no Registries.STRUCTURE and no Registry#wrapAsHolder; the registry key lives on Registry as
        // STRUCTURE_REGISTRY, and a value is turned into a Holder by looking its key back up. Same test, same
        // answer -- "is this structure in the village tag" -- just spelled with the holder lookup that exists here.
        net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.Structure> structures =
                level.registryAccess().registryOrThrow(net.minecraft.core.Registry.STRUCTURE_REGISTRY);
        for (StructureStart s : level.structureManager().startsForStructure(new ChunkPos(center),
                structure -> structures.getResourceKey(structure)
                        .flatMap(structures::getHolder)
                        .map(holder -> holder.is(StructureTags.VILLAGE))
                        .orElse(false))) {
            if (s.isValid()) return s;
        }
        return null;
    }
}
