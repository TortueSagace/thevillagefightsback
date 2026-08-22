package com.example.villagefightsback.client;

import java.util.ArrayList;
import java.util.List;

import com.example.villagefightsback.ModArmor;
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
 * <p>The panel height is worked out at open time rather than fixed, because the pages were overflowing the
 * bottom of a fixed 194. It grows to {@value #MAX_PANEL_H} but never past the window, so it still fits a small
 * screen or a large GUI scale.
 *
 * <p>The first page is deliberately not an explanation. Someone opening this book is very often standing in a
 * black room wondering how to leave, so the way out comes before anything else, in red.
 */
public class LimboBookScreen extends Screen {
    private static final int PANEL_W = 244;
    private static final int MAX_PANEL_H = 240;
    private static final int MIN_PANEL_H = 160;
    private static final int MARGIN = 12;
    private static final int ICON_W = 20;      // item is 16 wide; 4px of air before the text
    private static final int ICON_H = 18;      // least vertical space an icon row may take
    private static final int LINE = 10;

    private final List<List<Line>> pages = buildPages();

    private int leftPos;
    private int topPos;
    private int panelH;
    private int page;

    /** One rendered row: text, and optionally an item drawn to its left. */
    private record Line(Component text, ItemStack icon) {}

    private LimboBookScreen() {
        super(Component.literal("The Limbo"));
    }

    /** Called from the OpenLimboBookPacket handler on the client. */
    public static void show() {
        Minecraft.getInstance().setScreen(new LimboBookScreen());
    }

    @Override
    protected void init() {
        this.panelH = Math.max(MIN_PANEL_H, Math.min(MAX_PANEL_H, this.height - 20));
        this.leftPos = (this.width - PANEL_W) / 2;
        this.topPos = (this.height - panelH) / 2;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1))
                .bounds(leftPos + MARGIN, topPos + panelH - 26, 24, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1))
                .bounds(leftPos + PANEL_W - MARGIN - 24, topPos + panelH - 26, 24, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(leftPos + (PANEL_W - 60) / 2, topPos + panelH - 26, 60, 18).build());
    }

    private void turn(int by) {
        page = Math.max(0, Math.min(pages.size() - 1, page + by));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // 1.21 note: main calls the 1.20.1 one-argument renderBackground(g); 1.20.2 replaced it with the
        // four-argument form, which is what BrainNodeScreen on this branch already calls.
        this.renderBackground(g, mouseX, mouseY, partial);
        // Black rather than the Reputation Book's slate: this is the Limbo's book.
        g.fill(leftPos - 1, topPos - 1, leftPos + PANEL_W + 1, topPos + panelH + 1, 0xFF000000);
        g.fill(leftPos, topPos, leftPos + PANEL_W, topPos + panelH, 0xFF16161C);

        Component title = Component.literal("The Limbo").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD);
        g.drawString(font, title, leftPos + MARGIN, topPos + 10, 0xFFFFFFFF, false);

        int y = topPos + 30;
        for (Line line : pages.get(page)) {
            if (line.text.getString().isEmpty()) { y += 5; continue; }
            boolean hasIcon = line.icon != null;
            int rowTop = y;
            if (hasIcon) g.renderItem(line.icon, leftPos + MARGIN, y - 4);
            int x = leftPos + MARGIN + (hasIcon ? ICON_W : 0);
            int wrap = PANEL_W - MARGIN * 2 - (hasIcon ? ICON_W : 0);
            for (FormattedCharSequence seq : font.split(line.text, wrap)) {
                g.drawString(font, seq, x, y, 0xFFFFFFFF, false);
                y += LINE;
            }
            if (hasIcon) y = Math.max(y, rowTop + ICON_H);   // never let the next row clip a 16px item
        }

        String n = (page + 1) + " / " + pages.size();
        g.drawString(font, Component.literal(n).withStyle(ChatFormatting.DARK_GRAY),
                leftPos + (PANEL_W - font.width(n)) / 2, topPos + panelH - 40, 0xFF555555, false);
        super.render(g, mouseX, mouseY, partial);
    }

    /** A book, not a menu: it does not pause a single-player world. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- the text ------------------------------------------------------------------------------------
    // Plain, direct English on purpose: short sentences, common words, one idea at a time. This is a manual
    // for somebody who is lost, not a piece of writing.

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

        out.add(new ArrayList<>(List.of(
                head("Are you currently in the Limbo?"),
                BLANK,
                red("Right-click on the bedrock bed to"),
                red("GET OUT."),
                BLANK,
                item(ModBlocks.LIMBO_BED_ITEM.get(),
                        "It is the black bed with the bedrock cover, in the room where you arrived."),
                BLANK,
                body("It sends you back to the exact place you were taken from."),
                BLANK,
                body("Any bed works here. Trying to sleep is how you go home."))));

        out.add(new ArrayList<>(List.of(
                head("What this place is"),
                BLANK,
                body("A dark world with very little in it. You arrive in a bedrock room at its centre, 22 blocks "
                        + "across."),
                BLANK,
                body("The walls only look like bedrock. You can walk straight through them."),
                BLANK,
                body("Outside, blocks of bedrock float in the dark. Green Forgotten Glowstone grows on them."),
                BLANK,
                item(ModItems.FORGOTTEN_POWDER.get(), "Break the glowstone and you get Forgotten Powder."))));

        out.add(new ArrayList<>(List.of(
                head("The glitching chunks"),
                BLANK,
                body("The Limbo is not completely empty. Parts of it are copies of the real world."),
                BLANK,
                body("About 2 chunks in 100 copy the same chunk in the Overworld. About 1 in 100 copies the "
                        + "Nether."),
                BLANK,
                body("The copy works BOTH ways. Break a block or empty a chest in one of these chunks and it "
                        + "changes in the real world too."),
                BLANK,
                body("Living creatures do not cross. Only the world is shared."))));

        out.add(new ArrayList<>(List.of(
                head("How you got here"),
                BLANK,
                item(ModItems.LIMBO_POTION.get(), "Drink a Limbo Potion and you are sent here."),
                item(ModItems.VOLATILE_LIMBO_POTION.get(),
                        "Throw the volatile one to send everything near it."),
                BLANK,
                body("If you fall out of the world, you land here instead of dying. If you fall out of the "
                        + "Limbo, you land in the End, and that fall does kill you."),
                BLANK,
                body("You can also light a frame of fake obsidian. It opens a green portal. One block here is "
                        + "sixteen blocks at home."))));

        out.add(new ArrayList<>(List.of(
                head("Arming yourself"),
                BLANK,
                item(ModItems.LIMBO_MEMBRANE.get(), "Limbo Membrane drops from a Limbo Phantom."),
                item(ModItems.LIMBO_INGOT.get(),
                        "8 membranes and 1 Emerald Apple craft into 1 Limbo Ingot."),
                item(ModItems.LIMBO_SMITHING_TEMPLATE.get(),
                        "The template is rare. Look in village chests."),
                BLANK,
                item(ModItems.LIMBO_PICKAXE.get(),
                        "At a smithing table: the template, a netherite pickaxe, and one Limbo Ingot."),
                BLANK,
                body("Only that last step is smithing. The same three slots upgrade the other netherite "
                        + "tools and armour."))));

        out.add(new ArrayList<>(List.of(
                head("The full armour set"),
                BLANK,
                body("Wear all four Limbo pieces and you get:"),
                BLANK,
                body("- no knockback"),
                body("- no fire or fall damage"),
                body("- harmful effects are cleared"),
                body("- phantoms leave you alone"),
                body("- you walk through bedrock"),
                BLANK,
                body("Press G to fire the banish beam. It recharges for 30 seconds."))));

        out.add(new ArrayList<>(List.of(
                head("The Limbo Scythe"),
                BLANK,
                item(ModItems.LIMBO_SCYTHE.get(),
                        "Made from a Limbo Hoe, with amethyst, a golden apple, soul sand and a second template."),
                BLANK,
                body("It collects souls. Kill a creature with 20 hearts or more and the scythe keeps its soul. "
                        + "It holds eight."),
                BLANK,
                body("Spend a soul on a grave and the villager buried there comes back. Only defenders who "
                        + "fought ever get a grave."))));

        out.add(new ArrayList<>(List.of(
                head("The Obliterator"),
                BLANK,
                item(ModItems.LIMBO_SWORD.get(), "The Limbo Sword does 9 damage."),
                item(ModItems.OBLITERATOR.get(), "The Obliterator does 14."),
                BLANK,
                body("That is all it does, until an Obliteration Furnace is burning somewhere in the world."),
                BLANK,
                body("After that, every hit marks the target. If a marked creature dies, that species stops "
                        + "spawning for as long as the furnace stays lit."))));

        out.add(new ArrayList<>(List.of(
                head("What lives here"),
                BLANK,
                body("Limbo Phantoms. They are three times the normal size and they fly through bedrock."),
                BLANK,
                body("One will pick you up and carry you off through a wall."),
                BLANK,
                body("They hatch from the black and white eggs in the nests on the largest cubes. Break an egg "
                        + "without Silk Touch and you have made a phantom."),
                BLANK,
                body("Stay too long and you get Very Bad Omen. Carry it back to a village and the Limbo "
                        + "follows you."))));

        out.add(new ArrayList<>(List.of(
                head("The Limbo Sage"),
                BLANK,
                body("A villager in a black robe. He works at the Village Brain Node, and each village has "
                        + "only one."),
                BLANK,
                body("He sells Limbo goods at high prices, from simple blocks up to enchanted Limbo tools. He "
                        + "never sells armour."),
                BLANK,
                body("He can defend himself. He throws a Volatile Limbo Potion at anything above 10 hearts."))));

        out.add(new ArrayList<>(List.of(
                head("The air is not free"),
                BLANK,
                red("Ten seconds here and it starts."),
                BLANK,
                body("Slow Annihilation: Wither, and Levitation together. The Wither is what kills you. The "
                        + "Levitation is what stops you leaving, because you cannot walk to the bed while you "
                        + "are drifting off the floor."),
                BLANK,
                body("CROUCH. You come down exactly as fast as it takes you up. That is the way out."),
                BLANK,
                item(ModArmor.GAS_MASK.get(),
                        "A Gas Mask stops it completely, for as long as you wear it. It cannot be crafted. "
                        + "Villages keep them in their chests, rarely."),
                BLANK,
                red("Die with it and you do not simply die."),
                body("Something of the Limbo's own gets up where you fell."))));

        return out;
    }
}
