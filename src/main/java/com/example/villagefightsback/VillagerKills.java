package com.example.villagefightsback;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Counts the hostile mobs each villager has killed, so a villager who actually fought for the village can be
 * told apart from one who never did.
 *
 * <p>A villager with at least one kill has earned a grave: it is buried when it dies (see
 * {@link com.example.villagefightsback.compat.MCATombstones}) and can be brought back from that grave with the
 * Limbo Scythe. One that never lifted a weapon is not memorialised, which keeps a besieged village from
 * carpeting itself in headstones.
 *
 * <p>The tally lives in the villager's persistent data and is mirrored to a scoreboard tag, so it survives the
 * villager being replaced by MCA Reborn's spawn queue (which copies tags but not persistent data).
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillagerKills {
    private VillagerKills() {}

    private static final String NBT_KILLS = "vgun_kills";

    /** How many hostiles this villager has killed. */
    public static int get(Entity e) {
        return e.getPersistentData().getInt(NBT_KILLS);
    }

    /** True once the villager has fought for the village at least once, i.e. it has earned a grave. */
    public static boolean earnedGrave(Entity e) {
        return get(e) > 0;
    }

    /**
     * True for a village defender whose kills are worth counting and who can be buried under a memorial.
     *
     * <p>Villagers AND guards, because which one a guard is depends entirely on the modpack: with MCA Reborn a
     * guard is an MCA villager (so {@code instanceof Villager} alone was enough), but WITHOUT MCA it is a Guard
     * Villagers {@code guardvillagers:guard}, which is not a Villager at all. That is exactly the unit that does
     * most of the dying in a vanilla-ish pack, so gating on Villager quietly denied a memorial to the village's
     * best fighters in the very packs where they matter most.
     *
     * <p>Iron golems and wall defenders are deliberately left out: they are constructs, not the village's people,
     * and burying every golem would carpet a besieged village in headstones. General Darius is handled on his own
     * terms in {@link Graves#onDeath} -- he is always buried, kills or not.
     */
    public static boolean isDefender(Entity e) {
        if (Factions.isInfected(e) || Factions.isEmerald(e)) return false;
        // An MCA outlaw or cultist is a Villager, but it is a raider: it fights the village, not for it.
        if (com.example.villagefightsback.compat.MCACompat.isHostileVillager(e)) return false;
        return e instanceof Villager || Factions.isGuard(e);
    }

    /**
     * The epitaph carved on a fallen defender's headstone.
     *
     * <p>Kept SHORT on purpose. On an MCA Reborn tombstone this line is sandwiched between MCA's own header
     * ("Here Lies") and its footer ("May he/she rest in peace"), both of which MCA renders itself and neither of
     * which this mod can remove without touching MCA's renderer. The only length we control is this line, so it
     * carries just the fact worth carving: that the villager defended the village, and how many it took down.
     * "At the cost of his life" was dropped as redundant -- it is a grave.
     */
    public static Component epitaph(LivingEntity villager) {
        int kills = get(villager);
        return Component.literal("Defended the village: killed " + kills
                + (kills == 1 ? " hostile mob" : " hostile mobs")).withStyle(ChatFormatting.WHITE);
    }

    /**
     * Credit a villager with a kill. Only counts what the village considers a THREAT (hostiles, the vindictive
     * faction, MCA raiders), so a villager that kills a cow or is caught in friendly fire earns nothing.
     */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!Factions.isVillageThreat(event.getEntity())) return;
        // getEntity() on the source is the responsible killer: the shooter for a projectile, not the arrow.
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)) return;
        if (!isDefender(killer)) return;   // villagers and guards, whichever kind of guard this pack has
        if (killer.getLevel().isClientSide()) return;

        int now = get(killer) + 1;
        killer.getPersistentData().putInt(NBT_KILLS, now);
        com.example.villagefightsback.compat.VillagerRoleTags.mirror(killer);
    }
}
