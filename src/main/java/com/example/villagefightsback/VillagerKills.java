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
     * The epitaph carved on a fallen defender's headstone. Gendered from the villager itself where MCA Reborn
     * supplies a gender, so it reads as a person rather than as a statistic.
     */
    public static Component epitaph(LivingEntity villager) {
        int kills = get(villager);
        String pronoun = com.example.villagefightsback.compat.MCACompat.isFemale(villager) ? "her" : "his";
        return Component.literal("Defended the village at the cost of " + pronoun + " life: killed "
                + kills + (kills == 1 ? " hostile mob" : " hostile mobs")).withStyle(ChatFormatting.WHITE);
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
        if (!(killer instanceof Villager)) return;   // villagers, which under MCA includes its guards and archers
        if (killer.level().isClientSide()) return;

        int now = get(killer) + 1;
        killer.getPersistentData().putInt(NBT_KILLS, now);
        com.example.villagefightsback.compat.VillagerRoleTags.mirror(killer);
    }
}
