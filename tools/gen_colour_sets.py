"""
Generate the four NON-green colour sets of the fertile/rich block family.

A 100% green landscape is monotonous, so the same family exists in five flavours. Only the tint changes:
every set is the same blocks with the same behaviour, the same light level and the same speck layout, so a
purple zone and a green zone differ only in how they look.

Two things this deliberately does NOT do:

  * It never runs the green set. gen_fertile_blocks.py still emits the old silk-touch/emerald-ore loot for
    every rich_* block, but the green tables on disk were long ago hand-edited to plain self-drops. Re-running
    green would silently revert all 25 of them. Colour sets take write_loot_self instead, matching what green
    actually ships.
  * It never touches the one-off items (rich sheep, emerald apple, emerald tools, virus arrow). Those are
    single things, not part of the family; gen_fertile_blocks.main() returns before them when SUFFIX is set.

The emeralds in the rich blocks stay GREEN in every colour, and that is free rather than special-cased: the
gem overlay is composited AFTER the tint, carrying raw untinted emerald-ore pixels. Do not reorder that.

Usage:  python tools/gen_colour_sets.py
"""
import gen_fertile_blocks as g

# (suffix, tint, salt, salt_dim, pepper). The tint is the colour the luminance blend pushes toward; salt and
# pepper are the animated glow specks at their brightest and dimmest. Values are the green set's, rotated in
# hue and kept at the same saturation and brightness so no colour reads louder than another.
# (suffix, tint, salt, salt_dim, pepper, lift, gamma). lift is the brightness floor and gamma bends the
# mid-tones (<1 brightens); both default to the green set's values where they are not overridden.
#
# YELLOW is deliberately lifted well above the others. At the shared settings it and orange were separated
# only by hue, which is the one axis that reads worst on a dim, speckled texture seen across a field -- they
# looked like the same set. Yellow is now the bright one and orange the dark one, so they differ in value as
# well as hue and are told apart at a glance.
COLOURS = [
    ("_cyan",   (0.20, 0.95, 1.00), (120, 245, 255), (34, 106, 118), (12, 62, 74),  0.30, 0.80),
    ("_purple", (0.72, 0.28, 1.00), (198, 130, 255), (86, 40, 122),  (46, 14, 74),  0.30, 0.80),
    ("_orange", (1.00, 0.50, 0.10), (255, 170, 70),  (120, 66, 20),  (70, 32, 8),   0.24, 0.85),
    ("_yellow", (1.00, 0.98, 0.34), (255, 252, 170), (170, 160, 60), (104, 96, 24), 0.52, 0.62),
]


def main():
    green = (g.GREEN, g.SALT, g.SALT_DIM, g.PEPPER, g.SUFFIX, g.GREEN_LIFT, g.GREEN_GAMMA)
    try:
        for suffix, tint, salt, salt_dim, pepper, lift, gamma in COLOURS:
            g.SUFFIX = suffix
            g.GREEN = tint            # the blend target
            g.SALT = salt
            g.SALT_DIM = salt_dim
            g.PEPPER = pepper
            g.GREEN_LIFT = lift       # brightness floor: what separates yellow from orange
            g.GREEN_GAMMA = gamma
            print("=== %s ===" % suffix.lstrip("_"))
            g.main()
    finally:
        (g.GREEN, g.SALT, g.SALT_DIM, g.PEPPER, g.SUFFIX, g.GREEN_LIFT, g.GREEN_GAMMA) = green


if __name__ == "__main__":
    main()
