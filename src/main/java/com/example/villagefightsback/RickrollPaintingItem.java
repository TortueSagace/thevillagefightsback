package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
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
 * <p>Built on {@code Painting}'s public four-argument constructor, which takes the variant outright. The
 * obvious route -- {@code Painting.create} plus an override -- is worse in two ways: it draws at random from
 * the {@code placeable} tag, so it fails outright if a datapack empties that tag even though a 1x1 would
 * plainly fit, and it prefers the LARGEST variant that fits, so it does needless work sizing a painting we are
 * about to resize anyway.
 *
 * <p>1.21 note: identical to main but for the holder. A {@code DeferredHolder} IS a {@code Holder}, so it goes
 * straight into the constructor; main's {@code RegistryObject} has to be unwrapped with {@code getHolder()}.
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
        Painting painting = new Painting(level, pos, face, ModPaintings.RICKROLL);
        if (!painting.survives()) return InteractionResult.CONSUME;   // no room, or something already there

        if (!level.isClientSide) {
            painting.playPlacementSound();
            level.gameEvent(player, GameEvent.ENTITY_PLACE, painting.position());
            level.addFreshEntity(painting);
        }
        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
