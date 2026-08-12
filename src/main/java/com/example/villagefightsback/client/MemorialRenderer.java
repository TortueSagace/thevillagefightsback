package com.example.villagefightsback.client;

import java.util.List;

import com.example.villagefightsback.MemorialBlock;
import com.example.villagefightsback.MemorialBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/**
 * Carves the epitaph onto the front of a {@link com.example.villagefightsback.MemorialBlock}.
 *
 * <p>Drawn the way a sign draws its text: rotate to the stone's facing, step out to just clear of the slab,
 * shrink the font (the inscription has to fit a one-block headstone), and wrap it over several lines. Carved on
 * BOTH faces, so it reads from either side of a graveyard row and cannot end up hidden behind its own stone.
 */
public class MemorialRenderer implements BlockEntityRenderer<MemorialBlockEntity> {
    /** Font units per block, close to the scale vanilla uses for sign text so it reads at the same distance. */
    private static final float SCALE = 0.010F;

    /** How far in front of the slab face the text sits. The face is 0.125 from the block centre, so this
     *  stands clearly OFF it. 0.14 was tried and left the words embedded in the stone and unreadable: at 0.015
     *  of clearance the glyphs simply intersect the slab. */
    private static final float FACE_OFFSET = 0.20F;
    /** Width of the inscription in font units before it wraps. 90 was wider than the stone itself: at the
     *  draw scale it spanned 0.9 blocks against a 0.625-wide slab, so the text hung off both edges. */
    private static final int WRAP = 55;
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

        // Carved on BOTH faces of the stone, which is how a real headstone in a graveyard reads, and which also
        // makes the inscription independent of which way the slab happens to be turned. The first attempt drew
        // one face and derived the side from -facing.toYRot(): for a north-facing stone that put the text at
        // z 0.63, the BACK of a slab whose front is at 0.375, so DisplayMode.NORMAL dutifully hid it behind the
        // stone and the grave came out blank.
        for (Direction side : new Direction[]{facing, facing.getOpposite()}) {
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-side.toYRot()));
            pose.translate(0.0, 0.0, -FACE_OFFSET);   // step clear of the slab face so it cannot z-fight
            pose.scale(SCALE, -SCALE, SCALE);         // negative Y: text is drawn with Y down

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
    }

    /** Readable from a fair way off, like a sign, so a graveyard can be read without standing on top of it. */
    @Override
    public int getViewDistance() {
        return 32;
    }
}
