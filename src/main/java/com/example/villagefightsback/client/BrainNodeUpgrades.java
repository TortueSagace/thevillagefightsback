package com.example.villagefightsback.client;

import java.util.List;

import com.example.villagefightsback.ModBlocks;
import com.example.villagefightsback.ModEntities;
import com.example.villagefightsback.TheVillageFightsBack;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Client-side definition of the Village Brain Node upgrade tree shown in {@link BrainNodeScreen}: one row
 * per brainNode level, each row a list of upgrades drawn as icons. Edit this table to add/change upgrades --
 * the screen lays out whatever it finds here. {@code LEVELS.get(0)} is level 1, etc.
 */
public final class BrainNodeUpgrades {
    private BrainNodeUpgrades() {}

    /**
     * One upgrade entry. The icon is, in priority order: a live ENTITY ({@code entity}, e.g. the spinning wall
     * defender) drawn in the slot, else a 16x16 GUI TEXTURE ({@code texture}, e.g. the Emeraldger face), else
     * an ITEM ({@code icon}). For an entity icon, {@code icon} doubles as the fallback if entity rendering is
     * unavailable. Plus a name and hover description.
     */
    public record Upgrade(ItemStack icon, ResourceLocation texture, EntityType<?> entity,
                          Component name, Component desc) {
        /** Item-icon upgrade. */
        public Upgrade(ItemStack icon, String name, String desc) {
            this(icon, null, null, lit(name), gray(desc));
        }
        /** Texture-icon upgrade (e.g. the Emeraldger face). */
        public Upgrade(ResourceLocation texture, String name, String desc) {
            this(ItemStack.EMPTY, texture, null, lit(name), gray(desc));
        }
        /** Live-entity-icon upgrade (e.g. the wall defender); {@code fallback} is shown if it can't render. */
        public Upgrade(EntityType<?> entity, ItemStack fallback, String name, String desc) {
            this(fallback, null, entity, lit(name), gray(desc));
        }
        private static Component lit(String s) { return Component.literal(s).withStyle(ChatFormatting.YELLOW); }
        private static Component gray(String s) { return Component.literal(s).withStyle(ChatFormatting.GRAY); }
    }

    /** Rows indexed by (level - 1). Add upgrades to a level by appending to its list. */
    public static final List<List<Upgrade>> LEVELS = List.of(
            // --- Level 1 ---
            List.of(
                    new Upgrade(new ItemStack(Items.IRON_SWORD),
                            "Guards", "Guard villagers patrol and defend the village."),
                    new Upgrade(new ItemStack(ModBlocks.FAKE_COBBLESTONE_ITEM.get()),
                            "Wall Building", "Villagers build a defensive wall around the village."),
                    new Upgrade(new ItemStack(ModBlocks.GOLEM_FACTORY_NODE_ITEM.get()),
                            "Iron Factory", "Iron Factory nodes (craftable now) spawn an iron golem every sunset.")
            ),
            // --- Level 2 ---
            List.of(
                    new Upgrade(ModEntities.WALL_DEFENDER.get(), new ItemStack(ModBlocks.WALL_DEFENDER_SPAWN.get()),
                            "Wall Defenders", "Turrets in protecting-glass shells rise above the gates and gun down attackers."),
                    new Upgrade(new ItemStack(ModBlocks.PROTECTING_GLASS_ITEM.get()),
                            "Defender Golems", "Iron golems become Defender Golems with 150 HP, a golem punch and a built-in turret."),
                    new Upgrade(new ItemStack(ModBlocks.BARRACKS_NODE_ITEM.get()),
                            "Barracks", "Barracks nodes (craftable now) muster a squad of guards every sunset."),
                    new Upgrade(new ItemStack(Items.EMERALD),
                            "Fertilization", "Paths fertilize the land; miners dig the Rich blocks for emeralds.")
            ),
            // --- Level 3 ---
            List.of(
                    new Upgrade(new ItemStack(com.example.villagefightsback.ModItems.ALIEN_SLASHER.get()),
                            "Advanced Weaponry", "Villagers, guards and golems hit much harder."),
                    new Upgrade(new ItemStack(ModBlocks.FAKE_OBSIDIAN_ITEM.get()),
                            "Obsidian Wall", "An obsidian-tough outer wall rises, with its own gate turrets."),
                    new Upgrade(new ItemStack(ModBlocks.LURE_TOWER_NODE_ITEM.get()),
                            "Lure Tower", "Lure Tower nodes trap attackers in powder snow and lava under a turret."),
                    new Upgrade(new ItemStack(ModBlocks.GREEN_TNT_ITEM.get()),
                            "Expansion", "Settlers stream out and found new houses and towers beyond the wall.")
            ),
            // --- Level 4 ---
            List.of(
                    new Upgrade(ModEntities.WALL_DEFENDER_STAGE4.get(),
                            new ItemStack(ModBlocks.WALL_DEFENDER_STAGE4_SPAWN.get()),
                            "Rocket Defenders", "Wall defenders fire rockets every two seconds, switching to gunfire at point-blank range."),
                    new Upgrade(new ItemStack(ModBlocks.REINFORCED_GLASS_ITEM.get()),
                            "Glass Dome", "A reinforced-glass dome seals the village from aerial attacks."),
                    new Upgrade(new ItemStack(com.example.villagefightsback.ModItems.ROCKET_LAUNCHER.get()),
                            "Gigachad Golems", "Defender Golems become gold-plated Gigachad Golems with 200 HP and a rocket launcher."),
                    new Upgrade(TheVillageFightsBack.id("textures/gui/emeraldger_face.png"),
                            "Emeraldgers", "Emerald Virus inflicts Wither II; dead hosts rise as Emeraldgers that grow with each kill."),
                    new Upgrade(TheVillageFightsBack.id("textures/gui/air_strike.png"),
                            "Air Strikes", "When attacked, the brain node rains 27 rockets on the strongest nearby enemy.")
            )
    );

    /** Upgrades for the given brainNode level (1-based), or an empty list if none are defined. */
    public static List<Upgrade> forLevel(int level) {
        int i = level - 1;
        return (i >= 0 && i < LEVELS.size()) ? LEVELS.get(i) : List.of();
    }
}
