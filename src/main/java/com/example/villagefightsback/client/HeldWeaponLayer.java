package com.example.villagefightsback.client;

import com.example.villagefightsback.TheVillageFightsBack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Purely-decorative layer that draws a JEG gun on a villager / iron golem. The mob does NOT actually
 * hold the item (equipping a GunItem makes JEG hijack the mob's AI), so we render a fixed gun here.
 *
 * Two modes via {@code bone}:
 *  - bone == null: apply a fixed transform in the entity's body space (used for villagers, mirroring
 *    vanilla CrossedArmsItemLayer so the gun sits in the folded arms).
 *  - bone != null: anchor to that model bone (e.g. "right_arm" for the golem) before offsetting.
 */
public class HeldWeaponLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    /**
     * When the decorative gun is shown -- the only entity state the client can read without custom syncing
     * is the equipped item, so we use it as the upgrade proxy: EMPTY_HAND = an upgraded villager (whose
     * hand is empty; a nerfed villager holds a stone axe), HELD_GUN = an upgraded golem (which holds the
     * rocket; a nerfed golem's hand is empty).
     */
    public enum Gate { EMPTY_HAND, HELD_GUN }

    private final ResourceLocation gunId;
    private final String bone;
    private final float dx, dy, dz, rx, ry, rz, scale;
    private final ItemDisplayContext context;
    private final Gate gate;
    private ItemStack stack = ItemStack.EMPTY;
    private boolean disabled = false;

    public HeldWeaponLayer(RenderLayerParent<T, M> parent, ResourceLocation gunId, String bone,
                        float dx, float dy, float dz, float rx, float ry, float rz, float scale,
                        ItemDisplayContext context, Gate gate) {
        super(parent);
        this.gunId = gunId;
        this.bone = bone;
        this.dx = dx; this.dy = dy; this.dz = dz;
        this.rx = rx; this.ry = ry; this.rz = rz;
        this.scale = scale;
        this.context = context;
        this.gate = gate;
    }

    private boolean shouldShow(T entity) {
        // NOTE: deliberately if/else, not a switch-over-enum. A switch on an enum makes javac emit a
        // synthetic HeldWeaponLayer$1 switch-map class which Forge's module classloader fails to load here
        // (NoClassDefFoundError at render time). Plain enum comparison generates no extra class.
        if (gate == Gate.EMPTY_HAND) {
            return entity.getMainHandItem().isEmpty();
        }
        // Gate.HELD_GUN: show only when the holder is actually holding the very item we render (the golem's
        // native rocket launcher), so we no longer reference JEG's GunItem here.
        // getOptional, not get: ITEM is a DefaultedRegistry that answers minecraft:air for an unknown id, and
        // an EMPTY main hand also reads as air -- so a missing gun id would make this true for every empty hand.
        return BuiltInRegistries.ITEM.getOptional(gunId)
                .map(gun -> entity.getMainHandItem().getItem() == gun)
                .orElse(false);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (disabled) return;
        if (entity instanceof IronGolem golem && golem.isPlayerCreated()) return; // armed golems only
        if (entity.isInvisible()) return;
        if (!shouldShow(entity)) return; // only show the gun on UPGRADED defenders (see Gate)

        if (stack.isEmpty()) {
            // getOptional, not get: ITEM is a DefaultedRegistry, so get() answers an unknown id with AIR rather
            // than null. The null-check below would never fire and we would render an empty stack every frame.
            Item item = BuiltInRegistries.ITEM.getOptional(gunId).orElse(null);
            if (item == null) return;
            stack = new ItemStack(item);
        }

        pose.pushPose();
        try {
            if (bone != null && getParentModel() instanceof HierarchicalModel<?> hm) {
                ModelPart part = hm.root().getChild(bone);
                part.translateAndRotate(pose);
            }
            pose.translate(dx, dy, dz);
            pose.mulPose(Axis.XP.rotationDegrees(rx));
            pose.mulPose(Axis.YP.rotationDegrees(ry));
            pose.mulPose(Axis.ZP.rotationDegrees(rz));
            pose.scale(scale, scale, scale);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack, context, packedLight, OverlayTexture.NO_OVERLAY, pose, buffer,
                    entity.level(), entity.getId());
        } catch (Throwable t) {
            // If JEG's gun item renderer can't handle a non-player holder, give up rather than
            // crash the client every frame.
            disabled = true;
            TheVillageFightsBack.LOGGER.warn("[The Village Fights Back] gun render layer disabled: {}", t.toString());
        } finally {
            pose.popPose();
        }
    }
}
