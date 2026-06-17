"""
Generate General Darius's skin: a plain HUMAN (player-model) texture so he renders as a man, not a zombie.

Darius always wears the full Limbo set (helmet + chestplate + leggings + boots, never dropped), so in practice
only his hands ever show -- the helmet hides his face/hair and the armour hides body/arms/legs. We still paint a
simple human head (skin, brown hair, a plain face) so he looks right if the armour is ever stripped. The whole
skin is opaque skin-tone; clothing is unnecessary since the armour covers it.

Output: src/main/resources/assets/thevillagefightsback/textures/entity/general_darius.png  (64x64)
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "thevillagefightsback", "textures", "entity", "general_darius.png")

SKIN = (224, 172, 138, 255)
SKIN_SHADE = (198, 148, 116, 255)
HAIR = (74, 48, 28, 255)
HAIR_HI = (96, 64, 38, 255)
EYE = (40, 30, 24, 255)
BROW = (60, 40, 26, 255)
MOUTH = (150, 92, 78, 255)


def rect(px, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = c


def main():
    im = Image.new("RGBA", (64, 64), SKIN)  # whole body opaque skin-tone (hands are all that show)
    px = im.load()

    # --- head net (base layer, 0..31 x, 0..15 y) ---
    # Top of head + a touch of the upper side/back faces = hair.
    rect(px, 8, 0, 15, 7, HAIR)            # top
    rect(px, 8, 0, 15, 0, HAIR_HI)         # a lighter front fringe row on the top
    # Front face (8,8)-(15,15): hairline, a single villager UNIBROW, eyes, nose shading, mouth -- a villager
    # face, not a player's (Darius is a villager guard, just non-zombie).
    rect(px, 8, 8, 15, 9, HAIR)            # fringe over the forehead
    rect(px, 9, 10, 14, 10, BROW)          # villager unibrow (one connected bar)
    rect(px, 9, 11, 10, 11, EYE)           # left eye
    rect(px, 13, 11, 14, 11, EYE)          # right eye
    px[11, 12] = SKIN_SHADE                # nose-bridge shading (the 3D nose is model geometry)
    px[12, 12] = SKIN_SHADE
    rect(px, 10, 14, 13, 14, MOUTH)        # mouth
    # Side faces (right 0-7, left 16-23) and back (24-31): hair across the top three rows.
    rect(px, 0, 8, 7, 10, HAIR)            # right side hair
    rect(px, 16, 8, 23, 10, HAIR)          # left side hair
    rect(px, 24, 8, 31, 11, HAIR)          # back hair (a bit lower)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    im.save(OUT)
    print("wrote", os.path.normpath(OUT), im.size)


if __name__ == "__main__":
    main()
