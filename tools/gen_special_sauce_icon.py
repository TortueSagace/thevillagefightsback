"""
Generate the "Darius Special Power" mob-effect HUD icon from a Reverse Uno card.

The effect reverses everything done to its holder (damage, status effects, drowning, fire, lava,
cactus, suffocation, fall, the void), so the Reverse Uno card is the clearest possible shorthand for
what it does. We crop the card's central double-arrow -- the part that reads at icon size -- and
downscale it; the card border, corner pips and lettering all disappear.

Forge resolves a mob-effect icon from assets/<modid>/textures/mob_effect/<registry_path>.png. The
registry path is still "karats_will" (the id is deliberately frozen for save compatibility, only the
display name changed), so this writes karats_will.png despite the new name.

The mod's other eight effect icons are 18x18; this one is 32x32 on purpose. Effect icons are stitched
into an atlas and blitted into an 18x18 slot whatever their source size, so the extra resolution just
makes the arrows legible instead of mushy.

Source image is not committed -- the generated PNG is the artifact of record. Pass a path as argv[1]
if you need to regenerate from a different copy.

Output: src/main/resources/assets/thevillagefightsback/textures/mob_effect/karats_will.png  (32x32)
"""
import os
import sys
from PIL import Image

SOURCE = sys.argv[1] if len(sys.argv) > 1 else r"reverse uno.jpg"

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "thevillagefightsback", "textures", "mob_effect", "karats_will.png")

SIZE = 32
# Centre of the card's middle arrow glyph, and the crop box around it, in source pixels. Tuned by eye
# against the 736x1118 card: 440 is the widest square that still keeps the white ellipse out of the
# corners, and the tightest that does not clip the arrowheads.
CENTRE = (360, 560)
CROP = 440


def main():
    src = Image.open(SOURCE).convert("RGB")
    cx, cy = CENTRE
    half = CROP // 2
    box = (cx - half, cy - half, cx + half, cy + half)
    icon = src.crop(box).resize((SIZE, SIZE), Image.LANCZOS).convert("RGBA")
    out = os.path.normpath(OUT)
    icon.save(out)
    print("wrote %s (%dx%d)" % (out, SIZE, SIZE))


if __name__ == "__main__":
    main()
