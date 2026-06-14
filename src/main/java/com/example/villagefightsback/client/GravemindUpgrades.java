package com.example.villagefightsback.client;

import java.util.List;

import com.example.villagefightsback.ModBlocks;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Client-side definition of the Village Gravemind upgrade tree shown in {@link GravemindScreen}: one row
 * per gravemind level, each row a list of upgrades drawn as item icons. Edit this table to add/change
 * upgrades -- the screen lays out whatever it finds here. {@code LEVELS.get(0)} is level 1, etc.
 */
public final class GravemindUpgrades {
    private GravemindUpgrades() {}

    /**
     * One upgrade entry. The icon is EITHER an item ({@code icon}) OR a 16x16 GUI texture ({@code texture},
     * e.g. the Emeraldger face) -- whichever is non-empty/non-null. Plus a name and hover description.
     */
    public record Upgrade(ItemStack icon, ResourceLocation texture, Component name, Component desc) {
        /** Item-icon upgrade. */
        public Upgrade(ItemStack icon, String name, String desc) {
            this(icon, null, lit(name), gray(desc));
        }
        /** Texture-icon upgrade (e.g. the Emeraldger face). */
        public Upgrade(ResourceLocation texture, String name, String desc) {
            this(ItemStack.EMPTY, texture, lit(name), gray(desc));
        }
        private static Component lit(String s) { return Component.literal(s).withStyle(ChatFormatting.YELLOW); }
        private static Component gray(String s) { return Component.literal(s).withStyle(ChatFormatting.GRAY); }
    }

    /** Rows indexed by (level - 1). Add upgrades to a level by appending to its list. */
    public static final List<List<Upgrade>> LEVELS = List.of(
            // --- Level 1 ---
            List.of(
                    new Upgrade(new ItemStack(ModBlocks.FAKE_COBBLESTONE_ITEM.get()),
                            "Wall Building", "Villagers build a defensive wall around the village."),
                    new Upgrade(new ItemStack(Items.IRON_SWORD),
                            "Guards", "Guard villagers patrol and defend the village."),
                    new Upgrade(new ItemStack(Items.STONE_PICKAXE),
                            "Basic Tools", "Villagers carry basic tools to work and fight.")
            ),
            // --- Level 2 ---
            List.of(
                    new Upgrade(new ItemStack(ModBlocks.WALL_DEFENDER_SPAWN.get()),
                            "Wall Defenders", "Conduit turrets rise in protecting-glass shells above the gates "
                            + "and gun down attackers."),
                    new Upgrade(new ItemStack(Items.EMERALD),
                            "Fertilization", "Dirt paths spread and fertilize the land into Fertile/Rich blocks; "
                            + "miner villagers dig the Rich ones for emeralds to feed the gravemind.")
            ),
            // --- Level 3 ---
            List.of(
                    new Upgrade(new ItemStack(com.example.villagefightsback.ModItems.ALIEN_SLASHER.get()),
                            "Advanced Weaponry",
                            "The battle power of villagers, guards and golems is strongly improved."),
                    new Upgrade(new ItemStack(ModBlocks.GREEN_TNT_ITEM.get()),
                            "Expansion", "The village outgrows its wall: a wave of settlers streams out and "
                            + "founds a ring of new houses and towers beyond it.")
            ),
            // --- Level 4 ---
            List.of(
                    new Upgrade(new ItemStack(ModBlocks.REINFORCED_GLASS_ITEM.get()),
                            "Glass Dome", "A reinforced-glass dome rises over the whole village, sealing it from "
                            + "aerial attacks."),
                    new Upgrade(new ResourceLocation(TheVillageFightsBack.MODID, "textures/gui/emeraldger_face.png"),
                            "Emeraldgers", "The Emerald Virus evolves: it inflicts Wither II, and every host that "
                            + "dies and explodes rises again as an Emeraldger -- a green-eyed warrior that grows "
                            + "with the strength of what it killed.")
            )
    );

    /** Upgrades for the given gravemind level (1-based), or an empty list if none are defined. */
    public static List<Upgrade> forLevel(int level) {
        int i = level - 1;
        return (i >= 0 && i < LEVELS.size()) ? LEVELS.get(i) : List.of();
    }
}
