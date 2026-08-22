package com.example.villagefightsback;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * What a brain node stage costs in ITEMS, on top of its emeralds.
 *
 * <p>Until 1.7.2 this was three hardcoded methods -- {@code copperFor}, {@code goldFor}, {@code limboFor} --
 * and three matching {@code held.is(...)} tests at the node. That was already the shape the configurable
 * version wants (up to three item types per stage, each with a count), so this replaces the hardcoding
 * rather than adding a second system beside it.
 *
 * <h2>The config format</h2>
 * One list per stage transition, each entry {@code "<item id> <count>"}, at most three entries:
 * <pre>
 *   upgrade_items_stage_1_2 = ["minecraft:copper_ingot 8"]
 *   upgrade_items_stage_2_3 = ["minecraft:copper_ingot 8", "minecraft:gold_ingot 8"]
 *   upgrade_items_stage_3_4 = ["thevillagefightsback:limbo_ingot 1"]
 * </pre>
 * which are the defaults, and are exactly what the hardcoded version charged. An empty list means that stage
 * costs emeralds alone.
 *
 * <h2>Parsing</h2>
 * Parsed on demand and cached against the config's own list objects, so a {@code /thevillagefightsback config}
 * change is picked up without a restart and without re-parsing on every right-click. A malformed entry or an
 * unknown item id is logged once and skipped -- a typo in a config file should cost you one requirement, not
 * the ability to upgrade at all.
 */
public final class UpgradeTribute {
    private UpgradeTribute() {}

    /** At most this many distinct items per stage, as specified. */
    public static final int MAX_ENTRIES = 3;

    /** One requirement: an item, and how many of it. */
    public record Need(Item item, int count) {
        public String label() {
            return count + " " + new ItemStack(item).getHoverName().getString();
        }
    }

    private static final List<List<Need>> CACHE = new ArrayList<>(List.of(new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
    private static final List<Object> CACHE_KEY = new ArrayList<>(java.util.Collections.nCopies(5, null));

    /**
     * What the stage AFTER {@code level} costs. Levels run 1..3; anything else costs nothing.
     *
     * <p>Indexed by the level being left, so {@code needsFor(1)} is the 1 -> 2 tribute, which is how the old
     * {@code copperFor(lvl)} read too.
     */
    public static List<Need> needsFor(int level) {
        if (level < 1 || level > 3) return List.of();
        List<? extends String> raw = switch (level) {
            case 1 -> Config.UPGRADE_ITEMS_1_2.get();
            case 2 -> Config.UPGRADE_ITEMS_2_3.get();
            default -> Config.UPGRADE_ITEMS_3_4.get();
        };
        // Identity, not equality: the config library hands back the same list object until something writes
        // to it, so this re-parses exactly when the value actually changed.
        if (CACHE_KEY.get(level) != raw) {
            CACHE.set(level, parse(raw));
            CACHE_KEY.set(level, raw);
        }
        return CACHE.get(level);
    }

    private static List<Need> parse(List<? extends String> raw) {
        List<Need> out = new ArrayList<>();
        for (String entry : raw) {
            if (out.size() >= MAX_ENTRIES) {
                TheVillageFightsBack.LOGGER.warn(
                        "[The Village Fights Back] a brain node stage lists more than {} tribute items; "
                        + "ignoring the rest.", MAX_ENTRIES);
                break;
            }
            String[] parts = entry.trim().split("\\s+");
            if (parts.length != 2) {
                TheVillageFightsBack.LOGGER.warn(
                        "[The Village Fights Back] cannot read the upgrade tribute \"{}\"; "
                        + "it should look like \"minecraft:copper_ingot 8\". Skipping it.", entry);
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(parts[0]);
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item == null || id == null) {
                TheVillageFightsBack.LOGGER.warn(
                        "[The Village Fights Back] no such item \"{}\" for an upgrade tribute; skipping it.",
                        parts[0]);
                continue;
            }
            int count;
            try {
                count = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                TheVillageFightsBack.LOGGER.warn(
                        "[The Village Fights Back] \"{}\" is not a count in the upgrade tribute \"{}\"; "
                        + "skipping it.", parts[1], entry);
                continue;
            }
            if (count > 0) out.add(new Need(item, count));
        }
        return out;
    }

    /** The item the dead brain node wants, and anything else the config says also works. */
    public static boolean isRevivingItem(ItemStack stack) {
        for (String s : Config.REVIVING_ITEMS.get()) {
            ResourceLocation id = ResourceLocation.tryParse(s.trim());
            if (id != null && stack.is(ForgeRegistries.ITEMS.getValue(id))) return true;
        }
        return false;
    }

    /** What to call it in a message, so the prompts never name an item the config no longer asks for. */
    public static String revivingItemName() {
        List<? extends String> ids = Config.REVIVING_ITEMS.get();
        if (ids.isEmpty()) return "nothing";
        ResourceLocation id = ResourceLocation.tryParse(ids.get(0).trim());
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return item == null ? ids.get(0) : new ItemStack(item).getHoverName().getString().toLowerCase();
    }
}
