package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Emerald Wither: an EMERALD-faction WitherBoss that hunts every non-spared target and shoots skulls like
 * the vanilla wither. Unlike the real boss, its boss bar is suppressed (no player is ever added to the boss
 * event) so it behaves like an ordinary faction mob. Always rendered with the emerald wither texture.
 */
public class EmeraldWither extends WitherBoss implements EmeraldUnit {
    public EmeraldWither(EntityType<? extends WitherBoss> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean emeraldPassive() {
        return false;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        EmeraldMobs.addEmeraldGoals(this, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return WitherBoss.createAttributes();
    }

    /**
     * The wither's side heads pick skull targets by scanning nearby mobs directly (bypassing the standard target
     * selector and the {@code onChangeTarget} faction block), and only fire when {@code canAttack} passes. So gate
     * it here: an emerald wither never attacks a fellow emerald unit or the village faction.
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        if (Factions.isEmerald(target) || Factions.isVillage(target)) return false;
        return super.canAttack(target);
    }

    // Suppress the boss bar: never add a player to the boss event so no boss bar is shown.
    @Override
    public void startSeenByPlayer(ServerPlayer p) {}

    @Override
    public void stopSeenByPlayer(ServerPlayer p) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
