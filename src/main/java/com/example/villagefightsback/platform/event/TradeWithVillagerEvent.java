package com.example.villagefightsback.platform.event;

import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;

/** A player completed a trade. */
public class TradeWithVillagerEvent extends PlayerEvent {
    private final MerchantOffer offer;
    private final AbstractVillager villager;

    public TradeWithVillagerEvent(Player player, MerchantOffer offer, AbstractVillager villager) {
        super(player);
        this.offer = offer;
        this.villager = villager;
    }

    public MerchantOffer getMerchantOffer() {
        return this.offer;
    }

    public AbstractVillager getAbstractVillager() {
        return this.villager;
    }
}
