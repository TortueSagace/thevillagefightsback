package com.example.villagefightsback;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Hangs the {@link ModPaintings#RICKROLL} painting. Behaves like a vanilla painting item, except that it
 * always produces the one variant instead of a random one.
 *
 * <p>Creative-only: there is no recipe and nothing drops it, so the creative menu is the only source.
 *
 * <p>Getting a CHOSEN variant onto the entity is the only interesting part. {@code Painting.create} picks at
 * random from the placeable pool and the setter is private, so instead the entity is created and then handed
 * an {@code EntityTag} through {@link EntityType#updateCustomEntityTag}, which is public and is the same
 * mechanism a {@code /give} with painting NBT uses. That merges over the entity's own saved data, so facing
 * and position survive and only the variant changes -- and re-reading the variant resizes the hitbox, which is
 * why {@code survives()} is checked afterwards rather than before.
 */
public class RickrollPaintingItem extends Item {
    public RickrollPaintingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isVertical()) return InteractionResult.FAIL;   // walls only, like any painting

        BlockPos pos = ctx.getClickedPos().relative(face);
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (player != null && !player.mayUseItemAt(pos, face, stack)) return InteractionResult.FAIL;

        Level level = ctx.getLevel();
        Optional<Painting> created = Painting.create(level, pos, face);
        if (created.isEmpty()) return InteractionResult.CONSUME;
        Painting painting = created.get();

        CompoundTag entityTag = new CompoundTag();
        entityTag.putString("variant", TheVillageFightsBack.MODID + ":rickroll");
        CompoundTag tag = new CompoundTag();
        tag.put("EntityTag", entityTag);
        EntityType.updateCustomEntityTag(level, player, painting, tag);

        if (!painting.survives()) return InteractionResult.CONSUME;
        if (!level.isClientSide) {
            painting.playPlacementSound();
            level.gameEvent(player, GameEvent.ENTITY_PLACE, painting.position());
            level.addFreshEntity(painting);
        }
        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
