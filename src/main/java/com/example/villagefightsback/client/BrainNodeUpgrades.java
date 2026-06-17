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
                    new Upgrade(new ItemStack(ModBlocks.FAKE_COBBLESTONE_ITEM.get()),
                            "Wall Building", "Villagers build a defensive wall around the village."),
                    new Upgrade(new ItemStack(Items.IRON_SWORD),
                            "Guards", "Guard villagers patrol and defend the village."),
                    new Upgrade(new ItemStack(ModBlocks.GOLEM_FACTORY_NODE_ITEM.get()),
                            "Iron Factory", "The brain node can craft Iron Factory nodes, each spawning an iron "
                            + "golem below it every sunset.")
            ),
            // --- Level 2 ---
            List.of(
                    new Upgrade(ModEntities.WALL_DEFENDER.get(), new ItemStack(ModBlocks.WALL_DEFENDER_SPAWN.get()),
                            "Wall Defenders", "Conduit turrets rise in protecting-glass shells above the gates "
                            + "and gun down attackers."),
                    new Upgrade(new ItemStack(ModBlocks.PROTECTING_GLASS_ITEM.get()),
                            "Defender Golems", "Village iron golems evolve into Defender Golems: a wall defender "
                            + "sealed in a protecting-glass cocoon for a head, 150 HP, with a golem punch AND a "
                            + "turret that guns down attackers from range."),
                    new Upgrade(new ItemStack(ModBlocks.BARRACKS_NODE_ITEM.get()),
                            "Barracks", "The brain node can craft Barracks nodes, each mustering a squad of "
                            + "stage-appropriate guards every sunset."),
                    new Upgrade(new ItemStack(Items.EMERALD),
                            "Fertilization", "Dirt paths spread and fertilize the land into Fertile/Rich blocks; "
                            + "miner villagers dig the Rich ones for emeralds to feed the brain node.")
            ),
            // --- Level 3 ---
            List.of(
                    new Upgrade(new ItemStack(com.example.villagefightsback.ModItems.ALIEN_SLASHER.get()),
                            "Advanced Weaponry",
                            "The battle power of villagers, guards and golems is strongly improved."),
                    new Upgrade(new ItemStack(ModBlocks.FAKE_OBSIDIAN_ITEM.get()),
                            "Obsidian Wall", "A second, obsidian-tough outer wall rises around the whole village, "
                            + "with its own gate turrets."),
                    new Upgrade(new ItemStack(ModBlocks.GREEN_TNT_ITEM.get()),
                            "Expansion", "The village outgrows its wall: a wave of settlers streams out and "
                            + "founds a ring of new houses and towers beyond it."),
                    new Upgrade(new ItemStack(ModBlocks.LURE_TOWER_NODE_ITEM.get()),
                            "Lure Tower", "The brain node can craft Lure Tower nodes: a powder-snow-and-lava trap "
                            + "topped by a wall defender that lures attackers in and guns them down.")
            ),
            // --- Level 4 ---
            List.of(
                    new Upgrade(new ItemStack(ModBlocks.REINFORCED_GLASS_ITEM.get()),
                            "Glass Dome", "A reinforced-glass dome rises over the whole village, sealing it from "
                            + "aerial attacks."),
                    new Upgrade(ModEntities.WALL_DEFENDER_STAGE4.get(),
                            new ItemStack(ModBlocks.WALL_DEFENDER_STAGE4_SPAWN.get()),
                            "Rocket Defenders", "Wall defenders are upgraded to launch rockets like the Gigachad "
                            + "Golems -- one every two seconds, with a revolver fallback at point-blank range."),
                    new Upgrade(new ItemStack(com.example.villagefightsback.ModItems.ROCKET_LAUNCHER.get()),
                            "Gigachad Golems", "Defender Golems evolve into Gigachad Golems: gold-plated, 200 HP, "
                            + "and armed with a rocket launcher that blasts attackers from range."),
                    new Upgrade(new ResourceLocation(TheVillageFightsBack.MODID, "textures/gui/emeraldger_face.png"),
                            "Emeraldgers", "The Emerald Virus evolves: it inflicts Wither II, and every host that "
                            + "dies and explodes rises again as an Emeraldger -- a green-eyed warrior that grows "
                            + "with the strength of what it killed."),
                    new Upgrade(new ResourceLocation(TheVillageFightsBack.MODID, "textures/gui/air_strike.png"),
                            "Air Strikes", "When the village is attacked, every 10 minutes the brain node calls "
                            + "down a rain of 27 rockets from the sky onto the strongest enemy in range.")
            )
    );

    /** Upgrades for the given brainNode level (1-based), or an empty list if none are defined. */
    public static List<Upgrade> forLevel(int level) {
        int i = level - 1;
        return (i >= 0 && i < LEVELS.size()) ? LEVELS.get(i) : List.of();
    }
}
