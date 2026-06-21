package com.example.villagefightsback;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "Emerald Marker": the brain node paints a target with it to RALLY its faction -- every emerald unit within 100
 * blocks runs at the nearest marked target (see {@link EmeraldMarker} + {@code RallyToMarkedGoal}). Unlike the
 * mod's other (hidden) effects, this one is applied with particles VISIBLE on purpose, so the player can see which
 * mob the swarm is converging on. It does nothing on its own tick -- it is purely a beacon.
 */
public class EmeraldMarkerEffect extends MobEffect {
    public EmeraldMarkerEffect() {
        super(MobEffectCategory.NEUTRAL, 0x3CE03C); // bright emerald-green -> green marker particles
    }
}
