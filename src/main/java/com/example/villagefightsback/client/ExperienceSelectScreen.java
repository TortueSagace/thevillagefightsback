package com.example.villagefightsback.client;

import com.example.villagefightsback.network.ChooseExperiencePacket;
import com.example.villagefightsback.network.ModNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * The second world-creation screen: how involved do you want to be?
 *
 * <p>Creative is the mod as it has always been -- you watch a village evolve. Survival makes the village
 * something you work for: nodes found in the world are dead until you revive one, the starter bell becomes a
 * map to a buried cache, and evolution costs metal as well as emeralds.
 *
 * <p>Both answers go on to the difficulty screen, which is the third and last question.
 */
public class ExperienceSelectScreen extends ModeArtScreen {
    private static final int BUTTON_H = 20;
    private static final int BUTTON_GAP = 8;
    private static final int BUTTON_PADDING = 20;
    private static final int BUTTON_MARGIN = 20;

    /** Set the instant an answer is picked, so a double click cannot send it twice. */
    private boolean answered;

    private int questionY;

    private static final Component CREATIVE = Component.literal("Creative experience")
            .withStyle(ChatFormatting.GREEN)
            .append(Component.literal(": you just wanna watch").withStyle(ChatFormatting.WHITE));

    private static final Component SURVIVAL = Component.literal("Survival experience")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal(": you want to actively be involved").withStyle(ChatFormatting.WHITE));

    public ExperienceSelectScreen() {
        super(Component.literal("The Village Fights Back"));
    }

    @Override
    protected void init() {
        this.questionY = logoBottom() + 12;
        int contentY = questionY + 26;

        // Measured off the longer of the two labels, like the difficulty screen does, so the column is tight
        // around the text rather than sized to a round number.
        int widest = Math.max(this.font.width(CREATIVE), this.font.width(SURVIVAL));
        int buttonW = Math.min(widest + BUTTON_PADDING, this.width - BUTTON_MARGIN * 2);
        int x = (this.width - buttonW) / 2;

        addRenderableWidget(Button.builder(CREATIVE, b -> choose(false))
                .bounds(x, contentY, buttonW, BUTTON_H).build());
        addRenderableWidget(Button.builder(SURVIVAL, b -> choose(true))
                .bounds(x, contentY + BUTTON_H + BUTTON_GAP, buttonW, BUTTON_H).build());
    }

    private void choose(boolean survival) {
        if (answered) return;
        answered = true;
        ModNetwork.CHANNEL.sendToServer(new ChooseExperiencePacket(survival));
        // BOTH experiences go on to pick a difficulty. Creative used to skip it and answer Neutral on the
        // player's behalf; it asks now, because "I only want to watch" still leaves open how hard the world
        // should be while you watch it.
        this.minecraft.setScreen(new DifficultySelectScreen());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderKeyArt(g);
        renderLogo(g);
        drawScaled(g, Component.literal("How involved do you want to be?").withStyle(ChatFormatting.WHITE),
                this.width / 2, questionY, 1.5F, 0xFFFFFFFF);
        super.render(g, mouseX, mouseY, partial);
    }
}
