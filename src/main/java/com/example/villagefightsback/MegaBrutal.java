package com.example.villagefightsback;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import com.example.villagefightsback.platform.event.TickEvent;
import com.example.villagefightsback.platform.SubscribeEvent;
import com.example.villagefightsback.platform.ModRegistries;
import com.example.villagefightsback.platform.ServerRef;

/**
 * {@code mega_brutal_mode}: while enabled, every village unit near a brain node permanently carries Strength III,
 * Regeneration III, Resistance III and Speed III (no potion particles), guards wield Alien Smashers in place of
 * the regular Mace, and golems can teleport to a distant target the way the Vindictive Village Spirit does.
 *
 * <p>Implemented as a light per-brain-node sweep (every {@value #INTERVAL} ticks) rather than spawn-time hooks, so
 * it covers units that already exist and cleanly stops when the switch is turned off (the short-duration effects
 * simply lapse). Off by default; the scan only runs while the switch is on.
 */
public final class MegaBrutal {
    private MegaBrutal() {}

    private static final int INTERVAL = 40;                 // ~2s sweep; effects last 3s so they stay continuous
    private static final int EFFECT_TICKS = 60;
    private static final int AMP_III = 2;                   // amplifier 2 == level III
    private static final double TELEPORT_MIN = 16.0;        // golems blink only when this far from their target

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!Config.megaBrutalMode()) return;
        MinecraftServer server = ServerRef.getCurrentServer();
        if (server == null || server.getTickCount() % INTERVAL != 0) return;
        BrainNodeData data = BrainNodeData.get(server);
        for (net.minecraft.core.GlobalPos gp : new java.util.ArrayList<>(data.blocks())) {
            ServerLevel level = server.getLevel(gp.dimension());
            if (level == null || !level.isLoaded(gp.pos())) continue;
            AABB box = new AABB(gp.pos()).inflate(BrainNodeEvents.INCOME_RADIUS);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, MegaBrutal::isBuffable)) {
                buff(e);
                if (isGuard(e) && e instanceof Mob m && m.getMainHandItem().is(ModItems.MACE.get())) {
                    m.setItemSlot(EquipmentSlot.MAINHAND, GuardWeapons.alienSmasher().copy());
                }
                if (e instanceof IronGolem golem) teleportToTarget(golem);
            }
        }
    }

    /** The EMERALD faction is buffed too while mega_brutal_mode is on. They roam the whole world (far from any
     *  brain node), so rather than a brain-node sweep this is a distributed per-entity tick: every loaded emerald
     *  unit refreshes the same Strength/Regen/Resistance/Speed III buff, and emerald golems gain the blink. The
     *  effects are short, so turning the switch off lets them lapse on their own. */
    @SubscribeEvent
    public static void onEmeraldTick(com.example.villagefightsback.platform.event.LivingEvent.LivingTickEvent event) {
        if (!Config.megaBrutalMode()) return;
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide() || !(e instanceof EmeraldUnit) || e.tickCount % INTERVAL != 0) return;
        buff(e);
        if (e instanceof IronGolem golem) teleportToTarget(golem);
    }

    /** Village units that should get the buffs (matches the village ally set; emeraldgers are effect-immune anyway). */
    private static boolean isBuffable(LivingEntity e) {
        if (!e.isAlive() || e instanceof EmeraldgerEntity) return false;
        return e instanceof Villager || e instanceof IronGolem || e instanceof WallDefenderEntity
                || e instanceof GeneralDariusEntity || isGuard(e);
    }

    private static boolean isGuard(Entity e) {
        return com.example.villagefightsback.Factions.isGuard(e);
    }

    private static void buff(LivingEntity e) {
        apply(e, MobEffects.DAMAGE_BOOST);      // Strength III
        apply(e, MobEffects.REGENERATION);      // Regeneration III
        apply(e, MobEffects.DAMAGE_RESISTANCE); // Resistance III
        apply(e, MobEffects.MOVEMENT_SPEED);    // Speed III
    }

    private static void apply(LivingEntity e, MobEffect eff) {
        e.addEffect(new MobEffectInstance(eff, EFFECT_TICKS, AMP_III, false, false, false)); // no particles, no icon
    }

    /** Blink the golem next to its target when it is far away -- the Vindictive Village Spirit's trick. */
    private static void teleportToTarget(IronGolem golem) {
        LivingEntity t = golem.getTarget();
        if (t == null || !t.isAlive() || golem.distanceToSqr(t) < TELEPORT_MIN * TELEPORT_MIN) return;
        for (int i = 0; i < 8; i++) {
            double tx = t.getX() + (golem.getRandom().nextDouble() - 0.5) * 5.0;
            double ty = t.getY();
            double tz = t.getZ() + (golem.getRandom().nextDouble() - 0.5) * 5.0;
            if (golem.randomTeleport(tx, ty, tz, true)) {
                golem.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.7F);
                return;
            }
        }
    }
}
