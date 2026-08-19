"""
Textures for the dead brain node, derived from the live one so the two always read as the same block.

The live top is `village_brain_node_top.png`, a 16x512 animated strip whose glowing zone sits inside a pale
"contour" ring. Comparing two frames of that strip shows the animation covers x2..13 / y2..13, and the flat
cream square inside the contour -- what the user called the zone inside the white contours -- is exactly
x3..12 / y3..12, 100 pixels. That square is what goes black.

The contour ring and the speckled border are left untouched on purpose: a dead node should still be
recognisable as a brain node at a glance, just an extinguished one.

Cracks are drawn on top, seeded so re-running this produces the same texture every time.

Usage:  python tools/gen_dead_brain_node.py
"""
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
BLOCK = os.path.join(HERE, "..", "src", "main", "resources", "assets",
                     "thevillagefightsback", "textures", "block")

# The dead zone, measured from the live texture (see the module docstring).
ZONE = (3, 3, 12, 12)          # inclusive x0, y0, x1, y1

DEAD_DARK = (10, 10, 12)       # the extinguished zone
DEAD_LIGHT = (26, 26, 30)      # faint mottling so it is not a flat black hole
CRACK = (48, 44, 40)           # crack colour on the dark zone
CRACK_LIGHT = (120, 114, 104)  # crack colour where it runs over the pale border


def _mottle(img, rnd):
    """Black out the inner zone, with a little variation so it reads as depth rather than a hole."""
    px = img.load()
    x0, y0, x1, y1 = ZONE
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c = DEAD_LIGHT if rnd.random() < 0.22 else DEAD_DARK
            px[x, y] = (c[0], c[1], c[2], 255)


def _crack(img, rnd, start, steps, colour_dark, colour_light):
    """
    Walk a crack from `start`, wandering mostly outward. Anything drawn inside the dead zone uses the dark
    crack colour; once the crack leaves the zone onto the pale border it switches, so it stays visible.
    """
    px = img.load()
    x, y = start
    dx, dy = rnd.choice([(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)])
    for _ in range(steps):
        if not (0 <= x < img.width and 0 <= y < img.height):
            return
        inside = ZONE[0] <= x <= ZONE[2] and ZONE[1] <= y <= ZONE[3]
        c = colour_dark if inside else colour_light
        px[x, y] = (c[0], c[1], c[2], 255)
        if rnd.random() < 0.35:                       # wander
            dx, dy = rnd.choice([(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)])
        x += dx
        y += dy


def make_top():
    src = os.path.join(BLOCK, "village_brain_node_top_off.png")
    img = Image.open(src).convert("RGBA")
    rnd = random.Random(20260819)
    _mottle(img, rnd)
    # Four cracks starting inside the dead zone and running out across the contour.
    for start in ((5, 6), (10, 5), (7, 11), (11, 10)):
        _crack(img, rnd, start, 9, CRACK, CRACK_LIGHT)
    out = os.path.join(BLOCK, "dead_brain_node_top.png")
    img.save(out)
    print("dead_brain_node_top.png written")


def _darken(img, rnd, start, steps, factor=0.45):
    """
    A crack across ART we want to keep: darken the pixels along the path instead of painting a flat colour.

    Painting flat grey over the side produced a smear rather than a crack -- the side is a busy, pale texture
    and a solid line reads as damage to the FILE, not to the block. Scaling what is already there keeps the
    speckle underneath and looks like a fissure. Wanders far more than the top cracks, which have a flat black
    field to travel across and can afford to run straight.
    """
    px = img.load()
    x, y = start
    dx, dy = rnd.choice([(1, 0), (0, 1), (1, 1), (1, -1)])
    for _ in range(steps):
        if not (0 <= x < img.width and 0 <= y < img.height):
            return
        r, g, b, a = px[x, y]
        px[x, y] = (int(r * factor), int(g * factor), int(b * factor), a)
        if rnd.random() < 0.6:
            dx, dy = rnd.choice([(1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1)])
        x += dx
        y += dy


def make_side():
    """The side keeps the unlit charges-0 art and just gains a couple of hairline cracks."""
    src = os.path.join(BLOCK, "village_brain_node_side0.png")
    img = Image.open(src).convert("RGBA")
    rnd = random.Random(760813)
    for start in ((3, 4), (9, 2), (12, 8)):
        _darken(img, rnd, start, 7)
    out = os.path.join(BLOCK, "dead_brain_node_side.png")
    img.save(out)
    print("dead_brain_node_side.png written")


def main():
    make_top()
    make_side()


if __name__ == "__main__":
    main()
