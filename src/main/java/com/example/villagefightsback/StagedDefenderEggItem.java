package com.example.villagefightsback;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A creative spawn egg that places a defender ALREADY configured to a given BrainNode stage, with no brainNode
 * present:
 * <ul>
 *   <li>stage 1 -> nerfed villager / vanilla guard / normal iron golem;</li>
 *   <li>stage 3 -> the full upgrade (gun/slasher loadout; "gigachad" rocket golem with iron-set armour);</li>
 *   <li>stage 4 -> the stage-3 loadout whose weapon attacks apply Emerald Virus <b>II</b> instead of I.</li>
 * </ul>
 * The stage flags are stamped on the entity BEFORE it joins the level, so {@link VillageEvents}'s join handler
 * arms it to match. Renders as a two-tone egg via a client item-colour handler (see TheVillageFightsBackClient).
 */
public class StagedDefenderEggItem extends Item {
    public enum Kind { VILLAGER, GUARD, GOLEM }

    private static final ResourceLocation GUARD_ID = new ResourceLocation("guardvillagers", "guard");

    private final Kind kind;
    private final int stage;          // 1, 3 or 4
    private final int primaryColor;   // egg base colour (tint layer 0)
    private final int secondaryColor; // egg spots colour (tint layer 1)

    public StagedDefenderEggItem(Kind kind, int stage, int primaryColor, int secondaryColor, Properties props) {
        super(props);
        this.kind = kind;
        this.stage = stage;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
    }

    /** Tint for the spawn-egg model layer (0 = base, 1 = spots). */
    public int getColor(int layer) {
        return layer == 0 ? primaryColor : secondaryColor;
    }

    private EntityType<?> entityType() {
        return switch (kind) {
            case VILLAGER -> EntityType.VILLAGER;
            case GOLEM -> golemTypeForStage();
            case GUARD -> ForgeRegistries.ENTITY_TYPES.getValue(GUARD_ID); // null if Guard Villagers absent
        };
    }

    /**
     * A golem egg spawns its named EVOLUTION form directly, never a plain iron golem to be upgraded later:
     * Gigachad Golem (stage 4), Defender Golem (stage 2-3), else a normal iron golem (stage 1). Golem
     * evolutions are now distinct entity types, so -- unlike villagers/guards, which stay one type and are
     * armed by a stage flag -- the egg must pick the right type up front, independent of any brain node.
     */
    private EntityType<?> golemTypeForStage() {
        if (stage >= 4) return ModEntities.GIGACHAD_GOLEM.get();
        if (stage >= 2) return ModEntities.DEFENDER_GOLEM.get();
        return EntityType.IRON_GOLEM;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        EntityType<?> type = entityType();
        if (type == null) return InteractionResult.FAIL; // e.g. Guard Villagers not installed

        BlockPos clicked = ctx.getClickedPos();
        BlockPos pos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
                ? clicked : clicked.relative(ctx.getClickedFace());

        Entity entity = type.create(sl);
        if (entity == null) return InteractionResult.FAIL;
        float yaw = Mth.wrapDegrees(sl.getRandom().nextFloat() * 360.0F);
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        if (entity instanceof Mob mob) {
            mob.setYHeadRot(yaw);
            mob.finalizeSpawn(sl, sl.getCurrentDifficultyAt(pos), MobSpawnType.SPAWN_EGG, null, null);
        }
        // Stamp the stage BEFORE the entity joins the level, so VillageEvents#onEntityJoin arms it to match.
        if (stage == 4) UpgradeState.setStage4(entity);
        else if (stage == 3) UpgradeState.setUpgraded(entity);
        sl.addFreshEntity(entity);

        if (ctx.getPlayer() != null && !ctx.getPlayer().isCreative()) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
