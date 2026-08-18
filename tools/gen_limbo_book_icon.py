"""
Generate the Limbo Book item icon from the Reputation Book's.

The two are siblings -- both are read-only in-hand manuals the village hands you -- so they share a
silhouette on purpose, and only the accent colour separates them: the Reputation Book is green, the
Limbo Book is black. Recolouring rather than redrawing keeps them recognisably a pair.

The source is a clean 5-colour palette (brown cover, dark outline, gold clasp, and the two greens),
so this is an exact palette swap rather than a hue filter: no antialiasing to smear, no stray pixels.

Output: src/main/resources/assets/thevillagefightsback/textures/item/limbo_book.png  (16x16)
"""
import os
from PIL import Image

HERE = os.path.dirname(__file__)
SRC = os.path.join(HERE, "..", "src", "main", "resources", "assets", "thevillagefightsback",
                   "textures", "item", "reputation_book.png")
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "thevillagefightsback",
                   "textures", "item", "limbo_book.png")

# green -> black. The pale mint is the page/highlight and stays light so the shape still reads at
# 16x16; the saturated green is the gem and ribbon and goes to near-black.
SWAP = {
    (0xAE, 0xFC, 0xC5): (0xC6, 0xC8, 0xD2),   # pale mint   -> pale cool grey (pages stay light to read at 16px)
    (0x11, 0x8C, 0x36): (0x14, 0x14, 0x1A),   # green       -> near-black (gem + ribbon)
    (0x6D, 0x45, 0x22): (0x2E, 0x2E, 0x36),   # brown cover -> charcoal
    (0x32, 0x1D, 0x15): (0x0E, 0x0E, 0x12),   # dark brown  -> near-black outline
}
# The cover is swapped too, not just the accents. Recolouring the green alone left an obviously BROWN
# book with black trim, which is not "a black book". The gold clasp is deliberately kept: it is what
# still marks this as the Reputation Book's sibling rather than an unrelated item.


def main():
    src = Image.open(os.path.normpath(SRC)).convert("RGBA")
    px = src.load()
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            repl = SWAP.get((r, g, b))
            if repl:
                px[x, y] = (repl[0], repl[1], repl[2], a)
    out = os.path.normpath(OUT)
    src.save(out)
    print("wrote %s (%dx%d)" % (out, src.width, src.height))


if __name__ == "__main__":
    main()
