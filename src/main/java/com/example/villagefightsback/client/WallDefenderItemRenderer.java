package com.example.villagefightsback.client;

import java.util.HashMap;
import java.util.Map;

import com.example.villagefightsback.ModBlocks;
import com.example.villagefightsback.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Item renderer for the Wall Defender spawn eggs: instead of the two-tone egg, the icon IS a live, spinning
 * Wall Defender (gold for the stage-4 egg) -- the same dummy-entity-with-advancing-tickCount trick the brain
 * node UI uses ({@link GuiEntityIcons}), but as the item's model so it shows in the creative menu, inventory
 * and hand. The position/scale live in the item model's {@code display.gui} (so they hot-reload); this just
 * spins and draws the cached dummy. Fully guarded: any failure leaves the slot empty rather than crashing.
 */
public final class WallDefenderItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static WallDefenderItemRenderer instance;

    public static WallDefenderItemRenderer instance() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new WallDefenderItemRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
        }
        return instance;
    }

    private final Map<EntityType<?>, LivingEntity> cache = new HashMap<>();
    private Level cacheLevel;

    private WallDefenderItemRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher d,
                                     net.minecraft.client.model.geom.EntityModelSet m) {
        super(d, m);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemTransforms.TransformType ctx, PoseStack pose, MultiBufferSource buf,
                             int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        EntityType<?> type = stack.is(ModBlocks.WALL_DEFENDER_STAGE4_SPAWN.get())
                ? ModEntities.WALL_DEFENDER_STAGE4.get() : ModEntities.WALL_DEFENDER.get();
        try {
            if (cacheLevel != mc.level) { cache.clear(); cacheLevel = mc.level; }
            LivingEntity e = cache.get(type);
            if (e == null) {
                if (!(type.create(mc.level) instanceof LivingEntity created)) return;
                e = created;
                cache.put(type, e);
            }
            e.tickCount = (int) (Util.getMillis() / 50L); // advance ageInTicks in real time -> the core spins
            // The item model's display.gui has already centred + scaled the pose; just render the spinning dummy.
            pose.pushPose();
            EntityRenderDispatcher erd = mc.getEntityRenderDispatcher();
            erd.setRenderShadow(false);
            erd.render(e, 0.0, 0.0, 0.0, 0.0F, 1.0F, pose, buf, LightTexture.FULL_BRIGHT);
            erd.setRenderShadow(true);
            pose.popPose();
        } catch (Throwable ignored) {
            // never crash item rendering over a cosmetic icon
        }
    }
}
