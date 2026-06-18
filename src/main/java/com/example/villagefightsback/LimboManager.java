package com.example.villagefightsback;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Runs the Limbo "holding cell": each creature banished into Limbo is leashed by a {@link LimboGravelBlock}
 * dropped where it stood (with a floating name tag), tracked in {@link LimboData}, and returned when that gravel
 * is freed -- broken, or its carried "soul gravel" item dropped/destroyed. Falling just re-homes a leash.
 *
 * <p>Extras layered on top: a banished WITHER STORM is one creature anchored by MANY gravel "parts" (breaking
 * any returns the whole storm and clears the rest); creatures over {@value LimboData#UNSTABLE_HP} HP are
 * UNSTABLE -- their placed gravel self-destructs after {@value #UNSTABLE_TICKS} ticks (silk-mining it into the
 * carried item is the only stable hold); and Silk Touch converts a gravel into a carried soul-gravel item that
 * keeps the creature banished until it is freed.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LimboManager {
    private LimboManager() {}

    public static final int MAX_CREATURES = 100;
    public static final int UNSTABLE_TICKS = 600;    // 30s: a >150 HP hold's placed gravel self-destructs
    private static final int FORGET_GRACE_TICKS = 100; // 5s grace before a missing creature is "forgotten"
    private static final int TICK_INTERVAL = 20;     // register / cap / countdown upkeep ~1/s
    private static final int PENDING_STALE_TICKS = 200;
    private static final AABB SCAN = new AABB(-48, 40, -48, 48, 100, 48);

    private record PendingOrigin(ResourceKey<Level> dim, BlockPos pos, long tick) {}
    private static final Map<UUID, PendingOrigin> PENDING = new ConcurrentHashMap<>();
    private static final Set<UUID> LIMBO_PLAYERS = ConcurrentHashMap.newKeySet();

    // --- capture where a MOB came from, just before it enters Limbo (changeDimension path) ---
    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!LimboDimension.LIMBO.equals(event.getDimension())) return;
        Entity e = event.getEntity();
        if (e.level().isClientSide() || !(e instanceof LivingEntity)) return;
        if (LimboDimension.LIMBO.equals(e.level().dimension())) return;
        PENDING.put(e.getUUID(), new PendingOrigin(e.level().dimension(), e.blockPosition(), e.level().getGameTime()));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        UUID id = sp.getUUID();
        if (LimboDimension.LIMBO.equals(sp.level().dimension())) {
            if (LIMBO_PLAYERS.add(id)) {
                sp.sendSystemMessage(Component.literal("Get out. Now.").withStyle(ChatFormatting.DARK_RED));
            }
        } else {
            LIMBO_PLAYERS.remove(id);
            PENDING.put(id, new PendingOrigin(sp.level().dimension(), sp.blockPosition(), sp.level().getGameTime()));
        }
    }

    // --- a creature that dies IN Limbo: drop all its leashes (markers + blocks + carried items), no return ---
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel sl)) return;
        if (!LimboDimension.LIMBO.equals(sl.dimension())) return;
        releaseCreatureNoReturn(sl.getServer(), event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % TICK_INTERVAL != 0) return;
        ServerLevel limbo = server.getLevel(LimboDimension.LIMBO);
        if (limbo == null) return;
        long _p = ModProfiler.now();
        LimboData data = LimboData.get(server);
        long now = limbo.getGameTime();

        // 1. Register newly-arrived tracked creatures (the auto path: changeDimension'd mobs + teleported players).
        //    The wither storm is registered explicitly at banish time, so it is already known here and skipped.
        for (LivingEntity e : limbo.getEntitiesOfClass(LivingEntity.class, SCAN, LivingEntity::isAlive)) {
            UUID id = e.getUUID();
            if (data.isCreatureRegistered(id)) continue;
            PendingOrigin po = PENDING.remove(id);
            if (po == null) continue; // not tracked-in (e.g. /summon) -> ignore
            // BASE max-health (the entity's designed HP), not getMaxHealth() -- which folds in transient
            // attribute modifiers (buffs/debuffs) -- and certainly not current HP. A mob's instability is a
            // property of what it IS, so a temporary buff or a chunk of damage must not change the verdict.
            float maxhp = (float) e.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            String typeId = typeKey(e);
            long unstable = maxhp > LimboData.UNSTABLE_HP ? now + UNSTABLE_TICKS : 0L;
            BlockPos gravelPos = placeGravel(server, po.dim(), po.pos(), data);
            ServerLevel origin = server.getLevel(po.dim());
            String name = e.getName().getString();
            UUID marker = origin != null ? spawnMarker(origin, gravelPos, name) : null;
            LimboData.Entry entry = new LimboData.Entry(UUID.randomUUID(), id, po.dim(), gravelPos, now, name,
                    marker, typeId, maxhp, false, false, unstable);
            data.add(entry);
            if (origin != null) stampBE(origin, gravelPos, entry);
        }

        // 2. Containment: keep every banished creature INSIDE the force-loaded cube. The cube walls are passable,
        //    so a big/mobile creature (the wither storm!) can drift out, unload, and then be wrongly "forgotten"
        //    in step 4 -- which would vanish its gravels long before the countdown. Re-centre any drifter and pin
        //    it so it never despawns.
        Set<UUID> contained = new HashSet<>();
        for (LimboData.Entry entry : data.entries()) {
            if (!contained.add(entry.creature)) continue;
            Entity e = limbo.getEntity(entry.creature);
            if (e == null) continue;
            if (e instanceof net.minecraft.world.entity.Mob mob) mob.setPersistenceRequired();
            if (LimboDimension.outsideCube(e)) {
                BlockPos sp = LimboDimension.cubeSpawn();
                e.teleportTo(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5);
            }
        }

        // 3. Unstable holds (> 150 HP): a placed gravel past its deadline self-destructs -> the creature returns.
        for (LimboData.Entry entry : data.entries()) {
            if (entry.held || entry.unstableUntil == 0L || now < entry.unstableUntil) continue;
            // If the player has STARTED collecting this creature -- any sibling leash already silk-mined into a
            // carried soul gravel -- stop the auto-return so they can finish gathering the rest at their own pace
            // (collecting them all banishes it for good; deliberately breaking a remaining piece still returns
            // it). Without this, a multi-part wither storm's still-placed parts self-destruct at 30s and yank the
            // whole storm back mid-collection, wiping the pieces already gathered -- making silk collection
            // unwinnable. A lone-gravel boss has no siblings, so it is unaffected.
            if (anyHeldSibling(data, entry.creature)) continue;
            ServerLevel origin = server.getLevel(entry.originDim);
            if (origin != null && origin.getBlockState(entry.gravelPos).is(ModBlocks.LIMBO_GRAVEL.get())) {
                TheVillageFightsBack.LOGGER.info("[Limbo] unstable self-destruct: creature {} {}t after entry (deadline {}t)",
                        entry.creature, now - entry.entryTime, UNSTABLE_TICKS);
                origin.removeBlock(entry.gravelPos, false); // entry present -> onGravelDestroyed -> return
            }
        }

        // 4. Forget creatures that genuinely vanished from Limbo (died/removed) -- clear their leashes. A grace
        //    period after registration prevents forgetting one that is merely still settling or briefly unloaded
        //    (which would vanish its gravels before the 30s countdown).
        Set<UUID> handled = new HashSet<>();
        for (LimboData.Entry entry : data.entries()) {
            if (!handled.add(entry.creature)) continue;
            if (now - entry.entryTime < FORGET_GRACE_TICKS) continue;
            if (limbo.getEntity(entry.creature) == null) {
                Entity elsewhere = findAnywhere(server, entry.creature);
                String where = elsewhere == null ? "no (dead/unloaded)"
                        : elsewhere.level().dimension().location() + " @ " + elsewhere.blockPosition();
                TheVillageFightsBack.LOGGER.info("[Limbo] forgetting creature {} ({}t after entry); found elsewhere: {}",
                        entry.creature, now - entry.entryTime, where);
                releaseCreatureNoReturn(server, entry.creature);
            }
        }

        // 4. Over-population: release the most ancient CREATURES (a storm counts once) until back at the limit.
        //    A creature the player has fully COLLECTED -- every one of its leashes carried as a soul-gravel item
        //    (all held) -- is NEVER auto-evicted: collecting all of a boss's gravels banishes it for good. Only
        //    creatures with at least one still-PLACED gravel are eligible.
        while (data.creatureCount() > MAX_CREATURES) {
            LimboData.Entry oldest = null;
            for (LimboData.Entry e : data.entries()) {
                if (e.held) continue; // skip carried leashes -> a fully-held creature can't be the victim
                if (oldest == null || e.entryTime < oldest.entryTime) oldest = e;
            }
            if (oldest == null) break; // everything left is fully held -> stop (don't betray the player's hold)
            ServerLevel dest = server.getLevel(oldest.originDim);
            restoreCreatureAndCleanup(server, oldest.creature, dest,
                    oldest.gravelPos.getX() + 0.5, oldest.gravelPos.getY(), oldest.gravelPos.getZ() + 0.5);
            if (data.isCreatureRegistered(oldest.creature)) data.removeByCreature(oldest.creature); // safety
        }

        // 5. Drop stale pending-origins.
        PENDING.values().removeIf(po -> now - po.tick() > PENDING_STALE_TICKS);
        ModProfiler.add("limbo", _p);
    }

    // --- registration helpers -------------------------------------------------------------------------

    private static BlockPos placeGravel(MinecraftServer server, ResourceKey<Level> dim, BlockPos pos, LimboData data) {
        ServerLevel origin = server.getLevel(dim);
        if (origin == null) return pos;
        BlockPos p = restOnSolid(origin, pos); // rest on solid ground so the gravel can't fall and break
        int guard = 0;
        while (data.hasGravelAt(dim, p) && guard++ < 8) p = p.above();
        origin.setBlock(p, ModBlocks.LIMBO_GRAVEL.get().defaultBlockState(), 3);
        return p;
    }

    /** Walk a candidate gravel cell DOWN until it rests on a solid (non-free) block. A gravel left floating over
     *  air/fluid (common on the wrecked, cratered terrain a wither storm leaves behind) would FALL the moment it
     *  ticks; if it then failed to re-place it would break and wrongly return its creature long before the 30s
     *  countdown. Resting it on solid ground up front removes that accidental-return path entirely. */
    private static BlockPos restOnSolid(ServerLevel level, BlockPos spot) {
        int minY = level.getMinBuildHeight() + 1;
        while (spot.getY() > minY
                && net.minecraft.world.level.block.FallingBlock.isFree(level.getBlockState(spot.below()))) {
            spot = spot.below();
        }
        return spot;
    }

    /** Stamp a gravel's block entity with its hold data (the renderer reads the colour + instability deadline). */
    private static void stampBE(ServerLevel origin, BlockPos pos, LimboData.Entry e) {
        if (origin.getBlockEntity(pos) instanceof LimboGravelBlockEntity be) {
            be.setHold(e.typeId, e.name, e.stormPart, e.unstableUntil);
        }
    }

    /** Explicitly register a banished WITHER STORM: ONE creature anchored by {@code partCount} gravel "parts"
     *  scattered around its origin. Called from the banish path (the storm never fires a travel event). */
    public static void registerWitherStorm(MinecraftServer server, UUID creature, ResourceKey<Level> dim,
                                           BlockPos origin, float maxHealth, int partCount) {
        ServerLevel level = server.getLevel(dim);
        if (level == null) return;
        LimboData data = LimboData.get(server);
        long now = level.getGameTime();
        String typeId = Factions.WITHER_STORM_ID.toString();
        long unstable = maxHealth > LimboData.UNSTABLE_HP ? now + UNSTABLE_TICKS : 0L;
        int placed = 0;
        for (int attempt = 0; attempt < partCount * 4 && placed < partCount; attempt++) {
            int dx = (int) Math.round((level.random.nextDouble() * 2 - 1) * 10);
            int dz = (int) Math.round((level.random.nextDouble() * 2 - 1) * 10);
            int x = origin.getX() + dx, z = origin.getZ() + dz;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos spot = restOnSolid(level, new BlockPos(x, y, z)); // rest on solid so the part can't fall+break
            if (data.hasGravelAt(dim, spot)) continue;
            placeStormPart(level, data, creature, dim, spot, now, typeId, maxHealth, unstable);
            placed++;
        }
        // Never leave the storm with NO leash (it could never be returned): force one at the origin column.
        if (placed == 0) {
            BlockPos p = restOnSolid(level, origin);
            int guard = 0;
            while (data.hasGravelAt(dim, p) && guard++ < 8) p = p.above();
            placeStormPart(level, data, creature, dim, p, now, typeId, maxHealth, unstable);
        }
    }

    private static void placeStormPart(ServerLevel level, LimboData data, UUID creature, ResourceKey<Level> dim,
                                       BlockPos spot, long now, String typeId, float maxHealth, long unstable) {
        level.setBlock(spot, ModBlocks.LIMBO_GRAVEL.get().defaultBlockState(), 3);
        UUID marker = spawnMarker(level, spot, "Wither Storm part");
        LimboData.Entry e = new LimboData.Entry(UUID.randomUUID(), creature, dim, spot, now,
                "Wither Storm part", marker, typeId, maxHealth, true, false, unstable);
        data.add(e);
        stampBE(level, spot, e);
    }

    // --- destruction / restore -------------------------------------------------------------------------

    /** A gravel was DESTROYED (broken, exploded, self-destructed) -> return its creature here. A wither-storm
     *  part returns the WHOLE storm and clears all its other parts. */
    public static void onGravelDestroyed(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        LimboData.Entry entry = LimboData.get(server).getByGravel(level.dimension(), pos);
        if (entry == null) return;
        TheVillageFightsBack.LOGGER.info("[Limbo] gravel destroyed at {} ({}{}) -> returning creature {}",
                pos, entry.name, entry.stormPart ? " [storm part]" : "", entry.creature);
        restoreCreatureAndCleanup(server, entry.creature, level,
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    /** A falling gravel could NOT re-place after sliding down (it broke as an item). This is a PHYSICS loss, not
     *  a deliberate break -- so for a multi-part wither storm, dropping one redundant "part" leash must NOT end
     *  the banishment (the player breaking a part on purpose still does, via {@link #onGravelDestroyed}). Only if
     *  this was the creature's LAST leash do we return it, so a single-gravel creature is never stranded. */
    public static void onGravelFellAndBroke(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        LimboData data = LimboData.get(server);
        LimboData.Entry entry = data.getByGravel(level.dimension(), pos);
        if (entry == null) return;
        int remaining = data.getByCreature(entry.creature).size();
        if (remaining > 1) {
            TheVillageFightsBack.LOGGER.info("[Limbo] gravel fell+broke at {} ({}); dropping 1 of {} leashes, "
                    + "creature {} stays banished", pos, entry.name, remaining, entry.creature);
            killMarker(server, entry);
            data.removeByGravel(level.dimension(), pos);
            return;
        }
        TheVillageFightsBack.LOGGER.info("[Limbo] last gravel fell+broke at {} ({}) -> returning creature {}",
                pos, entry.name, entry.creature);
        restoreCreatureAndCleanup(server, entry.creature, level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    /** A gravel was SILK-mined -> it becomes a carried soul-gravel item; the creature STAYS banished. */
    public static void onGravelSilkHarvested(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        LimboData data = LimboData.get(server);
        LimboData.Entry entry = data.getByGravel(level.dimension(), pos);
        if (entry == null) return;
        data.markHeld(entry);                       // no placed block, countdown paused -- creature stays in Limbo
        killMarker(server, entry);
        Block.popResource(level, pos, LimboSoulGravelItem.of(entry)); // loose item; 3s grace to pick it up
    }

    /** A soul-gravel item was placed back as a block -> re-anchor its leash (wherever it now sits), restart the
     *  self-destruct countdown for unstable holds, re-spawn the name tag and stamp the block entity. */
    public static void onSoulGravelPlaced(ServerLevel level, BlockPos pos, ItemStack stack) {
        MinecraftServer server = level.getServer();
        LimboData data = LimboData.get(server);
        UUID token = LimboSoulGravelItem.token(stack);
        LimboData.Entry entry = token != null ? data.getByToken(token) : null;
        // Re-anchor only a genuinely HELD leash. A stale token (creature already returned) or a DUPLICATED item
        // (creative pick-block / /give) whose leash is still placed elsewhere must NOT steal the index -> inert.
        if (entry == null || !entry.held) return;
        long unstable = entry.maxHealth > LimboData.UNSTABLE_HP ? level.getGameTime() + UNSTABLE_TICKS : 0L;
        UUID marker = spawnMarker(level, pos, entry.name);
        data.markPlaced(entry, level.dimension(), pos, marker, unstable);
        stampBE(level, pos, entry);
    }

    /** A carried soul-gravel item was FREED (loose &gt;3s, or destroyed in lava/void) -> return its creature at
     *  the item's location. A wither-storm part frees the WHOLE storm there. */
    public static void freeSoulGravel(ServerLevel level, double x, double y, double z, UUID token) {
        MinecraftServer server = level.getServer();
        LimboData.Entry entry = LimboData.get(server).getByToken(token);
        if (entry == null) return; // stale -> nothing to free
        restoreCreatureAndCleanup(server, entry.creature, level, x, y, z);
    }

    /** A marker fell to a new spot -> re-home its leash + name tag + block-entity stamp (creature stays). */
    public static void onGravelLanded(ServerLevel level, BlockPos from, BlockPos to) {
        MinecraftServer server = level.getServer();
        LimboData data = LimboData.get(server);
        LimboData.Entry e = data.getByGravel(level.dimension(), from);
        if (e == null) return;
        data.moveGravel(e, to);
        moveMarker(level, e.marker, to);
        stampBE(level, to, e); // a falling block does not carry its block entity -> repopulate on landing
    }

    /** Sleeping in the Limbo bed = return (destroy your own leash). */
    public static void requestReturn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        LimboData data = LimboData.get(server);
        LimboData.Entry entry = firstLeash(data, player.getUUID());
        if (entry == null) {
            player.sendSystemMessage(Component.literal("There is no way back from here.")
                    .withStyle(ChatFormatting.DARK_RED));
            return;
        }
        ServerLevel origin = server.getLevel(entry.originDim);
        boolean removed = !entry.held && origin != null
                && origin.getBlockState(entry.gravelPos).is(ModBlocks.LIMBO_GRAVEL.get())
                && origin.removeBlock(entry.gravelPos, false); // -> onGravelDestroyed -> return
        if (!removed) {
            restoreCreatureAndCleanup(server, player.getUUID(), origin,
                    entry.gravelPos.getX() + 0.5, entry.gravelPos.getY(), entry.gravelPos.getZ() + 0.5);
        }
    }

    /** Banish toggle: an entity ALREADY in Limbo that is hit by a beam/potion (or drinks one) returns to the
     *  overworld. Tracked creatures go back via their leash; an untracked one is dropped at the overworld spawn. */
    public static boolean sendBackToOverworld(Entity e) {
        if (!(e.level() instanceof ServerLevel sl) || !LimboDimension.LIMBO.equals(sl.dimension())) return false;
        MinecraftServer server = sl.getServer();
        LimboData data = LimboData.get(server);
        LimboData.Entry leash = firstLeash(data, e.getUUID());
        if (leash != null) {
            ServerLevel dest = server.getLevel(leash.originDim);
            restoreCreatureAndCleanup(server, e.getUUID(), dest,
                    leash.gravelPos.getX() + 0.5, leash.gravelPos.getY(), leash.gravelPos.getZ() + 0.5);
            return true;
        }
        ServerLevel overworld = server.overworld();
        BlockPos sp = overworld.getSharedSpawnPos();
        double x = sp.getX() + 0.5, y = sp.getY(), z = sp.getZ() + 0.5;
        if (e instanceof ServerPlayer p) p.teleportTo(overworld, x, y, z, p.getYRot(), p.getXRot());
        else e.teleportTo(overworld, x, y, z, java.util.Set.of(), e.getYRot(), e.getXRot());
        return true;
    }

    // --- shared restore / cleanup ---------------------------------------------------------------------

    /** Restore a creature to {@code dest} at (x,y,z), then clear ALL of its leashes (markers, blocks, items). */
    private static void restoreCreatureAndCleanup(MinecraftServer server, UUID creature, ServerLevel dest,
                                                  double x, double y, double z) {
        if (dest == null) dest = server.overworld(); // never strand the creature if its origin dim is gone
        Set<LimboData.Entry> removed = releaseCreatureNoReturn(server, creature);
        boolean moved = teleportCreature(server, creature, dest, x, y, z);
        // A returned wither storm regenerates its parts at the destination -- discard the now-orphaned ones the
        // core left ticking inside the (always-loaded) Limbo cube.
        if (moved && removed.stream().anyMatch(e -> e.stormPart)) discardLimboWitherStormParts(server);
    }

    /** Discard any orphaned wither-storm body parts left inside Limbo (after the core has been teleported out). */
    private static void discardLimboWitherStormParts(MinecraftServer server) {
        ServerLevel limbo = server.getLevel(LimboDimension.LIMBO);
        if (limbo == null) return;
        for (Entity e : limbo.getAllEntities()) {
            if (Factions.isWitherStormPart(e)) e.discard();
        }
    }

    /** Clear every leash for a creature WITHOUT returning it: kill name tags, silently remove placed gravel
     *  blocks, and strip any carried soul-gravel items from online players' inventories. */
    private static Set<LimboData.Entry> releaseCreatureNoReturn(MinecraftServer server, UUID creature) {
        LimboData data = LimboData.get(server);
        Set<LimboData.Entry> removed = data.removeByCreature(creature);
        if (removed.isEmpty()) return removed;
        for (LimboData.Entry e : removed) {
            killMarker(server, e);
            if (!e.held) {
                ServerLevel o = server.getLevel(e.originDim);
                if (o != null && o.getBlockState(e.gravelPos).is(ModBlocks.LIMBO_GRAVEL.get())) {
                    LimboGravelBlock.suppress(o, e.gravelPos);
                    o.removeBlock(e.gravelPos, false);
                }
            }
        }
        removeSoulItemsForCreature(server, creature);
        return removed;
    }

    /** Move the live Limbo creature out to {@code dest} at (x,y,z). Uses the engine cross-dimension teleport
     *  ({@code teleportTo}, like {@code /tp}) so multipart bosses that can't change dimensions still move, and
     *  ALL effects/NBT are preserved. No-op if the creature is no longer alive in Limbo. */
    private static boolean teleportCreature(MinecraftServer server, UUID creature, ServerLevel dest,
                                            double x, double y, double z) {
        ServerLevel limbo = server.getLevel(LimboDimension.LIMBO);
        if (limbo == null || dest == null) return false;
        Entity e = limbo.getEntity(creature);
        if (e == null || !e.isAlive()) return false;
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(dest, x, y, z, sp.getYRot(), sp.getXRot());
        } else {
            e.teleportTo(dest, x, y, z, java.util.Set.of(), e.getYRot(), e.getXRot());
        }
        return true;
    }

    /** True if any online player is currently carrying a soul-gravel item for {@code token} (so a removed loose
     *  item was picked up / stored, not destroyed -- don't free the creature). */
    public static boolean isTokenCarried(MinecraftServer server, UUID token) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (LimboSoulGravelItem.isSoulGravel(s) && token.equals(LimboSoulGravelItem.token(s))) return true;
            }
        }
        return false;
    }

    /** Remove every carried soul-gravel item referencing {@code creature} from online players' inventories. */
    private static void removeSoulItemsForCreature(MinecraftServer server, UUID creature) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!LimboSoulGravelItem.isSoulGravel(s)) continue;
                CompoundTag h = LimboSoulGravelItem.hold(s);
                if (h != null && h.hasUUID("creature") && creature.equals(h.getUUID("creature"))) {
                    inv.setItem(i, ItemStack.EMPTY);
                }
            }
        }
    }

    /** A leash to return this creature by -- preferring a PLACED one (its gravelPos is a real spot) over a
     *  carried/held one (whose gravelPos is stale). */
    private static LimboData.Entry firstLeash(LimboData data, UUID creature) {
        LimboData.Entry held = null;
        for (LimboData.Entry e : data.getByCreature(creature)) {
            if (!e.held) return e;
            held = e;
        }
        return held;
    }

    /** Find a creature by UUID across every server dimension (diagnostic: tells us where a "missing from Limbo"
     *  creature actually went -- back to the overworld, or truly gone). */
    private static Entity findAnywhere(MinecraftServer server, UUID id) {
        for (ServerLevel lvl : server.getAllLevels()) {
            Entity e = lvl.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    /** True if any leash for {@code creature} is currently HELD (carried as a soul gravel) -- i.e. the player has
     *  begun collecting it. Used to pause a multi-part storm's self-destruct once collection is under way. */
    private static boolean anyHeldSibling(LimboData data, UUID creature) {
        for (LimboData.Entry e : data.getByCreature(creature)) if (e.held) return true;
        return false;
    }

    private static String typeKey(Entity e) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return id == null ? "" : id.toString();
    }

    // --- floating name-tag marker (an inert armor stand wearing the creature's name) ------------------

    private static UUID spawnMarker(ServerLevel origin, BlockPos pos, String name) {
        ArmorStand as = EntityType.ARMOR_STAND.create(origin);
        if (as == null) return null;
        as.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0.0F, 0.0F);
        as.setInvisible(true);
        as.setSilent(true);
        as.setInvulnerable(true);
        as.setNoGravity(true);
        as.setCustomName(Component.literal(name));
        as.setCustomNameVisible(true);
        CompoundTag tag = new CompoundTag();
        if (as.save(tag)) {
            tag.putBoolean("Marker", true);
            tag.putBoolean("Small", true);
            tag.putBoolean("NoBasePlate", true);
            as.load(tag);
        }
        origin.addFreshEntity(as);
        return as.getUUID();
    }

    private static void moveMarker(ServerLevel origin, UUID marker, BlockPos pos) {
        if (marker == null) return;
        Entity m = origin.getEntity(marker);
        if (m != null) m.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, m.getYRot(), m.getXRot());
    }

    private static void killMarker(MinecraftServer server, LimboData.Entry entry) {
        if (entry.marker == null) return;
        ServerLevel origin = server.getLevel(entry.originDim);
        if (origin == null) return;
        Entity m = origin.getEntity(entry.marker);
        if (m != null) m.discard();
    }
}
