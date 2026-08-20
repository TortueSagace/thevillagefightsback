package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.List;

import com.example.villagefightsback.goal.AxeCombatGoal;
import com.example.villagefightsback.wall.WallBuild;
import com.example.villagefightsback.wall.WallPlan;
import com.example.villagefightsback.goal.GolemRocketGoal;
import com.example.villagefightsback.goal.StoneAxeDefenseGoal;
import com.example.villagefightsback.goal.TntFleeGoal;
import com.example.villagefightsback.goal.WallBuildGoal;

import com.example.villagefightsback.platform.PersistentData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;
import com.example.villagefightsback.platform.event.EntityJoinLevelEvent;
import com.example.villagefightsback.platform.event.LivingAttackEvent;
import com.example.villagefightsback.platform.event.LivingChangeTargetEvent;
import com.example.villagefightsback.platform.event.LivingDeathEvent;
import com.example.villagefightsback.platform.event.LivingDropsEvent;
import com.example.villagefightsback.platform.event.LivingEquipmentChangeEvent;
import com.example.villagefightsback.platform.event.LivingHurtEvent;
import com.example.villagefightsback.platform.event.VillagerTradesEvent;
import com.example.villagefightsback.platform.EventPriority;
import com.example.villagefightsback.platform.SubscribeEvent;
import com.example.villagefightsback.platform.ModRegistries;

/**
 * Arms villagers, iron golems, and Guard Villagers guards, and wires the combat behaviour to them. As the
 * village evolves, defenders are upgraded (stage-3 villagers get the Alien Slasher, guards become emerald
 * knights, golems become gigachad golems) and the mod's ally-immunity keeps them from harming one another.
 */
public final class VillageEvents {
    private VillageEvents() {}

    /** Persistent retaliation state: which player angered this villager, and until when. */
    public static final String NBT_AGGRO_UUID = "vgun_aggro_uuid";
    public static final String NBT_AGGRO_UNTIL = "vgun_aggro_until";
    public static final int AGGRO_TICKS = 100; // ~5 seconds

    private static final double FOLLOW_RANGE = 32.0;

    // Bonus damage injected via NBT so the guns actually hurt (minigun base dmg is low). The villager
    // revolver is handled separately by the nerf below, so it has no bonus constant.
    private static final float MINIGUN_BONUS = 9.0F;    // 4  -> 13 (minigun also fades over range)
    private static final float FLAME_BONUS = 3.0F;      // 3  -> 6 per flame (x3 flames)


    // Guards have a chance to drop our native Fire Grenade on death.
    private static final float GUARD_FIRE_GRENADE_DROP_CHANCE = 0.5F;

    /** Arm villagers / iron golems / guards whenever they join a level (including world reload). */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof PathfinderMob mob)) return;

        // MCA Reborn only: an outlaw / cultist is a HOSTILE villager (a profession, not a separate entity). It
        // gets nothing from the village -- no kit, no roles, no wall-phasing. Bail before any of that is applied,
        // otherwise the village would hand a raider its Alien Slasher and a key to its own doors.
        if (com.example.villagefightsback.compat.MCACompat.isHostileVillager(mob)) return;

        // MCA Reborn only: re-hydrate any role flag that survived a villager replacement as a scoreboard tag,
        // BEFORE the stage flags are read below. No-op without MCA installed, and it never overwrites data
        // that came through intact -- it only fills gaps. Then re-mirror, so a villager whose data IS intact
        // refreshes its tags on every load.
        com.example.villagefightsback.compat.VillagerRoleTags.restore(mob);
        com.example.villagefightsback.compat.VillagerRoleTags.mirror(mob);

        // Let village defenders (and all iron golems) PATH through fake-cobblestone "doors": flip our
        // custom passable type's malus to 0 for them only (others keep the -1 default = blocked). This
        // mirrors FakeCobblestoneBlock.isPhaser, which lets the same set physically walk through.
        if (mob instanceof IronGolem || mob instanceof Villager || isGuard(mob) || Factions.isEmerald(mob)) {
            mob.setPathfindingMalus(ModPathTypes.ALLY_PASSABLE, 0.0F);
        }

        // Emeraldgers are stage-4 virus spawns: combat-only villagers that can NEVER take a role (miner/
        // settler/builder) or profession. So skip every role goal for them -- they still get the stage-3
        // combat loadout below (they spawn pre-upgraded).
        boolean emeraldger = mob instanceof EmeraldgerEntity;
        // An MCA Reborn guard/archer is a Villager doing a SOLDIER's job. It takes the guard kit further down,
        // and none of the civilian roles below: a guard has no business settling, building walls or mining.
        boolean soldier = com.example.villagefightsback.compat.MCACompat.isMcaGuard(mob);

        // A villager far from every brainNode can found a new home with a Blue TNT (independent of the
        // combat upgrade). Idempotent: goals are re-added on every load; the goal settles at most once.
        if (!emeraldger && !soldier && mob instanceof Villager settler
                && !hasGoal(settler, com.example.villagefightsback.goal.SettlerGoal.class)) {
            settler.goalSelector.addGoal(6, new com.example.villagefightsback.goal.SettlerGoal(settler));
        }

        // NO return-to-village goal. There used to be one: a villager outside its wall walked back toward the
        // brain node. It was dropped because it caused the thing it was meant to prevent -- every returning
        // villager aimed at the same single block, it fired the instant anyone stepped outside, and it never
        // gave up, so villagers heaped around the core and looked stuck.
        //
        // Aiming at a gate instead would have fixed the pile, but not the root problem: deciding "is this
        // villager outside the wall" means a point-in-polygon test against every column of a concave wall that
        // runs to a thousand columns. There is no cheap way to ask that per villager per tick, and a goal that
        // has to be throttled into vagueness to afford its own trigger is not worth having. Vanilla brings
        // villagers home via their beds and POIs, which is what it did before this existed.

        // A unit that falls in a hole and stops getting anywhere is lifted back to its node. Villagers AND
        // guards -- both walk into pits, and neither noticed before. Idempotent, and cheap when nothing is
        // stuck: see the goal's own note on why the gate order is the design.
        if (!emeraldger && (mob instanceof Villager || isGuard(mob))
                && !hasGoal(mob, com.example.villagefightsback.goal.UnstickVillagerGoal.class)) {
            mob.goalSelector.addGoal(7, new com.example.villagefightsback.goal.UnstickVillagerGoal(mob));
        }

        // A wall builder must not ignore a point-blank threat: every villager carries this (dormant) goal;
        // it only fires when the villager is a builder and a hostile is within ~3 blocks, interrupting the
        // build (priority 2, above WallBuildGoal's 3) until the threat is dead or leaves. Idempotent.
        if (!emeraldger && !soldier && mob instanceof Villager defender
                && !hasGoal(defender, com.example.villagefightsback.goal.BuilderDefenseGoal.class)) {
            defender.goalSelector.addGoal(2, new com.example.villagefightsback.goal.BuilderDefenseGoal(defender));
        }

        // Every villager carries the (dormant) miner goal; it only runs once the brain node tags it a miner
        // (level-2 infection). Idempotent. (Emeraldgers can't be miners, so they don't need it.)
        if (!emeraldger && !soldier && mob instanceof Villager v) {
            com.example.villagefightsback.infection.InfectionManager.ensureMinerGoal(v);
        }

        // Defenders start NERFED and permanently upgrade to the full gun loadout once they have been near a
        // level-3 Village Brain Node (UpgradeEvents flips the flag). Until then: villagers fight only with a
        // stone axe (and only when badly hurt), guards and golems are vanilla.
        boolean upgraded = UpgradeState.isUpgraded(mob);
        // Villagers and golems do NOT get the dig goal here. They did briefly, so a Nightmare raid could follow
        // through walls, and it was a bad trade: "only wakes when a pathfind fails" turned out to happen all the
        // time in ordinary village life, and wall builders in particular spent their days digging themselves
        // into holes they then could not climb out of. Raiders get it when they are drafted instead
        // (NightmareRaid.send), which is the only time any of them actually needs it.

        if (mob instanceof IronGolem golem) {
            if (golem.isPlayerCreated()) return;       // village golems only
            // Golems never consulted the village's grievance, so an Enemy of the Village could stroll past a
            // yard full of them. The villagers' goals have always read getAggroPlayer; the golems now do too.
            if (!hasGoal(golem, com.example.villagefightsback.goal.VillageEnemyTargetGoal.class)) {
                golem.targetSelector.addGoal(2, new com.example.villagefightsback.goal.VillageEnemyTargetGoal(golem));
            }
            // A gigachad is its own type: re-apply its kit on every load (goals aren't saved to NBT). Plain
            // village golems are converted into GigachadGolemEntity by UpgradeEvents near a lvl-3 brainNode.
            if (golem instanceof GigachadGolemEntity) armGolemFull(golem);
        } else if (isGuard(mob)) {
            // BEFORE the Villager branch: an MCA guard/archer is a Villager too, and must take the guard kit.
            ensureVillageDigGoal(mob);                             // EVERY guard, at every stage
            if (upgraded) armGuardFull(mob);                       // stage 3+: full emerald knight (mace-bounce)
            else if (UpgradeState.isStage2(mob)) armGuardStage2(mob); // stage 2: virus archer (no mace)
            // else: normal guard
        } else if (mob instanceof Villager villager) {
            if (upgraded) armVillagerFull(villager);
            else armVillagerNerfed(villager);
        }
    }

    /**
     * Give a guard the same digging ability the emerald faction has: when it cannot PATH to its prey it breaks
     * the soft block in the way instead of standing there. Idempotent, and applied to EVERY guard at EVERY
     * stage, not just an upgraded one, since being walled off from a fight is not a problem that waits for a
     * promotion.
     *
     * <p>Priority 5 mirrors the emerald units'. The goal carries NO goal flags, so the number barely matters:
     * it runs ALONGSIDE {@code StageThreeGuardGoal} rather than competing with it for the MOVE flag, and only
     * wakes up once a real pathfind to the target has already failed.
     *
     * <p>The village's own masonry is safe. {@code EmeraldDigGoal.isDiggable} refuses cobblestone, planks,
     * brick, both fake gates and this mod's reinforced/protecting glass, so a guard can tunnel through dirt or
     * a besieger's makeshift cover but cannot chew a hole in the wall it is defending.
     */
    private static void ensureVillageDigGoal(PathfinderMob mob) {
        // DIGGING IS GONE FROM ORDINARY VILLAGE LIFE. A guard used to carry EmeraldDigGoal at every stage,
        // and the trade never paid: the goal only wakes when a pathfind fails, which turns out to happen
        // constantly in a normal village -- around a fence, across a doorway, up its own wall -- so guards
        // spent their days quarrying the ground they were standing on, and the holes they left were a bigger
        // problem than the walls they got through.
        //
        // A raider still digs. NightmareRaid.send hands the goal out at the moment a unit is drafted, which
        // is the one time in the mod when following a player through terrain is the entire point.
        if (hasGoal(mob, com.example.villagefightsback.goal.CobwebCutGoal.class)) return; // re-added each load
        // Cutting free of a web is a separate problem from tunnelling to prey: no enemy and no failed path
        // needed, just a caught defender. It stays -- being stuck in a web is not digging.
        mob.goalSelector.addGoal(4, new com.example.villagefightsback.goal.CobwebCutGoal(mob));
    }

    /** Replace a plain village iron golem with a {@link GigachadGolemEntity} at the same spot; the new type
     *  arms itself in finalizeSpawn. Used when a village golem first reaches a level-3 brainNode. */
    public static void convertToGigachad(IronGolem old) {
        if (old instanceof GigachadGolemEntity) return;
        if (!(old.level() instanceof ServerLevel sl)) return;
        GigachadGolemEntity g = ModEntities.GIGACHAD_GOLEM.get().create(sl);
        if (g == null) return;
        g.moveTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), old.getXRot());
        g.yBodyRot = old.yBodyRot;
        g.yHeadRot = old.yHeadRot;
        g.finalizeSpawn(sl, sl.getCurrentDifficultyAt(old.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.CONVERSION, null, null);
        UpgradeState.setUpgraded(g);
        sl.addFreshEntity(g);
        old.discard();
    }

    /** Replace a plain village iron golem with a {@link DefenderGolemEntity} at the same spot (the stage-2
     *  golem evolution). The new type carries its own kit (150 HP + turret) by construction, so no arming is
     *  needed. A Defender Golem itself is later replaced by a {@link GigachadGolemEntity} at stage 4 via
     *  {@link #convertToGigachad}. */
    public static void convertToDefenderGolem(IronGolem old) {
        if (old instanceof DefenderGolemEntity || old instanceof GigachadGolemEntity) return;
        if (!(old.level() instanceof ServerLevel sl)) return;
        DefenderGolemEntity d = ModEntities.DEFENDER_GOLEM.get().create(sl);
        if (d == null) return;
        d.moveTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), old.getXRot());
        d.yBodyRot = old.yBodyRot;
        d.yHeadRot = old.yHeadRot;
        d.finalizeSpawn(sl, sl.getCurrentDifficultyAt(old.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.CONVERSION, null, null);
        sl.addFreshEntity(d);
        old.discard();
    }

    /** Apply the full upgrade to an already-spawned defender (called when it first reaches a lvl-3 brainNode). */
    public static void applyUpgrade(LivingEntity e) {
        if (e instanceof PathfinderMob soldier && isGuard(soldier)) {
            // Checked first: an MCA guard is a Villager as well, and must upgrade to the GUARD kit.
            if (UpgradeState.isUpgraded(soldier)) armGuardFull(soldier);
            else if (UpgradeState.isStage2(soldier)) armGuardStage2(soldier);
        } else if (e instanceof Villager villager) {
            removeGoal(villager, StoneAxeDefenseGoal.class);
            armVillagerFull(villager); // replaces the stone axe with the revolver
        } else if (e instanceof IronGolem golem) {
            if (!golem.isPlayerCreated()) armGolemFull(golem);
        }
    }

    /** NERFED villager: a stone axe (shown via the vanilla crossed-arms layer), defensive melee only. */
    private static void armVillagerNerfed(Villager villager) {
        if (hasGoal(villager, StoneAxeDefenseGoal.class)) return;
        if (!(villager.getMainHandItem().getItem() instanceof AxeItem)) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
            villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        villager.goalSelector.addGoal(2, new StoneAxeDefenseGoal(villager));
        // (WallBuildGoal is added separately by BrainNodeEvents.ensureWallBuildGoal when a brainNode is near.)
    }

    /**
     * Put the correct melee weapon back in a defender villager's hand: its stage-3+ Alien Slasher (or the
     * Alien Eradicator at stage 4 / for an Emeraldger) if upgraded, else the nerfed stone axe. Wall-building
     * empties the hand ({@link com.example.villagefightsback.goal.WallBuildGoal} digs with stone tools, then
     * clears the slot), so the build goal's stop() and the combat goals call this to make sure an idle or
     * fighting defender is actually holding -- and so visibly wielding, and dealing the on-hit payload of --
     * its weapon. Idempotent: only touches the slot when it holds the wrong item.
     */
    public static void restoreDefenderWeapon(Villager villager) {
        Item weapon = UpgradeState.isUpgraded(villager)
                ? ((UpgradeState.isStage4(villager) || villager instanceof EmeraldgerEntity)
                        ? ModItems.ALIEN_ERADICATOR.get() : ModItems.ALIEN_SLASHER.get())
                : Items.STONE_AXE;
        if (villager.getMainHandItem().getItem() != weapon) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
            villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
    }

    /**
     * Put a defender's weapon back when something else takes it out of its hand.
     *
     * <p>This exists for MCA Reborn. MCA runs an {@code EquipmentTask} brain behaviour that periodically
     * re-equips its villagers from its own configured {@code EquipmentSet}, which silently strips the stage
     * weapon this mod gave them: the weapon appears on spawn and vanishes a moment later, leaving a stage-4
     * defender holding nothing. Since the kit is applied once on join, nothing put it back.
     *
     * <p>Edge-triggered on the equipment change itself, so there is no polling. Re-arming fires this event
     * again, but {@link #restoreDefenderWeapon} and the guard branch below only touch the slot when it holds
     * the WRONG item, so the second pass is a no-op and it cannot recurse.
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getSlot() != EquipmentSlot.MAINHAND) return;
        if (event.getEntity().level().isClientSide()) return;
        LivingEntity e = event.getEntity();
        // Only defenders this mod has actually armed. An un-upgraded villager legitimately holds nothing.
        if (!UpgradeState.isUpgraded(e) && !UpgradeState.isStage2(e)) return;
        // Leave OUR OWN hand changes alone. This mod swaps the main hand constantly and on purpose:
        // StageThreeGuardGoal cycles mace / crossbow / slasher through a fight, WallBuildGoal holds stone tools
        // and clears the slot, MinerGoal carries the emerald it dug. Reacting to those would fight the goals and
        // break guard combat and wall building outright, so only a FOREIGN item counts as the weapon being taken.
        if (isSanctionedHandItem(event.getTo())) return;

        if (e instanceof PathfinderMob pm && isGuard(pm)) {
            // A guard's weapon is the mace (stage 3+) or the virus crossbow (stage 2), not the villager blade.
            ItemStack want = UpgradeState.isUpgraded(pm) ? GuardWeapons.mace() : GuardWeapons.crossbow();
            pm.setItemSlot(EquipmentSlot.MAINHAND, want.copy());
            pm.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        } else if (e instanceof Villager villager) {
            restoreDefenderWeapon(villager);
        }
    }

    /**
     * Is this something this mod itself put in a defender's hand, or an empty slot it emptied on purpose?
     *
     * <p>An EMPTY hand is sanctioned: the build and mine goals clear the slot deliberately and restore the
     * weapon in their own stop(). Every blade this mod issues is a netherite AXE and the work goals hold stone
     * tools, so any {@link DiggerItem} is ours; the rest are named outright. Anything else (MCA's EquipmentTask
     * handing out an iron sword, for instance) means the weapon was taken and should be put back.
     */
    private static boolean isSanctionedHandItem(ItemStack stack) {
        if (stack.isEmpty()) return true;
        Item i = stack.getItem();
        if (i instanceof DiggerItem) return true;              // slasher / eradicator / build + mine tools
        if (i == Items.EMERALD) return true;                   // MinerGoal carries the ore it just dug
        // The emerald sword is the ONE tool this mod issues that is not a DiggerItem, so it needs naming or the
        // restore handler would rip it out of a guard's hand mid-swing: CobwebCutGoal and the dig goal both draw
        // it (a sword is what cuts cobweb), and each puts the weapon back itself when it finishes.
        if (i == com.example.villagefightsback.ModItems.EMERALD_SWORD.get()) return true;
        return i == GuardWeapons.mace().getItem()
                || i == GuardWeapons.crossbow().getItem()
                || i == GuardWeapons.alienSmasher().getItem();
    }

    private static void armVillagerFull(Villager villager) {
        // Stage-3 villagers wield the Alien Slasher (a netherite axe) instead of the revolver. They hold it
        // for real -- the vanilla CrossedArmsItemLayer renders the held item -- and melee with it; the axe's
        // on-hit payload (Emerald Virus, Slowness IV, fire, huge knockback) is applied in EmeraldVirusEvents.
        // No guns, flamethrower, flare or molotov: the slasher is the villager's only stage-3 weapon.
        // Stage-4 villagers (and Emeraldgers, the stage-4 virus spawns) wield the Alien Eradicator (Virus II);
        // stage-3 wields the plain slasher (Virus I).
        restoreDefenderWeapon(villager);
        // Iron boots on upgraded villagers (functional armour; villagers have no armour render layer).
        if (!(villager.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof net.minecraft.world.item.ArmorItem)) {
            villager.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            villager.setDropChance(EquipmentSlot.FEET, 0.0F);
        }
        applyLeatherArmor(villager); // protection of a full leather set (idempotent; survives reload)
        if (hasGoal(villager, AxeCombatGoal.class)) return; // goals are re-added each load
        CombatUtil.extendFollowRange(villager, FOLLOW_RANGE);
        // Priority 0 (above combat): drop TNT and flee when standing on From Another World infection.
        villager.goalSelector.addGoal(0, new TntFleeGoal(villager));
        villager.goalSelector.addGoal(1, new AxeCombatGoal(villager)); // hunt and slash hostiles with the axe
        villager.goalSelector.addGoal(3, new WallBuildGoal(villager)); // build the brain node's wall when idle
        enableDoorOpening(villager); // so running builders/miners can pass through normal wooden doors
    }

    /**
     * Let a village villager ROUTE THROUGH and physically OPEN normal wooden doors while it runs its errands
     * (build / mine / settle). Our goals drive the navigation directly, so we (1) enable door routing on the
     * ground navigation -- the default villager navigation can't path through a CLOSED wooden door -- and
     * (2) add a vanilla {@link net.minecraft.world.entity.ai.goal.OpenDoorGoal} (auto-closes behind) at a high
     * priority so it can briefly take over movement to open a door the path runs through. Idempotent.
     */
    public static void enableDoorOpening(Villager villager) {
        if (villager.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation nav) {
            nav.setCanPassDoors(true);
            nav.setCanOpenDoors(true);
        }
        if (!hasGoal(villager, net.minecraft.world.entity.ai.goal.OpenDoorGoal.class)) {
            villager.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(villager, true));
        }
    }

    // Fixed UUID so re-applying the armour buff on world reload is idempotent.
    private static final java.util.UUID VILLAGER_ARMOR_UUID =
            java.util.UUID.fromString("c0ffee00-0000-4000-8000-000000000002");
    private static final double LEATHER_ARMOR = 7.0; // full leather set: helmet 1 + chest 3 + legs 2 + boots 1

    /**
     * Grant an upgraded villager the protection of a full leather set (+7 armour) as a permanent attribute
     * modifier. (Leather's armour TOUGHNESS is 0, so the meaningful "leather equivalent" is its armour value.)
     * Idempotent: the fixed UUID means a reload won't stack a second copy.
     */
    private static void applyLeatherArmor(Villager villager) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance inst =
                villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (inst == null) return;
        net.minecraft.world.entity.ai.attributes.AttributeModifier mod =
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(VILLAGER_ARMOR_UUID,
                        "alien slasher leather armour", LEATHER_ARMOR,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        if (!inst.hasModifier(mod)) inst.addPermanentModifier(mod);
    }

    /** Add the wall-build goal to a villager if it lacks it (idempotent) -- retrofits already-loaded villagers. */
    public static void ensureWallBuildGoal(Villager villager) {
        if (!hasGoal(villager, WallBuildGoal.class)) {
            villager.goalSelector.addGoal(3, new WallBuildGoal(villager));
        }
        enableDoorOpening(villager); // a running builder must be able to open normal wooden doors
    }

    /**
     * STAGE 3: guards drop JEG entirely and become emerald knights -- an enchanted mace + crossbow + emerald
     * armour (Fire/Blast Protection), driven by {@link com.example.villagefightsback.goal.StageThreeGuardGoal}.
     */
    private static void armGuardFull(PathfinderMob guard) {
        if (hasGoal(guard, com.example.villagefightsback.goal.StageThreeGuardGoal.class)) return; // re-added each load
        CombatUtil.extendFollowRange(guard, FOLLOW_RANGE);
        // The mace replaces any JEG gun (the equipment change makes onEquipmentChange strip JEG's gun goal);
        // the combat goal swaps in the crossbow / Alien Slasher as the situation demands.
        guard.setItemSlot(EquipmentSlot.MAINHAND, GuardWeapons.mace().copy());
        guard.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        GuardWeapons.equipEmeraldArmor(guard);
        guard.goalSelector.addGoal(1, new com.example.villagefightsback.goal.StageThreeGuardGoal(guard));
    }

    /**
     * STAGE 2: a "virus archer" guard -- the same emerald-virus crossbow + emerald armour as the full knight and
     * the same {@link com.example.villagefightsback.goal.StageThreeGuardGoal} combat brain, but NO mace (the goal's
     * mace-bounce is gated to stage 3+, so a stage-2 guard fights with crossbow + Alien Slasher only).
     */
    private static void armGuardStage2(PathfinderMob guard) {
        if (hasGoal(guard, com.example.villagefightsback.goal.StageThreeGuardGoal.class)) return; // re-added each load
        CombatUtil.extendFollowRange(guard, FOLLOW_RANGE);
        guard.setItemSlot(EquipmentSlot.MAINHAND, GuardWeapons.crossbow().copy()); // idle look: an archer (no mace)
        guard.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        GuardWeapons.equipEmeraldArmor(guard);
        guard.goalSelector.addGoal(1, new com.example.villagefightsback.goal.StageThreeGuardGoal(guard));
    }

    static void armGolemFull(IronGolem golem) {
        // Equip our NATIVE rocket launcher so the render layer can show it (and only on upgraded golems).
        // It is a plain item (not a JEG GunItem), so JEG never hijacks the golem; GolemRocketGoal fires our
        // own RocketEntity. The held item also gates the decorative HeldWeaponLayer (it shows only this item).
        if (golem.getMainHandItem().getItem() != ModItems.ROCKET_LAUNCHER.get()) {
            golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.ROCKET_LAUNCHER.get()));
            golem.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        applyGolemArmor(golem);  // gigachad: protection of a full iron set (idempotent; survives reload)
        applyGolemHealth(golem); // gigachad: 200 HP (base 100 + 100)
        // No artificial name tag: a gigachad/vindictive gigachad is its own creature TYPE, so its name comes
        // from the entity type (lang) and shows on hover/death like any mob -- no floating custom name.
        if (hasGoal(golem, GolemRocketGoal.class)) return;
        CombatUtil.extendFollowRange(golem, FOLLOW_RANGE);
        golem.goalSelector.addGoal(0, new GolemRocketGoal(golem));
    }

    // Fixed UUID so re-applying the gigachad armour buff on world reload is idempotent.
    private static final java.util.UUID GOLEM_ARMOR_UUID =
            java.util.UUID.fromString("c0ffee00-0000-4000-8000-000000000003");
    private static final double IRON_ARMOR = 15.0; // full iron set: helmet 2 + chest 6 + legs 5 + boots 2

    /** Give a stage-3 ("gigachad") iron golem the armour of a full iron set (+15 armour) as a permanent
     *  attribute modifier. Idempotent via the fixed UUID, so a reload never stacks a second copy. */
    private static void applyGolemArmor(IronGolem golem) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance inst =
                golem.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (inst == null) return;
        net.minecraft.world.entity.ai.attributes.AttributeModifier mod =
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(GOLEM_ARMOR_UUID,
                        "gigachad iron armour", IRON_ARMOR,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        if (!inst.hasModifier(mod)) inst.addPermanentModifier(mod);
    }

    private static final java.util.UUID GOLEM_HEALTH_UUID =
            java.util.UUID.fromString("c0ffee00-0000-4000-8000-000000000004");
    private static final double GOLEM_HEALTH_BONUS = 100.0; // base iron golem 100 -> gigachad 200

    /** Give a stage-3 ("gigachad") iron golem +100 max health (-> 200 total), as a permanent, idempotent
     *  attribute modifier. Heals to full only the first time it's applied (so reloads don't re-heal). */
    private static void applyGolemHealth(IronGolem golem) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance inst =
                golem.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (inst == null) return;
        net.minecraft.world.entity.ai.attributes.AttributeModifier mod =
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(GOLEM_HEALTH_UUID,
                        "gigachad health", GOLEM_HEALTH_BONUS,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        if (!inst.hasModifier(mod)) {
            inst.addPermanentModifier(mod);
            golem.setHealth(golem.getMaxHealth()); // top up to the new 200 on first upgrade
        }
    }

    /** Remove every goal of {@code goalClass} from the mob's goal selector (used when upgrading). */
    private static void removeGoal(Mob mob, Class<?> goalClass) {
        List<Goal> remove = new ArrayList<>();
        for (WrappedGoal w : mob.goalSelector.getAvailableGoals()) {
            if (goalClass.isInstance(w.getGoal())) remove.add(w.getGoal());
        }
        for (Goal g : remove) mob.goalSelector.removeGoal(g);
    }

    /** Guards have a chance to drop our native Fire Grenade when they die. */
    @SubscribeEvent
    public static void onGuardDeathDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || !isGuard(dead)) return;
        if (dead.getRandom().nextFloat() >= GUARD_FIRE_GRENADE_DROP_CHANCE) return;
        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(dead.level(),
                dead.getX(), dead.getY(), dead.getZ(), new ItemStack(ModItems.FIRE_GRENADE.get())));
    }

    // --- The Weaponsmith sells the mace + wind charges + the three mace enchanted books -----------

    /** Master-level weaponsmiths sell a plain mace; the wind charges and the three mace enchanted books are
     *  spread across journeyman/expert/master. NONE of these are craftable -- they are only sold or dropped. */
    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.WEAPONSMITH) return;
        var trades = event.getTrades();
        // Journeyman (3): the Density book.
        trades.get(3).add(bookTrade(ModEnchantments.DENSITY.get(), 5, 22, 2, 10));
        // Expert (4): wind charges in stacks, and the Breach book.
        trades.get(4).add(itemTrade(new ItemStack(ModItems.WIND_CHARGE.get(), 4), 8, 6, 12));
        trades.get(4).add(bookTrade(ModEnchantments.BREACH.get(), 4, 30, 2, 15));
        // Master (5): a plain mace, and the Wind Burst book.
        trades.get(5).add(itemTrade(new ItemStack(ModItems.MACE.get()), 48, 2, 30));
        trades.get(5).add(bookTrade(ModEnchantments.WIND_BURST.get(), 3, 38, 2, 15));
    }

    /** A simple "N emeralds -> {@code result}" trade (no crafting recipe involved). */
    private static VillagerTrades.ItemListing itemTrade(ItemStack result, int emeralds, int maxUses, int xp) {
        return (trader, rand) -> new MerchantOffer(
                new ItemStack(Items.EMERALD, emeralds), result.copy(), maxUses, xp, 0.05F);
    }

    /** A "N emeralds -> enchanted book(enchant @ level)" trade. */
    private static VillagerTrades.ItemListing bookTrade(
            net.minecraft.world.item.enchantment.Enchantment ench, int level, int emeralds, int maxUses, int xp) {
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ench, level));
        return (trader, rand) -> new MerchantOffer(
                new ItemStack(Items.EMERALD, emeralds), book.copy(), maxUses, xp, 0.05F);
    }

    // --- Drops: the general 5% used-weapon rule + guard wind charges -----------------------------

    /** Chance a dead villager / guard / vindictive guard drops the actual weapon it was holding. */
    private static final float USED_WEAPON_DROP_CHANCE = 0.05F;
    /** Range of wind charges a dead guard / vindictive guard drops (1 + nextInt(5) = 1..5). */
    private static final int GUARD_WIND_CHARGE_MAX = 5;

    /**
     * GENERAL 5% USED-WEAPON RULE: a dead Villager / Guard / Vindictive Guard has a 5% chance to drop a copy of
     * the weapon it was actually holding (preserving enchants + durability). Plus: a dead guard / vindictive
     * guard always drops 1-5 wind charges. Kept separate from {@link #onGuardDeathDrops} (fire grenade) and
     * {@link #onGigachadDrops} (rockets) so the rules don't double up.
     */
    @SubscribeEvent
    public static void onUsedWeaponDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) return;
        boolean villager = dead instanceof Villager;
        boolean guard = isGuard(dead) || dead instanceof VindictiveGuardEntity;
        if (!villager && !guard) return;

        // 5% chance: drop the real held weapon.
        ItemStack held = dead.getMainHandItem();
        if (!held.isEmpty() && isWeapon(held) && dead.getRandom().nextFloat() < USED_WEAPON_DROP_CHANCE) {
            event.getDrops().add(new ItemEntity(dead.level(),
                    dead.getX(), dead.getY(), dead.getZ(), held.copy()));
        }

        // Guards / vindictive guards also drop 1-5 wind charges.
        if (guard) {
            int charges = 1 + dead.getRandom().nextInt(GUARD_WIND_CHARGE_MAX);
            event.getDrops().add(new ItemEntity(dead.level(),
                    dead.getX(), dead.getY(), dead.getZ(), new ItemStack(ModItems.WIND_CHARGE.get(), charges)));
        }
    }

    /** True if {@code stack} is a weapon worth dropping (an axe/sword/mace/slasher/crossbow, i.e. not food or a block). */
    private static boolean isWeapon(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof AxeItem || item instanceof SwordItem || item instanceof CrossbowItem) return true;
        if (item instanceof MaceItem || item instanceof AlienSlasherItem || item instanceof AlienEradicatorItem) return true;
        // generic fallback: anything that isn't food or a placeable block counts as a weapon.
        return !(item instanceof BlockItem) && !stack.isEdible();
    }

    /** Gigachad golems + vindictive gigachads drop their rocket launcher + a few rockets when they die. */
    @SubscribeEvent
    public static void onGigachadDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || !(dead instanceof GigachadGolemEntity)) return; // covers the vindictive subclass
        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(dead.level(),
                dead.getX(), dead.getY(), dead.getZ(), new ItemStack(ModItems.ROCKET_LAUNCHER.get())));
        int rockets = dead.getRandom().nextInt(3); // 0 to 2 rockets
        if (rockets > 0) {
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(dead.level(),
                    dead.getX(), dead.getY(), dead.getZ(), new ItemStack(ModItems.ROCKET.get(), rockets)));
        }
    }

    /**
     * Hard ally immunity: villagers, iron golems, and guards must NEVER target one another. Their
     * native AI (e.g. HurtByTargetGoal after rocket splash) tries to; we cancel any such target change.
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity self = event.getEntity();
        LivingEntity newTarget = event.getNewTarget();
        if (isAlly(self) && newTarget != null && isAlly(newTarget)
                && Factions.isInfected(self) == Factions.isInfected(newTarget)) {
            event.setCanceled(true);            // SAME-side allies never fight each other (a vindictive
            return;                             // gigachad is an IronGolem but a boss-faction ENEMY of the village)
        }
        // The village never targets what it spares: General Darius (its champion) or the Vindictive Village
        // Spirit (friendly toward villagers). Catches vanilla golem/guard target acquisition too. The spirit's
        // two vindictive minions are NOT spared, so the village still hunts those.
        if (newTarget != null && isAlly(self) && !Factions.isInfected(self)
                && Factions.isSparedByVillage(newTarget)) {
            event.setCanceled(true);
            return;
        }
        // Villagers/guards must not leave the walled zone to chase a mob: if WE stand inside our village wall
        // but the would-be target is on the far side of it, refuse the target (we engage it only once it
        // breaks IN). Players are exempt -- anti-grief retaliation can still chase the griefer.
        if (newTarget != null && (self instanceof Villager || isGuard(self)) && beyondHomeWall(self, newTarget)) {
            event.setCanceled(true);
        }
    }

    /**
     * True if {@code self} stands inside its village wall but {@code target} is OUTSIDE that wall -- i.e.
     * engaging it would mean leaving the protected zone. Players are never "beyond the wall" (so anti-grief
     * still works). Used by the combat goals and {@link #onChangeTarget} to keep defenders home.
     */
    public static boolean beyondHomeWall(LivingEntity self, LivingEntity target) {
        if (target == null || target instanceof Player) return false;
        if (!(self.level() instanceof ServerLevel sl)) return false;
        WallPlan wall = WallBuild.enclosingPlan(sl, self.blockPosition());
        return wall != null && !wall.contains(target.blockPosition());
    }

    static double GUARD_KILL_EMERALD_RANGE = 100.0; // brain influence radius (set from Config.apply(); base 100)

    /** Every hostile mob a guard kills feeds one emerald to the nearest in-range Village Brain Node. */
    @SubscribeEvent
    public static void onMobKilledByGuard(LivingDeathEvent event) {
        if (!Factions.isVillageThreat(event.getEntity())) return; // hostile / boss-minion / MCA-raider kills count
        if (!(event.getEntity().level() instanceof ServerLevel sl)) return;
        Entity killer = event.getSource().getEntity();
        if (killer == null || !isGuard(killer)) return;                    // only kills credited to a guard
        BrainNodeData data = BrainNodeData.get(sl.getServer());
        BlockPos at = event.getEntity().blockPosition();
        GlobalPos best = null;
        double bestD = GUARD_KILL_EMERALD_RANGE * GUARD_KILL_EMERALD_RANGE;
        for (GlobalPos gp : data.blocks()) {
            if (!gp.dimension().equals(sl.dimension())) continue;
            double d = gp.pos().distSqr(at);
            if (d <= bestD) { bestD = d; best = gp; }
        }
        if (best != null) data.econ(best).addEmeralds(1); // addEmeralds() self-dirties the saved data
    }

    /**
     * A wall builder takes no FALL damage, so it stops crying out every time it steps off its own scaffolding.
     *
     * <p>Builders work at height on scaffolding they place themselves, so short drops are constant and normal
     * for the job. Under MCA Reborn each one plays a human hurt sound, and a work site turns into a chorus of
     * villagers yelping. Cancelling at {@link LivingAttackEvent} is what makes that stop: it aborts
     * {@code hurt()} before it runs, and the hurt sound is played inside {@code hurt()}, so there is no way to
     * silence the shout while still applying the damage. Only fall damage and only while actually building --
     * a builder is still perfectly killable by anything else, and reverts to a normal villager when it finishes.
     */
    @SubscribeEvent
    public static void onBuilderFall(LivingAttackEvent event) {
        if (!event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) return;
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!BuilderRole.isBuilder(villager)) return;
        event.setCanceled(true);
    }

    /** Immunity effect: the holder is immune to FIRE damage only (in-fire / on-fire / lava / magma /
     *  fireballs). It does NOT block ordinary combat damage -- its protection is fire immunity plus the
     *  harmful-status-effect block below. */
    @SubscribeEvent
    public static void onImmunityAttack(LivingAttackEvent event) {
        if (event.getEntity().hasEffect(ModEffects.IMMUNITY.get())
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
        }
    }

    /** Immunity also blocks every HARMFUL status effect (poison, wither, slowness, the emerald virus, ...)
     *  from ever landing on the holder. Beneficial effects (incl. Immunity itself) are unaffected. */
    @SubscribeEvent
    public static void onImmunityEffect(com.example.villagefightsback.platform.event.MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect().getCategory()
                    == net.minecraft.world.effect.MobEffectCategory.HARMFUL
                && event.getEntity().hasEffect(ModEffects.IMMUNITY.get())) {
            event.setResult(com.example.villagefightsback.platform.Event.Result.DENY);
        }
    }

    /** Player hits a village defender -> the defender retaliates AND the player is branded an Enemy of the
     *  Village (status + nearby rally + warning), same as mining a node. */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        // No friendly fire among SAME-side defenders (e.g. rocket splash hitting an allied golem/villager).
        // A vindictive gigachad is an IronGolem (so isAlly), but it's a boss-faction enemy -- the faction
        // check lets the village and the vindictive actually damage each other.
        if (isAlly(event.getEntity()) && event.getSource().getEntity() != null
                && isAlly(event.getSource().getEntity())
                && Factions.isInfected(event.getEntity()) == Factions.isInfected(event.getSource().getEntity())) {
            event.setCanceled(true);
            return;
        }

        // The Vindictive Village Spirit is friendly toward the village: village allies (villagers, golems,
        // guards, wall defenders, Darius) can never harm it -- only the player's own attacks do. Its two
        // vindictive minions (guards + gigachads) are NOT protected; the village still fights those.
        if (event.getEntity() instanceof UnleashedBrainNodeEntity && event.getSource().getEntity() != null
                && isAlly(event.getSource().getEntity()) && !Factions.isInfected(event.getSource().getEntity())) {
            event.setCanceled(true);
            return;
        }

        // A player hurting a real (non-infected) village defender -- villager / guard / iron golem / wall
        // defender -- loses reputation for it. (Boss-faction golems like the vindictive gigachad are isAlly
        // but infected, so hitting THEM costs nothing: they are not the village.)
        //
        // NO IMMEDIATE RETALIATION. A grudge used to be stamped on the struck villager on the spot, which
        // meant one punch turned a village hostile for a hundred ticks whatever your history with it. The
        // village answers a REPUTATION now, so one blow is a price (-20) and not a verdict; land enough of
        // them to reach -100 and the whole village turns on you, which is the intended way to earn that.
        DamageSource src = event.getSource();
        if (src.getEntity() instanceof ServerPlayer player
                && isAlly(event.getEntity()) && !Factions.isInfected(event.getEntity())
                && event.getEntity().level() instanceof ServerLevel sl) {
            ReputationData.get(sl.getServer()).add(player, -20, "hit " + allyName(event.getEntity()));
        }
    }

    /** A short word for what was struck, for the reputation log. Kept tiny: the log panel is 252px wide. */
    private static String allyName(net.minecraft.world.entity.Entity e) {
        if (e instanceof IronGolem) return "golem";
        if (isGuard(e)) return "guard";
        if (e instanceof WallDefenderEntity) return "defender";
        if (e instanceof GeneralDariusEntity) return "Darius";
        return "villager";
    }

    /**
     * The current valid attack target for a mob: its recent retaliation target, or -- failing that -- the
     * nearest "Enemy of the Village" player within {@link #NODE_DEFEND_RADIUS}. Null if none.
     *
     * <p>Still reads the STATUS rather than the reputation number, deliberately. Four combat goals ask this
     * for every village unit several times a second, and the status is exactly a cached copy of "is this
     * reputation at or below -100" that {@link ReputationData} keeps in step. Reaching for the saved data
     * here would put a map lookup and a data-storage fetch on the hottest path in the mod.
     */
    public static Player getAggroPlayer(Mob mob) {
        CompoundTag data = PersistentData.of(mob);
        if (data.hasUUID(NBT_AGGRO_UUID) && mob.level().getGameTime() <= data.getLong(NBT_AGGRO_UNTIL)) {
            Player p = mob.level().getPlayerByUUID(data.getUUID(NBT_AGGRO_UUID));
            // The record outlives the standing that justified it -- a raider keeps hunting after the scent
            // lapses -- but an ordinary defender must not, or one old grudge would survive a full pardon.
            if (p != null && p.isAlive()
                    && (p.hasEffect(ModEffects.ENEMY_OF_THE_VILLAGE.get())
                        || NightmareRaid.isHunting(mob))) return p;
        }
        // Any nearby player carrying the Enemy of the Village status is hunted on sight.
        if (mob.level() instanceof ServerLevel sl) {
            Player best = null;
            double bestSq = NODE_DEFEND_RADIUS * NODE_DEFEND_RADIUS;
            for (Player p : sl.players()) {
                if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
                if (!p.hasEffect(ModEffects.ENEMY_OF_THE_VILLAGE.get())) continue;
                double dsq = p.distanceToSqr(mob);
                if (dsq < bestSq) { bestSq = dsq; best = p; }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** Allies within this radius of an attack rush the attacker. */
    public static final double NODE_DEFEND_RADIUS = 30.0;
    private static final int NODE_AGGRO_TICKS = 200;          // ...for ~10s per trigger

    /**
     * A player has just fallen to Enemy of the Village: rally the defenders around them and say so, once.
     *
     * <p>Called by {@link ReputationData} at the moment reputation crosses -100, and only then. It used to be
     * called on every offence, which is why a single punch could turn a village hostile -- the offence and
     * the verdict were the same event. They are separate now: an offence moves a number, and only the number
     * crossing the line brings the village down on you.
     *
     * <p>The rally is not what makes them hostile. {@link #getAggroPlayer} already hands any Enemy within
     * {@link #NODE_DEFEND_RADIUS} to every combat goal that asks. This writes the aggro record so defenders
     * will follow you PAST that radius rather than losing interest at its edge.
     */
    public static void onBecameEnemy(ServerLevel level, Player player) {
        if (player.isSpectator()) return;
        aggroDefendersAgainst(level, player.blockPosition(), NODE_DEFEND_RADIUS, player, NODE_AGGRO_TICKS);
        level.getServer().getPlayerList().broadcastSystemMessage(
                net.minecraft.network.chat.Component.literal(
                        "§c" + player.getName().getString() + " is an enemy of the village!"), false);
    }

    /**
     * Make every defender (villager / guard / iron golem) within {@code radius} of {@code center} turn on
     * {@code attacker} for {@code ticks}. Villagers/guards use the aggro NBT (their combat goal then shoots
     * the player); golems get the target set directly (their melee goal punches it). Used when the brain node
     * is mined. One bounded entity query -- only runs on the (throttled) attack, never per tick.
     */
    public static void aggroDefendersAgainst(ServerLevel level, BlockPos center, double radius,
                                             Player attacker, int ticks) {
        long until = level.getGameTime() + ticks;
        AABB box = new AABB(center).inflate(radius);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, e -> e.isAlive() && isAlly(e))) {
            if (m instanceof IronGolem) {
                m.setTarget(attacker); // golems melee the attacker (the rocket goal ignores non-Enemy targets)
            } else { // villager or guard
                CompoundTag data = PersistentData.of(m);
                data.putUUID(NBT_AGGRO_UUID, attacker.getUUID());
                data.putLong(NBT_AGGRO_UNTIL, until);
            }
        }
    }

    // --- The village treats a player as hostile iff they hold Enemy of the Village ---
    //
    // The ADDED hook that used to live here is gone. It rallied the village the instant the status landed,
    // which was right when the status was stamped on by a single offence, and wrong now: the status is
    // derived from a reputation number and is re-asserted a few times a minute, so rallying on every
    // application would re-rally the whole village every refresh. ReputationData calls onBecameEnemy once,
    // at the crossing. The REMOVE and EXPIRE hooks below stay, and matter more than they used to: climbing
    // back above -100 is now a real thing that happens, and the village has to let go when it does.

    /** When Enemy of the Village is removed early (e.g. it was cleared), stop the hunt immediately. */
    @SubscribeEvent
    public static void onEnemyEffectRemoved(com.example.villagefightsback.platform.event.MobEffectEvent.Remove event) {
        if (event.getEffect() != ModEffects.ENEMY_OF_THE_VILLAGE.get()) return;
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && player.level() instanceof ServerLevel sl) {
            clearEnemyTargets(sl, player);
        }
    }

    /** When Enemy of the Village expires on its own, stop the hunt immediately (no polling). */
    @SubscribeEvent
    public static void onEnemyEffectExpired(com.example.villagefightsback.platform.event.MobEffectEvent.Expired event) {
        if (event.getEffectInstance() == null
                || event.getEffectInstance().getEffect() != ModEffects.ENEMY_OF_THE_VILLAGE.get()) return;
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && player.level() instanceof ServerLevel sl) {
            clearEnemyTargets(sl, player);
        }
    }

    /**
     * Stop every nearby village defender (villager / guard / iron golem / wall defender) from hunting
     * {@code player} the moment Enemy of the Village ends: clear it as their set target, and wipe the aggro NBT
     * that {@link #getAggroPlayer} reads. This disengages even guards/golems that kept a target set, with no
     * per-tick scan -- it runs once, on the remove/expire event.
     */
    private static void clearEnemyTargets(ServerLevel level, Player player) {
        AABB box = player.getBoundingBox().inflate(NODE_DEFEND_RADIUS + FOLLOW_RANGE);
        for (Mob m : level.getEntitiesOfClass(Mob.class, box, e -> e.isAlive() && isAlly(e))) {
            if (m.getTarget() == player) m.setTarget(null);
            CompoundTag data = PersistentData.of(m);
            if (data.hasUUID(NBT_AGGRO_UUID) && player.getUUID().equals(data.getUUID(NBT_AGGRO_UUID))) {
                data.remove(NBT_AGGRO_UUID);
                data.remove(NBT_AGGRO_UNTIL);
            }
        }
    }

    /** A village GUARD: a Guard Villagers guard entity, or an MCA Reborn villager whose profession is a
     *  soldier's one (mca:guard / mca:archer). Both get the guard kit rather than the civilian villager kit. */
    private static boolean isGuard(Entity e) {
        return com.example.villagefightsback.Factions.isGuard(e);
    }

    /** Defenders that must never fight each other: villagers, iron golems, guards, wall defenders, and the
     *  village's summoned champion General Darius. (Darius is an Enemy/Monster, so he must be listed explicitly
     *  to be covered by the friendly-fire and change-target guards below.) */
    /**
     * May a village unit go on attacking {@code target}?
     *
     * <p>For anything but a player, yes: the village's quarrel with a zombie does not depend on paperwork.
     * For a PLAYER it asks the standing, every time, because standing changes mid-fight -- feed the brain
     * node enough emeralds while a villager is swinging at you and the village has, as of that moment, no
     * grievance left to swing about.
     *
     * <p>This exists because gating the moment a target is SET is not enough. {@code StandingEffects} refuses
     * to let a village unit take a non-Enemy player as its target, but a goal that latched on while you were
     * an Enemy keeps its own copy and never asks again -- so the village went on hunting a pardoned player
     * until they walked out of range. Every retention check in the mod's combat goals asks this now.
     */
    public static boolean mayStillAttack(LivingEntity target) {
        if (!(target instanceof Player p)) return true;
        return p.hasEffect(ModEffects.ENEMY_OF_THE_VILLAGE.get());
    }

    /** A real member of the village: villager, guard, iron golem, wall defender or Darius.
     *  Public because the reputation rules price a blow against any of them the same way. */
    public static boolean isVillageAlly(Entity e) {
        return isAlly(e);
    }

    private static boolean isAlly(Entity e) {
        return e instanceof Villager || e instanceof IronGolem || e instanceof WallDefenderEntity
                || e instanceof GeneralDariusEntity || isGuard(e);
    }

    private static boolean hasGoal(Mob mob, Class<?> goalClass) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (goalClass.isInstance(wrapped.getGoal())) return true;
        }
        return false;
    }
}
