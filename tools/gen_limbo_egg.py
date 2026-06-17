"""
Improve the Limbo Egg texture: a black-and-white "dragon egg" with a faint Limbo-green glow, used as the single
wrap texture on the vanilla dragon-egg block model (parent minecraft:block/dragon_egg, texture "all").

The look: a near-black shell with a smooth vertical sheen, scattered bone-white speckles, and a few vivid-green
forgotten-glowstone flecks so it reads as a Limbo curio rather than a plain End dragon egg.

Output: src/main/resources/assets/thevillagefightsback/textures/block/limbo_egg.png  (16x16)
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "thevillagefightsback", "textures", "block", "limbo_egg.png")

W = H = 16
# deterministic speckle positions (no randomness so the asset is reproducible)
WHITE_SPECKS = [(3, 2), (11, 1), (6, 5), (13, 6), (2, 8), (9, 9), (14, 11), (5, 12), (10, 13), (4, 15), (12, 14)]
GREEN_SPECKS = [(8, 3), (4, 7), (12, 9), (7, 11), (2, 13)]
WHITE = (224, 230, 226, 255)
WHITE_DIM = (150, 160, 156, 255)
GREEN = (90, 230, 130, 255)
GREEN_DIM = (40, 150, 80, 255)


def main():
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = im.load()
    for y in range(H):
        for x in range(W):
            # base shell: near-black, slightly lighter toward the top, with a soft vertical highlight column.
            base = 14 + (H - 1 - y) * 2            # 14..44 dark gradient (lighter at top)
            sheen = max(0, 6 - abs(x - 6))         # a highlight band around x=6
            v = min(70, base + sheen)
            px[x, y] = (v, v, v + 4, 255)
    for (x, y) in WHITE_SPECKS:
        px[x, y] = WHITE
        if x + 1 < W:
            px[x + 1, y] = WHITE_DIM
    for (x, y) in GREEN_SPECKS:
        px[x, y] = GREEN
        if y + 1 < H:
            px[x, y + 1] = GREEN_DIM

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    im.save(OUT)
    print("wrote", os.path.normpath(OUT), im.size)


if __name__ == "__main__":
    main()
