"""
Generate the "Do not build here" sign texture: a green board on a post, with a red bar across it.

Drawn rather than lettered on purpose. At 16 pixels a word is an unreadable smear, so the sign says what it
means with a shape -- a red diagonal bar over a pale house -- which reads at a glance from across a field and
needs no translation. The item name and its tooltip carry the actual wording.

Output: src/main/resources/assets/thevillagefightsback/textures/block/no_build_sign.png  (16x16)
"""
import os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "thevillagefightsback", "textures", "block", "no_build_sign.png")

BOARD = (34, 122, 54, 255)      # village green, a shade deeper than the fertile tint so it reads as painted
BOARD_HI = (58, 158, 78, 255)   # top-left bevel
BOARD_LO = (20, 82, 36, 255)    # bottom-right bevel
FRAME = (12, 46, 22, 255)       # outline
HOUSE = (226, 232, 226, 255)    # the pale house glyph
BAR = (206, 48, 40, 255)        # the red "no" bar


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    d.rectangle([0, 0, 15, 15], fill=BOARD, outline=FRAME)
    d.line([1, 1, 14, 1], fill=BOARD_HI)
    d.line([1, 1, 1, 14], fill=BOARD_HI)
    d.line([14, 2, 14, 14], fill=BOARD_LO)
    d.line([2, 14, 14, 14], fill=BOARD_LO)

    # a little house, drawn big enough to survive the bar crossing it: roof, then walls, then a door
    d.line([4, 8, 8, 4], fill=HOUSE)
    d.line([8, 4, 12, 8], fill=HOUSE)
    d.rectangle([5, 8, 11, 12], outline=HOUSE)
    d.rectangle([7, 10, 9, 12], fill=HOUSE)

    # the bar, drawn last so it sits over the house. One pixel wide with a dark keyline underneath, so it
    # reads as a bar ON the sign rather than merging into the house outline.
    d.line([2, 13, 13, 2], fill=FRAME, width=3)
    d.line([2, 13, 13, 2], fill=BAR, width=1)

    out = os.path.normpath(OUT)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("wrote %s (16x16)" % out)


if __name__ == "__main__":
    main()
