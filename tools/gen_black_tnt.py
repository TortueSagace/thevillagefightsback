"""
Black TNT textures, derived from the Blue TNT the same way the Cyan and Green sets were.

Black is not a hue, so this is the one recolour in the family that cannot just rotate one: it drains the
body's saturation and pulls its lightness down instead. Everything the other generators protect is
protected here too -- greyscale pixels (the lettering and the shading) and the 0.67 accent hue are left
exactly as they are, so the block still reads as a TNT crate rather than a black cube.

Usage:  python tools/gen_black_tnt.py
"""
import colorsys
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
BLOCK = os.path.join(HERE, "..", "src", "main", "resources", "assets",
                     "thevillagefightsback", "textures", "block")

BODY_HUE = 0.62      # the blue body, as in gen_cyan_tnt.py
TOLERANCE = 0.03     # anything further away is the accent, and is left alone
DARKEN = 0.32        # what fraction of its lightness the body keeps
DESATURATE = 0.18    # ...and how much colour is left in it, so it is charcoal rather than flat black


def recolour(face):
    src = os.path.join(BLOCK, "blue_tnt_%s.png" % face)
    img = Image.open(src).convert("RGBA")
    px = img.load()
    moved = 0
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255.0, g / 255.0, b / 255.0)
            if s <= 0.12 or abs(h - BODY_HUE) > TOLERANCE:
                continue                      # greyscale, or the accent hue -> leave exactly as it is
            nr, ng, nb = colorsys.hls_to_rgb(h, l * DARKEN, s * DESATURATE)
            px[x, y] = (int(round(nr * 255)), int(round(ng * 255)), int(round(nb * 255)), a)
            moved += 1
    out = os.path.join(BLOCK, "black_tnt_%s.png" % face)
    img.save(out)
    print("  wrote black_tnt_%s.png (%d body pixels darkened)" % (face, moved))


if __name__ == "__main__":
    for face in ("top", "side", "bottom"):
        recolour(face)
