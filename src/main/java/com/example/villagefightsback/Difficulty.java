package com.example.villagefightsback;

import net.minecraft.ChatFormatting;

/**
 * How the village TREATS YOU, chosen once on the difficulty screen that follows the game-mode screen.
 *
 * <p>Orthogonal to {@link WorldMode}, which decides what the mod DOES (what spreads, what spawns). This decides
 * where you stand with the village, which is why the two are separate questions on separate screens rather than
 * one list of twelve combinations.
 *
 * <p>Stored per world in {@link WorldModeData} and pushed over the {@code difficulty} config key, so it can be
 * changed afterwards with {@code /thevillagefightsback config difficulty <0-3>} like any other setting.
 */
public enum Difficulty {
    /** Permanently a Hero of the Village, and never its Enemy. The village will not turn on you at all. */
    EASY(0, "Easy", ChatFormatting.BLUE, ": permanent \"Hero of the Village\" effect"),
    /** The village's normal rules: earn Hero status, lose it, become an Enemy, all as usual. */
    NORMAL(1, "Normal", ChatFormatting.YELLOW, " (recommended mode)"),
    /** Permanently an Enemy of the Village, and never its Hero. Every defender is hostile from the first day. */
    HARD(2, "Hard", ChatFormatting.RED, ": permanent \"Enemy of the Village\" effect"),
    /** Hard, plus the nightly hunt: at sunset the brain node marks you and sends a raid after you. */
    NIGHTMARE(3, "Nightmare", ChatFormatting.DARK_RED, ": villagers will chase you anywhere in the Overworld");

    private final int id;
    private final String title;
    private final ChatFormatting colour;
    private final String suffix;

    Difficulty(int id, String title, ChatFormatting colour, String suffix) {
        this.id = id;
        this.title = title;
        this.colour = colour;
        this.suffix = suffix;
    }

    public int id() { return id; }
    public String title() { return title; }
    public ChatFormatting colour() { return colour; }
    /**
     * The whole button label: the name in this difficulty's colour, then what it does, in white.
     *
     * <p>All of it on ONE line inside the button. A separate caption line underneath was tried and was wrong:
     * it read as two competing things rather than one choice, and the captions collided with the buttons above
     * and below them.
     */
    public net.minecraft.network.chat.Component label() {
        return net.minecraft.network.chat.Component.literal(title).withStyle(colour)
                .append(net.minecraft.network.chat.Component.literal(suffix)
                        .withStyle(ChatFormatting.WHITE));
    }

    /** True when the player should be permanently, uncurably a Hero of the Village. */
    public boolean forcesHero() { return this == EASY; }

    /** True when the player should be permanently, uncurably an Enemy of the Village. */
    public boolean forcesEnemy() { return this == HARD || this == NIGHTMARE; }

    /** True when the village hunts the player at sunset (see {@code NightmareRaid}). */
    public boolean huntsAtSunset() { return this == NIGHTMARE; }

    /**
     * True when the world starts you with a brain node already placed near spawn instead of a bell in hand.
     *
     * <p>Both hostile difficulties, not just Nightmare. On Hard you are an Enemy of the Village from the first
     * morning, and handing an Enemy the bell that founds the village reads backwards: the village should already
     * be there, and already against you. The bell is a gift to someone the village has not yet judged.
     */
    public boolean placesBrainNodeAtSpawn() { return this == HARD || this == NIGHTMARE; }

    /** The difficulty with this id, or {@link #NORMAL} for anything unexpected. Never trust a number off the
     *  network or out of a hand-edited .toml: an unknown value should play normally, not crash or lock you out. */
    public static Difficulty byId(int id) {
        for (Difficulty d : values()) if (d.id == id) return d;
        return NORMAL;
    }

    /** The difficulty currently in force, read from the config the world-mode data pushes into. */
    public static Difficulty current() {
        return byId(Config.difficulty());
    }
}
