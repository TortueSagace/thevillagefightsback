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
COLOURS = [
    ("_cyan",   (0.20, 0.95, 1.00), (120, 245, 255), (34, 106, 118), (12, 62, 74)),
    ("_purple", (0.72, 0.28, 1.00), (198, 130, 255), (86, 40, 122),  (46, 14, 74)),
    ("_orange", (1.00, 0.55, 0.16), (255, 178, 90),  (128, 74, 26),  (78, 38, 10)),
    ("_yellow", (1.00, 0.94, 0.20), (255, 246, 120), (130, 120, 30), (78, 70, 12)),
]


def main():
    green = (g.GREEN, g.SALT, g.SALT_DIM, g.PEPPER, g.SUFFIX)
    try:
        for suffix, tint, salt, salt_dim, pepper in COLOURS:
            g.SUFFIX = suffix
            g.GREEN = tint            # the blend target; GREEN_BLEND/LIFT/GAMMA stay as they are
            g.SALT = salt
            g.SALT_DIM = salt_dim
            g.PEPPER = pepper
            print("=== %s ===" % suffix.lstrip("_"))
            g.main()
    finally:
        g.GREEN, g.SALT, g.SALT_DIM, g.PEPPER, g.SUFFIX = green


if __name__ == "__main__":
    main()
