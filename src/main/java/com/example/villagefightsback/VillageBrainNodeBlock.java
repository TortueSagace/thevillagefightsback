package com.example.villagefightsback;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
 * The Village Brain Node block. Its level/emerald state is GLOBAL (see {@link BrainNodeData}) and shared
 * by every brainNode. Right-click for the village level + emeralds-to-next-level; right-click holding
 * emeralds to donate them and accelerate the evolution. The {@code charges} visual (0-4, reusing the
 * inverted respawn-anchor textures) tracks the global level.
 */
public class VillageBrainNodeBlock extends Block {
    public static final IntegerProperty CHARGES = IntegerProperty.create("charges", 0, 4);

    public VillageBrainNodeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGES, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGES);
    }

    /** Visual charge level (1-4) for a given brainNode level. */
    public static int chargesForLevel(int level) {
        return Math.max(1, Math.min(4, level));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        BrainNodeData data = BrainNodeData.get(sl.getServer());
        BrainNodeData.Econ econ = data.econ(GlobalPos.of(level.dimension(), pos.immutable())); // THIS village
        ItemStack held = player.getItemInHand(hand);

        // SURVIVAL: the node also eats the metals a stage costs. Fed exactly like emeralds, and the promotion
        // fires the moment the last of the tribute lands (addMetal re-runs the check).
        int cu = held.is(Items.COPPER_INGOT) ? held.getCount() : 0;
        int au = held.is(Items.GOLD_INGOT) ? held.getCount() : 0;
        int li = held.is(ModItems.LIMBO_INGOT.get()) ? held.getCount() : 0;
        if (cu + au + li > 0) {
            int gained = econ.addMetal(cu, au, li);
            if (!player.getAbilities().instabuild) held.shrink(cu + au + li);
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.3F);
            if (gained > 0) {
                player.displayClientMessage(
                        Component.literal(BrainNodeEvents.phaseBanner(econ.getLevel())), false);
            } else {
                player.displayClientMessage(metalNeeded(econ), false);
            }
            return InteractionResult.CONSUME;
        }

        if (held.is(Items.EMERALD)) {
            int donated = held.getCount();
            int gained = econ.addEmeralds(donated);
            if (!player.getAbilities().instabuild) held.shrink(donated);
            // An emerald is worth one point of standing, and nothing else. It used to BUY Hero of the Village
            // outright, a minute at a time, which meant a single emerald bought a village's trust -- the exact
            // thing the reputation system exists to stop. A hundred emeralds is still a route to Hero I; it is
            // just no longer a shortcut past every other way of earning it.
            if (player instanceof ServerPlayer donor) {
                ReputationData.get(sl.getServer()).add(donor, donated, "fed " + donated + " emerald");
            }
            // Reflect any level-up on the clicked block immediately; the rest sync via BrainNodeEvents.
            int target = chargesForLevel(econ.getLevel());
            if (state.getValue(CHARGES) != target) {
                sl.setBlock(pos, state.setValue(CHARGES, target), 3);
            }
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (gained > 0) {
                // Show the named phase in purple (SETTLEMENT / FERTILIZATION / EXPANSION / CULMINATION PHASE),
                // matching the discovery banner -- not a generic orange "evolved" line.
                player.displayClientMessage(
                        Component.literal(BrainNodeEvents.phaseBanner(econ.getLevel())), false);
            } else {
                player.displayClientMessage(info(econ), false);
            }
            return InteractionResult.CONSUME;
        }

        // Empty hand (or any non-emerald item): open the upgrade screen for THIS village.
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            int[] defenders = BrainNodeEvents.countDefenderTypes(sl, pos); // [villagers, guards, golems] in radius
            int fertilized = com.example.villagefightsback.infection.InfectionData.get(sl).convertedCount();
            net.minecraft.core.GlobalPos brainGp = net.minecraft.core.GlobalPos.of(sl.dimension(), pos.immutable());
            java.util.List<String> reflexLog = BrainReflexes.logFor(brainGp);
            java.util.List<String> organicLog = OrganicMass.logFor(brainGp);
            // Reputation is the faction's, not this node's, so both the number and the log are world-wide --
            // every brain node shows the same pair.
            ReputationData rep = ReputationData.get(sl.getServer());
            com.example.villagefightsback.network.ModNetwork.sendTo(sp,
                    new com.example.villagefightsback.network.OpenBrainNodeScreenPacket(
                            econ.getLevel(), BrainNodeData.MAX_LEVEL, econ.getEmeralds(), econ.costForNext(),
                            defenders[0], defenders[1], defenders[2], fertilized, econ.getOrganicPoints(),
                            rep.get(sp.getUUID()), reflexLog, organicLog, rep.logLines(),
                            metalTribute(sl, econ)));
        }
        return InteractionResult.CONSUME;
    }

    /**
     * The metal row for the brain node screen, or "" outside Survival.
     *
     * <p>Shows held / needed for each metal the NEXT stage wants, so the panel answers "what else does it
     * need" at a glance rather than only after you feed it something.
     */
    private static String metalTribute(ServerLevel sl, BrainNodeData.Econ econ) {
        if (!com.example.villagefightsback.WorldModeData.isSurvival(sl.getServer())) return "";
        if (econ.isMaxLevel()) return "";
        int lvl = econ.getLevel();
        int cu = BrainNodeData.Econ.copperFor(lvl);
        int au = BrainNodeData.Econ.goldFor(lvl);
        int li = BrainNodeData.Econ.limboFor(lvl);
        if (cu + au + li == 0) return "";
        StringBuilder sb = new StringBuilder();
        if (cu > 0) sb.append("§6").append(econ.getCopper()).append(" / ").append(cu).append(" copper");
        if (au > 0) {
            if (sb.length() > 0) sb.append("§7   ");
            sb.append("§e").append(econ.getGold()).append(" / ").append(au).append(" gold");
        }
        if (li > 0) {
            if (sb.length() > 0) sb.append("§7   ");
            sb.append("§b").append(econ.getLimbo()).append(" / ").append(li).append(" Limbo ingot");
        }
        return sb.toString();
    }

    /** What the stage still wants, so feeding a stack tells you what is missing rather than nothing at all. */
    private static Component metalNeeded(BrainNodeData.Econ econ) {
        if (econ.isMaxLevel()) return Component.literal("§7The brain node is at its final stage.");
        int lvl = econ.getLevel();
        StringBuilder sb = new StringBuilder("§7Still needed: ");
        sb.append("§a").append(Math.max(0, econ.costForNext() - econ.getEmeralds())).append(" emerald");
        int cu = Math.max(0, BrainNodeData.Econ.copperFor(lvl) - econ.getCopper());
        int au = Math.max(0, BrainNodeData.Econ.goldFor(lvl) - econ.getGold());
        int li = Math.max(0, BrainNodeData.Econ.limboFor(lvl) - econ.getLimbo());
        if (cu > 0) sb.append("§7, §6").append(cu).append(" copper");
        if (au > 0) sb.append("§7, §e").append(au).append(" gold");
        if (li > 0) sb.append("§7, §b").append(li).append(" Limbo ingot");
        return Component.literal(sb.toString());
    }

    private static Component info(BrainNodeData.Econ econ) {
        if (econ.isMaxLevel()) {
            return Component.literal("§aVillage Brain Node §7| Level §e" + econ.getLevel()
                    + " §7(max) §7| §a" + econ.getEmeralds() + " §7emeralds stored");
        }
        return Component.literal("§aVillage Brain Node §7| Level §e" + econ.getLevel()
                + " §7| §a" + econ.getEmeralds() + "§7/§a" + econ.costForNext()
                + " §7emeralds to level §e" + (econ.getLevel() + 1));
    }

    /**
     * Anti-grief: swinging at the brain node costs {@value ReputationEvents#NODE_MINING_PENALTY} reputation.
     *
     * <p>Enough, from nothing, to make you a Nightmare of the Village in one swing -- which is the intent. The
     * node is the village's heart and there is no reading of a pickaxe against it that the village could take
     * as anything but the worst thing you could do to it.
     *
     * <p>ONCE PER DAY, because this fires on the swing rather than the break and a held left-click fires it
     * every tick. Without that the first second of mining would cost thirty thousand points, and reputation
     * would stop being a scale and start being a switch.
     */
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        super.attack(state, level, pos, player);
        if (level.isClientSide) return;
        if (player instanceof ServerPlayer sp) ReputationEvents.onMinedBrainNode(sp);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level.isClientSide || oldState.is(this)) return;
        ServerLevel sl = (ServerLevel) level;
        BrainNodeData data = BrainNodeData.get(sl.getServer());
        GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
        data.addBlock(gp);
        BrainNodeEvents.scheduleBirthScouts(sl, pos); // one-time scout cats appear ~2s later, just above the node
        int target = chargesForLevel(data.econ(gp).getLevel());
        if (state.getValue(CHARGES) != target) {
            sl.setBlock(pos, state.setValue(CHARGES, target), 3);
        }
    }

    // Packed pos + game-time of the last CLEAN brainNode break (silk touch / creative). onRemove consults it
    // to tell a harmless pickup from any other removal -- a non-clean removal unleashes the boss.
    private static long cleanBreakPos = Long.MIN_VALUE;
    private static long cleanBreakTick = Long.MIN_VALUE;

    /** Mark a silk-touch / creative break as "clean" so it does NOT summon the boss. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            int silk = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                    net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, player.getMainHandItem());
            if (silk > 0) { // ONLY silk touch is a clean pickup; everything else (incl. creative) unleashes the boss
                cleanBreakPos = pos.asLong();
                cleanBreakTick = level.getGameTime();
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Drops the brain node ONLY on a silk-touch break; any other break consumes it (it becomes the boss). */
    @Override
    public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        ItemStack tool = params.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL);
        int silk = tool == null ? 0 : net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, tool);
        return silk > 0 ? List.of(new ItemStack(this)) : List.of(); // only silk touch yields the block back
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !newState.is(this)) {
            ServerLevel sl = (ServerLevel) level;
            BrainNodeData data = BrainNodeData.get(sl.getServer());
            GlobalPos key = GlobalPos.of(level.dimension(), pos.immutable());
            data.removeBlock(key);
            data.removeWallPlan(key); // forget the wall plan too -> replacing the brain node recomputes it fresh
            BrainNodeEvents.clearSurveyBackoff(key);
            // Any removal that is NOT a clean silk-touch / creative pickup unleashes the brain node boss here.
            boolean cleanBreak = cleanBreakPos == pos.asLong() && level.getGameTime() - cleanBreakTick <= 1;
            if (!cleanBreak) {
                // Start the respawn grace BEFORE summoning, so the auto-placer can't race a duplicate node in
                // while the boss emerges (it only summons on a non-clean removal, so the grace only gates those).
                BrainNodeEvents.recordRemoval(key);
                UnleashedManager.onNodeDestroyed(sl, pos.immutable());
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
