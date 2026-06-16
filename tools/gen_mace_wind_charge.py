"""
Generates the 16x16 item textures for the native Mace + Wind Charge (no mace_port needed).
  - mace.png:        a heavy dark-iron mace head on a short handle, with a gold/copper core dot.
  - wind_charge.png: a small swirling pale-blue/white wind orb (like vanilla's wind charge).
Re-runnable (overwrites). Uses Pillow (see gen_fertile_blocks.py for precedent).
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # villager-arms-mod/
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback", "textures", "item")


def mkdir(p):
    os.makedirs(p, exist_ok=True)


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c)


def mace():
    """A heavy diagonal mace: a short wooden handle (bottom-left) up to a BIG chunky spiked dark-iron head
    (top-right) with a glowing gold core. The head is deliberately large/spiked so it reads as threatening,
    not like a shovel. Readable at 16x16."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    HANDLE_DARK = (74, 52, 30, 255)
    HANDLE = (110, 78, 44, 255)
    HANDLE_HI = (140, 102, 60, 255)
    IRON_DARK = (38, 40, 46, 255)
    IRON = (62, 66, 74, 255)
    IRON_HI = (96, 102, 112, 255)
    GOLD = (224, 170, 56, 255)
    GOLD_HI = (255, 214, 110, 255)

    # handle: a 2-wide diagonal shaft from the pommel (bottom-left) up to the head
    handle_cells = [(2, 14), (3, 13), (4, 12), (5, 11), (6, 10), (7, 9)]
    for (x, y) in handle_cells:
        px(img, x, y, HANDLE)
        px(img, x + 1, y, HANDLE_HI)
        px(img, x, y + 1, HANDLE_DARK)
    # pommel knob at the grip end
    px(img, 1, 15, HANDLE_DARK)
    px(img, 2, 15, HANDLE_DARK)
    px(img, 1, 14, HANDLE_HI)

    # BIG mace head: an 8x8 chunky block at (7..14, 1..8), rounded corners
    for x in range(7, 15):
        for y in range(1, 9):
            if (x, y) in [(7, 1), (14, 1), (7, 8), (14, 8)]:
                continue
            img.putpixel((x, y), IRON)
    # shading: dark lower-left, highlight upper-right
    for (x, y) in [(7, 5), (7, 6), (7, 7), (8, 7), (8, 8), (9, 8), (10, 8)]:
        px(img, x, y, IRON_DARK)
    for (x, y) in [(12, 1), (13, 1), (14, 2), (13, 2), (14, 3), (14, 4)]:
        px(img, x, y, IRON_HI)
    # spikes poking OUT of the head (top, right, bottom) + a couple of surface studs -> threatening
    for (x, y) in [(9, 0), (10, 0), (11, 0), (15, 4), (15, 5), (10, 9), (11, 9), (6, 2)]:
        px(img, x, y, IRON)
    for (x, y) in [(10, 0), (15, 5), (11, 9)]:
        px(img, x, y, IRON_DARK)
    px(img, 8, 3, IRON_DARK)
    px(img, 13, 6, IRON_DARK)
    # glowing gold core in the head's centre
    px(img, 10, 4, GOLD)
    px(img, 11, 4, GOLD_HI)
    px(img, 10, 5, GOLD)
    px(img, 11, 5, GOLD)

    img.save(os.path.join(TEX, "mace.png"))


def wind_charge():
    """A small swirling pale-blue/white wind orb: a soft teal-white ball with a couple of curl streaks."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    CORE = (235, 252, 255, 255)
    PALE = (196, 240, 248, 255)
    TEAL = (140, 214, 228, 255)
    TEAL_D = (96, 178, 196, 255)
    RIM = (66, 142, 162, 255)

    cx, cy = 7.5, 7.5
    for x in range(16):
        for y in range(16):
            dx, dy = x - cx, y - cy
            d = (dx * dx + dy * dy) ** 0.5
            if d <= 2.2:
                img.putpixel((x, y), CORE)
            elif d <= 3.6:
                img.putpixel((x, y), PALE)
            elif d <= 5.0:
                img.putpixel((x, y), TEAL)
            elif d <= 6.0:
                img.putpixel((x, y), TEAL_D)
            elif d <= 6.8:
                img.putpixel((x, y), RIM)
    # a couple of darker "swirl" curl streaks so it reads as moving wind, not a flat ball
    for (x, y) in [(4, 6), (5, 5), (6, 5), (10, 10), (11, 10), (11, 9), (9, 4), (4, 9)]:
        px(img, x, y, TEAL_D)
    # bright glint top-left
    px(img, 5, 5, CORE)
    px(img, 6, 6, CORE)

    img.save(os.path.join(TEX, "wind_charge.png"))


if __name__ == "__main__":
    mkdir(TEX)
    mace()
    wind_charge()
    print("wrote mace.png + wind_charge.png to", TEX)
