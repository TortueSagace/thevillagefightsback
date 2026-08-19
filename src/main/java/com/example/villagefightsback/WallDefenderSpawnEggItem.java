package com.example.villagefightsback;

import java.util.function.Supplier;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.SpawnEggItem;

/**
 * The Wall Defender spawn eggs. Identical to a normal {@link SpawnEggItem} except their icon is a live, spinning
 * Wall Defender, so the creative menu shows the actual defender -- the same animated look as the brain node UI.
 *
 * <p>On Forge the custom renderer is attached here, through initializeClient. Fabric has no such per-item client
 * extension, so the renderer is registered against this item from the client initializer instead (see
 * {@code TheVillageFightsBackClient}); the class itself stays free of client types, which is what kept a
 * dedicated server from ever loading the renderer.
 */
public class WallDefenderSpawnEggItem extends SpawnEggItem {
    public WallDefenderSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int background,
                                    int highlight, Properties props) {
        super(type.get(), background, highlight, props);
    }
}
