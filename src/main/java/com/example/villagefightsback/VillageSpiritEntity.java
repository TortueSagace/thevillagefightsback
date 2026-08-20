package com.example.villagefightsback;

import com.mojang.serialization.Dynamic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.example.villagefightsback.infection.BlockMatcher;
import com.example.villagefightsback.infection.MarkerZones;
import com.example.villagefightsback.infection.StructureZones;

/**
 * The "Village Spirit": what rises from a fallen brain node when the world is NOT out for blood.
 *
 * <p>Destroying a node used to always raise the Vindictive Village Spirit boss. That is the right answer for
 * a player who went looking for a fight, and far too heavy for everyone else, so the boss is now reserved for
 * Mega Brutal mode and Nightmare difficulty (see {@link UnleashedManager#onNodeDestroyed}). Below that the
 * village does not fight back -- it MOVES. This is the scout that moves it: it rises where the node stood,
 * climbs, drifts away looking for somewhere better, and plants a new brain node when it finds it.
 *
 * <p>It is harmless. {@value #SPIRIT_HEALTH} HP, immune to every effect, no attack, and it runs from anything
 * dangerous. Killing it in flight is how a player ends that village for good -- there is no drop and no second
 * chance -- which is a real lever, just one that asks you to catch a fleeing flier instead of win a boss fight.
 *
 * <h2>Why it extends Allay</h2>
 * The model, the texture, the {@code FlyingMoveControl} and the {@code FlyingPathNavigation} all come free, and
 * the renderer binds by subclass -- exactly the trick {@link UnleashedBrainNodeEntity} already uses to reuse the
 * warden renderer. Nothing is shipped and nothing is licensed, because the Allay is vanilla.
 *
 * <p>What an Allay carries that we do NOT want is switched off deliberately below: its brain, its item pickup
 * and duplication, its dancing, its vibration and jukebox listeners, and -- easy to miss -- the 1 HP every 10
 * ticks it heals itself in {@code aiStep}, which on a 10 HP mob would have made it far tougher than intended.
 */
public class VillageSpiritEntity extends Allay {
    public static final float SPIRIT_HEALTH = 10.0F;

    /** Reputation for reviving a village -- enough to clear the 100 that Hero of the Village asks for. */
    private static final int REVIVE_REWARD = 120;

    /** How far above the ground it cruises while searching. */
    private static final int CRUISE_HEIGHT = 10;
    /** ...and how far a restoring spirit lifts off before it drops back onto its own brain. */
    private static final int RESTORE_RISE = 10;
    /** It must be airborne at least this long before it is allowed to settle. */
    private static final int MIN_SEARCH_TICKS = 20 * 20;      // 20 seconds
    /** ...and after this long it settles wherever it is, ideal or not. */
    private static final int RELAX_TICKS = 60 * 20;           // 60 seconds
    /** Ticks of climbing without gaining height before it assumes a ceiling and slides sideways. */
    private static final int WEDGED_TICKS = 10;
    /** ...and the hard cap on climbing at all. Past this it searches from whatever height it reached. */
    private static final int ASCEND_TIMEOUT = 100;            // 5 seconds
    /** Radius that must be free of hostiles and alien blocks for a site to count. */
    private static final int CLEAR_RADIUS = 10;
    /** How often the (relatively expensive) steering + site evaluation runs. */
    private static final int EVAL_INTERVAL = 20;
    /** Cruise speed, blocks/tick. */
    private static final double SPEED = 0.22;
    /** How far out it looks for things to avoid or be drawn to. */
    private static final double SENSE_RANGE = 24.0;
    /** Brain nodes push it away from this far out, so a new village is never founded on top of an old one. */
    private static final double NODE_REPEL_RANGE = 96.0;

    private enum Phase { ASCEND, SEARCH, DESCEND }

    private Phase phase = Phase.ASCEND;
    private BlockPos origin;
    /**
     * A grave to fly to, or null for the founding flight.
     *
     * <p>Set when the brain node buys a resurrection: with an errand the spirit ignores the whole
     * site-search state machine, beelines to the memorial and raises whoever lies in it. It is also what the
     * renderer reads to withhold the halo -- the founding spirit carries a village and glows for it; this one
     * is running an errand.
     */
    private BlockPos errand;
    /**
     * The husk to fly back to and wake, or null for the founding flight.
     *
     * <p>Set when the husk was one the world was found with rather than one that died (see
     * {@link DeadBrainNodeBlock#FOUND}). There is already a village standing here, so there is nothing to
     * look for: the spirit rises {@value #RESTORE_RISE} blocks at most, comes straight back down to the same
     * block and turns it into a living node. The site search, the steering and the whole founding state
     * machine are skipped.
     */
    private BlockPos restore;
    /** Who fed the husk its golden apple, paid when (and only when) the new village is founded. */
    private java.util.UUID reviver;
    private int airTicks;
    /** Steering direction, recomputed every {@value #EVAL_INTERVAL} ticks and flown in between. */
    private Vec3 heading = Vec3.ZERO;
    /** Height last tick and how long it has failed to gain any, for getting out from under a ceiling. */
    private double lastY = Double.NaN;
    private int wedgedTicks;

    public VillageSpiritEntity(EntityType<? extends VillageSpiritEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, SPIRIT_HEALTH)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    /** Where it started, so it can be made to travel a real distance rather than circle the ruin. */
    public void setOrigin(BlockPos pos) {
        this.origin = pos.immutable();
    }

    /** Remember who bought this flight, so founding the village can pay them back. */
    public void setReviver(java.util.UUID id) {
        this.reviver = id;
    }

    /** Send it back to {@code husk} to wake it, instead of carrying the village somewhere else. */
    public void setRestore(BlockPos husk) {
        this.restore = husk.immutable();
    }

    /** Send it to raise the dead at {@code memorial} instead of founding a village. */
    public void setErrand(BlockPos memorial) {
        this.errand = memorial.immutable();
        this.entityData.set(DATA_ERRAND, true);
    }

    /** True for a spirit on a resurrection errand -- read on the client, which is where the halo is drawn. */
    public boolean isErrand() {
        return this.entityData.get(DATA_ERRAND);
    }

    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_ERRAND =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    VillageSpiritEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ERRAND, false);
    }

    // --- everything an Allay does that this is not -------------------------------------------------

    /** An empty brain: the state machine below replaces it, and nothing ever ticks it. */
    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return this.brainProvider().makeBrain(dynamic);
    }

    /**
     * The whole behaviour. Note it does NOT call {@code super}: an Allay's version ticks the Allay brain, and
     * this entity has no use for a single one of its tasks.
     */
    @Override
    protected void customServerAiStep() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        long _p = ModProfiler.now();
        try {
            fly(sl);
        } finally {
            ModProfiler.add("village_spirit", _p);
        }
    }

    /**
     * Allay heals itself 1 HP every 10 ticks in {@code aiStep}. On a {@value #SPIRIT_HEALTH} HP mob that is
     * regeneration fast enough to shrug off most of what a player can do to it, which would quietly undo the
     * point of it being fragile. There is nothing else that should ever heal it, so the simplest correct
     * answer is that it does not heal at all.
     */
    @Override
    public void heal(float amount) {
        // deliberately empty
    }

    /** Invulnerable to every effect, as specified. Damage still works -- that is how you stop it. */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    /** Not a pet and not a courier: no item handing, no duplication. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    /** No vibration or jukebox listening: it does not dance, and this is free tick time back. */
    @Override
    public void updateDynamicGameEventListener(
            java.util.function.BiConsumer<DynamicGameEventListener<?>, ServerLevel> consumer) {
        // deliberately empty
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false; // it is carrying a village; it does not despawn
    }

    // --- the flight --------------------------------------------------------------------------------

    private void fly(ServerLevel sl) {
        if (origin == null) origin = this.blockPosition();
        // Counted from the moment it appears, deliberately -- not from when it reaches cruising height. A
        // spirit that never gets high still ages, so the 20-second minimum and the 60-second relaxation
        // arrive on time and it settles somewhere rather than hovering under a ceiling for good.
        airTicks++;

        if (errand != null) { runErrand(sl); return; }
        if (restore != null) { runRestore(sl); return; }

        switch (phase) {
            case ASCEND -> {
                int target = cruiseY(sl, this.blockPosition());
                // Give up climbing once it is high enough, OR once it has spent long enough trying. A spirit
                // that rose under an overhang used to push at it forever and never reach SEARCH at all, so it
                // never founded anything. Cruising lower than intended is fine; never arriving is not.
                if (this.getY() >= target - 0.5 || airTicks > ASCEND_TIMEOUT) {
                    phase = Phase.SEARCH;
                } else {
                    // Not gaining height means something is directly overhead. Slide sideways to get out from
                    // under it rather than grinding upward into the same block.
                    boolean gaining = Double.isNaN(lastY) || this.getY() > lastY + 0.01;
                    wedgedTicks = gaining ? 0 : wedgedTicks + 1;
                    lastY = this.getY();
                    if (wedgedTicks >= WEDGED_TICKS) {
                        if (heading.lengthSqr() < 1.0E-4 || wedgedTicks % 20 == 0) {
                            heading = new Vec3(this.random.nextDouble() * 2 - 1, 0.0,
                                    this.random.nextDouble() * 2 - 1).normalize();
                        }
                        this.setDeltaMovement(heading.x * SPEED, SPEED * 0.5, heading.z * SPEED);
                    } else {
                        this.setDeltaMovement(0.0, SPEED, 0.0);
                    }
                    this.hasImpulse = true;
                    return;
                }
            }
            case DESCEND -> {
                descend(sl);
                return;
            }
            default -> { }
        }

        // SEARCH. The steering and the site test are the only costly parts, so they run on an interval and the
        // spirit simply flies the last heading in between.
        if (airTicks % EVAL_INTERVAL == 0) {
            heading = steer(sl);
            if (airTicks >= MIN_SEARCH_TICKS && siteIsGood(sl, this.blockPosition())) {
                phase = Phase.DESCEND;
                return;
            }
        }

        // Hold altitude while drifting: a spirit that sinks into a hillside cannot see anything.
        int want = cruiseY(sl, this.blockPosition());
        double climb = Math.max(-SPEED, Math.min(SPEED, (want - this.getY()) * 0.1));
        this.setDeltaMovement(heading.x * SPEED, climb, heading.z * SPEED);
        this.hasImpulse = true;
        if (heading.lengthSqr() > 1.0E-4) {
            this.setYRot((float) (Math.atan2(heading.z, heading.x) * (180.0 / Math.PI)) - 90.0F);
            this.yBodyRot = this.getYRot();
        }
    }

    /**
     * Fly to the grave and raise whoever lies in it.
     *
     * <p>No search, no scoring, no minimum flight: this spirit was bought for one job. It steers straight at
     * the memorial, and on arrival hands off to the scythe's own resurrection so there is exactly one code
     * path that opens a grave.
     *
     * <p>If the grave is gone or empty by the time it arrives -- someone else raised them, or broke it -- the
     * spirit simply leaves. It does not fall back to founding a village, because the emerald was spent on a
     * resurrection and quietly turning that into a new settlement is not what was paid for.
     */
    private void runErrand(ServerLevel sl) {
        double dx = errand.getX() + 0.5 - this.getX();
        double dy = errand.getY() + 0.9 - this.getY();
        double dz = errand.getZ() + 0.5 - this.getZ();
        double d2 = dx * dx + dy * dy + dz * dz;

        if (d2 <= 1.4 * 1.4) {                              // arrived: contact with the stone
            boolean raised = LimboScytheItem.raiseFromMemorial(sl, errand);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    errand.getX() + 0.5, errand.getY() + 1.0, errand.getZ() + 0.5, 30, 0.5, 0.6, 0.5, 0.02);
            sl.playSound(null, errand, SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 1.0F, 1.2F);
            if (raised) {
                TheVillageFightsBack.LOGGER.info(
                        "[The Village Fights Back] a Village Spirit raised the dead at {}.", errand);
            }
            this.discard();
            return;
        }

        double len = Math.sqrt(d2);
        this.setDeltaMovement(dx / len * SPEED, dy / len * SPEED, dz / len * SPEED);
        this.hasImpulse = true;
        this.setYRot((float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
        this.yBodyRot = this.getYRot();
    }

    /**
     * Up, then straight back down onto the brain it rose from.
     *
     * <p>Deliberately not the founding flight in miniature: there is no steering, no site test and no
     * minimum time aloft, because the site was decided the day the village was built. The lift-off is
     * theatre -- the spirit has to be seen leaving the block before it can be seen returning to it.
     *
     * <p>The climb stops at {@value #RESTORE_RISE} blocks, OR as soon as it stops gaining height, OR at the
     * ordinary ascend timeout. A brain under a roof is completely normal (villages have roofs), and without
     * the middle test the spirit would grind against the ceiling for the full timeout every time.
     */
    private void runRestore(ServerLevel sl) {
        if (phase == Phase.ASCEND) {
            boolean gaining = Double.isNaN(lastY) || this.getY() > lastY + 0.01;
            wedgedTicks = gaining ? 0 : wedgedTicks + 1;
            lastY = this.getY();
            if (this.getY() < restore.getY() + 1.0 + RESTORE_RISE
                    && wedgedTicks < WEDGED_TICKS && airTicks <= ASCEND_TIMEOUT) {
                this.setDeltaMovement(0.0, SPEED, 0.0);
                this.hasImpulse = true;
                return;
            }
            phase = Phase.DESCEND;
        }

        double dx = restore.getX() + 0.5 - this.getX();
        double dy = restore.getY() + 1.1 - this.getY();
        double dz = restore.getZ() + 0.5 - this.getZ();
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 > 1.2 * 1.2) {
            double len = Math.sqrt(d2);
            this.setDeltaMovement(dx / len * SPEED, dy / len * SPEED, dz / len * SPEED);
            this.hasImpulse = true;
            return;
        }
        relight(sl);
    }

    /**
     * Contact: the husk wakes up. {@code onPlace} on the living node registers the village, and
     * {@code onRemove} on the husk drops the dead claim, so one {@code setBlock} is the whole handover.
     */
    private void relight(ServerLevel sl) {
        if (!sl.getBlockState(restore).is(ModBlocks.DEAD_BRAIN_NODE.get())) {
            // Somebody broke the husk while the spirit was in the air. The apple was still spent, so rather
            // than waste it the spirit falls back to the founding flight and carries the village elsewhere.
            TheVillageFightsBack.LOGGER.info(
                    "[The Village Fights Back] the husk at {} was gone when its Village Spirit came back; "
                    + "it goes looking for a new home instead.", restore);
            restore = null;
            phase = Phase.SEARCH;
            return;
        }
        sl.setBlock(restore, ModBlocks.VILLAGE_BRAIN_NODE.get().defaultBlockState(), 3);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                restore.getX() + 0.5, restore.getY() + 1.2, restore.getZ() + 0.5, 40, 0.6, 0.6, 0.6, 0.02);
        sl.playSound(null, restore, SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 1.0F, 1.0F);
        sl.players().forEach(pl -> pl.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§aThe village brain beats again."), false));
        TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] a Village Spirit woke the brain node at {}.", restore);
        payReviver(sl);
        this.discard();
    }

    /**
     * Killing it turns the world over, exactly as falling into the void with Darius' Special Sauce does.
     *
     * <p>Shooting the spirit down is what ends a village for good, and until now it cost nothing to do and
     * looked like nothing happening -- a small blue thing stops flying. The dimension going upside down for
     * five seconds is the village's answer, and it reuses the reversal already built for the Special Sauce
     * rather than inventing a second one. The camera roll still honours
     * {@code reversed_gravity_flips_screen}; that check lives inside the broadcast.
     */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource cause) {
        super.die(cause);
        if (this.level() instanceof ServerLevel sl) ReversedGravity.flip(sl);
    }

    /** {@value #CRUISE_HEIGHT} above the ground, or as high as the dimension's ceiling allows. */
    private int cruiseY(ServerLevel sl, BlockPos at) {
        int ground = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        return Math.min(ground + CRUISE_HEIGHT, sl.getMaxBuildHeight() - 2);
    }

    /**
     * One steering vector, built from what is around it. Repulsion from anything dangerous, attraction toward
     * passive animals -- the spirit reads a landscape by its livestock, and a place with cows and sheep in it
     * is a place a village can live.
     *
     * <p>Every query is a bounded box around the spirit; there is never a world-sized one.
     */
    private Vec3 steer(ServerLevel sl) {
        Vec3 here = this.position();
        Vec3 push = Vec3.ZERO;

        AABB box = this.getBoundingBox().inflate(SENSE_RANGE);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e == this) continue;
            Vec3 away = here.subtract(e.position());
            double d2 = Math.max(1.0, away.lengthSqr());
            if (e instanceof Player || e instanceof Enemy) {
                push = push.add(away.normalize().scale(6.0 / d2 * 64.0));   // strongest: get clear of danger
            } else if (e instanceof Animal) {
                push = push.subtract(away.normalize().scale(1.0 / d2 * 24.0)); // drawn toward density
            }
        }

        // Away from every other brain node: a relocated village should be a NEW village, not a suburb.
        if (sl.getServer() != null) {
            for (GlobalPos gp : BrainNodeData.get(sl.getServer()).blocks()) {
                if (!gp.dimension().equals(sl.dimension())) continue;
                Vec3 away = here.subtract(Vec3.atCenterOf(gp.pos()));
                double len = away.length();
                if (len > NODE_REPEL_RANGE || len < 1.0E-3) continue;
                push = push.add(away.normalize().scale(8.0 * (1.0 - len / NODE_REPEL_RANGE)));
            }
        }

        // And away from where it started, so it actually leaves rather than orbiting the ruin.
        if (origin != null) {
            Vec3 away = here.subtract(Vec3.atCenterOf(origin));
            double len = away.length();
            if (len > 1.0E-3 && len < 160.0) push = push.add(away.normalize().scale(4.0));
        }

        if (push.lengthSqr() < 1.0E-6) {                       // nothing to react to -> keep going, or pick a way
            return heading.lengthSqr() > 1.0E-4 ? heading
                    : new Vec3(this.random.nextDouble() * 2 - 1, 0, this.random.nextDouble() * 2 - 1).normalize();
        }
        return new Vec3(push.x, 0.0, push.z).normalize();
    }

    /**
     * Is this a place to found a village? No hostile within {@value #CLEAR_RADIUS} blocks and no alien block
     * (sculk, or anything from another mod -- {@link BlockMatcher#isAlien} already means exactly that) in the
     * same radius.
     *
     * <p>After {@value #RELAX_TICKS} the requirement is dropped entirely, so a spirit in a bad neighbourhood
     * eventually settles instead of circling forever.
     *
     * <p>Ordered cheap-first on purpose: the entity query rejects most candidates, and only what survives it
     * pays for the block scan. That scan samples every other block on each axis -- a
     * {@value #CLEAR_RADIUS}-radius sphere is some 4,000 blocks and this runs on a
     * {@value #EVAL_INTERVAL}-tick interval, so full resolution would be real money for an answer that does
     * not need to be exact. Missing one sculk block in eight is not a correctness problem.
     */
    private boolean siteIsGood(ServerLevel sl, BlockPos at) {
        if (airTicks >= RELAX_TICKS) return true;

        AABB box = new AABB(at).inflate(CLEAR_RADIUS);
        if (!sl.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && (e instanceof Enemy)).isEmpty()) {
            return false;
        }

        int r2 = CLEAR_RADIUS * CLEAR_RADIUS;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx += 2) {
            for (int dz = -CLEAR_RADIUS; dz <= CLEAR_RADIUS; dz += 2) {
                for (int dy = -CLEAR_RADIUS; dy <= CLEAR_RADIUS; dy += 2) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (!sl.isLoaded(m)) continue;
                    if (BlockMatcher.isAlien(sl.getBlockState(m))) return false;
                }
            }
        }
        return true;
    }

    /** Drop to the floor and plant the node there. */
    private void descend(ServerLevel sl) {
        BlockPos floor = floorUnder(sl, this.blockPosition());
        if (floor == null) {                       // nothing below (a void column) -> go back to looking
            phase = Phase.SEARCH;
            return;
        }
        if (this.getY() > floor.getY() + 1.2) {
            this.setDeltaMovement(0.0, -SPEED, 0.0);
            this.hasImpulse = true;
            return;
        }
        found(sl, floor.above());
    }

    private BlockPos floorUnder(ServerLevel sl, BlockPos from) {
        BlockPos.MutableBlockPos m = from.mutable();
        for (int i = 0; i < CRUISE_HEIGHT * 4 && m.getY() > sl.getMinBuildHeight(); i++) {
            m.move(0, -1, 0);
            if (!sl.isLoaded(m)) return null;
            BlockState s = sl.getBlockState(m);
            if (s.blocksMotion()) return m.immutable();
        }
        return null;
    }

    /**
     * Plant the brain node and vanish. {@code onPlace} registers it, so the new village starts itself.
     *
     * <p>The same two refusals a settler's build answers to are checked here, so a spirit cannot drop a village
     * centre inside a player's claim or through a building that is already standing.
     */
    private void found(ServerLevel sl, BlockPos at) {
        if (StructureZones.get(sl).intersectsForeign(at.getX(), at.getY(), at.getZ(),
                at.getX(), at.getY(), at.getZ())
                || MarkerZones.get(sl).blocked(at.getX(), at.getY(), at.getZ())) {
            phase = Phase.SEARCH;                  // not here -- keep looking
            return;
        }
        sl.setBlock(at, ModBlocks.VILLAGE_BRAIN_NODE.get().defaultBlockState(), 3);
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0.0);
        sl.playSound(null, at, SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 1.0F, 1.0F);
        TheVillageFightsBack.LOGGER.info(
                "[The Village Fights Back] a Village Spirit founded a new village at {} after {}s aloft.",
                at, airTicks / 20);

        // Pay the player who fed the husk. Deliberately on ARRIVAL rather than on the apple: the apple buys an
        // attempt, and a spirit shot down in flight founds nothing -- the village standing again is the thing
        // being rewarded. On both experiences, since bringing a village back is worth the same either way.
        payReviver(sl);
        this.discard();
    }

    /**
     * Pay whoever fed the husk its golden apple.
     *
     * <p>Deliberately on ARRIVAL rather than on the apple: the apple buys an attempt, and a spirit shot down
     * in flight leaves nothing standing. A village saved in place and a village founded anew are worth the
     * same {@value #REVIVE_REWARD}, because the player did the same thing to earn either.
     */
    private void payReviver(ServerLevel sl) {
        if (reviver == null) return;
        net.minecraft.server.level.ServerPlayer p = sl.getServer().getPlayerList().getPlayer(reviver);
        if (p != null) {
            ReputationData.get(sl.getServer()).add(p, REVIVE_REWARD, "brought a village back to life");
        }
    }

    // --- persistence -------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SpiritPhase", phase.ordinal());
        tag.putInt("SpiritAirTicks", airTicks);
        if (origin != null) tag.putLong("SpiritOrigin", origin.asLong());
        if (reviver != null) tag.putUUID("SpiritReviver", reviver);
        if (errand != null) tag.putLong("SpiritErrand", errand.asLong());
        if (restore != null) tag.putLong("SpiritRestore", restore.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Phase[] all = Phase.values();
        int i = tag.getInt("SpiritPhase");
        phase = (i >= 0 && i < all.length) ? all[i] : Phase.ASCEND;
        airTicks = tag.getInt("SpiritAirTicks");
        origin = tag.contains("SpiritOrigin") ? BlockPos.of(tag.getLong("SpiritOrigin")) : null;
        if (tag.contains("SpiritErrand")) setErrand(BlockPos.of(tag.getLong("SpiritErrand")));
        restore = tag.contains("SpiritRestore") ? BlockPos.of(tag.getLong("SpiritRestore")) : null;
        reviver = tag.hasUUID("SpiritReviver") ? tag.getUUID("SpiritReviver") : null;
    }
}
