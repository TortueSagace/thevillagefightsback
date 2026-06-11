package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Village Gravemind block. Its level/emerald state is GLOBAL (see {@link GravemindData}) and shared
 * by every gravemind. Right-click for the village level + emeralds-to-next-level; right-click holding
 * emeralds to donate them and accelerate the evolution. The {@code charges} visual (0-4, reusing the
 * inverted respawn-anchor textures) tracks the global level.
 */
public class VillageGravemindBlock extends Block {
    public static final IntegerProperty CHARGES = IntegerProperty.create("charges", 0, 4);

    public VillageGravemindBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGES, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGES);
    }

    /** Visual charge level (1-4) for a given gravemind level. */
    public static int chargesForLevel(int level) {
        return Math.max(1, Math.min(4, level));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        GravemindData data = GravemindData.get(sl.getServer());
        GravemindData.Econ econ = data.econ(GlobalPos.of(level.dimension(), pos.immutable())); // THIS village
        ItemStack held = player.getItemInHand(hand);

        // DEBUG/admin: right-click with a stick to instantly paste the computed wall (bypasses builders).
        if (held.is(Items.STICK)) {
            int n = GravemindEvents.debugPasteWall(sl, pos);
            player.displayClientMessage(Component.literal(n < 0
                    ? "§c[debug] no wall: not enough beds nearby"
                    : "§e[debug] pasted §a" + n + "§e wall blocks"), false);
            return InteractionResult.CONSUME;
        }

        if (held.is(Items.EMERALD) && !econ.isMaxLevel()) {
            int donated = held.getCount();
            int gained = econ.addEmeralds(donated);
            if (!player.getAbilities().instabuild) held.shrink(donated);
            // Reflect any level-up on the clicked block immediately; the rest sync via GravemindEvents.
            int target = chargesForLevel(econ.getLevel());
            if (state.getValue(CHARGES) != target) {
                sl.setBlock(pos, state.setValue(CHARGES, target), 3);
            }
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (gained > 0) {
                player.displayClientMessage(Component.literal(
                        "§6The Village Gravemind evolves — this village is now level §e" + econ.getLevel()
                        + (econ.isMaxLevel() ? " §6(max)!" : "§6!")), false);
            } else {
                player.displayClientMessage(info(econ), false);
            }
            return InteractionResult.CONSUME;
        }

        // Empty hand (or any non-emerald item): open the upgrade screen for THIS village.
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            int[] defenders = GravemindEvents.countDefenderTypes(sl, pos); // [villagers, guards, golems] in radius
            int fertilized = com.example.villagergunners.infection.InfectionData.get(sl).convertedCount();
            com.example.villagergunners.network.ModNetwork.sendTo(sp,
                    new com.example.villagergunners.network.OpenGravemindScreenPacket(
                            econ.getLevel(), GravemindData.MAX_LEVEL, econ.getEmeralds(), econ.costForNext(),
                            defenders[0], defenders[1], defenders[2], fertilized));
        }
        return InteractionResult.CONSUME;
    }

    private static Component info(GravemindData.Econ econ) {
        if (econ.isMaxLevel()) {
            return Component.literal("§aVillage Gravemind §7| Level §e" + econ.getLevel() + " §7(max)");
        }
        return Component.literal("§aVillage Gravemind §7| Level §e" + econ.getLevel()
                + " §7— §a" + econ.getEmeralds() + "§7/§a" + econ.costForNext()
                + " §7emeralds to level §e" + (econ.getLevel() + 1));
    }

    // Global throttle so sustained mining doesn't re-aggro/spam the message every tick.
    private static long lastAttackResponse = Long.MIN_VALUE;

    /**
     * Anti-grief: the instant any player starts mining the gravemind, the whole village turns on them and a
     * warning is shown. This stops players from mining + replacing the gravemind to farm bonuses. Throttled.
     */
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        super.attack(state, level, pos, player);
        if (level.isClientSide) return;
        ServerLevel sl = (ServerLevel) level;
        long now = sl.getGameTime();
        if (now - lastAttackResponse < 40L) return; // at most once per ~2s
        lastAttackResponse = now;
        GunnerEvents.aggroDefendersAgainst(sl, pos, 100.0, player, 200); // village attacks the griefer ~10s
        sl.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("§cThe gravemind is attacked!"), false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Placing the gravemind by hand founds the village -> Hero of the Village for the placer.
        if (!level.isClientSide && placer instanceof ServerPlayer player) {
            GravemindEvents.rewardHero(player);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level.isClientSide || oldState.is(this)) return;
        ServerLevel sl = (ServerLevel) level;
        GravemindData data = GravemindData.get(sl.getServer());
        GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
        data.addBlock(gp);
        int target = chargesForLevel(data.econ(gp).getLevel());
        if (state.getValue(CHARGES) != target) {
            sl.setBlock(pos, state.setValue(CHARGES, target), 3);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !newState.is(this)) {
            GravemindData data = GravemindData.get(((ServerLevel) level).getServer());
            GlobalPos key = GlobalPos.of(level.dimension(), pos.immutable());
            data.removeBlock(key);
            data.removeWallPlan(key); // forget the wall plan too -> replacing the gravemind recomputes it fresh
            GravemindEvents.clearSurveyBackoff(key);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
