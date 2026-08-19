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
 * <p>Each button carries its whole meaning on one line: the name in its own colour, then what it does. A first
 * version put the consequence on a caption line under each button and it was wrong -- it read as two competing
 * things instead of one choice, and the captions ran into the buttons above and below them.
 */
public class DifficultySelectScreen extends ModeArtScreen {
    /** Breathing room either side of the widest label. */
    private static final int BUTTON_PADDING = 16;
    private static final int BUTTON_MARGIN = 20;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_GAP = 6;

    /** Set the instant an answer is picked, so a double click cannot send it twice. */
    private boolean answered;

    private int questionY;

    public DifficultySelectScreen() {
        super(Component.literal("The Village Fights Back"));
    }

    @Override
    protected void init() {
        this.questionY = logoBottom() + 12;
        int contentY = questionY + 26;

        // Measured, not guessed: as wide as the LONGEST label needs and not a pixel more, so the column is
        // tight around the Nightmare line rather than sized to a round number someone picked.
        int widest = 0;
        for (Difficulty d : Difficulty.values()) widest = Math.max(widest, this.font.width(d.label()));
        int buttonW = Math.min(widest + BUTTON_PADDING, this.width - BUTTON_MARGIN * 2);
        int x = (this.width - buttonW) / 2;
        int y = contentY;
        for (Difficulty d : Difficulty.values()) {
            addRenderableWidget(Button.builder(d.label(), b -> choose(d))
                    .bounds(x, y, buttonW, BUTTON_H).build());
            y += BUTTON_H + BUTTON_GAP;
        }
    }

    /** Send the answer, then hand over to the send-off panel (which has the only way into the world). */
    private void choose(Difficulty d) {
        if (answered) return;
        answered = true;
        ModNetwork.sendToServer(new ChooseDifficultyPacket(d.id()));
        this.minecraft.setScreen(new ThankYouScreen());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderKeyArt(g);
        renderLogo(g);

        drawScaled(g, Component.literal("Choose your difficulty mode").withStyle(ChatFormatting.WHITE),
                this.width / 2, questionY, 1.5F, 0xFFFFFFFF);

        super.render(g, mouseX, mouseY, partial);
    }

    /** No escaping the question: the only way out is to pick a difficulty, which moves the screen on. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
