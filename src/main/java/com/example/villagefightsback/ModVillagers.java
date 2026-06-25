package com.example.villagefightsback;

import com.google.common.collect.ImmutableSet;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's villager content: the Limbo Sage profession and the POI it works at.
 *
 * <p>The Limbo Sage's job site is the {@link VillageBrainNodeBlock} itself -- the POI's matching states are ALL
 * of the brain node's blockstates. The POI is registered with {@code maxTickets = 1}, so a given brain node can
 * be claimed by exactly ONE villager: there is therefore at most one Limbo Sage per village (a village has a
 * single brain node cluster).</p>
 */
public final class ModVillagers {
    private ModVillagers() {}

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(ForgeRegistries.POI_TYPES, TheVillageFightsBack.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, TheVillageFightsBack.MODID);

    /** The POI's registry key, used by the profession predicates (a sage works/acquires only at this POI). */
    public static final ResourceKey<PoiType> LIMBO_SAGE_POI_KEY = ResourceKey.create(
            Registry.POINT_OF_INTEREST_TYPE_REGISTRY, new ResourceLocation(TheVillageFightsBack.MODID, "limbo_sage"));

    /**
     * The Limbo Sage POI: the brain node block. maxTickets = 1 (one sage per node = one per village),
     * validRange = 1 (the sage's job-site search radius around a claimed node).
     */
    public static final RegistryObject<PoiType> LIMBO_SAGE_POI = POI_TYPES.register("limbo_sage",
            () -> new PoiType(brainNodeStates(), 1, 1));

    /**
     * The Limbo Sage profession. It works at (and can only acquire) the Limbo Sage POI -- the brain node.
     * No requested items, no secondary POI; it uses the cleric work sound. Built with the record's canonical
     * constructor (the predicates test the POI Holder against {@link #LIMBO_SAGE_POI_KEY}).
     */
    public static final RegistryObject<VillagerProfession> LIMBO_SAGE = PROFESSIONS.register("limbo_sage",
            () -> new VillagerProfession("limbo_sage",
                    holder -> holder.is(LIMBO_SAGE_POI_KEY),
                    holder -> holder.is(LIMBO_SAGE_POI_KEY),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_CLERIC));

    /** Every blockstate of the Village Brain Node, so the POI matches the node in any of its charge levels. */
    private static ImmutableSet<BlockState> brainNodeStates() {
        return ImmutableSet.copyOf(
                ModBlocks.VILLAGE_BRAIN_NODE.get().getStateDefinition().getPossibleStates());
    }

    public static void register(IEventBus modBus) {
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
    }
}
