package com.example.villagefightsback;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;

/**
 * The "Gigachad Golem": an upgraded village iron golem -- its own creature type (not a renamed vanilla golem).
 * A village iron golem near a level-3 gravemind is converted into one ({@link VillageEvents#convertToGigachad}).
 * It is a normal village defender that has been given the full gigachad kit (rocket launcher, iron-set armour,
 * 200 HP, rocket goal) via the shared {@link VillageEvents#armGolemFull(IronGolem)}.
 *
 * Its boss-minion cousin is {@link VindictiveGigachadEntity}, which extends this class.
 */
public class GigachadGolemEntity extends IronGolem {
    public GigachadGolemEntity(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor lvl,
            net.minecraft.world.DifficultyInstance diff, net.minecraft.world.entity.MobSpawnType reason,
            net.minecraft.world.entity.SpawnGroupData data, net.minecraft.nbt.CompoundTag tag) {
        net.minecraft.world.entity.SpawnGroupData r = super.finalizeSpawn(lvl, diff, reason, data, tag);
        VillageEvents.armGolemFull(this); // shared gigachad kit (rocket launcher, armour, 200 HP, rocket goal)
        return r;
    }
}
