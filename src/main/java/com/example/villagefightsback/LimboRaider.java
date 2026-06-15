package com.example.villagefightsback;

/**
 * Marker for the Limbo raid mobs (Limbo Pillager / Vindicator / Evoker / Ravager / Vex): the ghostly,
 * black-and-white 2x versions of the vanilla raid roster. They render at {@link #SCALE}x and their hitbox is
 * baked at the same 2x in {@link ModEntities} (so model and box always match). They have 3x HP (set in their
 * attributes) and deal 3x damage, applied uniformly to every attack they cause (melee, crossbow, evoker fangs,
 * vex charge) by {@link LimboRaidEvents#onHurt}. They drop Forgotten Powder (the Evoker also drops totems);
 * only Limbo Phantoms drop Limbo Membrane.
 */
public interface LimboRaider {
    /** Every Limbo raider is twice the size of its vanilla counterpart (model render scale; hitbox baked to match). */
    float SCALE = 2.0F;
    /** Every attack a Limbo raider causes deals this many times the normal damage. */
    float DAMAGE_MULT = 3.0F;
}
