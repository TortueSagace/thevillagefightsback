package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Lets MCA Reborn's own Scythe raise the dead from THIS mod's memorial.
 *
 * <p>MCA's scythe only understands MCA's tombstone block entity, so on our stone it would do nothing and read as
 * broken to a player holding the tool the game told them raises the dead. We cannot change MCA's item, but we do
 * not need to: the interaction can be answered from our side. When a player right-clicks one of our memorials
 * holding MCA's scythe, we perform the resurrection ourselves and cancel the interaction so MCA's item never
 * runs its own (which would fail on a block entity it does not recognise).
 *
 * <p>Matched by registry id, so MCA stays a soft dep and this is dead weight in a pack without it.
 */
// GAME bus: a player interaction. (1.21 throws for an @EventBusSubscriber class with no handler at all.)
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class MemorialScytheEvents {
    private MemorialScytheEvents() {}

    private static final ResourceLocation MCA_SCYTHE = ResourceLocation.fromNamespaceAndPath("mca", "scythe");

    @SubscribeEvent
    public static void onScytheOnMemorial(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ItemStack held = event.getItemStack();
        if (!MCA_SCYTHE.equals(BuiltInRegistries.ITEM.getKey(held.getItem()))) return;

        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof MemorialBlockEntity grave) || !grave.hasOccupant()) return;

        CompoundTag occupant = grave.takeOccupant();
        if (occupant == null || !Graves.spawnFromNbt(level, pos, occupant)) return;

        com.example.villagefightsback.compat.MCATombstones.unregisterGrave(level, pos);
        level.destroyBlock(pos, false);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.02);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);

        // Cancelled so MCA's scythe does not then try to act on a block entity it cannot read.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }
}
