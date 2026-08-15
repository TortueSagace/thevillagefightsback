"""
Draw the Obliteration Furnace's block textures.

DRAWN, not recoloured. The first version of this file ran vanilla's blast furnace through a violet luminance
ramp, which kept every rivet, hinge and grate Mojang drew and only changed the hue -- a purple blast furnace,
not a new block. What follows is authored from scratch so the block says what it does.

What it is supposed to say: this thing does not SMELT, it ERASES. So the front is not a grate with a fire
behind it, it is a torn arch with nothing inside -- a hole in the world with an edge that glows when it is
running. The casing is Limbo stone shot through with the same amethyst the Obliterator is tipped with, so the
weapon and the furnace obviously belong together, and the amethyst is what lights up: veins that are dull rock
when cold and lit filament when burning.

Everything is procedural and seeded, so re-running this reproduces the same block exactly rather than
reshuffling the noise.

Faces:
  front      arch closed, veins dark        front_on   arch open onto the void, veins lit, 4-frame pulse
  side       casing + crystal cluster       top        vent ring around an amethyst eye

Output: textures/block/obliteration_furnace_{front,front_on,side,top}.png (+ the front_on mcmeta)
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback",
                       "textures", "block")

N = 16
FRAMES = 4

VOID = (6, 3, 12, 255)          # the nothing inside the arch
STONE_D = (20, 13, 32, 255)     # Limbo stone, shadow
STONE = (56, 40, 78, 255)
STONE_L = (86, 66, 116, 255)    # its lit face
BAND_D = (12, 7, 20, 255)       # the iron bands
BAND = (40, 28, 58, 255)
AME_D = (74, 36, 122, 255)      # amethyst, inert
AME = (132, 78, 196, 255)
AME_L = (188, 146, 246, 255)    # amethyst, live
CORE = (242, 220, 255, 255)     # the hottest filament
MCMETA = '{"animation": {"frametime": 4, "interpolate": true}}' + chr(10)


def base_casing(rng):
    """Limbo stone with a seeded speckle, plus a band across the top and bottom courses."""
    img = Image.new("RGBA", (N, N), STONE)
    px = img.load()
    for y in range(N):
        for x in range(N):
            r = rng.random()
            px[x, y] = STONE_D if r < 0.22 else (STONE_L if r > 0.86 else STONE)
    for y in (0, 1, 14, 15):                      # the bands
        for x in range(N):
            px[x, y] = BAND if (x + y) % 5 else BAND_D
    for x in range(N):                            # lit lip under the top band, shadow over the bottom one
        px[x, 2] = STONE_L if x % 3 else STONE
        px[x, 13] = STONE_D
    return img


def arch(px, lit, phase):
    """The maw: a torn arch, dark when cold, opening onto lit void when burning."""
    # Column extents of the arch, row by row -- narrow at the crown, wide at the floor.
    spans = {3: (6, 9), 4: (5, 10), 5: (4, 11), 6: (4, 11), 7: (3, 12),
             8: (3, 12), 9: (3, 12), 10: (4, 11), 11: (4, 11), 12: (5, 10), 13: (6, 9)}
    for y, (x0, x1) in spans.items():
        for x in range(x0, x1 + 1):
            px[x, y] = VOID
    # The rim. Cold it is dead amethyst; lit it is filament, brightest at the crown and guttering at the floor.
    for y, (x0, x1) in spans.items():
        for x in (x0, x1):
            if not lit:
                px[x, y] = AME_D
                continue
            # A slow pulse travelling down the rim, so the arch breathes instead of blinking.
            wave = (y * 2 - phase * 3) % 8
            px[x, y] = CORE if wave < 2 else (AME_L if wave < 5 else AME)
    if lit:
        # Something is in there. Sparse motes drifting up the void, seeded per frame so they move.
        r = random.Random(0xB00 + phase)
        for _ in range(7):
            y = r.randint(4, 12)
            x0, x1 = spans.get(y, (5, 10))
            x = r.randint(x0 + 1, x1 - 1)
            px[x, y] = AME_L if r.random() < 0.6 else CORE


def veins(px, lit, rng):
    """Amethyst veins through the casing: dull rock when cold, live filament when burning."""
    seams = [(0, 5), (1, 4), (2, 4), (13, 11), (14, 11), (15, 10),
             (1, 9), (2, 10), (13, 6), (14, 5)]
    for x, y in seams:
        px[x, y] = (AME_L if lit else AME_D)
        if lit and rng.random() < 0.5:
            px[x, y] = AME


def make_front(lit, phase=0):
    rng = random.Random(0x0B11)
    img = base_casing(rng)
    px = img.load()
    arch(px, lit, phase)
    veins(px, lit, random.Random(0x0B12 + phase))
    return img


def make_side():
    rng = random.Random(0x0B21)
    img = base_casing(rng)
    px = img.load()
    # A crystal cluster growing out of the stone: three shards of different heights, so the block has a
    # readable silhouette from the side instead of being flat noise.
    for x, top, tip in ((4, 10, 7), (6, 11, 5), (8, 10, 8), (11, 12, 9)):
        for y in range(tip, top + 1):
            px[x, y] = AME if y > tip + 1 else AME_L
        px[x, tip] = CORE
        px[x - 1, top] = AME_D
        px[x + 1, top] = AME_D
    for x, y in ((2, 6), (13, 8), (12, 5)):
        px[x, y] = AME_D
    return img


def make_top():
    rng = random.Random(0x0B31)
    img = base_casing(rng)
    px = img.load()
    # A vent ring around an amethyst eye: what you see looking down at a running furnace.
    ring = [(x, y) for x in range(4, 12) for y in range(4, 12)
            if 2.4 <= ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 <= 3.6]
    for x, y in ring:
        px[x, y] = BAND_D
    for x, y in [(x, y) for x in range(3, 13) for y in range(3, 13)
                 if 3.6 < ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 <= 4.4]:
        px[x, y] = STONE_L      # a lit collar outside the vent, so the ring has an edge to read against
    eye = [(x, y) for x in range(6, 10) for y in range(6, 10)
           if ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 <= 1.6]
    for x, y in eye:
        px[x, y] = AME
    px[7, 7] = CORE
    px[8, 8] = AME_L
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):   # bolts
        px[x, y] = BAND_D
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    make_front(False).save(os.path.join(OUT_DIR, "obliteration_furnace_front.png"))
    make_side().save(os.path.join(OUT_DIR, "obliteration_furnace_side.png"))
    make_top().save(os.path.join(OUT_DIR, "obliteration_furnace_top.png"))

    strip = Image.new("RGBA", (N, N * FRAMES), (0, 0, 0, 0))
    for f in range(FRAMES):
        strip.paste(make_front(True, f), (0, N * f))
    strip.save(os.path.join(OUT_DIR, "obliteration_furnace_front_on.png"))
    # interpolate: the rim pulse should slide, not step, which is the difference between breathing and blinking.
    with open(os.path.join(OUT_DIR, "obliteration_furnace_front_on.png.mcmeta"), "w",
              encoding="utf-8", newline="\n") as f:
        f.write(MCMETA)
    print("drew front, front_on (%d frames), side, top" % FRAMES)


if __name__ == "__main__":
    main()
