package com.example.villagergunners.client;

import com.example.villagergunners.VillagerGunners;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Purely-decorative layer that draws a JEG gun on a villager / iron golem. The mob does NOT actually
 * hold the item (equipping a GunItem makes JEG hijack the mob's AI), so we render a fixed gun here.
 *
 * Two modes via {@code bone}:
 *  - bone == null: apply a fixed transform in the entity's body space (used for villagers, mirroring
 *    vanilla CrossedArmsItemLayer so the gun sits in the folded arms).
 *  - bone != null: anchor to that model bone (e.g. "right_arm" for the golem) before offsetting.
 */
public class HeldGunLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final ResourceLocation gunId;
    private final String bone;
    private final float dx, dy, dz, rx, ry, rz, scale;
    private final ItemDisplayContext context;
    private ItemStack stack = ItemStack.EMPTY;
    private boolean disabled = false;

    public HeldGunLayer(RenderLayerParent<T, M> parent, ResourceLocation gunId, String bone,
                        float dx, float dy, float dz, float rx, float ry, float rz, float scale,
                        ItemDisplayContext context) {
        super(parent);
        this.gunId = gunId;
        this.bone = bone;
        this.dx = dx; this.dy = dy; this.dz = dz;
        this.rx = rx; this.ry = ry; this.rz = rz;
        this.scale = scale;
        this.context = context;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (disabled) return;
        if (entity instanceof IronGolem golem && golem.isPlayerCreated()) return; // armed golems only
        if (entity.isInvisible()) return;

        if (stack.isEmpty()) {
            Item item = ForgeRegistries.ITEMS.getValue(gunId);
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
            VillagerGunners.LOGGER.warn("[Go Back To Your World] gun render layer disabled: {}", t.toString());
        } finally {
            pose.popPose();
        }
    }
}
