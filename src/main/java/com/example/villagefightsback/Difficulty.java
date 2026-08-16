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
    // The label after each name is the STARTING REPUTATION, because that is now the whole of what a
    // difficulty is: where your standing with the village begins, and how far it is allowed to move from
    // there. Everything the village then does to you follows from the number (see ReputationData), so
    // quoting the number says more than a sentence describing its consequences ever did.

    /** Starts a hero, and floored at 0: the village will never turn on you, whatever you do. */
    PEACEFUL(0, "Peaceful", ChatFormatting.BLUE, ": start at reputation = 200"),
    /** The village's normal rules, unbounded in both directions. */
    NEUTRAL(1, "Neutral", ChatFormatting.YELLOW, ": start at reputation = 0"),
    /** Starts an Enemy, and capped at -100: reputation can still fall, but it can never climb out. */
    HOSTILE(2, "Hostile", ChatFormatting.RED, ": start at reputation = -300"),
    /** Starts a Nightmare, and capped at -500: the sunset hunt is on from the first night and never lifts. */
    NIGHTMARE(3, "Nightmare", ChatFormatting.DARK_RED,
            ": start at reputation = -1000000 (I hope you run fast)");

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
    public boolean forcesHero() { return this == PEACEFUL; }

    /** True when the player should be permanently, uncurably an Enemy of the Village. */
    public boolean forcesEnemy() { return this == HOSTILE || this == NIGHTMARE; }

    /** True when the village hunts the player at sunset (see {@code NightmareRaid}). */
    public boolean huntsAtSunset() { return this == NIGHTMARE; }

    /**
     * True when the world starts you with a brain node already placed near spawn instead of a bell in hand.
     *
     * <p>Both hostile difficulties, not just Nightmare. On Hostile you are an Enemy of the Village from the first
     * morning, and handing an Enemy the bell that founds the village reads backwards: the village should already
     * be there, and already against you. The bell is a gift to someone the village has not yet judged.
     */
    public boolean placesBrainNodeAtSpawn() { return this == HOSTILE || this == NIGHTMARE; }

    /** The difficulty with this id, or {@link #NEUTRAL} for anything unexpected. Never trust a number off the
     *  network or out of a hand-edited .toml: an unknown value should play normally, not crash or lock you out. */
    public static Difficulty byId(int id) {
        for (Difficulty d : VALUES) if (d.id == id) return d;
        return NEUTRAL;
    }

    /**
     * {@link #values()} once, not once per call.
     *
     * <p>values() hands back a fresh clone of the array every time, because the caller could write to it. This
     * is reached from {@link #current()}, and 1.4.0 put current() at the top of {@code VillageEvents
     * .getAggroPlayer} -- which every combat goal in the mod asks, for every villager, guard, golem and wall
     * defender, several times a second. A four-element array is nothing; one per mob per tick forever is not.
     */
    private static final Difficulty[] VALUES = values();

    /** The difficulty currently in force, read from the config the world-mode data pushes into. */
    public static Difficulty current() {
        return byId(Config.difficulty());
    }
}
