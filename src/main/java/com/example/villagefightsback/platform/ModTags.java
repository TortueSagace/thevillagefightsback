package com.example.villagefightsback.platform;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * The cross-mod block tags the mod reasons about, which on Forge came from {@code net.minecraftforge.common.Tags}.
 *
 * <p>Fabric's equivalent convention is the {@code c:} namespace. Tag files merge across mods, so the copies this
 * mod ships (under {@code data/c/tags/blocks/}) list the vanilla blocks while any other mod's glass lands in the
 * same tag -- which is the behaviour the Forge tags gave, and what the emerald diggers and the infection matcher
 * are written against.
 */
public final class ModTags {
    private ModTags() {}

    /** Glass blocks: plain, stained and tinted. */
    public static final TagKey<Block> GLASS =
            TagKey.create(Registries.BLOCK, new ResourceLocation("c", "glass_blocks"));

    /** Glass panes: plain and stained. */
    public static final TagKey<Block> GLASS_PANES =
            TagKey.create(Registries.BLOCK, new ResourceLocation("c", "glass_panes"));

    /** Ore blocks of every kind, overworld and nether. */
    public static final TagKey<Block> ORES =
            TagKey.create(Registries.BLOCK, new ResourceLocation("c", "ores"));

    /** Full blocks of a crafting resource: iron, gold, diamond, and the rest. */
    public static final TagKey<Block> STORAGE_BLOCKS =
            TagKey.create(Registries.BLOCK, new ResourceLocation("c", "storage_blocks"));
}
