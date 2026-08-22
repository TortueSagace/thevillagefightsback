"""
Generates the shields' ENTITY textures -- the 64x64 sheets vanilla's ShieldModel is skinned with.

WHY THIS AND NOT AN ITEM SPRITE
A shield is not an icon. Vanilla renders it with a real model through a BlockEntityWithoutLevelRenderer,
which is what makes it hang edge-on at the arm when carried and swing round flat in front of you when you
raise it. An item/generated sprite is a billboard and cannot do either, so a mod shield built that way is
always going to sit wrong no matter what its display transforms say. These textures let our shields use
vanilla's own model and vanilla's own transforms, so they behave exactly like the shield the player knows.

THE LAYOUT is dictated by ShieldModel.createLayer():
    plate  texOffs(0, 0)   box 12 x 22 x 1
    handle texOffs(26, 0)  box  2 x  6 x 6
which unwraps to, for a box (w,h,d) at (u,v):
    top (u+d, v, w, d)   bottom (u+d+w, v, w, d)
    right (u, v+d, d, h) front (u+d, v+d, w, h) left (u+d+w, v+d, d, h) back (u+d+w+d, v+d, w, h)

Re-runnable (overwrites). Uses Pillow.
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback", "textures", "entity")

PLATE_W, PLATE_H, PLATE_D = 12, 22, 1
HANDLE_U, HANDLE_V = 26, 0

# The same palettes the item sprites use, so the two never disagree about what a Limbo Shield looks like.
LIMBO = {
    "rim": (130, 226, 158, 255),
    "rim_sh": (85, 185, 122, 255),
    "field": (15, 47, 27, 255),
    "field_hi": (25, 97, 46, 255),
    "crest": (150, 245, 175, 255),
    "core": (206, 168, 255, 255),
    "grip": (28, 40, 28, 255),
    "grip_hi": (44, 60, 40, 255),
}
PALADIN = {
    "rim": (232, 200, 120, 255),
    "rim_sh": (168, 132, 60, 255),
    "field": (36, 22, 58, 255),
    "field_hi": (68, 44, 108, 255),
    "crest": (232, 196, 255, 255),
    "core": (255, 236, 170, 255),
    "grip": (38, 30, 24, 255),
    "grip_hi": (60, 48, 38, 255),
}


def rect(img, x, y, w, h, colour):
    for dy in range(h):
        for dx in range(w):
            img.putpixel((x + dx, y + dy), colour)


def board(pal, front):
    """The 12x22 face of the plate.

    The board is a rectangle, because the MODEL is -- the shield's familiar outline comes from vanilla's
    geometry, not from a cut-out sprite. So this paints a rim, a field and (on the front only) the crest.
    """
    img = Image.new("RGBA", (PLATE_W, PLATE_H), pal["field"])
    # top-lit field
    rect(img, 0, 0, PLATE_W, PLATE_H // 2, pal["field_hi"])
    rect(img, 0, PLATE_H // 2, PLATE_W, PLATE_H - PLATE_H // 2, pal["field"])
    # rim: bright along the top and sides, shadowed along the bottom
    rect(img, 0, 0, PLATE_W, 1, pal["rim"])
    rect(img, 0, 0, 1, PLATE_H, pal["rim"])
    rect(img, PLATE_W - 1, 0, 1, PLATE_H, pal["rim_sh"])
    rect(img, 0, PLATE_H - 1, PLATE_W, 1, pal["rim_sh"])
    if not front:
        return img

    # The crest, ours: the brain node's eye over a pair of folded wings.
    cx = PLATE_W // 2
    eye_y = 7
    for dx in (-2, -1, 0, 1):
        img.putpixel((cx + dx, eye_y), pal["crest"])
        img.putpixel((cx + dx, eye_y + 2), pal["crest"])
    for dx in (-3, 2):
        img.putpixel((cx + dx, eye_y + 1), pal["crest"])
    img.putpixel((cx - 1, eye_y + 1), pal["core"])
    img.putpixel((cx, eye_y + 1), pal["core"])
    for i, dy in enumerate((4, 5, 6)):
        for dx in range(-3 + i, -1 + i):
            img.putpixel((cx + dx, eye_y + dy), pal["crest"])
            img.putpixel((cx - dx - 1, eye_y + dy), pal["crest"])
    return img


def build(pal):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    front = board(pal, True)
    back = board(pal, False)

    d, w, h = PLATE_D, PLATE_W, PLATE_H
    img.paste(front, (d, d))                       # front  (1, 1)
    img.paste(back, (d + w + d, d))                # back   (14, 1)
    rect(img, 0, d, d, h, pal["rim_sh"])           # right edge
    rect(img, d + w, d, d, h, pal["rim_sh"])       # left edge
    rect(img, d, 0, w, d, pal["rim"])              # top edge
    rect(img, d + w, 0, w, d, pal["rim_sh"])       # bottom edge

    # The handle: a plain dark grip on every face, lit on top.
    hw, hh, hd = 2, 6, 6
    u, v = HANDLE_U, HANDLE_V
    rect(img, u + hd, v, hw, hd, pal["grip_hi"])           # top
    rect(img, u + hd + hw, v, hw, hd, pal["grip"])         # bottom
    rect(img, u, v + hd, hd, hh, pal["grip"])              # right
    rect(img, u + hd, v + hd, hw, hh, pal["grip_hi"])      # front
    rect(img, u + hd + hw, v + hd, hd, hh, pal["grip"])    # left
    rect(img, u + hd + hw + hd, v + hd, hw, hh, pal["grip"])  # back
    return img


if __name__ == "__main__":
    os.makedirs(TEX, exist_ok=True)
    for name, pal in (("limbo_shield", LIMBO), ("paladin_shield", PALADIN)):
        build(pal).save(os.path.join(TEX, name + ".png"))
        print("  wrote entity/%s.png (64x64)" % name)
