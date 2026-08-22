package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Keeps an Obliteration Furnace LOOKING like what {@link ObliterationData} says it is.
 *
 * <p>It holds no state of its own, on purpose. The fuel and the obliteration list live in the world-level
 * record so they keep working while this chunk is unloaded, which is the whole design (see
 * {@link ObliterationData}). Duplicating either here would create a second source of truth that drifts the
 * moment the chunk is out of sight, and the first symptom would be a furnace claiming to burn while the ban it
 * is supposed to enforce has already lapsed.
 *
 * <p>So its entire job is the resync: when the chunk comes back, read the record and set {@link
 * ObliterationFurnaceBlock#LIT} to match. That is what makes a furnace that ran dry while nobody was watching
 * go dark the instant you walk up to it, instead of sitting there glowing at a ban that expired days ago.
 *
 * <h2>...and one slot, since 1.7.2</h2>
 * It now also exposes a single-slot fuel inventory, so a hopper, a dropper or anything else that speaks the
 * item-handler capability can feed it. That REVERSES the original decision, which was that there deliberately
 * was no slot "because a slot would let a hopper feed the furnace forever". The reasoning survives as a
 * warning rather than a prohibition: the emerald a minute is still charged, so an automated furnace is an
 * automated COST rather than a free permanent ban. What changed is that paying it no longer means standing
 * there clicking.
 *
 * <p>The slot is a letterbox, not a store: whatever lands in it is converted to burn time on the next tick and
 * consumed. Nothing is ever left in it to drift out of step with {@link ObliterationData}, which is still the
 * only place fuel actually lives.
 */
public class ObliterationFurnaceBlockEntity extends BlockEntity {
    /** How often to re-check the record. Once a second is plenty for a light that changes twice an hour. */
    private static final int RESYNC_INTERVAL = 20;

    /**
     * The letterbox. One slot, emeralds and emerald blocks only, emptied on the next tick.
     *
     * <p>{@code isItemValid} asks {@link ObliterationFurnaceBlock#fuelValue}, so a hopper is told "no" for
     * anything that is not fuel instead of pushing it in and having it dropped on the floor.
     */
    private final net.minecraftforge.items.ItemStackHandler fuelSlot =
            new net.minecraftforge.items.ItemStackHandler(1) {
                @Override
                public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
                    return ObliterationFurnaceBlock.fuelValue(stack) > 0;
                }

                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
                }
            };

    private final net.minecraftforge.common.util.LazyOptional<net.minecraftforge.items.IItemHandler> fuelCap =
            net.minecraftforge.common.util.LazyOptional.of(() -> fuelSlot);

    public ObliterationFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.OBLITERATION_FURNACE_BE.get(), pos, state);
    }

    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> cap, net.minecraft.core.Direction side) {
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
            return fuelCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fuelCap.invalidate();
    }

    /**
     * The slot survives a reload.
     *
     * <p>It only ever holds anything for the fraction of a second between a hopper pushing and the next tick
     * burning it -- but a chunk can unload inside that window, and an item quietly vanishing is worse than a
     * little NBT.
     */
    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("FuelSlot", fuelSlot.serializeNBT());
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        if (tag.contains("FuelSlot")) fuelSlot.deserializeNBT(tag.getCompound("FuelSlot"));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ObliterationFurnaceBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        be.burnDelivered(sl, pos);
        if (level.getGameTime() % RESYNC_INTERVAL != 0) return;
        boolean burning = ObliterationData.get(sl.getServer())
                .isBurning(GlobalPos.of(sl.dimension(), pos.immutable()));
        if (state.getValue(ObliterationFurnaceBlock.LIT) != burning) {
            syncLit(sl, pos, burning);
        }
    }

    /**
     * Empty the letterbox into the record.
     *
     * <p>Every tick rather than on the resync interval, so a delivery is never visibly sitting in a slot the
     * player has no screen for. It is one {@code isEmpty} test in the overwhelmingly common case.
     */
    private void burnDelivered(ServerLevel sl, BlockPos pos) {
        net.minecraft.world.item.ItemStack held = fuelSlot.getStackInSlot(0);
        if (held.isEmpty()) return;
        int per = ObliterationFurnaceBlock.fuelValue(held);
        if (per <= 0) return;                       // isItemValid should have refused it; cheap insurance
        int count = held.getCount();
        fuelSlot.setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY);
        ObliterationData.get(sl.getServer())
                .addFuel(GlobalPos.of(sl.dimension(), pos.immutable()), per * count);
        syncLit(sl, pos, true);
    }

    /** Set the blockstate's lit flag, which also drives the light level and the animated fire texture. */
    public static void syncLit(ServerLevel level, BlockPos pos, boolean lit) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ObliterationFurnaceBlock)) return;
        if (state.getValue(ObliterationFurnaceBlock.LIT) == lit) return;
        level.setBlock(pos, state.setValue(ObliterationFurnaceBlock.LIT, lit), 3);
    }
}
