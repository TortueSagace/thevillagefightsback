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

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Arms villagers, iron golems, and Guard Villagers guards, and wires the combat behaviour to them. As the
 * village evolves, defenders are upgraded (stage-3 villagers get the Alien Slasher, guards become emerald
 * knights, golems become gigachad golems) and the mod's ally-immunity keeps them from harming one another.
 */
@Mod.EventBusSubscriber(modid = TheVillageFightsBack.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");

    // Guards have a chance to drop our native Fire Grenade on death.
    private static final float GUARD_FIRE_GRENADE_DROP_CHANCE = 0.5F;

    /** Arm villagers / iron golems / guards whenever they join a level (including world reload). */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof PathfinderMob mob)) return;

        // Let village defenders (and all iron golems) PATH through fake-cobblestone "doors": flip our
        // custom passable type's malus to 0 for them only (others keep the -1 default = blocked). This
        // mirrors FakeCobblestoneBlock.isPhaser, which lets the same set physically walk through.
        if (mob instanceof IronGolem || mob instanceof Villager || isGuard(mob)) {
            mob.setPathfindingMalus(ModPathTypes.ALLY_PASSABLE, 0.0F);
        }

        // Emeraldgers are stage-4 virus spawns: combat-only villagers that can NEVER take a role (miner/
        // settler/builder) or profession. So skip every role goal for them -- they still get the stage-3
        // combat loadout below (they spawn pre-upgraded).
        boolean emeraldger = mob instanceof EmeraldgerEntity;

        // A villager far from every brainNode can found a new home with a Blue TNT (independent of the
        // combat upgrade). Idempotent: goals are re-added on every load; the goal settles at most once.
        if (!emeraldger && mob instanceof Villager settler
                && !hasGoal(settler, com.example.villagefightsback.goal.SettlerGoal.class)) {
            settler.goalSelector.addGoal(6, new com.example.villagefightsback.goal.SettlerGoal(settler));
        }

        // A villager stranded OUTSIDE its wall heads back toward the brain node when it isn't fighting (low
        // priority, so combat preempts it). Villagers never CROSS the wall to chase -- this only brings home
        // one that is already out. Idempotent.
        if (!emeraldger && mob instanceof Villager strayed
                && !hasGoal(strayed, com.example.villagefightsback.goal.ReturnToVillageGoal.class)) {
            strayed.goalSelector.addGoal(5, new com.example.villagefightsback.goal.ReturnToVillageGoal(strayed));
        }

        // A wall builder must not ignore a point-blank threat: every villager carries this (dormant) goal;
        // it only fires when the villager is a builder and a hostile is within ~3 blocks, interrupting the
        // build (priority 2, above WallBuildGoal's 3) until the threat is dead or leaves. Idempotent.
        if (!emeraldger && mob instanceof Villager defender
                && !hasGoal(defender, com.example.villagefightsback.goal.BuilderDefenseGoal.class)) {
            defender.goalSelector.addGoal(2, new com.example.villagefightsback.goal.BuilderDefenseGoal(defender));
        }

        // Every villager carries the (dormant) miner goal; it only runs once the brain node tags it a miner
        // (level-2 infection). Idempotent. (Emeraldgers can't be miners, so they don't need it.)
        if (!emeraldger && mob instanceof Villager v) {
            com.example.villagefightsback.infection.InfectionManager.ensureMinerGoal(v);
        }

        // Defenders start NERFED and permanently upgrade to the full gun loadout once they have been near a
        // level-3 Village Brain Node (UpgradeEvents flips the flag). Until then: villagers fight only with a
        // stone axe (and only when badly hurt), guards and golems are vanilla.
        boolean upgraded = UpgradeState.isUpgraded(mob);
        if (mob instanceof IronGolem golem) {
            if (golem.isPlayerCreated()) return;       // village golems only
            // A gigachad is its own type: re-apply its kit on every load (goals aren't saved to NBT). Plain
            // village golems are converted into GigachadGolemEntity by UpgradeEvents near a lvl-3 brainNode.
            if (golem instanceof GigachadGolemEntity) armGolemFull(golem);
        } else if (mob instanceof Villager villager) {
            if (upgraded) armVillagerFull(villager);
            else armVillagerNerfed(villager);
        } else if (isGuard(mob)) {
            if (upgraded) armGuardFull(mob);            // else: normal guard
        }
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
        if (e instanceof Villager villager) {
            removeGoal(villager, StoneAxeDefenseGoal.class);
            armVillagerFull(villager); // replaces the stone axe with the revolver
        } else if (e instanceof IronGolem golem) {
            if (!golem.isPlayerCreated()) armGolemFull(golem);
        } else if (e instanceof PathfinderMob pm && isGuard(pm)) {
            armGuardFull(pm);
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

    private static final double GUARD_KILL_EMERALD_RANGE = 100.0; // brainNode must be within this of the kill

    /** Every hostile mob a guard kills feeds one emerald to the nearest in-range Village Brain Node. */
    @SubscribeEvent
    public static void onMobKilledByGuard(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Enemy) && !Factions.isInfected(event.getEntity())) return; // hostile / boss-minion kills count
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

    /** Immunity effect: the holder takes NO damage while it is active -- except sources that bypass
     *  invulnerability (the void, /kill), so an immune entity can never get permanently stuck. */
    @SubscribeEvent
    public static void onImmunityAttack(LivingAttackEvent event) {
        if (event.getEntity().hasEffect(ModEffects.IMMUNITY.get())
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    /** Immunity also blocks every HARMFUL status effect (poison, wither, slowness, the emerald virus, ...)
     *  from ever landing on the holder. Beneficial effects (incl. Immunity itself) are unaffected. */
    @SubscribeEvent
    public static void onImmunityEffect(net.minecraftforge.event.entity.living.MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect().getCategory()
                    == net.minecraft.world.effect.MobEffectCategory.HARMFUL
                && event.getEntity().hasEffect(ModEffects.IMMUNITY.get())) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
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

        // A player hurting a real (non-infected) village defender -- villager / guard / iron golem / wall
        // defender -- brands them an Enemy of the Village. (Boss-faction golems like the vindictive gigachad
        // are isAlly but infected, so hitting THEM doesn't make you a village enemy.)
        DamageSource src = event.getSource();
        if (src.getEntity() instanceof Player player
                && isAlly(event.getEntity()) && !Factions.isInfected(event.getEntity())
                && event.getEntity().level() instanceof ServerLevel sl) {
            markVillageEnemy(sl, event.getEntity().blockPosition(), player);
            // Immediate personal retaliation for the hurt villager / wall defender (snappier than the rally throttle).
            if (event.getEntity() instanceof Villager || event.getEntity() instanceof WallDefenderEntity) {
                CompoundTag data = event.getEntity().getPersistentData();
                data.putUUID(NBT_AGGRO_UUID, player.getUUID());
                data.putLong(NBT_AGGRO_UNTIL, sl.getGameTime() + AGGRO_TICKS);
            }
        }
    }

    /** The current valid attack target for a mob: its recent retaliation target, or -- failing that -- the
     *  nearest "Enemy of the Village" player within {@link #NODE_DEFEND_RADIUS}. Null if none. */
    public static Player getAggroPlayer(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (data.hasUUID(NBT_AGGRO_UUID) && mob.level().getGameTime() <= data.getLong(NBT_AGGRO_UNTIL)) {
            Player p = mob.level().getPlayerByUUID(data.getUUID(NBT_AGGRO_UUID));
            if (p != null && p.isAlive()) return p;
        }
        // Any nearby player carrying the Enemy of the Village status is hunted on sight (the status itself
        // expires after 20 min or on death, so no separate bookkeeping is needed here).
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
    public static final int ENEMY_STATUS_TICKS = 24000;      // Enemy of the Village lasts 20 minutes
    public static final int THIEF_STATUS_TICKS = 1200;       // a chest thief is an Enemy of the Village for 1 min

    /** Build an Enemy of the Village instance for {@code ticks}: no particles, HUD icon on, and EMPTY curative
     *  items so milk can never cure it (it still clears on death). Shared by every ETV grant. */
    private static net.minecraft.world.effect.MobEffectInstance enemyInstance(int ticks) {
        net.minecraft.world.effect.MobEffectInstance inst = new net.minecraft.world.effect.MobEffectInstance(
                ModEffects.ENEMY_OF_THE_VILLAGE.get(), ticks, 0, false, false, true);
        inst.setCurativeItems(new ArrayList<>()); // milk cannot cure it
        return inst;
    }

    /**
     * Brand a player a chest THIEF: Enemy of the Village for {@value #THIEF_STATUS_TICKS} ticks (1 min) with a RED
     * broadcast. Distinct from {@link #markVillageEnemy} (20 min) -- a short, sharp punishment for taking from a
     * village chest. Like every ETV grant it removes Hero of the Village, uses empty curative items (milk-proof),
     * and -- via the {@link MobEffectEvent.Added} handler -- rallies the village the instant it lands.
     */
    public static void markThief(ServerLevel level, Player player) {
        if (player.isSpectator()) return; // ETV/HTV apply in any game mode (creative included); spectators can't steal
        player.addEffect(enemyInstance(THIEF_STATUS_TICKS));
        player.removeEffect(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE);
        level.getServer().getPlayerList().broadcastSystemMessage(
                net.minecraft.network.chat.Component.literal(
                        player.getName().getString() + " is stealing from the village!")
                        .withStyle(net.minecraft.ChatFormatting.RED), false);
    }

    /** Rally every defender within {@link #NODE_DEFEND_RADIUS} of {@code player} against them (used by the
     *  event-driven hostility handler the moment Enemy of the Village is added). */
    public static void aggroDefendersAgainst(ServerLevel level, Player player) {
        aggroDefendersAgainst(level, player.blockPosition(), NODE_DEFEND_RADIUS, player, NODE_AGGRO_TICKS);
    }
    // Per-player throttle so a sustained attack doesn't re-broadcast / re-rally every tick.
    private static final java.util.Map<java.util.UUID, Long> lastEnemyTrigger = new java.util.HashMap<>();

    /**
     * A player attacked the village -- by mining a Village Brain Node / Emerald Node, or by harming a
     * villager / guard / iron golem. Brand them an {@link ModEffects#ENEMY_OF_THE_VILLAGE} for 20 min (always
     * refreshed, so re-offending resets the timer; it clears on death), rally every defender within
     * {@link #NODE_DEFEND_RADIUS} of {@code center}, and -- the first time they become an enemy -- warn the
     * server. The rally + broadcast are throttled per player (~2s); the status refresh is not.
     */
    public static void markVillageEnemy(ServerLevel level, BlockPos center, Player player) {
        if (player.isSpectator()) return; // ETV/HTV apply in any game mode (creative included); spectators excluded
        boolean wasEnemy = player.hasEffect(ModEffects.ENEMY_OF_THE_VILLAGE.get());
        // visible=false -> no particles; showIcon=true -> the bad-omen icon shows in the HUD. Empty curative
        // items -> milk cannot cure it (it still clears on death). The MobEffectEvent.Added handler rallies
        // every defender the moment this lands (event-driven hostility, no per-tick scan).
        player.addEffect(enemyInstance(ENEMY_STATUS_TICKS));
        // You can't be a hero of the village and its enemy at once -- the enemy status revokes Hero of the Village.
        player.removeEffect(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE);

        long now = level.getGameTime();
        Long last = lastEnemyTrigger.get(player.getUUID());
        if (last != null && now - last < 40L) return;        // rally + broadcast at most once per ~2s per player
        lastEnemyTrigger.put(player.getUUID(), now);

        aggroDefendersAgainst(level, center, NODE_DEFEND_RADIUS, player, NODE_AGGRO_TICKS);
        if (!wasEnemy) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§c" + player.getName().getString() + " attacks the village!"), false);
        }
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
                CompoundTag data = m.getPersistentData();
                data.putUUID(NBT_AGGRO_UUID, attacker.getUUID());
                data.putLong(NBT_AGGRO_UNTIL, until);
            }
        }
    }

    // --- EVENT-DRIVEN hostility: the village treats a player as hostile iff they hold Enemy of the Village ---

    /**
     * The instant Enemy of the Village lands on a player, rally every nearby defender against them. This is the
     * single hook that turns ETV into hostility -- golems setTarget, villagers/guards get the aggro NBT --
     * no matter HOW the status was applied (mining, hitting a defender, theft, ...). No per-tick verifier needed.
     */
    @SubscribeEvent
    public static void onEnemyEffectAdded(net.minecraftforge.event.entity.living.MobEffectEvent.Added event) {
        if (event.getEffectInstance().getEffect() != ModEffects.ENEMY_OF_THE_VILLAGE.get()) return;
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && player.level() instanceof ServerLevel sl) {
            aggroDefendersAgainst(sl, player);
        }
    }

    /** When Enemy of the Village is removed early (e.g. it was cleared), stop the hunt immediately. */
    @SubscribeEvent
    public static void onEnemyEffectRemoved(net.minecraftforge.event.entity.living.MobEffectEvent.Remove event) {
        if (event.getEffect() != ModEffects.ENEMY_OF_THE_VILLAGE.get()) return;
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && player.level() instanceof ServerLevel sl) {
            clearEnemyTargets(sl, player);
        }
    }

    /** When Enemy of the Village expires on its own, stop the hunt immediately (no polling). */
    @SubscribeEvent
    public static void onEnemyEffectExpired(net.minecraftforge.event.entity.living.MobEffectEvent.Expired event) {
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
            CompoundTag data = m.getPersistentData();
            if (data.hasUUID(NBT_AGGRO_UUID) && player.getUUID().equals(data.getUUID(NBT_AGGRO_UUID))) {
                data.remove(NBT_AGGRO_UUID);
                data.remove(NBT_AGGRO_UNTIL);
            }
        }
    }

    private static boolean isGuard(Entity e) {
        return GUARD_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
    }

    /** Defenders that must never fight each other: villagers, iron golems, guards, and wall defenders. */
    private static boolean isAlly(Entity e) {
        return e instanceof Villager || e instanceof IronGolem || e instanceof WallDefenderEntity || isGuard(e);
    }

    private static boolean hasGoal(Mob mob, Class<?> goalClass) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (goalClass.isInstance(wrapped.getGoal())) return true;
        }
        return false;
    }
}
