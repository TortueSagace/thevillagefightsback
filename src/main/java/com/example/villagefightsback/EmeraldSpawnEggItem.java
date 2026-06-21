package com.example.villagefightsback;

import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeSpawnEggItem;

/**
 * An emerald-faction spawn egg that respects the population cap ({@code emerald_mobs_max_number}). When the cap is
 * already reached the egg does NOT spawn a unit (and is not consumed); the player is told why on the action bar.
 * Below the cap it behaves exactly like a normal spawn egg.
 */
public class EmeraldSpawnEggItem extends ForgeSpawnEggItem {
    public EmeraldSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int bg, int hl, Item.Properties props) {
        super(type, bg, hl, props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!level.isClientSide() && level instanceof ServerLevel sl && EmeraldPopulation.atCap(sl)) {
            Player p = ctx.getPlayer();
            if (p != null) {
                p.displayClientMessage(Component.literal(
                        "Emerald population cap reached (" + Config.emeraldMobsMaxNumber()
                                + ") in loaded chunks -- no emerald unit spawned."), true);
            }
            return InteractionResult.FAIL; // do not spawn, do not consume the egg
        }
        return super.useOn(ctx);
    }
}
