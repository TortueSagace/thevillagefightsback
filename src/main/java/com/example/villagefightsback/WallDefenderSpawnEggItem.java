package com.example.villagefightsback;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

/**
 * The Wall Defender spawn eggs. Identical to a normal {@link DeferredSpawnEggItem} except their icon is a live,
 * spinning Wall Defender (via {@code WallDefenderItemRenderer}), so the creative menu shows the actual defender
 * -- the same animated look as the brain node UI. The custom renderer is client-only (initializeClient runs
 * only on the client, so the client class never loads on a dedicated server).
 */
public class WallDefenderSpawnEggItem extends DeferredSpawnEggItem {
    public WallDefenderSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int background,
                                    int highlight, Properties props) {
        super(type, background, highlight, props);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.example.villagefightsback.client.WallDefenderItemRenderer.instance();
            }
        });
    }
}
