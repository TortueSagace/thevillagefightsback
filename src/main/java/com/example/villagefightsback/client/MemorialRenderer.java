package com.example.villagefightsback.client;

import java.util.List;

import com.example.villagefightsback.MemorialBlock;
import com.example.villagefightsback.MemorialBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Carves the epitaph onto the front of a {@link com.example.villagefightsback.MemorialBlock}.
 *
 * <p>Drawn the way a sign draws its text: rotate to the stone's facing, step out to just in front of the slab,
 * shrink the font hard (the inscription has to fit a one-block headstone), and wrap it over several lines. The
 * text is drawn face-on only, so the back of the stone stays blank as a real headstone does.
 */
public class MemorialRenderer implements BlockEntityRenderer<MemorialBlockEntity> {
    /** Font units per block. The vanilla font is 8px tall, so this fits roughly 5 short lines on the stone. */
    private static final float SCALE = 0.008F;
    /** Width of the inscription in font units before it wraps. */
    private static final int WRAP = 90;
    private static final int COLOUR = 0xFFDDDDDD;

    private final Font font;

    public MemorialRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(MemorialBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        String epitaph = be.epitaph();
        if (epitaph == null || epitaph.isEmpty()) return;

        Direction facing = be.getBlockState().hasProperty(MemorialBlock.FACING)
                ? be.getBlockState().getValue(MemorialBlock.FACING)
                : Direction.NORTH;

        List<FormattedCharSequence> lines = font.split(Component.literal(epitaph), WRAP);
        if (lines.isEmpty()) return;

        pose.pushPose();
        // Centre of the block, then turn to face outward and step just clear of the slab face.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-facing.toYRot()));
        Vec3 out = new Vec3(0.0, 0.0, -0.13);   // half the slab thickness plus a hair, so it never z-fights
        pose.translate(out.x, out.y, out.z);
        pose.scale(SCALE, -SCALE, SCALE);       // negative Y: text is drawn with Y down

        int lineHeight = font.lineHeight + 1;
        int top = -(lines.size() * lineHeight) / 2;
        Matrix4f matrix = pose.last().pose();
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            float x = -font.width(line) / 2.0F;
            font.drawInBatch(line, x, top + i * lineHeight, COLOUR, false, matrix, buffers,
                    Font.DisplayMode.NORMAL, 0, light);
        }
        pose.popPose();
    }

    /** Readable from a fair way off, like a sign, so a graveyard can be read without standing on top of it. */
    @Override
    public int getViewDistance() {
        return 32;
    }
}
