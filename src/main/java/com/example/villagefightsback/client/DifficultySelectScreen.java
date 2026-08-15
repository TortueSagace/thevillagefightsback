package com.example.villagefightsback.client;

import com.example.villagefightsback.Difficulty;
import com.example.villagefightsback.network.ChooseDifficultyPacket;
import com.example.villagefightsback.network.ModNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * The second of the three world-creation panels: game mode, THEN difficulty, then the send-off.
 *
 * <p>Two screens rather than one list, because they are two different questions. The mode decides what the mod
 * DOES to the world; the difficulty decides where you STAND with the village. Offering the twelve combinations
 * as one menu would bury both.
 *
 * <p>Each button is coloured by its difficulty and carries a one-line consequence underneath, because "Hard"
 * on its own does not tell you that the village will be hostile before you have done anything, and that is the
 * kind of thing a player should learn here rather than by being shot.
 */
public class DifficultySelectScreen extends ModeArtScreen {
    private static final int BUTTON_W = 260;
    private static final int BUTTON_H = 20;
    /** Room for the button plus its blurb line beneath it. */
    private static final int ROW_H = BUTTON_H + 12;

    /** Set the instant an answer is picked, so a double click cannot send it twice. */
    private boolean answered;

    private int contentY;
    private int questionY;

    public DifficultySelectScreen() {
        super(Component.literal("The Village Fights Back"));
    }

    @Override
    protected void init() {
        this.questionY = logoBottom() + 12;
        this.contentY = questionY + 26;

        int x = (this.width - BUTTON_W) / 2;
        int y = contentY;
        for (Difficulty d : Difficulty.values()) {
            Component label = Component.literal(d.title()).withStyle(d.colour());
            if (d == Difficulty.NORMAL) {
                label = label.copy().append(Component.literal("  (recommended)").withStyle(ChatFormatting.GRAY));
            }
            addRenderableWidget(Button.builder(label, b -> choose(d))
                    .bounds(x, y, BUTTON_W, BUTTON_H).build());
            y += ROW_H;
        }
    }

    /** Send the answer, then hand over to the send-off panel (which has the only way into the world). */
    private void choose(Difficulty d) {
        if (answered) return;
        answered = true;
        ModNetwork.CHANNEL.sendToServer(new ChooseDifficultyPacket(d.id()));
        this.minecraft.setScreen(new ThankYouScreen());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderKeyArt(g);
        renderLogo(g);

        drawScaled(g, Component.literal("Choose your difficulty mode").withStyle(ChatFormatting.WHITE),
                this.width / 2, questionY, 1.5F, 0xFFFFFFFF);

        // The blurbs sit under their own buttons, drawn after the art and before the widgets so a hovered
        // button still paints over its own row rather than being dimmed by text drawn on top of it.
        int y = contentY + BUTTON_H + 2;
        for (Difficulty d : Difficulty.values()) {
            g.drawCenteredString(this.font, Component.literal(d.blurb()).withStyle(ChatFormatting.GRAY),
                    this.width / 2, y, 0xFFBFBFBF);
            y += ROW_H;
        }

        super.render(g, mouseX, mouseY, partial);
    }

    /** No escaping the question: the only way out is to pick a difficulty, which moves the screen on. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
