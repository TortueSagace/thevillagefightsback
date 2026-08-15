"""
Generate the Obliterator's item texture from the reference art.

Same trace as the Limbo Scythe (see gen_limbo_scythe.py) and for the same reason: hand-drawing a blade onto a
pixel grid is guesswork, so the silhouette is taken mechanically from `obliterator_ref.png` and only the
COLOURS are ours.

Two differences from the scythe's trace, both forced by this particular reference:

  1. Its transparency is a BAKED-IN CHECKERBOARD, not alpha -- the file is fully opaque, and the "empty" area
     is two greys alternating. Keying on white alone would have left the darker squares behind as a grid of
     confetti, so the key takes any near-neutral pixel above both greys.
  2. The art is a tall rectangle and the blade runs corner to corner. Resizing that straight to a square would
     squash the diagonal, so the keyed art is cropped to its content and padded to a square FIRST, and only
     then resampled. The blade keeps its 45 degrees.

The palette is the Obliteration violet rather than the Limbo green. The Obliterator is the only weapon that
inflicts Obliteration, whose particles are purple, and a player should be able to tell it from the scythe in a
full hotbar without reading the tooltip. The reference's own colours map onto it cleanly: its cyan/blue blade
becomes the violet body, its bright core stripe becomes the amethyst edge, and its grey hilt stays neutral
metal so the weapon still reads as a sword rather than a glowing bar.

Output: src/main/resources/assets/thevillagefightsback/textures/item/obliterator.png  (32x256, 8 frames)
        plus its .mcmeta animation strip
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REF = os.path.join(ROOT, "tools", "obliterator_ref.png")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback",
                   "textures", "item", "obliterator.png")

GRID = 32
ALPHA_CUT = 0.42
FRAMES = 8
SPECKS_PER_FRAME = 14

OUTLINE = (14, 6, 24, 255)      # near-black violet
BLADE_SH = (48, 22, 78, 255)    # deep violet shadow
BLADE = (94, 44, 148, 255)      # violet body
BLADE_HI = (186, 132, 255, 255) # lit edge
CORE = (232, 196, 255, 255)     # the reference's bright core stripe
HILT = (48, 46, 56, 255)        # neutral metal
HILT_HI = (128, 126, 140, 255)
SALT = (206, 168, 255, 255)
PEPPER = (86, 42, 140, 255)
MCMETA = '{"animation": {"frametime": 3, "interpolate": false}}' + chr(10)


def principal_angle(img):
    """The angle of the blade's long axis, by PCA over the opaque pixels."""
    px = img.load()
    xs, ys = [], []
    for y in range(img.height):
        for x in range(img.width):
            if px[x, y][3] > 0:
                xs.append(x)
                ys.append(y)
    n = len(xs)
    mx, my = sum(xs) / n, sum(ys) / n
    sxx = sum((x - mx) ** 2 for x in xs) / n
    syy = sum((y - my) ** 2 for y in ys) / n
    sxy = sum((xs[i] - mx) * (ys[i] - my) for i in range(n)) / n
    return math.degrees(0.5 * math.atan2(2 * sxy, sxx - syy))


def keyed_reference():
    """
    Reference with the checkerboard removed, ROTATED onto the conventional blade axis, then squared.

    <p>The rotation is the fix for a weapon that looked wrong in the hand. Minecraft draws a held item along
    the sprite's bottom-left to top-right diagonal, so every vanilla tool is drawn climbing at roughly 45
    degrees and the hand transform lines the blade up with the arm. This reference is a TALL rectangle: padding
    it to a square (which is what the first version did) preserved its proportions but left the blade sitting
    at a much shallower angle than vanilla's, and the result read as a sword being held sideways and drooping
    rather than pointed. Measured against the diamond sword, its tip climbed barely half as far.

    So the art is rotated so its long axis lands on that diagonal before anything else happens. PCA finds the
    axis rather than a hard-coded angle, which means this keeps working if the reference is ever swapped for
    another weapon drawn at a different tilt.
    """
    src = Image.open(REF).convert("RGBA")
    w, h = src.size
    keyed = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sp, kp = src.load(), keyed.load()
    for y in range(h):
        for x in range(w):
            r, g, b, _ = sp[x, y]
            if r > 232 and g > 232 and b > 232 and abs(r - g) < 4 and abs(g - b) < 4:
                continue                      # a checker square, not the sword
            kp[x, y] = (r, g, b, 255)

    keyed = keyed.crop(keyed.getbbox())
    # Screen coordinates run downward, so "up and to the right" is -45 degrees.
    delta = principal_angle(keyed) - (-45.0)
    keyed = keyed.rotate(-delta, resample=Image.BICUBIC, expand=True,
                         fillcolor=(0, 0, 0, 0))
    keyed = keyed.crop(keyed.getbbox())

    cw, ch = keyed.size
    side = max(cw, ch)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(keyed, ((side - cw) // 2, (side - ch) // 2))
    return square


def trace(keyed):
    """Area-average to GRID and repaint from the Obliteration palette."""
    small = keyed.resize((GRID, GRID), Image.BOX)
    out = Image.new("RGBA", (GRID, GRID), (0, 0, 0, 0))
    sp, op = small.load(), out.load()
    for y in range(GRID):
        for x in range(GRID):
            r, g, b, a = sp[x, y]
            if a < 255 * ALPHA_CUT:
                continue
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            neutral = max(r, g, b) - min(r, g, b) < 28   # the hilt is grey; the blade never is
            warm = r > b + 30                            # the reference's orange/red core stripe
            if lum < 34:
                op[x, y] = OUTLINE
            elif neutral:
                op[x, y] = HILT if lum < 120 else HILT_HI
            elif warm:
                op[x, y] = CORE
            elif lum < 70:
                op[x, y] = BLADE_SH
            elif lum < 130:
                op[x, y] = BLADE
            else:
                op[x, y] = BLADE_HI
    return out


def main():
    base = trace(keyed_reference())
    solid = [(x, y) for y in range(GRID) for x in range(GRID) if base.getpixel((x, y))[3] > 0]

    strip = Image.new("RGBA", (GRID, GRID * FRAMES), (0, 0, 0, 0))
    rng = random.Random(0x0B17)
    for f in range(FRAMES):
        frame = base.copy()
        fpx = frame.load()
        for cell in rng.sample(solid, min(SPECKS_PER_FRAME, len(solid))):
            fpx[cell] = SALT if rng.random() < 0.6 else PEPPER
        strip.paste(frame, (0, GRID * f))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    strip.save(OUT)
    with open(OUT + ".mcmeta", "w", encoding="utf-8") as f:
        f.write(MCMETA)
    print("wrote", OUT, strip.size, "from", len(solid), "solid pixels")


if __name__ == "__main__":
    main()
