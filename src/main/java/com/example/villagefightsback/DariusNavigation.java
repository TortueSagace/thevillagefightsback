package com.example.villagefightsback;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

/**
 * General Darius's navigation: a normal {@link GroundPathNavigation} whose path-finder is backed by the
 * {@link DariusNodeEvaluator}. The evaluator makes Darius PREFER to route around the village's own wall blocks
 * (cobblestone / obsidian / fake-gate doors) while treating every other solid block as walkable (he breaks it
 * on contact as he walks through). Set on the entity via {@code GeneralDariusEntity#createNavigation}.
 */
public class DariusNavigation extends GroundPathNavigation {
    public DariusNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new DariusNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }
}
