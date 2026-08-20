package com.example.villagefightsback;

import net.minecraft.world.level.block.LeavesBlock;

/**
 * Fertile / Rich leaves: a real {@link LeavesBlock} -- so heightmaps (MOTION_BLOCKING_NO_LEAVES), the wall planner
 * and leaf rendering all treat it as foliage -- with normal leaf decay. Decay distance is measured from blocks in
 * the {@code #minecraft:logs} tag, so the fertile/rich logs are added to that tag (see data/minecraft/tags) and
 * fertile leaves attached to their (fertile) tree persist, while detached ones decay like any leaves.
 */
public class FertileLeavesBlock extends LeavesBlock {
    public FertileLeavesBlock(Properties properties) {
        super(properties);
    }

    // --- Flammable, like the vanilla leaves they stand in for -------------------------------------
    //
    // Vanilla wires leaf flammability inside FireBlock.bootStrap, whose setFlammable is private and takes
    // vanilla blocks only, so a modded leaf is simply absent from the fire table and will not burn. The loader
    // Fabric keeps a registry of burn/spread values instead of asking the block, so the same three numbers are
    // handed over from the mod initializer rather than overridden here. Still covers EVERY variant -- green, rich
    // and all four colour sets -- because the sweep below finds them by class, exactly as the Forge overrides did,
    // and a variant added later is picked up with no extra wiring.

    /**
     * Make every fertile leaf burn exactly as an oak leaf does.
     *
     * <p>The numbers are copied from vanilla's own oak-leaves entry rather than written out again. Fabric's two
     * arguments are easy to hand over the wrong way round, and one silently wrong ordering would give leaves that
     * catch like planks or barely catch at all; copying the entry cannot be ordered wrongly. It also keeps the
     * promise the Forge version made -- "burns at the rate a normal one does" -- true by construction rather than
     * by two constants matching a third place.
     *
     * <p>Called from the mod initializer, after the blocks are registered.
     */
    public static void registerFlammability() {
        net.fabricmc.fabric.api.registry.FlammableBlockRegistry fire =
                net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance();
        net.fabricmc.fabric.api.registry.FlammableBlockRegistry.Entry oak =
                fire.get(net.minecraft.world.level.block.Blocks.OAK_LEAVES);
        int registered = 0;
        for (net.minecraft.world.level.block.Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            if (block instanceof FertileLeavesBlock) {
                fire.add(block, oak.getBurnChance(), oak.getSpreadChance());
                registered++;
            }
        }
        // Reported at INFO on purpose: a count of 0 is what a broken initialiser order looks like, and it is the
        // only way this would show. Silence here would read exactly like success.
        com.example.villagefightsback.TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] {} fertile leaf blocks burn like oak leaves ({}/{}).",
                registered, oak.getBurnChance(), oak.getSpreadChance());
    }
}
