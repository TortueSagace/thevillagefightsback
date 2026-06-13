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
        if ((sp.tickCount & 1) != 0) return; // ~10 emissions/second

        // Green shimmer for worn Limbo armour (denser with a fuller set)...
        int pieces = LimboGear.wornCount(sp);
        if (pieces > 0) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    sp.getX(), sp.getY() + sp.getBbHeight() * 0.5, sp.getZ(),
                    1 + pieces, sp.getBbWidth() * 0.55, sp.getBbHeight() * 0.55, sp.getBbWidth() * 0.55, 0.0);
        }
        // ...and for a held Limbo tool (the pickaxe), a little glow at the hand.
        if (holdingLimboTool(sp)) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    sp.getX(), sp.getY() + sp.getBbHeight() * 0.7, sp.getZ(),
                    1, sp.getBbWidth() * 0.5, 0.3, sp.getBbWidth() * 0.5, 0.0);
        }
    }

    private static boolean holdingLimboTool(ServerPlayer sp) {
        return sp.getMainHandItem().is(ModItems.LIMBO_PICKAXE.get())
                || sp.getOffhandItem().is(ModItems.LIMBO_PICKAXE.get());
    }
}
