package com.example.villagefightsback;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Vex;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Combat glue for the Limbo raiders: every attack a {@link LimboRaider} causes deals 3x damage (uniformly --
 * melee, crossbow bolts, evoker fangs, vex charges), and a Limbo Evoker's summoned vexes are swapped for
 * {@link LimboVexEntity Limbo Vexes}.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboRaidEvents {
    private LimboRaidEvents() {}

    /** 3x strength: triple any damage whose ultimate cause is a Limbo raider (covers projectiles + spells). */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof LimboRaider) {
            event.setAmount(event.getAmount() * LimboRaider.DAMAGE_MULT);
        }
    }

    /** A Limbo Evoker summons Limbo Vexes: intercept each vanilla vex it spawns and replace it with ours. */
    @SubscribeEvent
    public static void onVexSummon(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Vex vex) || vex instanceof LimboVexEntity) return;
        if (!(vex.getOwner() instanceof LimboEvokerEntity evoker)) return;
        event.setCanceled(true); // drop the vanilla vex...
        ServerLevel level = (ServerLevel) event.getLevel();
        LimboVexEntity lv = ModEntities.LIMBO_VEX.get().create(level); // ...and summon a Limbo Vex in its place
        if (lv == null) return;
        lv.moveTo(vex.getX(), vex.getY(), vex.getZ(), vex.getYRot(), vex.getXRot());
        lv.setOwner(evoker);
        lv.setBoundOrigin(vex.blockPosition());
        lv.finalizeSpawn(level, level.getCurrentDifficultyAt(vex.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null, null);
        lv.setLimitedLife(20 * (3 + level.getRandom().nextInt(3)));
        level.addFreshEntity(lv);
    }
}
