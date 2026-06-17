"""
Generates / edits textures for the two Limbo features:

  1. item/limbo_elytra_chestplate.png  -- a 16x96 (6-frame) animated icon: the existing animated Limbo
     chestplate drawn IN FRONT of a pair of swept, greyish elytra wings (wings behind, chestplate centred).
     Re-uses limbo_chestplate.png frame-for-frame so the green glint animation still plays on the plate.

  2. item/limbo_helmet.png  -- edit IN PLACE: add 3 small spikes poking up from the helmet crown (centre +
     two sides), in the limbo palette (dark body, bright green tips), on every frame.

  3. entity/limbo_helmet_spikes.png  -- a 32x32 worn-spikes texture mapping the 3 spike cubes baked by
     LimboHelmetSpikesLayer (dark limbo body sampled from limbo_ingot.png, bright green tips).

Re-runnable (overwrites). Uses Pillow. No em dashes anywhere in output (this is code, not game text).
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # villager-arms-mod/
ITEM = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback", "textures", "item")
ENT = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback", "textures", "entity")

FRAME = 16          # one animation frame is 16x16
FRAMES = 6          # the limbo item textures are 16x96 (6 frames stacked)

# Limbo palette. Dark body + bright green tip (sampled from the limbo armour textures).
BODY = (10, 21, 18, 255)
BODY2 = (16, 53, 30, 255)
TIP = (60, 230, 90, 255)
# Greyish elytra wing colours (vanilla elytra is a muted grey-purple; keep it neutral grey here).
WING = (120, 120, 128, 255)
WING_EDGE = (80, 80, 90, 255)


def build_elytra_chestplate():
    src = Image.open(os.path.join(ITEM, "limbo_chestplate.png")).convert("RGBA")
    out = Image.new("RGBA", (FRAME, FRAME * FRAMES), (0, 0, 0, 0))
    for f in range(FRAMES):
        oy = f * FRAME
        # --- wings BEHIND: two swept wings, one each side, drawn first. They are placed in the LOWER
        #     half (rows 7..15) and pushed to the outer edges so they peek out past the narrower
        #     waist of the chestplate (which only spans x=3..12 in those rows). ---
        for y in range(7, 16):
            # the wing widens as it goes down (a swept-back elytra silhouette)
            w = 2 + (y - 7) // 2          # 2..6 wide
            for d in range(w):
                lx = d                    # left wing grows from the left edge inward
                rx = 15 - d               # right wing from the right edge inward
                col = WING_EDGE if d == w - 1 or d == 0 else WING
                out.putpixel((lx, oy + y), col)
                out.putpixel((rx, oy + y), col)
        # --- chestplate IN FRONT: copy this frame of the limbo chestplate over the wings ---
        for y in range(FRAME):
            for x in range(FRAME):
                p = src.getpixel((x, oy + y))
                if p[3] > 0:
                    out.putpixel((x, oy + y), p)
    out.save(os.path.join(ITEM, "limbo_elytra_chestplate.png"))
    # reuse the chestplate's animation mcmeta timing
    with open(os.path.join(ITEM, "limbo_elytra_chestplate.png.mcmeta"), "w") as fh:
        fh.write('{"animation": {"frametime": 5, "interpolate": true}}\n')


# 3 spikes drawn above the helmet crown (crown top is row 3, x 5..10). Coordinates are within a 16x16 frame.
# Each spike: a short vertical taper, dark body with a green tip pixel on top.
SPIKES = [
    # (x, top_y, height)  centre spike is tallest
    (7, 0, 3),   # centre, leftmost of the 2 centre cols
    (8, 0, 3),   # centre, rightmost of the 2 centre cols
    (4, 1, 2),   # left
    (11, 1, 2),  # right
]


def add_helmet_spikes():
    im = Image.open(os.path.join(ITEM, "limbo_helmet.png")).convert("RGBA")
    for f in range(FRAMES):
        oy = f * FRAME
        for (x, top, h) in SPIKES:
            for i in range(h):
                y = top + i
                col = TIP if i == 0 else (BODY2 if i == 1 else BODY)
                im.putpixel((x, oy + y), col)
    im.save(os.path.join(ITEM, "limbo_helmet.png"))


def build_spikes_entity():
    """32x32 texture for the 3 baked spike cubes in LimboHelmetSpikesLayer.
    Cube UVs: center at texOffs(0,0) (2x6x2), left at (8,0), right at (16,0) (2x5x2). A box of width W,
    height H, depth D at texOffs(u,v) lays out as a cross spanning u..u+2*(D+W), v..v+(D+H). We just paint
    the whole used region with the limbo body and put a green band on the top rows so the tips read green."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    # fill the regions each cube uses with the dark body, with subtle vertical shading.
    # center cube uses u=[0,8), v=[0,16) roughly; left/right similar widths. Paint a generous block.
    for x in range(0, 24):
        for y in range(0, 18):
            img.putpixel((x, y), BODY if (x + y) % 2 == 0 else BODY2)
    # green tip band along the very top rows (these map near the top of each cube's side faces).
    for x in range(0, 24):
        img.putpixel((x, 0), TIP)
        if x % 2 == 0:
            img.putpixel((x, 1), TIP)
    img.save(os.path.join(ENT, "limbo_helmet_spikes.png"))


if __name__ == "__main__":
    os.makedirs(ITEM, exist_ok=True)
    os.makedirs(ENT, exist_ok=True)
    build_elytra_chestplate()
    add_helmet_spikes()
    build_spikes_entity()
    print("wrote limbo_elytra_chestplate.png(+mcmeta), edited limbo_helmet.png, wrote entity/limbo_helmet_spikes.png")
