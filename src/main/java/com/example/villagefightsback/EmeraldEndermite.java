package com.example.villagefightsback;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Emerald Endermite ("emerald mite"): the swarm unit that erupts from infested paths when an emerald unit
 * dies. A hostile combat scout -- it keeps its vanilla attack, gains the emerald AI + the explorer roam (like the
 * cats/bats) so it actively hunts, despawns 1 minute after spawning, and drops no emerald (its loot table is empty).
 */
public class EmeraldEndermite extends Endermite implements EmeraldUnit {
    /** Mites vanish 1 minute (1200 ticks) after spawning -- they are a transient swarm, not a lasting population. */
    private static final int LIFETIME_TICKS = 20 * 60;

    public EmeraldEndermite(EntityType<? extends Endermite> type, Level level) {
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
        EmeraldMobs.addExplorer(this); // scout: roam to seek out infection targets, like the cats/bats
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide() && this.tickCount >= LIFETIME_TICKS) {
            this.discard(); // silent despawn after 1 minute (no death effects, no drops)
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Large follow range so the mite detects targets from afar and goes hunting (matches the cat/bat scouts).
        return Endermite.createAttributes().add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
