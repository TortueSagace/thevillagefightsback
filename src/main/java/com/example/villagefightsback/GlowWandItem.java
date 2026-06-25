package com.example.villagefightsback;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The Glow Wand: right-click to give every unit of this mod within {@value #RADIUS} blocks the Glowing effect for
 * 3 seconds, so you can spot them through walls. "Units of this mod" = every entity this mod registers (the emerald
 * faction, emeraldgers, wall defenders, the vindictive units, Darius, the mod's golems, ...) plus the village
 * faction it builds on: villagers, iron golems and Guard Villagers guards.
 */
public class GlowWandItem extends Item {
    private static final double RADIUS = 128.0;
    private static final int GLOW_TICKS = 20 * 3; // 3 seconds
    private static final ResourceLocation GUARD_ID = ResourceLocation.fromNamespaceAndPath("guardvillagers", "guard");

    public GlowWandItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && level instanceof ServerLevel sl) {
            AABB box = player.getBoundingBox().inflate(RADIUS);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, GlowWandItem::isModMob)) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false, true));
            }
            // Blocks can't take the Glowing outline, so mark the 4 summoning node blocks (brain node + golem
            // factory / barracks / lure tower) with a 3s lime swirl instead.
            BrainNodeData data = BrainNodeData.get(sl.getServer());
            markNodes(sl, player, data.blocks());
            markNodes(sl, player, data.nodes());
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
        }
        player.getCooldowns().addCooldown(this, 20); // 1s, so it isn't spammed
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** Mark every node in {@code set} that is within {@link #RADIUS} of the player (same dimension) with a 3s lime
     *  swirl, so the summoning node blocks are easy to spot alongside the glowing units. */
    private static void markNodes(ServerLevel sl, Player player, java.util.Set<net.minecraft.core.GlobalPos> set) {
        double r2 = RADIUS * RADIUS;
        for (net.minecraft.core.GlobalPos gp : set) {
            if (!gp.dimension().equals(sl.dimension())) continue;
            net.minecraft.core.BlockPos p = gp.pos();
            if (net.minecraft.world.phys.Vec3.atCenterOf(p).distanceToSqr(player.position()) <= r2) {
                LimboBanishFx.startLimeAt(sl, p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            }
        }
    }

    /** A living unit that belongs to this mod: any entity in the mod's namespace, or the village faction it uses
     *  (villagers, iron golems, Guard Villagers guards). */
    private static boolean isModMob(LivingEntity e) {
        if (!e.isAlive()) return false;
        if (e instanceof Villager || e instanceof IronGolem) return true;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return id != null && (id.getNamespace().equals(TheVillageFightsBack.MODID) || GUARD_ID.equals(id));
    }
}
