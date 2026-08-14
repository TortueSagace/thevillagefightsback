"""
Generate the Limbo Scythe's item texture from the reference art.

The silhouette is NOT hand-drawn. Earlier versions were, and every one of them came out looking like a cane or
a pickaxe, because eyeballing a curve onto a 16x16 grid is guesswork and the guesses kept being wrong. The
shape is now traced mechanically from `limbo_scythe_ref.png` (vendored beside this script so the texture can
always be rebuilt from the repo alone), and only the COLOURS are ours.

How the trace works:
  1. Key out the reference's white background -- it is a screenshot, not a texture with alpha.
  2. Area-average it down to the target grid. BOX, not NEAREST: the reference is 148px, which is not an integer
     multiple of the grid, so point-sampling would drop whole pixels of the blade and break the snath into
     floating dots. Averaging then hard-thresholding the alpha keeps the silhouette whole.
  3. Repaint each surviving pixel from OUR palette, by what it is rather than by what colour it happens to be:
       - strongly red         -> amethyst   (the reference's two stones; amethyst is already the scythe's glow)
       - slightly warm + dark -> wood        (the snath: the reference's brown-black shaft, against a blade of
                                              neutral grey -- a few points of red is the only thing separating
                                              them, which is why the test is a delta and not a hue)
       - otherwise            -> the limbo greens, banded by brightness so the blade keeps its shading

32x32, not 16x16. At 16 the blade survives but the snath collapses into disconnected specks: the diagonal is
about one pixel wide at that scale and area-averaging leaves most of it under the alpha threshold. Minecraft
renders any square item texture at the same size in the hand and in the hotbar, so the higher grid costs
nothing but disk.

On top of the trace goes the PURPLE SALT-AND-PEPPER GLOW: fine amethyst speckle scattered over the metal that
twinkles frame to frame. Item textures cannot be truly emissive in vanilla, so the glow is sold by animation
instead -- bright and dark violet grains popping in and out over 8 frames, which reads as a shimmer under the
enchantment glint (LimboScytheItem.isFoil) rather than as a static pattern.

Output: src/main/resources/assets/thevillagefightsback/textures/item/limbo_scythe.png  (32x256, 8 frames)
        plus its .mcmeta animation strip
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REF = os.path.join(ROOT, "tools", "limbo_scythe_ref.png")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback",
                   "textures", "item", "limbo_scythe.png")

GRID = 32                 # see the module docstring: 16 breaks the snath
ALPHA_CUT = 0.42          # a downsampled pixel counts as solid past this coverage
WARM_DELTA = 3            # red-over-blue margin that separates the snath from the blade
FRAMES = 8
SPECKS_PER_FRAME = 14     # ~6% of the solid pixels: a shimmer under the glint, not a rash

# Ours, sampled from the rest of the Limbo set so the scythe belongs to it.
OUTLINE = (8, 16, 12, 255)
BLADE_SH = (16, 55, 30, 255)
BLADE = (25, 97, 46, 255)
BLADE_HI = (130, 226, 158, 255)
WOOD = (34, 27, 18, 255)
WOOD_HI = (58, 47, 32, 255)
SALT = (206, 168, 255, 255)    # bright amethyst grain
PEPPER = (86, 42, 140, 255)    # dark violet grain
MCMETA = '{"animation": {"frametime": 3, "interpolate": false}}' + chr(10)


def keyed_reference():
    """The reference with its white background removed."""
    src = Image.open(REF).convert("RGBA")
    w, h = src.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sp, op = src.load(), out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, _ = sp[x, y]
            if r > 235 and g > 235 and b > 235:
                continue
            op[x, y] = (r, g, b, 255)
    return out


def trace(keyed):
    """Area-average the reference to GRID and repaint it in our palette."""
    small = keyed.resize((GRID, GRID), Image.BOX)
    out = Image.new("RGBA", (GRID, GRID), (0, 0, 0, 0))
    sp, op = small.load(), out.load()
    for y in range(GRID):
        for x in range(GRID):
            r, g, b, a = sp[x, y]
            if a < 255 * ALPHA_CUT:
                continue
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            if r > g + 25 and r > b + 25:
                op[x, y] = SALT                                   # a stone
            elif r >= b + WARM_DELTA and lum < 85:
                op[x, y] = WOOD if lum < 48 else WOOD_HI          # the snath
            elif lum < 32:
                op[x, y] = OUTLINE
            elif lum < 62:
                op[x, y] = BLADE_SH
            elif lum < 108:
                op[x, y] = BLADE
            else:
                op[x, y] = BLADE_HI
    return out


def main():
    base = trace(keyed_reference())
    solid = [(x, y) for y in range(GRID) for x in range(GRID) if base.getpixel((x, y))[3] > 0]

    strip = Image.new("RGBA", (GRID, GRID * FRAMES), (0, 0, 0, 0))
    rng = random.Random(0xA3E7)   # fixed seed: regenerating the texture must not reshuffle it
    for f in range(FRAMES):
        frame = base.copy()
        fpx = frame.load()
        for cell in rng.sample(solid, min(SPECKS_PER_FRAME, len(solid))):
            fpx[cell] = SALT if rng.random() < 0.6 else PEPPER
        strip.paste(frame, (0, GRID * f))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    strip.save(OUT)
    # interpolate false: the specks should POP, not smear into each other, which is what sells a twinkle.
    with open(OUT + ".mcmeta", "w", encoding="utf-8") as f:
        f.write(MCMETA)
    print("wrote", OUT, strip.size, "from", len(solid), "solid pixels")


if __name__ == "__main__":
    main()
