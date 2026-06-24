"""Generate the MISSING emerald_salmon_eyes.png overlay.

The shared eyes generator (gen_emerald_eyes_extra.py) only emits overlays for emerald mobs above 10 HP; small
mobs (salmon = 3 HP) were expected to ship a hand-placed face-eye overlay. The salmon's was never created, so its
EyesLayer fell back to the magenta missing-texture -- and RenderType.eyes draws that fullbright/additive over the
whole body, turning the fish "mostly purple". This builds a proper transparent overlay with two emissive green
eyes (same colour + 1x2 vertical bar style as the rest of the faction), matching the body texture's size."""
import os
import zlib

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENT = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback", "textures", "entity")

EYE = (90, 255, 130, 255)   # emerald eye colour (matches the Emeraldger / the other emerald mobs)
EYES = 2                     # salmon is a small, non-ONE_EYE mob -> two eyes


def main():
    src = os.path.join(ENT, "emerald_salmon.png")
    base = Image.open(src).convert("RGBA")
    w, h = base.size
    bp = base.load()
    # Opaque body pixels with room for a 2px-tall eye below them (skip the bottom row).
    opaque = [(x, y) for y in range(h - 1) for x in range(w) if bp[x, y][3] >= 250]
    rnd_order = sorted(opaque, key=lambda p: zlib.crc32(b"eyes_salmon_%d_%d" % p))

    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    op = overlay.load()
    placed = 0
    used = set()
    for (x, y) in rnd_order:
        if placed >= EYES:
            break
        if (x, y) in used or (x, y + 1) in used:
            continue
        op[x, y] = EYE
        op[x, y + 1] = EYE     # vertical 2px eye
        used.add((x, y)); used.add((x, y + 1))
        placed += 1
    overlay.save(os.path.join(ENT, "emerald_salmon_eyes.png"))
    print(f"emerald_salmon_eyes.png written ({w}x{h}, {placed} eyes)")


if __name__ == "__main__":
    main()
