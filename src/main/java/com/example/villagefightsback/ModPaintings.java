package com.example.villagefightsback;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Painting-variant registration. */
public final class ModPaintings {
    private ModPaintings() {}

    public static final DeferredRegister<PaintingVariant> PAINTINGS =
            DeferredRegister.create(Registries.PAINTING_VARIANT, TheVillageFightsBack.MODID);

    /**
     * "Rickroll": a 1x1 animated painting, and a joke, and nothing else.
     *
     * <p>16x16 is one block. The texture at {@code textures/painting/rickroll.png} is a 40-frame vertical
     * strip with a {@code .mcmeta} beside it, which is how any atlas sprite animates -- painting sprites are
     * no exception.
     *
     * <p>Deliberately NOT in the {@code minecraft:placeable} painting tag. That tag is the pool vanilla draws
     * from when somebody hangs an ordinary painting, and being in it would mean any painting in the world
     * could randomly come out as this. It is placed only by {@link RickrollPaintingItem}, which names it
     * explicitly.
     *
     * <p>1.21 note: {@code PaintingVariant} is a record of (width, height, assetId) here -- main's 1.20.1
     * version derives the texture path from the registry name, while this one is told it outright. Same file
     * either way. Painting variants are still a CODE registry on 1.21.1; they only become data-driven later.
     */
    public static final DeferredHolder<PaintingVariant, PaintingVariant> RICKROLL =
            PAINTINGS.register("rickroll",
                    () -> new PaintingVariant(16, 16, TheVillageFightsBack.id("rickroll")));

    public static void register(IEventBus modBus) {
        PAINTINGS.register(modBus);
    }
}
