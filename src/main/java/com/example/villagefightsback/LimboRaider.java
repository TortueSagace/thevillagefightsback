package com.example.villagefightsback;

/**
 * Marker for the Limbo raid mobs (Limbo Pillager / Vindicator / Evoker / Ravager / Vex): the ghostly,
 * green-glowing 3x versions of the vanilla raid roster. They are scaled {@link #SCALE}x, have 3x HP (set in
 * their attributes), and deal 3x damage -- the damage multiplier is applied uniformly to every attack they
 * cause (melee, crossbow, evoker fangs, vex charge) by {@link LimboRaidEvents#onHurt}. They drop Forgotten
 * Powder (the Evoker also drops totems); only Limbo Phantoms drop Limbo Membrane.
 */
public interface LimboRaider {
    /** Every Limbo raider is three times the size of its vanilla counterpart (body + hitbox). */
    float SCALE = 3.0F;
    /** Every attack a Limbo raider causes deals this many times the normal damage. */
    float DAMAGE_MULT = 3.0F;
}
