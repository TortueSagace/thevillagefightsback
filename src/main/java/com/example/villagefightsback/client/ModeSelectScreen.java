package com.example.villagefightsback.client;

import com.example.villagefightsback.WorldMode;
import com.example.villagefightsback.network.ChooseModePacket;
import com.example.villagefightsback.network.ModNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * The one-off game-mode screen, shown over the spawn view the first time a player enters a freshly created
 * world. Full-bleed key art, the stylised mod logo, the question, and one vanilla button per mode.
 *
 * <p>It cannot be dismissed until a mode is picked (no ESC, no close button). Picking one sends the choice and
 * hands over to {@link DifficultySelectScreen}, which asks the second question before {@link ThankYouScreen}
 * carries the only way into the world. The server also acknowledges the choice in chat (see
 * {@link com.example.villagefightsback.network.ChooseModePacket}).
 */
public class ModeSelectScreen extends ModeArtScreen {
    private static final int BUTTON_W = 260;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_GAP = 6;

    /** Set the instant a mode is picked, so a double click cannot send the answer twice. */
    private boolean answered;

    /** Y of the top of the button column. */
    private int contentY;
    private int questionY;

    public ModeSelectScreen() {
        super(Component.literal("The Village Fights Back"));
    }

    @Override
    protected void init() {
        // The logo is fitted into a box that is generous on width but capped on height, so a short window
        // shrinks it instead of pushing the buttons off the bottom.
        this.questionY = logoBottom() + 12;
        this.contentY = questionY + 26;

        int x = (this.width - BUTTON_W) / 2;
        int y = contentY;
        for (WorldMode mode : WorldMode.values()) {
            addRenderableWidget(Button.builder(Component.literal(mode.title()), b -> choose(mode))
                    .bounds(x, y, BUTTON_W, BUTTON_H).build());
            y += BUTTON_H + BUTTON_GAP;
        }
    }

    /** Send the answer, then ask the second question: how involved the player wants to be. */
    private void choose(WorldMode mode) {
        if (answered) return;
        answered = true;
        ModNetwork.sendToServer(new ChooseModePacket(mode.id()));
        this.minecraft.setScreen(new ExperienceSelectScreen());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderKeyArt(g);
        renderLogo(g);

        // The question, drawn a little larger than the standard font so it carries against the art.
        drawScaled(g, Component.literal("Which Game Mode?").withStyle(ChatFormatting.WHITE),
                this.width / 2, questionY, 1.5F, 0xFFFFFFFF);

        super.render(g, mouseX, mouseY, partial);
    }

    /** No escaping the question: the only way out is to pick a mode, which closes the screen itself. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

}
