"""
Generates the Limbo Shield and the Paladin Shield item textures: 16x96 SIX-FRAME vertical animation
strips plus their .png.mcmeta, matching the Limbo gear's breathing-green look (gen_alien_smasher.py).

TWO SHAPES, deliberately:

  * limbo_shield   -- VANILLA's shield outline. It is a vanilla shield with the Limbo filter applied, so
                      it should look like one. The silhouette is lifted from the game's own
                      empty_armor_slot_shield icon -- which is the shield drawn as a one-pixel outline,
                      the only place the game ships its shape at icon size -- and flood filled.
  * paladin_shield -- the heater silhouette traced from the supplied reference, the same mechanical way
                      gen_limbo_scythe.py traces its scythe: key the background out, BOX-resample to
                      16x16, threshold the alpha. The upgrade should not look like the thing it upgrades.

The CREST is ours on both. The reference is Nintendo's Hylian Shield and its emblem is theirs, so only
the shape of the board is taken from it: the crest drawn into the field here is the brain node's eye
over a pair of folded wings, which is this mod's own motif. Nothing recognisable from the reference art
survives past the outline.

Palettes: limbo_shield is the dark Limbo green with a pale green metal rim; paladin_shield is the violet
Obliteration family with a gold rim and an amethyst crest.

Re-runnable (overwrites). Uses Pillow.
"""
import io
import os
import math
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback", "textures", "item")
REF = os.path.join(os.path.dirname(os.path.abspath(__file__)), "shield_ref.webp")

FRAMES = 6
FRAMETIME = 5      # ticks/frame, interpolated -- the same slow breath as the Limbo sword
ALPHA_CUT = 108    # BOX-resampled alpha above this counts as "shield"

# --- palettes ---------------------------------------------------------------------------------
# Sampled from limbo_sword.png, as gen_alien_smasher.py does.
LIMBO = {
    "outline":  (8, 16, 12, 255),
    "rim":      (130, 226, 158, 255),
    "rim_sh":   (85, 185, 122, 255),
    "field":    (15, 47, 27, 255),
    "field_hi": (25, 97, 46, 255),
    "crest":    (150, 245, 175, 255),
    "core":     (206, 168, 255, 255),   # the amethyst grain the Limbo family uses as its accent
    "glow":     (150, 245, 175, 255),
}
# The Obliteration violet family (gen_obliterator.py), so the upgrade reads as a tier up.
PALADIN = {
    "outline":  (18, 12, 26, 255),
    "rim":      (232, 200, 120, 255),
    "rim_sh":   (168, 132, 60, 255),
    "field":    (36, 22, 58, 255),
    "field_hi": (68, 44, 108, 255),
    "crest":    (232, 196, 255, 255),
    "core":     (255, 236, 170, 255),
    "glow":     (232, 196, 255, 255),
}


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


CLIENT_JAR = os.path.join(
    os.path.expanduser("~"), "AppData", "Roaming", "ModrinthApp", "meta", "libraries", "net", "minecraft",
    "client", "1.20.1-20230612.114412", "client-1.20.1-20230612.114412-extra.jar")
SLOT_ICON = "assets/minecraft/textures/item/empty_armor_slot_shield.png"


def vanilla_silhouette():
    """Vanilla's own shield shape, as a 16x16 mask.

    empty_armor_slot_shield is the shield drawn as a one-pixel OUTLINE -- the only place the game ships
    its silhouette at icon size. Flooding the OUTSIDE from the corners and keeping everything the flood
    could not reach turns that outline into the solid shape, which is more reliable than flooding the
    inside: the outline is one pixel thick and a flood started in the middle leaks through any diagonal.

    Falls back to a hand-drawn approximation when the client jar is not there, so this stays runnable on
    a machine that has never launched the game.
    """
    import zipfile
    try:
        z = zipfile.ZipFile(CLIENT_JAR)
        icon = Image.open(io.BytesIO(z.read(SLOT_ICON))).convert("RGBA")
    except Exception:
        print("  (client jar unavailable -- using the built-in shield outline)")
        return _fallback_silhouette()

    px = icon.load()
    outline = [[px[x, y][3] >= 40 for x in range(16)] for y in range(16)]

    outside = [[False] * 16 for _ in range(16)]
    stack = [(0, 0), (15, 0), (0, 15), (15, 15)]
    while stack:
        x, y = stack.pop()
        if x < 0 or x > 15 or y < 0 or y > 15 or outside[y][x] or outline[y][x]:
            continue
        outside[y][x] = True
        stack.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    return [[outline[y][x] or not outside[y][x] for x in range(16)] for y in range(16)]


def _fallback_silhouette():
    """The vanilla shape by hand: straight sides, shoulders in, a blunt point at the bottom."""
    rows = [
        "................",
        ".......##.......",
        "....########....",
        "..############..",
        ".##############.",
        ".##############.",
        "..############..",
        "..############..",
        "..############..",
        "...##########...",
        "...##########...",
        "...##########...",
        "....########....",
        ".....######.....",
        ".......##.......",
        "................",
    ]
    return [[c == "#" for c in row] for row in rows]


def silhouette():
    """The reference, keyed and resampled to a 16x16 boolean mask.

    BOX rather than NEAREST on purpose: NEAREST at this reduction picks one source pixel out of ~90
    and the outline comes back ragged, while BOX averages the area and the alpha threshold then cuts a
    clean edge. Same reasoning as gen_limbo_scythe.trace().
    """
    im = Image.open(REF).convert("RGBA")
    px = im.load()
    w, h = im.size
    keyed = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    kp = keyed.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 8 or (r > 235 and g > 235 and b > 235):
                continue            # the reference's white background
            kp[x, y] = (r, g, b, 255)
    small = keyed.resize((16, 16), Image.BOX).load()
    return [[small[x, y][3] >= ALPHA_CUT for x in range(16)] for y in range(16)]


def edge(mask, x, y):
    """A shield cell with at least one non-shield orthogonal neighbour: the rim."""
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, ny = x + dx, y + dy
        if nx < 0 or nx > 15 or ny < 0 or ny > 15 or not mask[ny][nx]:
            return True
    return False


def crest_cells():
    """Our own emblem: the brain node's eye over a pair of folded wings.

    Hand-placed rather than traced -- it is eight pixels of emblem in the middle of a board, and at
    16x16 anything derived from a larger drawing turns to mud. Coordinates are (x, y).
    """
    eye = [(7, 6), (8, 6), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8)]
    pupil = [(7, 7), (8, 7)]
    wings = [(5, 9), (6, 9), (9, 9), (10, 9), (6, 10), (9, 10), (7, 11), (8, 11)]
    return eye, pupil, wings


def build(pal, mask):
    """The still shield, before the pulse."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for y in range(16):
        for x in range(16):
            if not mask[y][x]:
                continue
            if edge(mask, x, y):
                # Top-lit: the upper half of the rim catches the light, the lower half is in shadow.
                p[x, y] = pal["rim"] if y <= 8 else pal["rim_sh"]
            else:
                p[x, y] = pal["field_hi"] if y <= 6 else pal["field"]
    eye, pupil, wings = crest_cells()
    for (x, y) in wings:
        if mask[y][x]:
            p[x, y] = pal["crest"]
    for (x, y) in eye:
        if mask[y][x]:
            p[x, y] = pal["crest"]
    for (x, y) in pupil:
        if mask[y][x]:
            p[x, y] = pal["core"]
    # A one-pixel dark seam just inside the rim, so the board reads as raised rather than flat.
    for y in range(16):
        for x in range(16):
            if mask[y][x] and not edge(mask, x, y):
                if any(mask[y + dy][x + dx] and edge(mask, x + dx, y + dy)
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                       if 0 <= x + dx < 16 and 0 <= y + dy < 16):
                    if (x, y) not in eye and (x, y) not in wings:
                        p[x, y] = pal["outline"]
    return img


def frames(pal, base):
    """Six frames of a glow breathing across the crest, the Limbo family's signature."""
    eye, pupil, wings = crest_cells()
    lit = set(eye) | set(wings) | set(pupil)
    out = []
    for f in range(FRAMES):
        img = base.copy()
        p = img.load()
        amt = 0.5 + 0.5 * math.sin(2.0 * math.pi * f / FRAMES)
        for (x, y) in lit:
            cur = p[x, y]
            p[x, y] = lerp(cur, pal["glow"], amt * 0.7)
        out.append(img)
    return out


def write(name, pal, mask):
    strip = Image.new("RGBA", (16, 16 * FRAMES), (0, 0, 0, 0))
    for i, fr in enumerate(frames(pal, build(pal, mask))):
        strip.paste(fr, (0, i * 16))
    os.makedirs(TEX, exist_ok=True)
    strip.save(os.path.join(TEX, name + ".png"))
    with open(os.path.join(TEX, name + ".png.mcmeta"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write('{"animation": {"frametime": %d, "interpolate": true}}\n' % FRAMETIME)
    print("  wrote %s.png (16x%d) + .mcmeta" % (name, 16 * FRAMES))


if __name__ == "__main__":
    write("limbo_shield", LIMBO, vanilla_silhouette())
    write("paladin_shield", PALADIN, silhouette())
