package com.example.villagergunners;

/**
 * "Fake obsidian": the stage-3 OUTER wall's gate block. Behaves exactly like {@link FakeCobblestoneBlock}
 * (a smart door -- players, villagers, iron golems and guards phase through while everyone else hits a solid
 * wall, and allied AI paths through it), but it is registered with obsidian toughness, so attackers can't
 * smash the gate. Subclassing keeps {@code below.is(this)} in the collision logic pointing at this block.
 */
public class FakeObsidianBlock extends FakeCobblestoneBlock {
    public FakeObsidianBlock(Properties properties) {
        super(properties);
    }
}
