package com.example.villagefightsback.platform.event;

import com.example.villagefightsback.platform.Event;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;

/**
 * The trade pool for one profession, keyed by career level. Fired once per profession during setup, and the map
 * is the live one the mod adds its listings to.
 */
public class VillagerTradesEvent extends Event {
    private final Int2ObjectMap<List<VillagerTrades.ItemListing>> trades;
    private final VillagerProfession type;

    public VillagerTradesEvent(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades, VillagerProfession type) {
        this.trades = trades;
        this.type = type;
    }

    public Int2ObjectMap<List<VillagerTrades.ItemListing>> getTrades() {
        return this.trades;
    }

    public VillagerProfession getType() {
        return this.type;
    }
}
