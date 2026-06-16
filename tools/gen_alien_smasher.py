"""
Generates the Alien Smasher item texture: a 16x96 SIX-FRAME vertical animation strip (mirrors the
Limbo Sword's dark-limbo green-animated look) plus its sibling .png.mcmeta.

The shape is the native Mace silhouette (a diagonal handle up to a chunky head), recoloured into the
dark Limbo body, with a pulsing green glow that sweeps DOWN the head across the six frames -- the same
breathing-green feel as the Limbo gear, applied to a mace.

Re-runnable (overwrites). Uses Pillow (see gen_mace_wind_charge.py / gen_fertile_blocks.py for precedent).
"""
import os
import math
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # villager-arms-mod/
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback", "textures", "item")

FRAMES = 6
FRAMETIME = 5  # ticks/frame; matches the Limbo Sword (interpolated -> smooth pulse)

# Dark-Limbo body palette (sampled from limbo_sword.png).
BODY_DARK = (10, 26, 19, 255)    # deepest shadow
BODY = (15, 47, 27, 255)         # base body
BODY_MID = (19, 71, 36, 255)     # mid tone
BODY_HI = (25, 97, 46, 255)      # body highlight
# Bright pulsing green accent (the "alive" glow).
GLOW = (85, 185, 122, 255)
GLOW_HOT = (150, 245, 175, 255)

# Handle stays a dark, faintly-green wood so it reads as a grip, not glowing metal.
HANDLE = (28, 40, 28, 255)
HANDLE_HI = (44, 60, 40, 255)
HANDLE_DARK = (18, 26, 18, 255)


def mkdir(p):
    os.makedirs(p, exist_ok=True)


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def base_frame():
    """The dark-Limbo Mace silhouette WITHOUT the glow (the glow is added per-frame). Mirrors the native
    mace layout: a 2-wide diagonal handle (bottom-left) and a BIG chunky 8x8 spiked head at (7..14, 1..8)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

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
            img.putpixel((x, y), BODY)
    # shading: dark lower-left, lighter upper-right
    for (x, y) in [(7, 5), (7, 6), (7, 7), (8, 7), (8, 8), (9, 8), (10, 8)]:
        px(img, x, y, BODY_DARK)
    for (x, y) in [(12, 1), (13, 1), (14, 2), (13, 2), (14, 3), (14, 4)]:
        px(img, x, y, BODY_HI)
    # spikes poking OUT of the head (top, right, bottom) + surface studs
    for (x, y) in [(9, 0), (10, 0), (11, 0), (15, 4), (15, 5), (10, 9), (11, 9), (6, 2)]:
        px(img, x, y, BODY)
    for (x, y) in [(10, 0), (15, 5), (11, 9)]:
        px(img, x, y, BODY_DARK)
    px(img, 8, 3, BODY_DARK)
    px(img, 13, 6, BODY_DARK)
    return img


# The head cells whose green glow PULSES (centre + a ring of accents across the bigger 8x8 head).
GLOW_CELLS = [(9, 3), (10, 3), (11, 3), (12, 3), (9, 4), (10, 4), (11, 4), (12, 4),
              (9, 5), (10, 5), (11, 5), (12, 5), (10, 6), (11, 6), (12, 6)]


def make_frame(frame):
    """One animation frame: the body + a green glow that sweeps DOWN the head as the frame advances."""
    img = base_frame()
    # phase 0..1 across the loop; sweep centre runs from top of the head (y=2) to the bottom (y=7).
    phase = frame / FRAMES
    sweep_y = 1.0 + phase * 7.0
    for (x, y) in GLOW_CELLS:
        # brightness peaks where the sweep line passes, plus a steady base glow so it never goes fully dark.
        dist = abs(y - sweep_y)
        intensity = max(0.0, 1.0 - dist / 2.5)
        # base breathing pulse shared by the whole head (so the accents always read as "alive")
        breathe = 0.35 + 0.25 * (0.5 + 0.5 * math.sin(phase * 2.0 * math.pi))
        t = min(1.0, breathe + intensity * 0.75)
        col = lerp(GLOW, GLOW_HOT, intensity) if t > 0.0 else GLOW
        # blend the glow over the body mid-tone by t
        out = lerp(BODY_MID, col, t)
        px(img, x, y, out)
    return img


def build():
    strip = Image.new("RGBA", (16, 16 * FRAMES), (0, 0, 0, 0))
    for f in range(FRAMES):
        strip.paste(make_frame(f), (0, 16 * f))
    strip.save(os.path.join(TEX, "alien_smasher.png"))
    with open(os.path.join(TEX, "alien_smasher.png.mcmeta"), "w", encoding="utf-8") as fh:
        fh.write('{"animation": {"frametime": %d, "interpolate": true}}' % FRAMETIME)


if __name__ == "__main__":
    mkdir(TEX)
    build()
    print("wrote alien_smasher.png (16x%d) + .mcmeta to %s" % (16 * FRAMES, TEX))
