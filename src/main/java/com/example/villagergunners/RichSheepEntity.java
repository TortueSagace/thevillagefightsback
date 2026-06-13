package com.example.villagergunners;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;

/**
 * The Rich Sheep: a sheep reskinned with the fertile/rich look (green body, emerald-flecked wool). Always
 * lime-coloured, so it drops lime wool + mutton on death; it also drops an emerald, and shearing yields
 * lime wool + an emerald. Spawned when a fertile tree generates (see {@link #trySpawn}).
 */
public class RichSheepEntity extends Sheep {
    public RichSheepEntity(EntityType<? extends Sheep> type, Level level) {
        super(type, level);
        this.setColor(DyeColor.LIME);
    }

    @Override
    public ResourceLocation getDefaultLootTable() {
        // Always drop the lime-sheep loot (lime wool + mutton) regardless of the synced colour byte.
        return new ResourceLocation("minecraft", "entities/sheep/lime");
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        this.spawnAtLocation(Items.EMERALD);
    }

    @Override
    public void shear(SoundSource soundSource) {
        // Replicates vanilla Sheep.shear, but drops 1-3 LIME_WOOL AND 1 EMERALD.
        this.level().playSound(null, this, SoundEvents.SHEEP_SHEAR, soundSource, 1.0F, 1.0F);
        this.setSheared(true);
        int count = 1 + this.random.nextInt(3);
        for (int j = 0; j < count; ++j) {
            ItemEntity wool = this.spawnAtLocation(Items.LIME_WOOL, 1);
            if (wool != null) {
                wool.setDeltaMovement(wool.getDeltaMovement().add(
                        (this.random.nextFloat() - this.random.nextFloat()) * 0.1F,
                        this.random.nextFloat() * 0.05F,
                        (this.random.nextFloat() - this.random.nextFloat()) * 0.1F));
            }
        }
        this.spawnAtLocation(Items.EMERALD);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }

    /**
     * With probability {@code chance}, create and finalize-spawn a Rich Sheep at {@code pos}. Another batch
     * calls this when a fertile tree generates.
     */
    public static void trySpawn(ServerLevel level, BlockPos pos, float chance) {
        if (level.random.nextFloat() >= chance) return;
        RichSheepEntity sheep = ModEntities.RICH_SHEEP.get().create(level);
        if (sheep == null) return;
        sheep.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        sheep.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);
        level.addFreshEntity(sheep);
    }
}
