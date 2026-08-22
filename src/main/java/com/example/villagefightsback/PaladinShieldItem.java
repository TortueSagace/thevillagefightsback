package com.example.villagefightsback;

/**
 * The Paladin Shield: the Limbo Shield, plus consequences.
 *
 * <p>Everything the {@link LimboShieldItem} does -- the durability, the bash, the Limbo Membrane repair --
 * and on top of it, in {@link ShieldBashEvents}:
 * <ul>
 *   <li><b>Half the blow goes home.</b> The attacker takes 50% of what they swung, their own damage source,
 *       their own fault. This is the Special Sauce's reflection at half strength (see
 *       {@link DariusSpecialEffectEvents}) rather than a second implementation of the same idea -- the
 *       Sauce returns 100% and protects its holder completely; the shield returns half and still lets the
 *       blocked hit through as a normal block.</li>
 *   <li><b>Arrows too</b>, at the same 50%, but without the bash. Shoving something you cannot reach is
 *       incoherent, and a bow at forty blocks would simply never see it.</li>
 *   <li><b>The bearer is fed.</b> Any successful block grants the enchanted-golden-apple package. It is
 *       granted to whoever is HOLDING it, not to a player specifically, so General Darius -- who carries one
 *       as standard from 1.7.2 -- benefits from his own shield exactly as a player does.</li>
 * </ul>
 *
 * <p>It is a subclass rather than a flag on the base item because the two are separate registry entries with
 * separate recipes and separate art, and every behavioural difference is then a plain {@code instanceof} at
 * the one place that cares.
 */
public class PaladinShieldItem extends LimboShieldItem {
    /** How much of the attacker's own blow comes back at them. */
    public static final float REFLECT = 0.5F;

    public PaladinShieldItem(Properties properties) {
        super(properties);
    }
}
