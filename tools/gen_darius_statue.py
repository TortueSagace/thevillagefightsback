"""
Generates darius_statue.nbt -- a giant statue of General Darius in his full Limbo set, derived from the
actual textures he is rendered with rather than drawn by hand.

HOW
The player model is boxes: head 8x8x8, body 8x12x4, arms and legs 4x12x4, measured in texels. Blow that
up one block per texel and you get a 32-block statue whose proportions are exactly the ones on screen.
Every voxel then samples the texture pixel on whichever face it sits on -- front, back, left, right, top,
bottom -- from limbo_layer_1.png (helmet, chest, arms) and limbo_layer_2.png (legs), which are the worn
armour textures the game itself draws on him.

THE MAN INSIDE THE ARMOUR. The armour layers are transparent wherever the armour does not cover, so
sampling them alone gives an empty suit -- and, worse, one that FLOATS: the leggings layer has nothing
below the knee, so the statue's lowest three rows came out empty and it stood three blocks off its own
base. So Darius's own skin is sampled first and the armour composited over it. Two things are then
forced to stay skin no matter what the armour says, because they are what makes it a statue of somebody
rather than of a suit: the FACE inside the helmet, and the HANDS at the ends of the sleeves.

BOOTS come off layer 1, not layer 2. Vanilla splits worn armour across two sheets -- helmet, chest and
BOOTS on layer 1, leggings on layer 2 -- so sampling the legs from layer 2 alone left him barefoot. The
bottom rows of each leg are taken from layer 1 instead.

SPIKES. The Limbo helmet is drawn with three spikes by a render layer, not by its texture, so nothing in
the armour sheet knows about them. They are added here as geometry.

FACING. The statue is mirrored on Z so it looks back at whoever set the charge down, rather than away.

Each sampled colour is snapped to the nearest block in a NON-FLAMMABLE palette: concretes, terracottas
and a few stones. That constraint is the whole reason wool is absent -- a 30-block statue of the village's
champion that a single flint could burn down would be a monument to the wrong thing.

The statue stands on a slab of dirt path, the same footing the village's houses are built on.

OUTPUT
A real structure template (gzipped NBT: size / palette / blocks / entities), written next to the other
seventeen, so placement reuses HousePlacer and HouseBuildManager unchanged and the black TNT is an
ordinary StructureTntBlock.

Re-runnable (overwrites). Uses Pillow.
"""
import gzip
import io
import os
import struct
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ARMOR = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback",
                     "textures", "models", "armor")
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "thevillagefightsback",
                   "structures", "darius_statue.nbt")

DATA_VERSION = 3465          # 1.20.1

# --- a non-flammable palette ------------------------------------------------------------------------
# Approximate rendered colours. Anything here must not burn: concrete, terracotta and stone only.
PALETTE = {
    "minecraft:black_concrete": (8, 10, 15),
    "minecraft:gray_concrete": (54, 57, 61),
    "minecraft:light_gray_concrete": (125, 125, 115),
    "minecraft:white_concrete": (207, 213, 214),
    "minecraft:green_concrete": (73, 91, 36),
    "minecraft:lime_concrete": (94, 168, 24),
    "minecraft:cyan_concrete": (21, 119, 136),
    "minecraft:blue_concrete": (44, 46, 143),
    "minecraft:light_blue_concrete": (36, 137, 199),
    "minecraft:purple_concrete": (100, 32, 156),
    "minecraft:magenta_concrete": (169, 48, 159),
    "minecraft:pink_concrete": (213, 101, 143),
    "minecraft:red_concrete": (142, 33, 33),
    "minecraft:orange_concrete": (224, 97, 0),
    "minecraft:yellow_concrete": (241, 175, 21),
    "minecraft:brown_concrete": (96, 60, 32),
    "minecraft:black_terracotta": (37, 23, 16),
    "minecraft:gray_terracotta": (58, 42, 36),
    "minecraft:green_terracotta": (76, 83, 42),
    "minecraft:brown_terracotta": (77, 51, 36),
    "minecraft:deepslate": (80, 80, 82),
    "minecraft:blackstone": (42, 35, 40),
    "minecraft:stone": (125, 125, 125),
}
PALETTE_ITEMS = list(PALETTE.items())

BASE_BLOCK = "minecraft:dirt_path"
# The spikes read as the helmet's brightest metal.
SPIKE_COLOUR = (130, 226, 158)


def nearest(rgb):
    r, g, b = rgb[:3]
    best, bestd = None, None
    for name, (pr, pg, pb) in PALETTE_ITEMS:
        # Weighted to how the eye reads it, so a dark green does not snap to a dark red of equal distance.
        d = 2 * (r - pr) ** 2 + 4 * (g - pg) ** 2 + 3 * (b - pb) ** 2
        if bestd is None or d < bestd:
            best, bestd = name, d
    return best


# --- the model ----------------------------------------------------------------------------------------
# (w, h, d) in texels, and the UV origin of each face on the 64x32 sheet.
def faces(ox, oy, w, h, d):
    return {
        "top": (ox + d, oy, w, d),
        "bottom": (ox + d + w, oy, w, d),
        "right": (ox, oy + d, d, h),
        "front": (ox + d, oy + d, w, h),
        "left": (ox + d + w, oy + d, d, h),
        "back": (ox + d + w + d, oy + d, w, h),
    }


PARTS = [
    # name,        layer, size (w,h,d),  armour uv, offset from the statue's origin (x,y,z)
    ("head", 1, (8, 8, 8), (0, 0), (2, 24, -2)),
    ("body", 1, (8, 12, 4), (16, 16), (2, 12, 0)),
    ("right_arm", 1, (4, 12, 4), (40, 16), (-2, 12, 0)),
    ("left_arm", 1, (4, 12, 4), (40, 16), (10, 12, 0)),
    ("right_leg", 2, (4, 12, 4), (0, 16), (2, 0, 0)),
    ("left_leg", 2, (4, 12, 4), (0, 16), (6, 0, 0)),
]

SKIN = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback",
                    "textures", "entity", "general_darius.png")

# Darius is drawn with a VILLAGER model, so his skin is a villager sheet and its boxes are not the
# player ones the armour layers use. These are the villager UV origins that best cover each part; the
# faces are stretched to the box's own size, which is why sample() takes the face size separately.
SKIN_PARTS = {
    "head": (0, 0, (8, 10, 8)),        # villager head is 8x10x8
    "body": (16, 20, (8, 12, 6)),      # ...and its body 8x12x6
    "right_arm": (0, 22, (4, 12, 4)),  # the villager's arms are one joined box, so the LEG uv is
    "left_arm": (0, 22, (4, 12, 4)),   # borrowed: same 4x12x4 shape, same robe and skin tones
    "right_leg": (0, 22, (4, 12, 4)),
    "left_leg": (0, 22, (4, 12, 4)),
}

# How far in from the edge of the head's front face the FACE starts (the rest of that face is helmet),
# and how many rows at the bottom of each arm are HAND rather than sleeve.
FACE_INSET = 1
HAND_ROWS = 3
# How many rows at the bottom of each leg are BOOT rather than legging.
BOOT_ROWS = 4
# The three spikes on the Limbo helmet, as (x offset from the head's left, height). Each is a
# square column SPIKE_BASE on a side, and they are Forgotten Glowstone, so the statue is lit
# from the head at night rather than being a silhouette.
SPIKES = ((0, 2), (3, 3), (6, 2))
SPIKE_BLOCK = "thevillagefightsback:forgotten_glowstone"
SPIKE_BASE = 2          # each spike is a SPIKE_BASE x SPIKE_BASE column, not a single stack


def sample(img, uv, fw, fh, u, v):
    ox, oy, w, h = uv
    x = ox + min(max(u, 0), w - 1)
    y = oy + min(max(v, 0), h - 1)
    return img.getpixel((x, y))


def _load(preferred, fallback):
    for name in (preferred, fallback):
        path = os.path.join(ARMOR, name)
        if os.path.exists(path):
            return Image.open(path).convert("RGBA")
    raise FileNotFoundError(preferred)


def stretch(colours):
    """Spread the sampled luminances across the palette's range before snapping.

    The armour is dark by design -- it is meant to read as a silhouette on a mob three blocks away. A
    statue is the opposite: it is thirty blocks tall and read from across a field, and a literal mapping
    of a 16..122 luminance range onto a palette that runs to 214 throws away every distinction the
    texture actually makes. Hue and saturation are untouched; only the value is rescaled, so the statue
    is the same armour, lit.
    """
    lums = [0.299 * r + 0.587 * g + 0.114 * b for (r, g, b) in colours]
    lo, hi = min(lums), max(lums)
    span = max(1.0, hi - lo)
    out = {}
    for (r, g, b) in colours:
        lum = 0.299 * r + 0.587 * g + 0.114 * b
        want = 25.0 + (lum - lo) / span * 195.0        # -> 25..220
        k = want / max(1.0, lum)
        out[(r, g, b)] = (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)))
    return out


def build():
    # The mid-pulse ANIMATION frame, not the base layer. The base is nine near-identical greens spanning
    # luminance 13..55 -- snap that to blocks and every one lands on black and the statue is a monolith.
    # Frame 8 is the top of the breath: 71 colours spanning 16..122, which is a statue with shading in it.
    layer = {1: _load("limbo_layer_1_8.png", "limbo_layer_1.png"),
             2: _load("limbo_layer_2_8.png", "limbo_layer_2.png")}
    skin = Image.open(SKIN).convert("RGBA")

    voxels = {}
    for name, lay, (w, h, d), (ux, uy), (ox, oy, oz) in PARTS:
        armour = layer[lay]
        af = faces(ux, uy, w, h, d)
        sx, sy, (sw, sh, sd) = SKIN_PARTS[name]
        sf = faces(sx, sy, sw, sh, sd)

        for x in range(w):
            for y in range(h):
                for z in range(d):
                    # Which face this voxel shows, and where on it. Interior voxels take the front face,
                    # which never shows and only has to be something.
                    if z == 0:
                        key, u, v, fw, fh = "front", x, h - 1 - y, w, h
                    elif z == d - 1:
                        key, u, v, fw, fh = "back", w - 1 - x, h - 1 - y, w, h
                    elif x == 0:
                        key, u, v, fw, fh = "right", z, h - 1 - y, d, h
                    elif x == w - 1:
                        key, u, v, fw, fh = "left", d - 1 - z, h - 1 - y, d, h
                    elif y == h - 1:
                        key, u, v, fw, fh = "top", x, z, w, d
                    else:
                        key, u, v, fw, fh = "front", x, h - 1 - y, w, h

                    # The skin underneath, stretched from the villager sheet onto this box's face.
                    su = u * sf[key][2] // max(1, fw)
                    sv = v * sf[key][3] // max(1, fh)
                    under = sample(skin, sf[key], fw, fh, su, sv)
                    over = sample(armour, af[key], fw, fh, u, v)

                    # The two places the man must win over the suit.
                    show_face = (name == "head" and key == "front"
                                 and FACE_INSET <= x < w - FACE_INSET and 1 <= (h - 1 - y) < h - 1)
                    show_hand = (name.endswith("_arm") and y < HAND_ROWS)

                    # Boots are on layer 1, not the leggings sheet, so the lower leg comes from there or
                    # he stands there barefoot.
                    if name.endswith("_leg") and y < BOOT_ROWS:
                        over = sample(layer[1], faces(0, 16, w, h, d)[key], fw, fh, u, v)

                    px = under if (show_face or show_hand or over[3] < 40) else over
                    if px[3] < 40:
                        px = under
                    if px[3] < 40:
                        continue                       # neither the suit nor the man is here
                    voxels[(ox + x, oy + y, oz + z)] = px[:3]

    # Mirrored on Z so he faces whoever set the charge down rather than turning his back on them.
    zs_all = [p[2] for p in voxels]
    zmin, zmax = min(zs_all), max(zs_all)
    voxels = {(x, y, zmin + (zmax - z)): c for (x, y, z), c in voxels.items()}

    lit = stretch(set(voxels.values()))
    for k in list(voxels):
        voxels[k] = nearest(lit[voxels[k]])

    # The three helmet spikes, AFTER the palette pass: they are a named block rather than a colour to be
    # matched, and Forgotten Glowstone is not in the palette (nor should it be -- nothing else on him
    # should glow). They are a render LAYER on the real helmet, so no armour texture carries them and
    # nothing sampled above could have found them; a statue of Darius without them is not him.
    #
    # Placed off the head's own top face rather than off the PARTS offsets, so the mirror above cannot put
    # them on the back of his skull.
    top = max(y for (_x, y, _z) in voxels)
    crown = [(x, z) for (x, y, z) in voxels if y == top]
    cx0 = min(x for x, _z in crown)
    cz0 = min(z for _x, z in crown)
    cz1 = max(z for _x, z in crown)
    zmid = (cz0 + cz1) // 2 - (SPIKE_BASE // 2) + 1
    for (sx, tall) in SPIKES:
        for t in range(tall):
            for dx in range(SPIKE_BASE):
                for dz in range(SPIKE_BASE):
                    voxels[(cx0 + sx + dx, top + 1 + t, zmid + dz)] = SPIKE_BLOCK

    # The footing: a slab of dirt path one block under the whole footprint, as the houses have.
    xs = [p[0] for p in voxels]
    zs = [p[2] for p in voxels]
    for x in range(min(xs) - 1, max(xs) + 2):
        for z in range(min(zs) - 1, max(zs) + 2):
            voxels.setdefault((x, -1, z), BASE_BLOCK)
    return voxels


# --- NBT writing ----------------------------------------------------------------------------------------
def w_byte(b, v):
    b.write(struct.pack(">b", v))


def w_str(b, s):
    e = s.encode("utf-8")
    b.write(struct.pack(">H", len(e)))
    b.write(e)


def tag(b, tid, name):
    w_byte(b, tid)
    w_str(b, name)


def w_int(b, v):
    b.write(struct.pack(">i", v))


def w_int_array(b, name, vals):
    tag(b, 11, name)
    w_int(b, len(vals))
    for v in vals:
        w_int(b, v)


def w_list_header(b, name, elem_type, count):
    tag(b, 9, name)
    w_byte(b, elem_type)
    w_int(b, count)


def write_nbt(voxels):
    names = sorted(set(voxels.values()))
    index = {n: i for i, n in enumerate(names)}

    b = io.BytesIO()
    tag(b, 10, "")            # root compound, unnamed

    tag(b, 3, "DataVersion")
    w_int(b, DATA_VERSION)

    xs = [p[0] for p in voxels]
    ys = [p[1] for p in voxels]
    zs = [p[2] for p in voxels]
    minx, miny, minz = min(xs), min(ys), min(zs)
    size = (max(xs) - minx + 1, max(ys) - miny + 1, max(zs) - minz + 1)
    w_list_header(b, "size", 3, 3)
    for v in size:
        w_int(b, v)

    # palette: a list of compounds, each just {Name: "..."} -- no block states to carry
    w_list_header(b, "palette", 10, len(names))
    for n in names:
        tag(b, 8, "Name")
        w_str(b, n)
        w_byte(b, 0)          # end of this compound

    w_list_header(b, "blocks", 10, len(voxels))
    for (x, y, z), name in voxels.items():
        w_list_header(b, "pos", 3, 3)
        w_int(b, x - minx)
        w_int(b, y - miny)
        w_int(b, z - minz)
        tag(b, 3, "state")
        w_int(b, index[name])
        w_byte(b, 0)

    w_list_header(b, "entities", 10, 0)

    w_byte(b, 0)              # end of root
    return b.getvalue(), size, len(names)


if __name__ == "__main__":
    voxels = build()
    raw, size, palette_size = write_nbt(voxels)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, "wb") as fh:
        fh.write(raw)
    print("  wrote darius_statue.nbt: %dx%dx%d, %d blocks, %d palette entries"
          % (size[0], size[1], size[2], len(voxels), palette_size))
