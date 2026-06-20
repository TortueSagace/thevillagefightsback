"""Generate the animated EMERALD GLOW frames for each emerald mob (v1.0.3): a twinkling lime "salt and pepper"
emissive overlay, the entity-side counterpart of the fertile blocks' animated specks. Entity textures cannot
animate via .mcmeta (only atlas sprites can), so we bake N discrete frames per mob and a custom render layer
(EmeraldGlowLayer) cycles them at fullbright. Specks sit on the OPAQUE body pixels and pulse brightness+alpha at
per-speck phases, so they shimmer on and off like the block version."""
import math
import os
import random
import zlib

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENT = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback", "textures", "entity")

MOBS = ["cow", "chicken", "cat", "endermite", "wolf", "horse", "pig", "salmon", "bat", "zombie", "skeleton",
        "creeper", "enderman", "iron_golem", "phantom", "wither", "guardian", "elder_guardian", "sheep"]

FRAMES = 8
DENSITY = 0.06                 # fraction of opaque body pixels that become twinkling salt specks
SALT = (190, 255, 85)          # bright lime (peak)
SALT_DIM = (52, 120, 28)       # dim lime (trough)


def gen_one(mob):
    src = os.path.join(ENT, f"emerald_{mob}.png")
    if not os.path.exists(src):
        print(f"  skip {mob}: no body texture")
        return 0
    base = Image.open(src).convert("RGBA")
    w, h = base.size
    bp = base.load()
    rnd = random.Random(zlib.crc32(("glow_" + mob).encode()))
    specks = []
    for y in range(h):
        for x in range(w):
            if bp[x, y][3] >= 250 and rnd.random() < DENSITY:
                specks.append((x, y, rnd.random()))      # x, y, phase
    for f in range(FRAMES):
        frame = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        fp = frame.load()
        for (x, y, ph) in specks:
            t = 0.5 + 0.5 * math.sin(2 * math.pi * (f / FRAMES + ph))
            r = int(round(SALT_DIM[0] + (SALT[0] - SALT_DIM[0]) * t))
            g = int(round(SALT_DIM[1] + (SALT[1] - SALT_DIM[1]) * t))
            b = int(round(SALT_DIM[2] + (SALT[2] - SALT_DIM[2]) * t))
            a = int(round(70 + 185 * t))                 # pulse alpha too -> specks twinkle on/off
            fp[x, y] = (r, g, b, a)
        frame.save(os.path.join(ENT, f"emerald_{mob}_glow{f}.png"))
    return len(specks)


def main():
    total = 0
    for m in MOBS:
        n = gen_one(m)
        total += n
    print(f"emerald glow: {FRAMES} frames x {len(MOBS)} mobs written ({total} specks total)")


if __name__ == "__main__":
    main()
