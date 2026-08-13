package com.example.villagefightsback;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Drives the permanent upgrade: once the (global) Village Brain Node reaches level {@value #REQUIRED_LEVEL},
 * any defender within {@value #RADIUS} blocks of a brainNode is upgraded to the full gun loadout, instantly
 * and forever (the flag never clears). Cheap: gated by the level check, throttled, only loaded brainNodes,
 * and already-upgraded defenders are filtered out of the query.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UpgradeEvents {
    private UpgradeEvents() {}

    private static final int REQUIRED_LEVEL = 3;    // villagers/guards get the full kit at stage 3+
    private static final int DEFENDER_LEVEL = 2;     // a plain iron golem becomes a Defender Golem at stage 2+
    private static final int GIGACHAD_LEVEL = 4;     // a golem (iron OR defender) becomes a Gigachad at stage 4+
    static double RADIUS = 100.0; // brain influence radius (set from Config.apply(); base 100)
    private static final int INTERVAL = 100; // check every ~5s (upgrade is permanent; no need to be fast)

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % INTERVAL != 0) return;
        long _p = ModProfiler.now();

        BrainNodeData data = BrainNodeData.get(server);

        for (GlobalPos gp : data.blocks()) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            int stage = data.econ(gp).getLevel();
            if (stage < DEFENDER_LEVEL) continue; // nothing happens below stage 2 (golems evolve from stage 2)
            AABB box = new AABB(gp.pos()).inflate(RADIUS);
            final int s = stage;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> needsProcessing(x, s))) {
                if (e instanceof IronGolem golem) {
                    // Golems have THREE forms: iron (s1) -> Defender Golem (s2-3) -> Gigachad Golem (s4).
                    if (stage >= GIGACHAD_LEVEL) {
                        VillageEvents.convertToGigachad(golem);           // iron OR defender -> gigachad
                    } else if (!(golem instanceof DefenderGolemEntity)) {
                        VillageEvents.convertToDefenderGolem(golem);      // plain iron -> defender (stage 2-3)
                    }
                    // else: already a Defender Golem at stage 2-3 -> leave it as-is
                } else if (stage >= REQUIRED_LEVEL) {
                    UpgradeState.setUpgraded(e);                          // villagers/guards: full kit at stage 3+
                    // At stage 4 the village goes Virus II: villagers wield the Alien Eradicator and guards' crossbows
                    // inflict Virus II (both keyed off this flag). This is what makes emeraldgers a stage-4-only thing.
                    if (stage >= GIGACHAD_LEVEL) UpgradeState.setStage4(e);
                    VillageEvents.applyUpgrade(e);
                } else {
                    // Stage 2 (2 <= stage < 3): a GUARD becomes a virus archer -- emerald-virus crossbow + emerald
                    // armour, but no mace (needsProcessing restricts this branch to guards). Villagers wait for stage 3.
                    UpgradeState.setStage2(e);
                    VillageEvents.applyUpgrade(e);
                }
            }
        }
        ModProfiler.add("upgrades", _p);
    }

    /** What the upgrade scan acts on: any plain village golem (-> converted to a gigachad creature type), a
     *  not-yet-upgraded villager/guard (-> armed in place), or -- once the brain is stage 4 -- an already-upgraded
     *  villager/guard that hasn't yet had its one-time stage-4 (Virus II) bump. */
    private static boolean needsProcessing(LivingEntity e, int stage) {
        if (!e.isAlive()) return false;
        if (Factions.isEmerald(e)) return false; // emerald golems are the ENEMY -> never village-upgraded/converted
        if (e instanceof IronGolem golem) {
            return !golem.isPlayerCreated() && !(golem instanceof GigachadGolemEntity);
        }
        if (!isUpgradable(e)) return false;
        if (stage >= REQUIRED_LEVEL) {
            return !UpgradeState.isUpgraded(e) || (stage >= GIGACHAD_LEVEL && !UpgradeState.isStage4(e));
        }
        // Stage 2: only GUARDS gain a tier here (the virus archer), and only until the stage-2 flag is stamped.
        return com.example.villagefightsback.Factions.isGuard(e) && !UpgradeState.isStage2(e);
    }

    private static boolean isUpgradable(LivingEntity e) {
        if (e instanceof GigachadGolemEntity) return false; // gigachad (+ vindictive) are already fully armed
        if (e instanceof IronGolem golem) return !golem.isPlayerCreated();
        if (e instanceof Villager) return true;
        return com.example.villagefightsback.Factions.isGuard(e);
    }
}
