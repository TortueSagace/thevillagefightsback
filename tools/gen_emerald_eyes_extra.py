"""Scatter EXTRA emerald eyes over high-HP emerald mobs (v1.0.3). For every emerald mob above 10 HP, the eye
overlay is regenerated with many vertical green eyes -- the base face eyes PLUS a count proportional to the mob's
max HP -- placed at random opaque body pixels, so a tougher unit is studded with watching emerald eyes all over.
Mobs at or below 10 HP keep their existing hand-placed face-eye overlay (left untouched). The eyes are emissive
green bars (1px wide, 2px tall) identical in colour to the Emeraldger's, rendered fullbright by the eyes layer."""
import os
import random
import zlib

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENT = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback", "textures", "entity")

# Nominal max HP per emerald mob (matches the entity createAttributes()).
HP = {
    "cow": 10, "chicken": 4, "cat": 10, "endermite": 8, "wolf": 8, "horse": 22, "pig": 10, "salmon": 3,
    "bat": 6, "zombie": 20, "skeleton": 20, "creeper": 20, "enderman": 40, "iron_golem": 100, "phantom": 20,
    "wither": 300, "guardian": 30, "elder_guardian": 80,
}
ONE_EYE = {"endermite", "guardian", "elder_guardian"}   # base face-eye count among the >10 HP set
EYE = (90, 255, 130, 255)                                # emerald eye colour (matches the Emeraldger)
EYES_PER_HP = 8.0                                        # one extra eye per ~8 HP


def gen_one(mob, hp):
    src = os.path.join(ENT, f"emerald_{mob}.png")
    if not os.path.exists(src):
        print(f"  skip {mob}: no body texture")
        return 0
    base = Image.open(src).convert("RGBA")
    w, h = base.size
    bp = base.load()
    opaque = [(x, y) for y in range(h - 1) for x in range(w) if bp[x, y][3] >= 250]
    if not opaque:
        return 0
    rnd = random.Random(zlib.crc32(("eyes_" + mob).encode()))
    base_eyes = 1 if mob in ONE_EYE else 2
    count = base_eyes + int(round(hp / EYES_PER_HP))
    count = min(count, len(opaque))
    rnd.shuffle(opaque)

    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    op = overlay.load()
    placed = 0
    used = set()
    for (x, y) in opaque:
        if placed >= count:
            break
        if (x, y) in used or (x, y + 1) in used:
            continue
        op[x, y] = EYE
        op[x, y + 1] = EYE                              # vertical 2px eye
        used.add((x, y)); used.add((x, y + 1))
        placed += 1
    overlay.save(os.path.join(ENT, f"emerald_{mob}_eyes.png"))
    return placed


def main():
    total = 0
    for mob, hp in HP.items():
        if hp <= 10:
            continue                                    # keep the hand-placed face eyes for small mobs
        total += gen_one(mob, hp)
    print(f"emerald extra eyes: scattered {total} eyes across the >10 HP mobs")


if __name__ == "__main__":
    main()
