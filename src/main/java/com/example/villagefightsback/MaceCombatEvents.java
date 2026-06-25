package com.example.villagefightsback;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The NATIVE mace combat: the signature SMASH attack and the three mace enchantments (Density, Breach, Wind
 * Burst), all driven from a game-bus {@link LivingIncomingDamageEvent} so no mixin is needed (this fires before
 * armour/absorption mitigation, matching the old Forge LivingHurtEvent semantics). A mace SMASH triggers when
 * the attacker strikes an entity while falling (fall distance &gt; 1.5 and not elytra-flying): it adds heavy
 * fall-scaled bonus damage (plus Density's per-fall bonus), negates the attacker's fall damage, launches the
 * attacker up if Wind Burst is present, and shoves nearby living entities outward. Breach reduces the target's
 * effective armour. An IDENTICAL clone of the vanilla mace behaviour (a fallback sound is used for the smash).
 */
@EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class MaceCombatEvents {
    private MaceCombatEvents() {}

    private static final float SMASH_FALL_THRESHOLD = 1.5F; // need to be falling more than this to smash
    private static final double KB_RADIUS = 3.5;            // area-knockback reach around a smash
    private static final double KB_UP = 0.7;                // fixed upward push on knocked-back entities

    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        ItemStack weapon = attacker.getMainHandItem();
        if (!MaceItem.isMace(weapon)) return;

        // The Alien Smasher carries a guaranteed baseline of the guards' enchanted mace (Density 3 + Wind
        // Burst 3 + Fire Aspect 2) even before the player enchants it. We apply it as a FLOOR in code so the
        // behaviour never depends on smithing copying NBT; real enchants the player adds stack on top (max).
        boolean smasher = weapon.getItem() instanceof AlienSmasherItem;

        // --- BREACH: shave off part of the target's armour effectiveness. Approximate by scaling the event
        //     amount so that, after vanilla applies the FULL armour, the net matches the reduced-armour result.
        float breach = ModEnchantments.getBreachFraction(attacker, weapon);
        if (breach > 0.0F) {
            float amount = event.getAmount();
            float armor = (float) victim.getArmorValue();
            float toughness = (float) victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            float afterFull = CombatRules.getDamageAfterAbsorb(victim, amount, event.getSource(), armor, toughness);
            float afterReduced = CombatRules.getDamageAfterAbsorb(victim, amount, event.getSource(),
                    armor * (1.0F - breach), toughness);
            if (afterFull > 0.0F) {
                event.setAmount(amount * (afterReduced / afterFull));
            }
        }

        // FIRE ASPECT 2 baseline for the Alien Smasher: ALWAYS set the target alight for 8s (FA-2 duration)
        // on any hit, like the guard mace's Fire Aspect 2. A player-added Fire Aspect stacks naturally
        // (vanilla applies its own burn in hurtEnemy), and igniteForSeconds only ever extends the burn.
        if (smasher) victim.igniteForSeconds(8.0F);

        // --- SMASH: only on a downward strike from a real fall (and never while elytra-flying).
        float fall = attacker.fallDistance;
        if (fall <= SMASH_FALL_THRESHOLD || attacker.isFallFlying()) return;

        // Bonus damage from the fall (vanilla curve) + Density's per-fall bonus.
        float bonus;
        if (fall <= 3.0F) {
            bonus = 4.0F * fall;
        } else if (fall <= 8.0F) {
            bonus = 12.0F + 2.0F * (fall - 3.0F);
        } else {
            bonus = 22.0F + (fall - 8.0F);
        }
        // Density's per-fall bonus, floored at level 3 for the Alien Smasher (guard-mace baseline).
        float densityPerFall = ModEnchantments.getDensityBonusPerFall(attacker, weapon);
        if (smasher) densityPerFall = Math.max(densityPerFall, 0.5F * 3);
        bonus += densityPerFall * fall;
        event.setAmount(event.getAmount() + bonus);

        // The smash negates the attacker's own fall damage for this fall.
        boolean heavy = fall > 5.0F;
        attacker.fallDistance = 0.0F;

        if (!(victim.level() instanceof ServerLevel sl)) return;

        // Area knockback: shove every OTHER living entity near the smash outward (skip the target, the
        // attacker, teammates, spectators), power scaled by distance + a heavy-smash multiplier.
        AABB box = victim.getBoundingBox().inflate(KB_RADIUS);
        Vec3 center = attacker.position();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e == attacker || e == victim) continue;
            if (e.isSpectator() || attacker.isAlliedTo(e)) continue;
            double dist = Math.sqrt(e.distanceToSqr(center.x, center.y, center.z));
            if (dist > KB_RADIUS) continue;
            double kbRes = e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            double power = (KB_RADIUS - dist) * 0.7 * (heavy ? 2.0 : 1.0) * (1.0 - kbRes);
            Vec3 out = e.position().subtract(center);
            double hl = Math.sqrt(out.x * out.x + out.z * out.z);
            double hx = hl > 1.0E-4 ? out.x / hl : 0.0, hz = hl > 1.0E-4 ? out.z / hl : 0.0;
            e.push(hx * power, KB_UP, hz * power);
            e.hurtMarked = true; // resync the new velocity to clients
        }

        // WIND BURST: launch the ATTACKER upward by the per-level strength (floored at the level-3 value
        // 2.2 for the Alien Smasher, the guard-mace baseline).
        double launch = ModEnchantments.getWindBurstLaunch(attacker, weapon);
        if (smasher) launch = Math.max(launch, 2.2);
        if (launch > 0.0) {
            Vec3 m = attacker.getDeltaMovement();
            attacker.setDeltaMovement(m.x, launch, m.z);
            attacker.hasImpulse = true;
            attacker.hurtMarked = true; // resync the launch to clients
            attacker.fallDistance = 0.0F;
        }

        // Smash sound (fall back to a low-pitch explosion / anvil land for the smash impact).
        sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                heavy ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.ANVIL_LAND, SoundSource.PLAYERS,
                heavy ? 1.0F : 0.8F, heavy ? 0.6F : 0.9F);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, victim.getX(), victim.getY(),
                victim.getZ(), heavy ? 2 : 1, 0.0, 0.0, 0.0, 0.0);
    }
}
