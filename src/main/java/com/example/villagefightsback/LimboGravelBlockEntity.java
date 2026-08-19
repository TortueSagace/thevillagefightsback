package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Render data for a {@link LimboGravelBlock}: which entity it holds (its type id -> colour palette), the held
 * creature's display name, whether it is a wither-storm part, and -- for a &gt;150 HP "unstable" hold -- the
 * game-time the gravel self-destructs (so the client can pulse purple faster as that deadline nears). This is a
 * pure render cache: {@link LimboData} is the authority and re-stamps the block entity whenever a gravel is
 * placed or lands (a falling block does not carry its block entity, so we repopulate it on landing).
 */
public class LimboGravelBlockEntity extends BlockEntity {
    private String typeId = "";
    private String holdName = "";
    private boolean stormPart = false;
    private long unstableUntil = 0L; // 0 = stable; else the game-time at which the gravel self-destructs

    public LimboGravelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.LIMBO_GRAVEL_BE.get(), pos, state);
    }

    /** Stamp the hold data (server side) and sync it to nearby clients for the renderer. */
    public void setHold(String typeId, String holdName, boolean stormPart, long unstableUntil) {
        this.typeId = typeId == null ? "" : typeId;
        this.holdName = holdName == null ? "" : holdName;
        this.stormPart = stormPart;
        this.unstableUntil = unstableUntil;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public String typeId() { return typeId; }
    public String holdName() { return holdName; }
    public boolean stormPart() { return stormPart; }
    public long unstableUntil() { return unstableUntil; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("type", typeId);
        tag.putString("holdName", holdName);
        tag.putBoolean("storm", stormPart);
        tag.putLong("unstable", unstableUntil);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        typeId = tag.getString("type");
        holdName = tag.getString("holdName");
        stormPart = tag.getBoolean("storm");
        unstableUntil = tag.getLong("unstable");
    }

    // --- client sync (the renderer reads typeId/unstableUntil) ---
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
