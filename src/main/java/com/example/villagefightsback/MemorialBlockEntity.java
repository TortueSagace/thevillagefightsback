package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a {@link MemorialBlock} remembers: the fallen defender itself, and the epitaph carved on the stone.
 *
 * <p>The buried entity is kept as a full NBT snapshot, which is what lets the Limbo Scythe bring it back exactly
 * as it was rather than spawning a fresh villager. The epitaph is stored SEPARATELY from that snapshot, so the
 * words on the stone can say whatever the village wants without altering the villager waiting inside it. That
 * mirrors how MCA Reborn's own tombstone keeps its display name apart from its entity data.
 */
public class MemorialBlockEntity extends BlockEntity {
    private static final String NBT_OCCUPANT = "Occupant";
    private static final String NBT_EPITAPH = "Epitaph";

    /** The buried defender, or null for an empty grave. */
    private CompoundTag occupant;
    /** The line carved on the stone. Plain text: it is rendered by us, not resolved as a translation. */
    private String epitaph = "";

    public MemorialBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MEMORIAL_BE.get(), pos, state);
    }

    public boolean hasOccupant() {
        return occupant != null;
    }

    public String epitaph() {
        return epitaph;
    }

    /** Bury {@code snapshot} here under {@code epitaph}. */
    public void bury(CompoundTag snapshot, String epitaph) {
        this.occupant = snapshot;
        this.epitaph = epitaph;
        setChanged();
        sync();
    }

    /** Look at the occupant WITHOUT removing it (used to recognise whose grave this is). Null if empty. */
    public CompoundTag peekOccupant() {
        return occupant;
    }

    /** Take the occupant out, leaving the grave empty. Null if there was nobody in it. */
    public CompoundTag takeOccupant() {
        CompoundTag out = occupant;
        occupant = null;
        setChanged();
        sync();
        return out;
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // 1.21 threads a HolderLookup.Provider through every block-entity read/write hook, and the read hook is
    // loadAdditional rather than load. Nothing stored here is registry data (an entity snapshot and a string),
    // so the provider is simply passed on to super. This matches LimboGravelBlockEntity on this branch.
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (occupant != null) tag.put(NBT_OCCUPANT, occupant);
        tag.putString(NBT_EPITAPH, epitaph);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        occupant = tag.contains(NBT_OCCUPANT) ? tag.getCompound(NBT_OCCUPANT) : null;
        epitaph = tag.getString(NBT_EPITAPH);
    }

    // The epitaph has to reach the client, since the renderer draws it on the stone. The occupant snapshot rides
    // along too, which is harmless and keeps the two in step.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // main overrode onDataPacket to feed the packet tag back through load(). NeoForge's IBlockEntityExtension
    // now does exactly that by default (it calls loadWithComponents, which reaches loadAdditional above), and the
    // chunk-load path arrives via handleUpdateTag, which defaults the same way -- so both are left to the default
    // rather than reimplemented. Overriding handleUpdateTag keeps the read path explicit and symmetric with the
    // getUpdateTag above.
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }
}
