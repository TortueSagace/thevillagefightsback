"""
Generates the Gas Mask (item icon + worn armour layer) and the Slow Annihilation effect icon.

GAS MASK -- built on the netherite helmet's silhouette: the same brow-to-jaw shell, in the same dark
metal, with two round glass eyepieces and a filter canister over the mouth. The shell is drawn here
rather than traced, because the netherite helmet sprite is in the client jar and this needs to build on
a machine that has not unpacked it (gen_limbo_sage_and_block.py reads the jar directly and is the one
script in this folder that will not run on a clean checkout).

SLOW ANNIHILATION -- the effect icon is Forgotten Powder, which is what the Limbo's air does to you.
The item texture is a SIX-FRAME 16x96 animation and effect icons are static 18x18, so one frame is
taken and padded rather than the strip being pointed at directly, which would render as a tall smear.

Re-runnable (overwrites). Uses Pillow.
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback")
TEX_ITEM = os.path.join(ASSETS, "textures", "item")
TEX_ARMOR = os.path.join(ASSETS, "textures", "models", "armor")
TEX_EFFECT = os.path.join(ASSETS, "textures", "mob_effect")

# Netherite's dark metal, one shade warmer so it does not read as pure black on a dark helmet slot.
SHELL_DARK = (28, 25, 28, 255)
SHELL = (44, 40, 44, 255)
SHELL_HI = (66, 61, 66, 255)
STRAP = (20, 18, 20, 255)
# The eyepieces: the Limbo green, so it belongs to this mod's palette rather than looking like a prop.
GLASS = (25, 97, 46, 255)
GLASS_HI = (130, 226, 158, 255)
GLASS_RIM = (16, 55, 30, 255)
# The filter canister.
FILTER = (58, 52, 46, 255)
FILTER_HI = (84, 76, 66, 255)
VENT = (14, 13, 14, 255)


def px(img, x, y, c, w=16, h=16):
    if 0 <= x < w and 0 <= y < h:
        img.putpixel((x, y), c)


def item_icon():
    """The 16x16 inventory sprite: a front-on mask."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    # shell: a rounded helmet from the brow (y=2) to the jaw (y=13)
    for y in range(2, 14):
        # narrower at the very top and the very bottom, widest across the eyes
        inset = 3 if y in (2, 13) else (2 if y in (3, 12) else 1)
        for x in range(inset, 16 - inset):
            shade = SHELL_HI if y <= 4 else (SHELL if y <= 10 else SHELL_DARK)
            px(img, x, y, shade)

    # the strap across the temples, so it reads as worn rather than as a bucket
    for x in range(1, 15):
        px(img, x, 5, STRAP)

    # two round eyepieces
    for (cx, cy) in ((5, 7), (10, 7)):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if abs(dx) == 1 and abs(dy) == 1:
                    px(img, cx + dx, cy + dy, GLASS_RIM)
                else:
                    px(img, cx + dx, cy + dy, GLASS)
        px(img, cx, cy - 1, GLASS_HI)      # a catchlight, top-left of each lens
        px(img, cx - 1, cy, GLASS_HI)

    # the filter canister over the mouth, with its vent slots
    for y in range(10, 13):
        for x in range(6, 10):
            px(img, x, y, FILTER if y > 10 else FILTER_HI)
    px(img, 7, 11, VENT)
    px(img, 8, 11, VENT)
    return img


def armor_layer():
    """The worn layer. 64x32, and only the head boxes are drawn -- layer 1 also carries body/arms/legs,
    which a helmet must leave completely transparent or it paints a shirt onto the wearer."""
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # Vanilla head layout on the armour sheet: top(8,0) bottom(16,0) right(0,8) front(8,8) left(16,8) back(24,8)
    faces = {"top": (8, 0), "bottom": (16, 0), "right": (0, 8), "front": (8, 8), "left": (16, 8), "back": (24, 8)}
    for face, (ox, oy) in faces.items():
        for y in range(8):
            for x in range(8):
                shade = SHELL_HI if y < 2 else (SHELL if y < 6 else SHELL_DARK)
                img.putpixel((ox + x, oy + y), shade)
        if face in ("right", "left", "back", "front"):
            for x in range(8):
                img.putpixel((ox + x, oy + 2), STRAP)
    # eyepieces + canister on the front face only
    fx, fy = faces["front"]
    for (cx, cy) in ((2, 3), (5, 3)):
        img.putpixel((fx + cx, fy + cy), GLASS_HI)
        for dx, dy in ((1, 0), (0, 1), (1, 1)):
            img.putpixel((fx + cx + dx, fy + cy + dy), GLASS)
    for y in range(5, 7):
        for x in range(3, 5):
            img.putpixel((fx + x, fy + y), FILTER)
    img.putpixel((fx + 3, fy + 6), VENT)
    return img


def effect_icon():
    """Forgotten Powder, one frame, padded to the 18x18 the other eight effect icons use."""
    src = Image.open(os.path.join(TEX_ITEM, "forgotten_powder.png")).convert("RGBA")
    w, h = src.size
    frames = max(1, h // w)
    # The middle frame: the first is the quietest point of the animation and reads as half-empty.
    frame = src.crop((0, (frames // 2) * w, w, (frames // 2 + 1) * w))
    out = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    out.paste(frame, ((18 - w) // 2, (18 - w) // 2))
    return out


if __name__ == "__main__":
    for d in (TEX_ITEM, TEX_ARMOR, TEX_EFFECT):
        os.makedirs(d, exist_ok=True)
    item_icon().save(os.path.join(TEX_ITEM, "gas_mask.png"))
    print("  wrote item/gas_mask.png")
    armor_layer().save(os.path.join(TEX_ARMOR, "gas_mask_layer_1.png"))
    print("  wrote models/armor/gas_mask_layer_1.png")
    effect_icon().save(os.path.join(TEX_EFFECT, "slow_annihilation.png"))
    print("  wrote mob_effect/slow_annihilation.png")
