package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * The village puts up a statue of its champion once he has earned it.
 *
 * <p>Twenty kills, and a Black TNT is placed somewhere clear nearby; it raises a thirty-six block Darius
 * in his full Limbo set, built block-by-block by {@link HouseBuildManager} like every other structure the
 * village puts up. The statue itself is generated from his own armour textures rather than drawn -- see
 * {@code tools/gen_darius_statue.py}.
 *
 * <p>Kills are counted here rather than in {@link VillagerKills}, which only counts villagers and guards
 * ({@code isDefender} excludes anything that is not one) and would never have seen him.
 *
 * <h2>Who builds it</h2>
 * The brain node sends a SETTLER, exactly as it does for a barracks or a lure tower: the village raising a
 * monument to its champion is a village decision, and it should look like one -- somebody walks out past the
 * wall carrying a black charge. Only when there is no node to ask (a hand-placed Darius has no home brain)
 * does it fall back to placing the charge itself.
 *
 * <h2>Where it goes</h2>
 * Away from the buildings, which is mostly a matter of asking. {@link HousePlacer} already refuses to place
 * anything that overlaps a claimed building ({@code StructureZones.intersectsForeign}) or a player's
 * "do not build here" sign ({@code MarkerZones}), so the search only has to offer it somewhere with room and
 * let those two refusals do the judging. If nothing within reach is clear the statue simply waits for the
 * next kill, which is the right outcome: a monument is not worth flattening a house for.
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DariusStatue {
    private DariusStatue() {}

    /** The bill. */
    public static final int KILLS_REQUIRED = 20;

    /** How near an Arm's kill a Darius has to be to be credited with it. They are leashed to 50. */
    private static final double ARM_CREDIT_RANGE = 64.0;

    /** How far out to look for somewhere to put it, and how coarsely. */
    private static final int MIN_AWAY = 24;
    private static final int MAX_AWAY = 48;
    private static final int BEARINGS = 8;

    /** The template's footprint, from tools/gen_darius_statue.py: 18 wide, 10 deep, 36 tall. */
    private static final int HALF_W = 9;
    private static final int HALF_D = 5;
    private static final int HEIGHT = 36;

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        GeneralDariusEntity darius = creditFor(event);
        if (darius == null) return;
        if (!(darius.level() instanceof ServerLevel sl)) return;
        if (event.getEntity() == darius) return;

        int kills = darius.recordKill();
        // Logged every time, because "no statue ever appeared" is otherwise indistinguishable from "he was
        // never actually credited with the kills" -- and most of what dies around him is killed by his
        // escort, whose kills are theirs and not his.
        TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] General Darius has {} kill(s) of the {} his statue costs.",
                kills, KILLS_REQUIRED);
        if (kills != KILLS_REQUIRED) return;      // exactly once, on the twentieth

        // The village builds it. A settler walks out with the charge, the way every other structure is
        // raised; the direct placement below is only for a Darius no village ever summoned.
        net.minecraft.core.GlobalPos home = darius.getHomeBrain();
        if (home != null) {
            com.example.villagefightsback.wall.WallPlan plan =
                    BrainNodeData.get(sl.getServer()).getWallPlan(home);
            if (ExpansionManager.summonReflexSettler(sl, home, plan, ExpansionManager.TNT_BLACK)) {
                sl.players().forEach(p -> p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§8The village sends someone out to raise a statue to General Darius."), false));
                TheVillageFightsBack.LOGGER.info(
                        "[The Village Fights Back] a settler was dispatched to build General Darius's statue.");
                return;
            }
            // No settler could be put on the ground -- nowhere standable outside the wall, most likely.
            // Fall through and place it directly rather than swallowing the twentieth kill silently, which
            // would mean the statue is never built and nothing ever says why.
            TheVillageFightsBack.LOGGER.info(
                    "[The Village Fights Back] no settler could be dispatched for the statue; placing it directly.");
        }

        BlockPos spot = findSpot(sl, darius.blockPosition());
        if (spot == null) {
            TheVillageFightsBack.LOGGER.info(
                    "[The Village Fights Back] General Darius reached {} kills but there is nowhere clear "
                    + "near {} to raise his statue; it will wait.", kills, darius.blockPosition());
            darius.rearmStatue();                 // try again on the next kill
            return;
        }
        sl.setBlock(spot, ModBlocks.BLACK_TNT.get().defaultBlockState(), 3);   // onPlace builds it
        sl.players().forEach(p -> p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§8The village raises a statue to General Darius."), false));
        TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] General Darius reached {} kills; his statue rises at {}.",
                kills, spot);
    }

    /**
     * Which Darius, if any, this death belongs to.
     *
     * <p>"Killed by Darius" is a wider thing than "the killing blow's source entity was Darius", and taking
     * the narrow reading is why the counter read 11 after a battle the player remembers as twenty-odd. He
     * sets his victims on FIRE -- both his smash and his Fire Aspect Smasher do -- and anything that dies of
     * that burn a second later dies to an ownerless fire source with no attacker at all. His escort does a
     * great deal of the killing too, and a monument to the champion that ignores everything his own two
     * Arms did is measuring the wrong thing.
     *
     * <p>So: the blow itself, or the last thing to hurt the victim, or a kill by one of his Arms.
     */
    private static GeneralDariusEntity creditFor(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof GeneralDariusEntity d) return d;

        LivingEntity dead = event.getEntity();
        // Burned, poisoned, bled out: the last thing that actually hurt it is the thing that killed it.
        if (dead.getLastHurtByMob() instanceof GeneralDariusEntity d) return d;

        // One of the Arms. Only ever asked when a NAMED guard gets a kill, so the box query below is rare.
        net.minecraft.world.entity.Entity killer = event.getSource().getEntity();
        if (killer instanceof LivingEntity le && le.hasCustomName()) {
            String name = le.getCustomName().getString();
            if (GeneralDariusEntity.ARM_RIGHT.equals(name) || GeneralDariusEntity.ARM_LEFT.equals(name)) {
                for (GeneralDariusEntity d : le.level().getEntitiesOfClass(GeneralDariusEntity.class,
                        le.getBoundingBox().inflate(ARM_CREDIT_RANGE), GeneralDariusEntity::isAlive)) {
                    return d;   // his escort's kills are his
                }
            }
        }
        return null;
    }

    /**
     * Somewhere with room for it.
     *
     * <p>Rings of bearings rather than a square scan: the statue is a landmark, so what matters is that it
     * is far enough out to be seen against the sky rather than that it is at the closest legal cell.
     */
    private static BlockPos findSpot(ServerLevel level, BlockPos from) {
        for (int dist = MIN_AWAY; dist <= MAX_AWAY; dist += 8) {
            for (int i = 0; i < BEARINGS; i++) {
                double a = (Math.PI * 2.0 / BEARINGS) * i;
                int x = from.getX() + (int) Math.round(Math.cos(a) * dist);
                int z = from.getZ() + (int) Math.round(Math.sin(a) * dist);
                BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(x, 0, z));
                if (at.getY() <= level.getMinBuildHeight() + 1) continue;
                if (!level.getBlockState(at).canBeReplaced()) continue;
                if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(),
                        net.minecraft.core.Direction.UP)) continue;
                if (occupied(level, at)) continue;
                return at;
            }
        }
        return null;
    }

    /** Does the statue's box overlap something the village built, or somewhere a player has forbidden? */
    private static boolean occupied(ServerLevel level, BlockPos at) {
        int x1 = at.getX() - HALF_W, z1 = at.getZ() - HALF_D;
        int x2 = at.getX() + HALF_W, z2 = at.getZ() + HALF_D;
        int y2 = at.getY() + HEIGHT;
        return com.example.villagefightsback.infection.StructureZones.get(level)
                        .intersectsForeign(x1, at.getY(), z1, x2, y2, z2)
                || com.example.villagefightsback.infection.MarkerZones.get(level)
                        .blocksBox(x1, at.getY(), z1, x2, y2, z2);
    }
}
