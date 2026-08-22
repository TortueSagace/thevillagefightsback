package com.example.villagefightsback.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.example.villagefightsback.ModItems;
import com.example.villagefightsback.TheVillageFightsBack;

/**
 * Draws the Limbo and Paladin shields with VANILLA's shield model.
 *
 * <p>This is the whole point of the class, and it is why the earlier attempt was wrong. A shield is not an
 * icon: the way it hangs edge-on at the arm while carried and swings round flat in front of you when raised
 * is the vanilla shield MODEL being posed by vanilla's {@code shield.json} and {@code shield_blocking.json}
 * transforms. An {@code item/generated} sprite is a flat billboard and can do neither, so a modded shield
 * built that way sits wrong however its transforms are tuned. Borrowing {@link ShieldModel} borrows the
 * behaviour with it -- there is nothing here to keep in step with vanilla, because it IS vanilla.
 *
 * <p>Only the texture is ours: {@code textures/entity/<shield>.png}, laid out for the model's own UVs by
 * {@code tools/gen_shield_entity_tex.py}.
 *
 * <p>Wired on the item through {@code IClientItemExtensions}, the same route
 * {@link WallDefenderItemRenderer} takes, and built lazily because the model layer cannot be baked before
 * the client has finished loading.
 */
public class ShieldItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static ShieldItemRenderer instance;

    private final ShieldModel model;

    private static final ResourceLocation LIMBO = TheVillageFightsBack.id("textures/entity/limbo_shield.png");
    private static final ResourceLocation PALADIN = TheVillageFightsBack.id("textures/entity/paladin_shield.png");

    private ShieldItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.model = new ShieldModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SHIELD));
    }

    public static ShieldItemRenderer instance() {
        if (instance == null) instance = new ShieldItemRenderer();
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffer, int light, int overlay) {
        // Wrapped, like WallDefenderItemRenderer: a cosmetic renderer must never be the thing that takes
        // the client down.
        try {
            ResourceLocation tex = stack.is(ModItems.PALADIN_SHIELD.get()) ? PALADIN : LIMBO;
            pose.pushPose();
            // Vanilla's own two lines. The model is authored upside down and back to front relative to item
            // space, and every shield in the game is drawn through this flip.
            pose.scale(1.0F, -1.0F, -1.0F);
            VertexConsumer vc = ItemRenderer.getFoilBufferDirect(
                    buffer, model.renderType(tex), true, stack.hasFoil());
            // 1.21 replaced the four float colour arguments with a single packed ARGB int.
            model.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            pose.popPose();
        } catch (Throwable t) {
            TheVillageFightsBack.LOGGER.error("[The Village Fights Back] shield render failed", t);
        }
    }
}
