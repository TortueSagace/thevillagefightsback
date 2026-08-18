package com.example.villagefightsback.client;

import java.util.ArrayList;
import java.util.List;

import com.example.villagefightsback.ModBlocks;
import com.example.villagefightsback.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * The Limbo Book, open.
 *
 * <p>Built like {@link ReputationBookScreen} -- a panel, some lines, two buttons -- with one addition: a line
 * may carry an item, drawn at its left with the text indented past it. That is what makes the gear pages worth
 * reading, because "smith a Limbo Ingot onto netherite" means very little next to a picture of the ingot.
 *
 * <p>The first page is deliberately not an explanation. Someone opening this book is very often standing in a
 * black room wondering how to leave, so the way out comes before the lore, in red, before anything else.
 */
public class LimboBookScreen extends Screen {
    private static final int PANEL_W = 244;
    private static final int PANEL_H = 194;
    private static final int MARGIN = 12;
    private static final int ICON_W = 20;      // item is 16 wide; 4px of air before the text

    private final List<List<Line>> pages = buildPages();

    private int leftPos;
    private int topPos;
    private int page;

    /** One rendered row: text, and optionally an item drawn to its left. */
    private record Line(Component text, ItemStack icon) {}

    private LimboBookScreen() {
        super(Component.literal("The Limbo"));
    }

    /** Called from the item, client-side. */
    public static void show() {
        Minecraft.getInstance().setScreen(new LimboBookScreen());
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - PANEL_W) / 2;
        this.topPos = (this.height - PANEL_H) / 2;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1))
                .bounds(leftPos + MARGIN, topPos + PANEL_H - 26, 24, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1))
                .bounds(leftPos + PANEL_W - MARGIN - 24, topPos + PANEL_H - 26, 24, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(leftPos + (PANEL_W - 60) / 2, topPos + PANEL_H - 26, 60, 18).build());
    }

    private void turn(int by) {
        page = Math.max(0, Math.min(pages.size() - 1, page + by));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        // Black rather than the Reputation Book's slate: this is the Limbo's book.
        g.fill(leftPos - 1, topPos - 1, leftPos + PANEL_W + 1, topPos + PANEL_H + 1, 0xFF000000);
        g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + PANEL_H, 0xFF16161C);

        Component title = Component.literal("The Limbo").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD);
        g.drawString(font, title, leftPos + MARGIN, topPos + 10, 0xFFFFFFFF, false);

        int y = topPos + 30;
        for (Line line : pages.get(page)) {
            if (line.icon != null) {
                g.renderItem(line.icon, leftPos + MARGIN, y - 4);
            }
            int x = leftPos + MARGIN + (line.icon != null ? ICON_W : 0);
            int wrap = PANEL_W - MARGIN * 2 - (line.icon != null ? ICON_W : 0);
            if (line.text.getString().isEmpty()) { y += 5; continue; }
            for (FormattedCharSequence seq : font.split(line.text, wrap)) {
                g.drawString(font, seq, x, y, 0xFFFFFFFF, false);
                y += 10;
            }
            if (line.icon != null) y += 4;     // keep the next row clear of a 16px item
        }

        String n = (page + 1) + " / " + pages.size();
        g.drawString(font, Component.literal(n).withStyle(ChatFormatting.DARK_GRAY),
                leftPos + (PANEL_W - font.width(n)) / 2, topPos + PANEL_H - 40, 0xFF555555, false);
        super.render(g, mouseX, mouseY, partial);
    }

    /** A book, not a menu: it does not pause a single-player world. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- the text ------------------------------------------------------------------------------------

    private static Line head(String s) {
        return new Line(Component.literal(s).withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD), null);
    }

    private static Line body(String s) {
        return new Line(Component.literal(s).withStyle(ChatFormatting.WHITE), null);
    }

    private static Line red(String s) {
        return new Line(Component.literal(s).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), null);
    }

    private static Line item(ItemLike what, String s) {
        return new Line(Component.literal(s).withStyle(ChatFormatting.WHITE), new ItemStack(what));
    }

    private static final Line BLANK = new Line(Component.literal(""), null);

    private static List<List<Line>> buildPages() {
        List<List<Line>> out = new ArrayList<>();

        // 1. The way out, before anything else.
        out.add(new ArrayList<>(List.of(
                head("Are you currently in the Limbo?"),
                BLANK,
                red("Right-click on the bedrock bed to"),
                red("GET OUT."),
                BLANK,
                item(ModBlocks.LIMBO_BED_ITEM.get(),
                        "The black bed with the bedrock blanket, in the holding cell you woke up in. It puts you "
                        + "back exactly where you were taken from."),
                BLANK,
                body("Any other bed works too, while you are here. Trying to sleep is the way home."))));

        // 2. Where you are.
        out.add(new ArrayList<>(List.of(
                head("What this place is"),
                BLANK,
                body("A dark dimension of nothing, with a 22x22x22 bedrock holding cell at its centre. The cell "
                        + "is where every newcomer arrives."),
                BLANK,
                body("The shell only looks like bedrock. Beyond it the void is scattered with floating bedrock "
                        + "cubes, crusted on the outside with Forgotten Glowstone."),
                BLANK,
                item(ModItems.FORGOTTEN_POWDER.get(),
                        "Forgotten Powder, what that glowstone drops. The rest of the Limbo is built out of "
                        + "what you can pry off those cubes."))));

        // 3. How you got here.
        out.add(new ArrayList<>(List.of(
                head("How you got here"),
                BLANK,
                item(ModItems.LIMBO_POTION.get(), "Drinking a Limbo Potion banishes you."),
                item(ModItems.VOLATILE_LIMBO_POTION.get(), "The volatile one is thrown, and banishes an area."),
                BLANK,
                body("Falling out of the world drops you in here rather than killing you. Fall out of the "
                        + "Limbo and you land in the End, and THAT one is fatal."),
                BLANK,
                body("A frame of fake obsidian, lit, opens a green portal. It travels sixteen blocks for "
                        + "every one you walk."))));

        // 4. The gear line.
        out.add(new ArrayList<>(List.of(
                head("Arming yourself"),
                BLANK,
                item(ModItems.LIMBO_MEMBRANE.get(), "Limbo Membrane, from a Limbo Phantom."),
                item(ModItems.LIMBO_INGOT.get(), "Melt membrane into a Limbo Ingot."),
                item(ModItems.LIMBO_SMITHING_TEMPLATE.get(),
                        "The template. Rare, in village chests. This is the ingredient that costs you."),
                BLANK,
                item(ModItems.LIMBO_PICKAXE.get(),
                        "Smith netherite with all three. The pickaxe mines bedrock and reinforced deepslate."),
                BLANK,
                body("The full armour set grants knockback immunity, fire and fall immunity, and lets you "
                        + "walk through bedrock. Press G for the banish beam."))));

        // 5. The Scythe.
        out.add(new ArrayList<>(List.of(
                head("The Limbo Scythe"),
                BLANK,
                item(ModItems.LIMBO_SCYTHE.get(),
                        "Smithed from a Limbo Hoe: amethyst at the corners, a golden apple above, soul sand "
                        + "either side, and a second Smithing Template beneath."),
                BLANK,
                body("It works by souls. Reap anything with 20 hearts of maximum health or more and the scythe "
                        + "keeps its soul. It holds eight."),
                BLANK,
                body("Spend a soul on a grave and the villager buried there walks back out of it. Only "
                        + "defenders that fought are ever buried, so it can only return someone who earned "
                        + "the headstone."))));

        // 6. The Obliterator.
        out.add(new ArrayList<>(List.of(
                head("The Obliterator"),
                BLANK,
                item(ModItems.LIMBO_SWORD.get(), "The Limbo Sword deals 9."),
                item(ModItems.OBLITERATOR.get(),
                        "The Obliterator deals 14. That is what the dragon egg in its recipe buys you."),
                BLANK,
                body("That is all it does, until an Obliteration Furnace is burning somewhere in the world."),
                BLANK,
                body("From then on every hit also marks its victim. A marked creature that dies is struck off "
                        + "the world's spawn tables entirely, for as long as that furnace stays lit."))));

        // 7. What lives here.
        out.add(new ArrayList<>(List.of(
                head("What lives here"),
                BLANK,
                body("Limbo Phantoms. Three times the size, glowing eyed, and they phase through bedrock. One "
                        + "will grab you and haul you off through a wall."),
                BLANK,
                body("They hatch from the black and white eggs in the ancient debris nests on the largest "
                        + "cubes. Break one without Silk Touch and you have made a phantom."),
                BLANK,
                body("Stay a while and you will be marked with Very Bad Omen. Carry that back to a village "
                        + "and the Limbo comes with you."))));

        // 8. The Sage.
        out.add(new ArrayList<>(List.of(
                head("The Limbo Sage"),
                BLANK,
                body("A black-robed villager whose workplace is the Village Brain Node itself. One per "
                        + "village, and never more."),
                BLANK,
                body("He sells Limbo goods, badly overpriced, from useless blocks up to randomly enchanted "
                        + "Limbo tools. He will never sell you armour."),
                BLANK,
                body("He is not defenceless. Anything above 10 hearts that comes near him gets a Volatile "
                        + "Limbo Potion thrown at it."))));

        return out;
    }
}
