"""
Generate the Karat's Will mob-effect HUD icon: a small orange shield (matching the potion's orange tint),
with a lighter deflection chevron, so the beneficial effect shows a proper icon in the inventory / HUD.

Forge resolves a mob-effect icon from assets/<modid>/textures/mob_effect/<registry_path>.png, so this file
(karats_will.png) is picked up automatically for the thevillagefightsback:karats_will effect.

Output: src/main/resources/assets/thevillagefightsback/textures/mob_effect/karats_will.png  (18x18)
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "thevillagefightsback", "textures", "mob_effect", "karats_will.png")

SIZE = 18
OUTLINE = (74, 38, 8, 255)
ORANGE = (224, 128, 32, 255)
ORANGE_HI = (247, 184, 96, 255)
ORANGE_DK = (150, 78, 18, 255)
CHEVRON = (255, 226, 170, 255)


def main():
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = im.load()
    # Heater-shield shape: a rectangular top that tapers to a point near the bottom.
    for y in range(2, 16):
        if y <= 9:
            left, right = 3, 14
        else:
            inset = round((y - 9) / 6.0 * 5)
            left, right = 3 + inset, 14 - inset
        for x in range(left, right + 1):
            edge = (x == left or x == right or y == 2 or y == 15)
            if edge:
                px[x, y] = OUTLINE
            else:
                # vertical shading: lighter at the top, darker toward the point
                px[x, y] = ORANGE_HI if y <= 5 else (ORANGE if y <= 11 else ORANGE_DK)
    # A bold deflection chevron (>) across the middle, suggesting a bounced-back hit.
    for (x, y) in [(6, 5), (7, 6), (8, 7), (9, 8), (8, 9), (7, 10), (6, 11),
                   (9, 5), (10, 6), (11, 7), (12, 8), (11, 9), (10, 10), (9, 11)]:
        if px[x, y][3] != 0:
            px[x, y] = CHEVRON

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    im.save(OUT)
    print("wrote", os.path.normpath(OUT), im.size)


if __name__ == "__main__":
    main()
