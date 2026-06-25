package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The Alien Smasher's two extra behaviours, both Forge-bus:
 *   - {@link #onSmasherHit(LivingIncomingDamageEvent)}: its signature triple block-and-fire blast. When an Alien Smasher
 *     hits a target, it carves three TNT-sized holes straight DOWN beneath the victim (at y, y-2, y-4) so the
 *     enemy tumbles in. The blast is built BY HAND (never vanilla {@code Explosion#explode}, which hardcodes
 *     entity damage) so it destroys blocks + scatters fire but deals NO creature damage and NO knockback.
 *   - {@link #onCrafted(PlayerEvent.ItemCraftedEvent)}: cosmetic tooltip faithfulness -- a freshly-smithed
 *     Alien Smasher with no stored enchantments is given Density 3 + Wind Burst 3 + Fire Aspect 2 (the guard
 *     mace). The combat baseline in {@link MaceCombatEvents} guarantees the behaviour regardless, so this is
 *     purely to show the enchants on the new item.
 *
 * The guard-mace baseline itself (Density >= 3, Wind Burst launch >= level-3, Fire Aspect 2 burn) lives in
 * {@link MaceCombatEvents}, applied as a floor whenever the weapon is an Alien Smasher.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class AlienSmasherEvents {
    private AlienSmasherEvents() {}

    private static final double BLAST_RADIUS = 3.0;        // TNT-sized carve per explosion
    private static final int VERTICAL_SPACING = 2;         // hole centres at y, y-2, y-4
    private static final float DROP_CHANCE = 0.35F;        // fraction of broken blocks that drop (DESTROY_WITH_DECAY feel)
    // A TNT-like resistance ceiling: anything tougher than this is left intact (obsidian = 1200, our brain
    // node / fake obsidian are >= 1200; reinforced/iron-grade walls below this still go). TNT itself can chew
    // through obsidian only over many blasts, so for a single carve we treat >= 600 as "too tough to break".
    private static final float RESISTANCE_CEILING = 600.0F;

    @SubscribeEvent
    public static void onSmasherHit(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (!(attacker.getMainHandItem().getItem() instanceof AlienSmasherItem)) return;
        if (!(victim.level() instanceof ServerLevel sl)) return;

        boolean grief = sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        // Three blasts straight down beneath the target: at its feet and two/four blocks lower, so it falls in.
        int baseY = (int) Math.floor(victim.getY());
        int x = (int) Math.floor(victim.getX());
        int z = (int) Math.floor(victim.getZ());
        for (int i = 0; i < 3; i++) {
            BlockPos center = new BlockPos(x, baseY - i * VERTICAL_SPACING, z);
            blast(sl, center, grief);
        }
    }

    /**
     * A MANUAL spherical block blast at {@code center}: it destroys blocks within {@link #BLAST_RADIUS}
     * (mobGriefing-gated), then scatters fire onto exposed air with a sturdy floor, and plays the explosion
     * sound + particles. It touches NO entities (no damage, no knockback) -- the goal is to dig a hole.
     */
    private static void blast(ServerLevel level, BlockPos center, boolean grief) {
        int r = (int) Math.ceil(BLAST_RADIUS);
        if (grief) {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
                if (center.distSqr(pos) > BLAST_RADIUS * BLAST_RADIUS) continue; // spherical, by distance
                BlockState s = level.getBlockState(pos);
                if (s.isAir()) continue;
                if (s.getDestroySpeed(level, pos) < 0.0F) continue;                       // unbreakable (bedrock/barrier)
                if (s.getBlock().getExplosionResistance() >= RESISTANCE_CEILING) continue; // brain node / obsidian-grade
                // DESTROY_WITH_DECAY feel: only a fraction of cells drop their items.
                level.destroyBlock(pos.immutable(), level.random.nextFloat() < DROP_CHANCE);
            }
            // fire pass: light exposed air that has a sturdy floor (like FireGrenade / explodeWithFire).
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
                if (center.distSqr(pos) > BLAST_RADIUS * BLAST_RADIUS) continue;
                if (!level.getBlockState(pos).isAir()) continue;
                BlockPos below = pos.below();
                if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                        && level.random.nextFloat() < 0.6F) {
                    level.setBlockAndUpdate(pos.immutable(), Blocks.FIRE.defaultBlockState());
                }
            }
        }
        // sound + particles always play (even with mobGriefing off, so the strike still reads).
        level.playSound(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.6F,
                (1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.getX() + 0.5, center.getY() + 0.5,
                center.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** Cosmetic: stamp a freshly-smithed Alien Smasher (no stored enchants) with the guard-mace enchants. */
    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting();
        if (!(result.getItem() instanceof AlienSmasherItem)) return;
        if (!EnchantmentHelper.getEnchantmentsForCrafting(result).isEmpty()) return; // player kept their own enchants (getEnchantmentsForCrafting reads the crafted stack's enchants)
        net.minecraft.core.RegistryAccess access = event.getEntity().level().registryAccess();
        result.enchant(ModEnchantments.holder(access, ModEnchantments.DENSITY), 3);
        result.enchant(ModEnchantments.holder(access, ModEnchantments.WIND_BURST), 3);
        result.enchant(ModEnchantments.holder(access, Enchantments.FIRE_ASPECT), 2);
    }
}
