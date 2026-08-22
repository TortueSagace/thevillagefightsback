package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * "Is this chest the village's?" -- the one answer every loot modifier in the mod asks.
 *
 * <p>They each used to answer it from the loot TABLE ID alone, which cannot tell a village apart from a
 * village. {@code minecraft:chests/village/village_armorer} is the table for the armourer's chest in every
 * village in the world, including the ones the brain node has never heard of, so the mod's rewards were
 * being scattered through villages that were not part of the mod's story at all.
 *
 * <p>Ownership is a question about the GROUND, so it is answered the same way
 * {@link VillageChestProtection#isVillageProperty} answers it for the chest-protection rules -- one rule,
 * one place, and the two can never drift apart:
 * <ul>
 *   <li><b>Our own buildings count outright.</b> The blue, cyan and green TNT structures fill their chests
 *       from {@code thevillagefightsback:chests/*}, and those tables generate nowhere else, so the id is
 *       proof enough.</li>
 *   <li><b>A vanilla village chest counts once the brain has claimed the ground under it</b> -- either it
 *       is inside a building the node claimed, or it is within the node's defended radius.</li>
 * </ul>
 *
 * <p>The timing works out because a naturally generated chest rolls its table when it is first OPENED, not
 * when the chunk is generated. By then the player has walked into the village, which is exactly when the
 * brain node gets placed, so "the village chests already there when the brain wakes up" is the literal
 * behaviour rather than an approximation of it.
 */
public final class VillageLoot {
    private VillageLoot() {}

    public static boolean isVillageChest(LootContext ctx) {
        ResourceLocation table = ctx.getQueriedLootTableId();
        if (table == null) return false;

        if (TheVillageFightsBack.MODID.equals(table.getNamespace()) && table.getPath().startsWith("chests/")) {
            return true;
        }
        if (!"minecraft".equals(table.getNamespace()) || !table.getPath().startsWith("chests/village/")) {
            return false;
        }

        Vec3 origin = ctx.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) return false;                    // no position -> cannot claim it
        ServerLevel level = ctx.getLevel();
        if (level == null) return false;
        return VillageChestProtection.isVillageProperty(level, BlockPos.containing(origin));
    }
}
