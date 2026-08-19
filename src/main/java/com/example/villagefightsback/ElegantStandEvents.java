package com.example.villagefightsback;

import com.example.villagefightsback.platform.PersistentData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import com.example.villagefightsback.platform.event.LivingEvent;
import com.example.villagefightsback.platform.SubscribeEvent;

/**
 * The Elegant Stand pedestal: any creature standing on an {@link ModBlocks#ELEGANT_STAND} block is frozen
 * (NoAI, no movement) and turns to face the nearest player every tick, like a museum display. The freeze is
 * reversible -- the instant the creature leaves the stand (or the stand is removed) its AI is restored. A
 * persistent-data marker records which mobs WE froze, so we never clear someone else's NoAI and we tidy up
 * even across a world reload.
 */
public final class ElegantStandEvents {
    private ElegantStandEvents() {}

    private static final String NBT_DISPLAYED = "vgun_on_elegant_stand";
    private static final double FACE_RANGE = 48.0;

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        if (mob instanceof UnleashedBrainNodeEntity) return;          // never freeze the boss

        // This fires for EVERY mob on the server EVERY tick, so the path for the 99.99% that are not standing
        // on a stand has to cost as close to nothing as possible.
        //
        // A displayed mob always has noAi set, and almost nothing else does, so that flag rejects the whole
        // world before we touch getPersistentData -- which is not a free read: Forge creates the tag on first
        // access, so calling it unconditionally allocated a CompoundTag for every mob in the world.
        //
        // What is left is spotting a mob that has just been PUT on a stand, which needs a real block lookup.
        // That one is throttled: a stand is a decoration, and taking up to 8 ticks to notice a mob placed on
        // one is imperceptible, where 20 blockstate reads per tick per mob is not.
        boolean displayed = mob.isNoAi() && PersistentData.of(mob).getBoolean(NBT_DISPLAYED);
        if (!displayed && (mob.tickCount & 7) != 0) return;
        boolean onStand = isOnStand(mob);
        if (onStand) {
            if (!displayed) {
                PersistentData.of(mob).putBoolean(NBT_DISPLAYED, true);
                mob.setNoAi(true);
                mob.setPersistenceRequired();                          // a display piece shouldn't despawn
                mob.getNavigation().stop();
            }
            facePlayer(mob);                                           // keep turning to whoever is nearest
        } else if (displayed) {
            PersistentData.of(mob).putBoolean(NBT_DISPLAYED, false);
            mob.setNoAi(false);                                        // off the stand -> resume normal behaviour
        }
    }

    /** On a stand if the block its feet rest on is an Elegant Stand (works for a bottom or top slab). */
    private static boolean isOnStand(Mob mob) {
        BlockPos feet = BlockPos.containing(mob.getX(), mob.getBoundingBox().minY - 0.1, mob.getZ());
        return mob.level().getBlockState(feet).is(ModBlocks.ELEGANT_STAND.get());
    }

    /** Turn the whole body + head + pitch toward the nearest player and stay perfectly still; no-op if none near. */
    private static void facePlayer(Mob mob) {
        mob.setDeltaMovement(0.0, 0.0, 0.0);
        Player p = mob.level().getNearestPlayer(mob, FACE_RANGE);
        if (p == null) return;
        double dx = p.getX() - mob.getX();
        double dz = p.getZ() - mob.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        mob.setYRot(yaw); mob.yRotO = yaw;
        mob.yBodyRot = yaw; mob.yBodyRotO = yaw;
        mob.yHeadRot = yaw; mob.yHeadRotO = yaw;
        double dy = p.getEyeY() - mob.getEyeY();
        double dh = Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) (-(Mth.atan2(dy, dh) * (180.0 / Math.PI)));
        mob.setXRot(pitch); mob.xRotO = pitch;
    }
}
