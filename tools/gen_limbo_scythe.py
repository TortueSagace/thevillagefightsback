"""
Generate the Limbo Scythe's item texture.

The scythe started life as a copy of the Limbo hoe's texture, which made it indistinguishable from the hoe in
the hotbar and in the creative menu. It gets its own silhouette here: a long snath running bottom-left to
top-right with a curved blade sweeping off the top, so it reads as a reaper's scythe at 16x16.

Colours are taken from the existing Limbo gear (dark greens) so it still belongs to the set, with a bright edge
on the blade. The item is drawn WITHOUT an animation strip, unlike the rest of the Limbo tools: it renders with
the enchantment glint (LimboScytheItem.isFoil), and a shimmering animation underneath the glint just read as
noise.

Output: src/main/resources/assets/thevillagefightsback/textures/item/limbo_scythe.png  (16x16)
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback",
                   "textures", "item", "limbo_scythe.png")

# Sampled from the existing Limbo tools so the scythe matches the set.
DARK = (10, 26, 19, 255)      # deepest shadow / outline
WOOD = (34, 27, 18, 255)      # the snath
WOOD_HI = (58, 47, 32, 255)   # its lit edge
BLADE = (25, 97, 46, 255)     # limbo green
BLADE_HI = (130, 226, 158, 255)  # the cutting edge
BLADE_SH = (16, 55, 30, 255)  # the blade's shaded back

# The snath: bottom-right up to the neck. (x, y) with y growing downward.
SNATH = [(11, 15), (11, 14), (11, 13), (10, 12), (10, 11), (10, 10),
         (9, 9), (9, 8), (9, 7), (8, 6), (8, 5)]
SNATH_HI = [(12, 14), (12, 13), (11, 11), (11, 10), (10, 8), (10, 7), (9, 5)]

# The blade: a curve sweeping left off the top of the snath.
BLADE_BACK = [(8, 4), (7, 3), (6, 3), (5, 3), (4, 4), (3, 5), (3, 6)]
BLADE_EDGE = [(8, 3), (7, 2), (6, 2), (5, 2), (4, 3), (3, 4), (2, 5), (2, 6), (3, 7)]
BLADE_FILL = [(7, 4), (6, 4), (5, 4), (4, 5), (4, 6)]


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    for x, y in BLADE_SH_PIXELS():
        px[x, y] = BLADE_SH
    for x, y in BLADE_FILL:
        px[x, y] = BLADE
    for x, y in BLADE_EDGE:
        px[x, y] = BLADE_HI
    for x, y in SNATH:
        px[x, y] = WOOD
    for x, y in SNATH_HI:
        px[x, y] = WOOD_HI
    # A dark heel where blade meets snath, so the join reads.
    px[8, 4] = DARK

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", OUT, img.size)


def BLADE_SH_PIXELS():
    return BLADE_BACK


if __name__ == "__main__":
    main()
