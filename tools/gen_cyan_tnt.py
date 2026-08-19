"""
Cyan TNT textures, derived from the Blue TNT the same way the Green TNT was.

The blue art is two hues: the body at 0.62 and a small 0.67 accent (22 px on the side face). Comparing
blue_tnt_side with green_tnt_side shows the green set moved ONLY the body -- both files carry exactly 22
pixels at 0.67 -- so the accent is deliberate and is left alone here too. Lightness and saturation are
preserved, so the shading and the lettering survive untouched.

Usage:  python tools/gen_cyan_tnt.py
"""
import colorsys
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
BLOCK = os.path.join(HERE, "..", "src", "main", "resources", "assets",
                     "thevillagefightsback", "textures", "block")

BODY_HUE = 0.62      # the blue body
CYAN_HUE = 0.50      # where it goes
TOLERANCE = 0.03     # how close to BODY_HUE a pixel must be to count as body (keeps the 0.67 accent)


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
            nr, ng, nb = colorsys.hls_to_rgb(CYAN_HUE, l, s)
            px[x, y] = (int(round(nr * 255)), int(round(ng * 255)), int(round(nb * 255)), a)
            moved += 1
    out = os.path.join(BLOCK, "cyan_tnt_%s.png" % face)
    img.save(out)
    print("cyan_tnt_%s.png: %d pixels moved" % (face, moved))


def main():
    for face in ("top", "side", "bottom"):
        recolour(face)


if __name__ == "__main__":
    main()
