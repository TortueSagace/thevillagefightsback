package com.example.villagergunners.client;

import java.util.List;

import com.example.villagergunners.ModBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Client-side definition of the Village Gravemind upgrade tree shown in {@link GravemindScreen}: one row
 * per gravemind level, each row a list of upgrades drawn as item icons. Edit this table to add/change
 * upgrades -- the screen lays out whatever it finds here. {@code LEVELS.get(0)} is level 1, etc.
 */
public final class GravemindUpgrades {
    private GravemindUpgrades() {}

    /** One upgrade entry: the icon to draw, its name, and a short description (shown on hover). */
    public record Upgrade(ItemStack icon, Component name, Component desc) {
        public Upgrade(ItemStack icon, String name, String desc) {
            this(icon, Component.literal(name).withStyle(ChatFormatting.YELLOW),
                    Component.literal(desc).withStyle(ChatFormatting.GRAY));
        }
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
            // --- Level 2 (to be defined) ---
            List.of(),
            // --- Level 3 (to be defined) ---
            List.of()
    );

    /** Upgrades for the given gravemind level (1-based), or an empty list if none are defined. */
    public static List<Upgrade> forLevel(int level) {
        int i = level - 1;
        return (i >= 0 && i < LEVELS.size()) ? LEVELS.get(i) : List.of();
    }
}
