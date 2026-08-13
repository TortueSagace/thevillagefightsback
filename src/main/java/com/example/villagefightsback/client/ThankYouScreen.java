package com.example.villagefightsback.client;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The send-off panel, shown once immediately after the player picks a game mode on {@link ModeSelectScreen}.
 * Same key art and logo, a thank-you note recommending the mods this one is built to play against, and a single
 * "Enter the world" button that drops the player into the game.
 *
 * <p>The mode packet has already been sent by the time this opens, so nothing here talks to the server. The
 * inherited {@link #isPauseScreen()} still has to stay false, though: pausing would stop the integrated server
 * processing that packet.
 */
public class ThankYouScreen extends ModeArtScreen {
    private static final int BUTTON_W = 200;
    private static final int BUTTON_H = 20;

    /** Breathing room between the last line of the message and the button. */
    private static final int TEXT_BUTTON_GAP = 14;

    /** The message is the point of the screen, so it is drawn above the standard font size. */
    private static final float TEXT_SCALE = 1.25F;

    /** Widest the message block may get, in screen pixels, before it wraps. */
    private static final int MAX_TEXT_PX = 460;

    private static final Component MESSAGE = Component.literal("Thank you for playing my mod! It is advised to play it with ")
            .append(Component.literal("MCA Reborn").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" and at least one infection mod (The Flesh That Hates, Spore, Sculk Horde, ...) "
                    + "to make the villagers face a serious adversary. If you really enjoy this mod and want to say "
                    + "thanks, you can buy me a coffee at "))
            .append(Component.literal("ko-fi.com/lexnowolf").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(". Enjoy this new journey!"));

    private List<FormattedCharSequence> lines = List.of();
    private int textTop;

    public ThankYouScreen() {
        super(Component.literal("The Village Fights Back"));
    }

    @Override
    protected void init() {
        this.textTop = logoBottom() + 14;
        // font.split works in unscaled font units, so the wrap width has to be divided by the draw scale.
        this.lines = font.split(MESSAGE, Math.round(textWidthPx() / TEXT_SCALE));

        int buttonY = textTop + lines.size() * lineStep() + TEXT_BUTTON_GAP;
        // On a short window (or a large GUI scale) keep the button on screen rather than off the bottom.
        buttonY = Math.min(buttonY, this.height - BUTTON_H - 8);

        addRenderableWidget(Button.builder(Component.literal("Enter the world"), b -> onClose())
                .bounds((this.width - BUTTON_W) / 2, buttonY, BUTTON_W, BUTTON_H).build());
    }

    /** Message block width: 72% of the window, capped, so it never runs edge to edge on a wide monitor. */
    private int textWidthPx() {
        return Math.min(MAX_TEXT_PX, Math.round(this.width * 0.72F) - 24);
    }

    private int lineStep() {
        return Math.max(1, Math.round((font.lineHeight + 2) * TEXT_SCALE));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderKeyArt(g);
        renderLogo(g);

        // A dark plate behind the message so it stays legible over the bright key art.
        int step = lineStep();
        int blockH = lines.size() * step;
        int widest = 0;
        for (FormattedCharSequence line : lines) widest = Math.max(widest, font.width(line));
        int half = Math.round(widest * TEXT_SCALE) / 2 + 10;
        g.fill(this.width / 2 - half, textTop - 8, this.width / 2 + half, textTop + blockH + 2, 0xB0000000);

        int y = textTop;
        for (FormattedCharSequence line : lines) {
            drawScaled(g, line, this.width / 2, y, TEXT_SCALE, 0xFFFFFFFF);
            y += step;
        }

        super.render(g, mouseX, mouseY, partial);
    }
}
