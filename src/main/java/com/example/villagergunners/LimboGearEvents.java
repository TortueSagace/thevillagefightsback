package com.example.villagergunners;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Worn-Limbo-gear effects. For now: the armour itself gives off glowing green particles while worn (more
 * pieces -> more glow). The full-SET bonuses (immunity, fire/fall immunity, phantom neutrality, the banish
 * beam ability + HUD) are layered on here in a later milestone.
 */
@Mod.EventBusSubscriber(modid = VillagerGunners.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboGearEvents {
    private LimboGearEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel sl)) return;
        int pieces = LimboGear.wornCount(sp);
        if (pieces <= 0) return;
        // A faint green shimmer around the wearer; denser with a fuller set. Emits ~1/4 ticks.
        if ((sp.tickCount & 3) != 0) return;
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                sp.getX(), sp.getY() + sp.getBbHeight() * 0.5, sp.getZ(),
                pieces, sp.getBbWidth() * 0.6, sp.getBbHeight() * 0.5, sp.getBbWidth() * 0.6, 0.0);
    }
}
