package com.example.villagefightsback.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The Reputation Book, open.
 *
 * <p>Read-only and hand-drawn, like {@code ObliterationFurnaceScreen}: a panel, some lines, and two buttons.
 * Every word lives here rather than in the item's NBT, so a book a player has carried since their first
 * morning always says whatever the current version says.
 *
 * <p>The pages differ by difficulty, and the differences are the point rather than decoration. On Peaceful
 * there is no way to lose standing at all, so the book never mentions losing it, and never mentions Enemy or
 * Nightmare of the Village -- naming a punishment that cannot happen would be a threat the game does not
 * intend to carry out. On the two hostile difficulties standing is pinned and unearnable, so there is nothing
 * to explain and the book says so in one page.
 */
public class ReputationBookScreen extends Screen {
    private static final int PANEL_W = 244;
    private static final int PANEL_H = 194;
    private static final int MARGIN = 12;

    private final int difficulty;
    private final int reputation;
    private final List<List<Component>> pages;

    private int leftPos;
    private int topPos;
    private int page;

    private ReputationBookScreen(int difficulty, int reputation) {
        super(Component.literal("Reputation"));
        this.difficulty = difficulty;
        this.reputation = reputation;
        this.pages = buildPages(difficulty);
    }

    /** Called from the packet handler on the client. */
    public static void show(int difficulty, int reputation) {
        Minecraft.getInstance().setScreen(new ReputationBookScreen(difficulty, reputation));
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - PANEL_W) / 2;
        this.topPos = (this.height - PANEL_H) / 2;
        if (pages.size() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1))
                    .bounds(leftPos + MARGIN, topPos + PANEL_H - 26, 24, 18).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1))
                    .bounds(leftPos + PANEL_W - MARGIN - 24, topPos + PANEL_H - 26, 24, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(leftPos + (PANEL_W - 60) / 2, topPos + PANEL_H - 26, 60, 18).build());
    }

    private void turn(int by) {
        page = Math.max(0, Math.min(pages.size() - 1, page + by));
    }

    /**
     * No blur behind this panel.
     *
     * <p>This is why the mod's screens look soft on 1.21 and sharp on 1.20.1, and nothing in the mod causes
     * it. 1.20.5 added a full-screen gaussian pass to {@code Screen.renderBackground}: it now calls
     * {@code renderBlurredBackground}, which runs {@code gameRenderer.processBlurEffect} over the whole
     * world behind the screen. On 1.20.1 the same call only laid a dark gradient down. Every screen that
     * asks for the vanilla background inherits the blur, so a panel that read as crisp on the original
     * version reads as out of focus here.
     *
     * <p>Overriding it away leaves the rest of {@code renderBackground} alone -- the translucent in-world
     * menu tile still goes down, which is what stands in for 1.20.1's gradient -- so the result matches the
     * original version rather than merely removing something.
     */
    @Override
    protected void renderBlurredBackground(float partial) {
        // Intentionally empty: see above. 1.20.1 had no blur and these panels were designed without one.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // 1.21 note: main calls the 1.20.1 one-argument renderBackground(g); 1.20.2 replaced it with the
        // four-argument form, which is what BrainNodeScreen on this branch already calls.
        this.renderBackground(g, mouseX, mouseY, partial);
        g.fill(leftPos - 1, topPos - 1, leftPos + PANEL_W + 1, topPos + PANEL_H + 1, 0xFF1A1A1F);
        g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + PANEL_H, 0xFF2A2A33);

        Component title = Component.literal("Reputation").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        g.drawString(font, title, leftPos + MARGIN, topPos + 10, 0xFFFFFFFF, false);
        String standing = "You: " + reputation;
        g.drawString(font, Component.literal(standing).withStyle(ChatFormatting.GOLD),
                leftPos + PANEL_W - MARGIN - font.width(standing), topPos + 10, 0xFFFFFFFF, false);

        int y = topPos + 30;
        int wrap = PANEL_W - MARGIN * 2;
        for (Component line : pages.get(page)) {
            if (line.getString().isEmpty()) { y += 5; continue; }
            for (FormattedCharSequence seq : font.split(line, wrap)) {
                g.drawString(font, seq, leftPos + MARGIN, y, 0xFFFFFFFF, false);
                y += 10;
            }
        }

        if (pages.size() > 1) {
            String n = (page + 1) + " / " + pages.size();
            g.drawString(font, Component.literal(n).withStyle(ChatFormatting.DARK_GRAY),
                    leftPos + (PANEL_W - font.width(n)) / 2, topPos + PANEL_H - 40, 0xFF555555, false);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    /** Nothing here pauses a single-player world: it is a book, not a menu. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- the text ------------------------------------------------------------------------------------

    private static Component head(String s) {
        return Component.literal(s).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    private static Component body(String s) {
        return Component.literal(s).withStyle(ChatFormatting.WHITE);
    }

    private static Component good(String s) {
        return Component.literal(s).withStyle(ChatFormatting.GREEN);
    }

    private static Component bad(String s) {
        return Component.literal(s).withStyle(ChatFormatting.RED);
    }

    private static final Component BLANK = Component.literal("");

    private static List<List<Component>> buildPages(int difficulty) {
        List<List<Component>> out = new ArrayList<>();

        // Hostile (2) and Nightmare (3): standing is pinned, so there is nothing to teach.
        if (difficulty == 2) {
            out.add(List.of(Component.literal(
                    "You picked Hostile difficulty. You will remain forever an enemy of the village. Sorry!")
                    .withStyle(ChatFormatting.RED)));
            return out;
        }
        if (difficulty == 3) {
            out.add(List.of(Component.literal("HAHAHAHAHAHAHAHAHAHA")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    BLANK,
                    Component.literal("You're so cooked. SO COOKED.").withStyle(ChatFormatting.DARK_RED),
                    BLANK,
                    Component.literal("Run, you fool").withStyle(ChatFormatting.DARK_RED,
                            ChatFormatting.BOLD)));
            return out;
        }

        boolean peaceful = difficulty == 0;

        List<Component> p1 = new ArrayList<>(List.of(
                head("What the village thinks of you"),
                BLANK,
                body("The village keeps a number. It goes up when you are useful to it and down when you are "
                        + "not, and everything it decides about you it decides from that number."),
                BLANK,
                body("It is not a timer and it does not wash off: no milk, no totem, no death will change "
                        + "where you stand.")));
        if (peaceful) {
            p1.add(BLANK);
            p1.add(good("On Peaceful you start at 200 -- already a Hero of the Village -- and it can never "
                    + "fall below zero. The village will never turn on you, whatever you do."));
        } else {
            p1.add(BLANK);
            p1.add(body("On Neutral it starts at zero, and there is no bound in either direction."));
        }
        out.add(p1);

        List<Component> p2 = new ArrayList<>(List.of(
                head("Earning it"),
                BLANK,
                good("+2   every trade with a villager"),
                good("+5   killing something the village would fight"),
                good("+1   every emerald fed to the brain node"),
                BLANK,
                body("At sunset the brain node will name the biggest threat near it and pay its full health "
                        + "in points to whoever brings it down. Ten minutes, and it must be your kill."),
                BLANK,
                // The two conditions that explain a quiet evening. Without them a player who never sees an
                // offer has no way to tell whether the feature is broken or simply not applicable tonight.
                body("Nothing under 75 health is worth naming, and the village will not hire you while "
                        + "General Darius is already out.")));
        out.add(p2);

        if (!peaceful) {
            out.add(new ArrayList<>(List.of(
                    head("Losing it"),
                    BLANK,
                    bad("-20   striking any villager, guard or golem"),
                    bad("-60   killing a villager, on top of the blow"),
                    bad("-10   every stack taken from a village chest"),
                    bad("-50   mining the village's iron, gold, diamond"),
                    bad("        or emerald blocks"),
                    bad("-50   breaking a village chest open"),
                    bad("-500  swinging at the brain node"),
                    BLANK,
                    body("Every sunset the number moves one tenth back toward zero. The village forgets, "
                            + "slowly, in both directions."))));
        } else {
            out.add(new ArrayList<>(List.of(
                    head("Forgetting"),
                    BLANK,
                    body("Every sunset the number moves one tenth back toward zero. Standing has to be kept "
                            + "up, not just reached."))));
        }

        List<Component> p4 = new ArrayList<>(List.of(
                head("Hero of the Village"),
                BLANK,
                good("100    Hero I"),
                good("200    Hero II"),
                good("300    Hero III"),
                good("400    Hero IV"),
                good("500    Hero V"),
                BLANK,
                body("Every level buys better prices from every villager. At Hero I the village trusts you "
                        + "with its chests; at Hero V, with its metal.")));
        out.add(p4);

        if (!peaceful) {
            out.add(new ArrayList<>(List.of(
                    head("The other direction"),
                    BLANK,
                    bad("-100   Enemy of the Village"),
                    body("Every defender it has will attack you on sight. Nothing else will: hit a villager "
                            + "once and you have paid for it, not declared war."),
                    BLANK,
                    bad("-500   Nightmare of the Village"),
                    body("At every sunset the village raises a raid and sends it after you, wherever you "
                            + "are. It stops when you climb back above -500."),
                    BLANK,
                    body("Die with a negative number and it returns to zero. The village considers the "
                            + "matter settled."))));
        }

        return out;
    }
}
