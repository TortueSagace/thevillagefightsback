"""
Generates the Fertile/Rich block assets for "Go Back To Your World":
  - Fertile X = the vanilla block texture hue-shifted toward green (still recognizable).
  - Rich X    = Fertile X with emerald gems overlaid (gems = emerald_ore minus stone).
Writes textures + blockstates + block/item models + loot tables. Java registration is done
separately. Re-runnable (overwrites). Source textures come from the vanilla client "extra" jar.
"""
import json, math, os, random, zipfile, zlib
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # villager-arms-mod/
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "thevillagefightsback")
MODID = "thevillagefightsback"

# Name suffix for a COLOUR VARIANT set ("" = the original green set). Everything the block family writes --
# texture, mcmeta, model, blockstate, loot -- goes through sfx(), so setting this and re-running produces a
# complete parallel set under e.g. "fertile_dirt_cyan" without touching the green one.
#
# Deliberately NOT applied to the one-off items (rich sheep, emerald apple, emerald tools, virus arrow):
# those are single things, not part of the recoloured family.
SUFFIX = ""


def sfx(name):
    return name + SUFFIX
JAR = (r"C:\Users\Administrator\AppData\Roaming\ModrinthApp\meta\libraries\net\minecraft"
       r"\client\1.20.1-20230612.114412\client-1.20.1-20230612.114412-extra.jar")

GREEN_BLEND = 0.58         # how far to push toward green (0=none, 1=fully green-luminance)
GREEN = (0.22, 1.00, 0.20) # the green the tint pushes toward -- saturated (low red/blue) so it reads vivid
GREEN_LIFT = 0.30          # brightness floor (0..1): lifts dark pixels so the tint is vivid, not muddy/black-green
GREEN_GAMMA = 0.80         # <1 brightens mid-tones (more punch)

# base id -> (kind, vanilla source texture(s)). kind: "all" = cube_all, "log" = cube_column.
BASES = {
    "dirt":       ("all", "dirt"),
    "sand":       ("all", "sand"),
    # Vanilla sandstone is top/bottom/side, but the only multi-face kind here is "log" -- and that comes with
    # axis blockstate variants the (non-pillar) fertile_sandstone block has no AXIS property for. So we take
    # the side texture on all 6 faces; it is the face you actually see in a wall.
    "sandstone":  ("all", "sandstone"),
    "stone":      ("all", "stone"),
    "log":        ("log", ("oak_log", "oak_log_top")),
    "deepslate":  ("log", ("deepslate", "deepslate_top")),  # axis pillar like logs (side + top)
    "gravel":     ("all", "gravel"),
    "netherrack": ("all", "netherrack"),
    "end_stone":  ("all", "end_stone"),
    "sculk":      ("all", "sculk"),
    "leaves":     ("leaves", "oak_leaves"),  # cutout cube_all (non-decaying), green-tinted
    "blackstone": ("all", "blackstone"),
    "basalt":     ("log", ("basalt_side", "basalt_top")),  # axis pillar like logs
    "soul_sand":  ("all", "soul_sand"),
    "soul_soil":  ("all", "soul_soil"),
    "snow_block": ("all", "snow"),
}

def mkdir(p): os.makedirs(p, exist_ok=True)

def load_vanilla(zf, name, crop=True, folder="block"):
    """Load assets/minecraft/textures/<folder>/<name>.png. By default crops to its first square frame
    (handles animated textures); pass crop=False to keep the full animation strip."""
    with zf.open(f"assets/minecraft/textures/{folder}/{name}.png") as fh:
        img = Image.open(fh).convert("RGBA")
    w, h = img.size
    if crop and h > w:  # animation strip -> take the top frame
        img = img.crop((0, 0, w, w))
    return img

def overlay_strip(base, overlay):
    """Composite the 16x16 gem overlay onto EACH frame of a (possibly multi-frame) vertical strip."""
    out = base.convert("RGBA").copy()
    w, h = base.size
    ov = overlay if overlay.size == (w, w) else overlay.resize((w, w), Image.NEAREST)
    for i in range(max(1, h // w)):
        out.alpha_composite(ov, (0, i * w))
    return out

SCULK_MCMETA = {"animation": {"frametime": 20, "interpolate": True}}

def green_tint(img):
    """Blend each pixel toward a brightness-modulated green, preserving texture detail and alpha."""
    src = img.load()
    out = Image.new("RGBA", img.size)
    dst = out.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            lum = lum ** GREEN_GAMMA                       # brighten mid-tones
            lum = GREEN_LIFT + (1.0 - GREEN_LIFT) * lum    # floor so darks stay vividly green, not muddy
            tr, tg, tb = 255 * lum * GREEN[0], 255 * lum * GREEN[1], 255 * lum * GREEN[2]
            nr = int(round(r * (1 - GREEN_BLEND) + tr * GREEN_BLEND))
            ng = int(round(g * (1 - GREEN_BLEND) + tg * GREEN_BLEND))
            nb = int(round(b * (1 - GREEN_BLEND) + tb * GREEN_BLEND))
            dst[x, y] = (min(255, nr), min(255, ng), min(255, nb), a)
    return out

def gem_overlay(zf):
    """ONLY the green emerald crystals of emerald ore, as a transparent overlay. We keep a pixel only if
    it is clearly GREEN (g dominates r and b) -- this drops the gray stone background AND the gray gem
    outline / shading, so rich = fertile + emerald gems (no leftover stone). 16x16 (vanilla native size)."""
    ore = load_vanilla(zf, "emerald_ore")
    op = ore.load()
    w, h = ore.size
    overlay = Image.new("RGBA", ore.size, (0, 0, 0, 0))
    od = overlay.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = op[x, y]
            # Emerald gems are green: green channel meaningfully above red & blue. Gray stone / outline
            # (r ~= g ~= b) fails this and stays transparent.
            if a > 0 and g - r >= 12 and g - b >= 6 and g >= 50:
                od[x, y] = (r, g, b, 255)
    return overlay

# Salt/pepper "glow" specks scattered on fertile blocks so a fertile zone twinkles green at night
# (the blocks render emissive/fullbright, so these read as a soft green glow visible from afar).
# Bright green = salt (the glow), dark green = pepper (contrast). Kept sparse so it isn't invasive.
SALT = (130, 255, 120)
PEPPER = (14, 74, 30)

def salt_pepper(img, name, salt=6, pepper=5):
    """Scatter a few bright/dark green specks onto the OPAQUE pixels of img (deterministic per name)."""
    out = img.convert("RGBA").copy()
    px = out.load()
    w, h = out.size
    rnd = random.Random(zlib.crc32(name.encode()))
    placed, attempts = 0, 0
    while placed < salt + pepper and attempts < 1000:
        attempts += 1
        x, y = rnd.randrange(w), rnd.randrange(h)
        r, g, b, a = px[x, y]
        if a < 250:                      # skip transparent/translucent pixels (e.g. leaf gaps)
            continue
        col = SALT if placed < salt else PEPPER
        px[x, y] = (col[0], col[1], col[2], a)
        placed += 1
    return out

# Animated glow: salt specks PULSE in brightness (sculk-like, phase-offset twinkle) across N frames so
# the green glow isn't static; pepper specks are baked static for contrast.
SALT_DIM = (36, 110, 46)   # the salt speck at its dimmest (still green)
FRAMES = 8
FRAMETIME = 6              # ticks/frame; with interpolate -> a smooth ~2.4s breathe cycle

def _speck_positions(img, name, salt, pepper):
    """Deterministic, distinct (x,y) speck positions on OPAQUE pixels: first `salt`, then `pepper`."""
    px = img.load(); w, h = img.size
    rnd = random.Random(zlib.crc32(name.encode()))
    chosen, used, attempts = [], set(), 0
    while len(chosen) < salt + pepper and attempts < 4000:
        attempts += 1
        x, y = rnd.randrange(w), rnd.randrange(h)
        if (x, y) in used or px[x, y][3] < 250:
            continue
        used.add((x, y)); chosen.append((x, y))
    return chosen[:salt], chosen[salt:salt + pepper], rnd

def animate_specks(img, name, frames=FRAMES, salt=6, pepper=5):
    """Return a vertical animation strip of `img` where salt specks gently pulse green (phase-offset) and
    pepper specks are static dark green. Used with an interpolating .mcmeta + emissive block rendering."""
    base = img.convert("RGBA").copy()
    w, h = base.size
    salts, peppers, rnd = _speck_positions(base, name, salt, pepper)
    bp = base.load()
    for (x, y) in peppers:                                   # bake pepper (static)
        bp[x, y] = (PEPPER[0], PEPPER[1], PEPPER[2], bp[x, y][3])
    phases = [rnd.random() for _ in salts]                   # desync the twinkle per speck
    strip = Image.new("RGBA", (w, w * frames), (0, 0, 0, 0))
    for f in range(frames):
        frame = base.copy(); fp = frame.load()
        for (x, y), ph in zip(salts, phases):
            t = 0.5 + 0.5 * math.sin(2 * math.pi * (f / frames + ph))
            r = int(round(SALT_DIM[0] + (SALT[0] - SALT_DIM[0]) * t))
            g = int(round(SALT_DIM[1] + (SALT[1] - SALT_DIM[1]) * t))
            b = int(round(SALT_DIM[2] + (SALT[2] - SALT_DIM[2]) * t))
            fp[x, y] = (r, g, b, fp[x, y][3])
        strip.paste(frame, (0, f * w))
    return strip

def save_anim_mcmeta(name, frametime=FRAMETIME):
    d = os.path.join(ASSETS, "textures", "block"); mkdir(d)
    with open(os.path.join(d, name + ".png.mcmeta"), "w", encoding="utf-8") as fh:
        json.dump({"animation": {"frametime": frametime, "interpolate": True}}, fh, indent=2)

# Emerald flecks for the rich sheep wool: small gems scattered at the texture's NATIVE resolution (instead
# of stretching the 16x16 ore overlay, which made blobby smears). Three shades give a faceted gem look.
EMERALD_HI = (130, 255, 160)
EMERALD_BRIGHT = (44, 224, 120)
EMERALD_MED = (22, 150, 86)

def emerald_fleck(img, name, density=0.10):
    """Sprinkle small emerald gems onto the OPAQUE pixels of img (deterministic per name)."""
    out = img.convert("RGBA").copy()
    px = out.load(); w, h = out.size
    rnd = random.Random(zlib.crc32(name.encode()))
    for y in range(h):
        for x in range(w):
            if px[x, y][3] < 250 or rnd.random() >= density:
                continue
            t = rnd.random()
            col = EMERALD_HI if t < 0.15 else (EMERALD_BRIGHT if t < 0.6 else EMERALD_MED)
            px[x, y] = (col[0], col[1], col[2], px[x, y][3])
    return out

def overlay_onto(base, overlay):
    if overlay.size != base.size:
        overlay = overlay.resize(base.size, Image.NEAREST)
    out = base.convert("RGBA").copy()
    out.alpha_composite(overlay)
    return out

def save_png(img, name):
    d = os.path.join(ASSETS, "textures", "block"); mkdir(d)
    img.save(os.path.join(d, name + ".png"))

def save_mcmeta(name):
    d = os.path.join(ASSETS, "textures", "block"); mkdir(d)
    with open(os.path.join(d, name + ".png.mcmeta"), "w", encoding="utf-8") as f:
        json.dump(SCULK_MCMETA, f, indent=2)

def write_json(path, obj):
    mkdir(os.path.dirname(path))
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)

def tex(name): return f"{MODID}:block/{name}"

def write_cube_all(bid):
    write_json(os.path.join(ASSETS, "blockstates", bid + ".json"),
               {"variants": {"": {"model": tex(bid)}}})
    write_json(os.path.join(ASSETS, "models", "block", bid + ".json"),
               {"parent": "minecraft:block/cube_all", "textures": {"all": tex(bid)}})
    write_json(os.path.join(ASSETS, "models", "item", bid + ".json"),
               {"parent": f"{MODID}:block/{bid}"})

def write_leaves(bid):
    # cube_all but cutout (the leaf gaps are transparent). Plain block (no decay) -> simple variant.
    write_json(os.path.join(ASSETS, "blockstates", bid + ".json"),
               {"variants": {"": {"model": tex(bid)}}})
    write_json(os.path.join(ASSETS, "models", "block", bid + ".json"),
               {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped",
                "textures": {"all": tex(bid)}})
    write_json(os.path.join(ASSETS, "models", "item", bid + ".json"),
               {"parent": f"{MODID}:block/{bid}"})

def write_log(bid):
    # cube_column with axis variants, like vanilla logs
    write_json(os.path.join(ASSETS, "blockstates", bid + ".json"), {"variants": {
        "axis=y": {"model": tex(bid)},
        "axis=z": {"model": tex(bid), "x": 90},
        "axis=x": {"model": tex(bid), "x": 90, "y": 90},
    }})
    write_json(os.path.join(ASSETS, "models", "block", bid + ".json"),
               {"parent": "minecraft:block/cube_column",
                "textures": {"side": tex(bid), "end": tex(bid + "_top")}})
    write_json(os.path.join(ASSETS, "models", "item", bid + ".json"),
               {"parent": f"{MODID}:block/{bid}"})

def write_loot_leaves(bid):
    # leaves drop themselves (like our other fertile blocks) AND occasionally an emerald apple (like oak->apple)
    write_json(os.path.join(DATA, "loot_tables", "blocks", bid + ".json"), {
        "type": "minecraft:block",
        "pools": [
            {"rolls": 1, "bonus_rolls": 0,
             "entries": [{"type": "minecraft:item", "name": f"{MODID}:{bid}"}],
             "conditions": [{"condition": "minecraft:survives_explosion"}]},
            {"rolls": 1, "bonus_rolls": 0,
             "entries": [{"type": "minecraft:item", "name": f"{MODID}:emerald_apple"}],
             "conditions": [
                 {"condition": "minecraft:survives_explosion"},
                 {"condition": "minecraft:random_chance", "chance": 0.01}]},
        ]
    })

def write_loot_self(bid):
    write_json(os.path.join(DATA, "loot_tables", "blocks", bid + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": f"{MODID}:{bid}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]
    })

def write_loot_emerald(bid):
    # emerald-ore style: silk touch -> the block itself; otherwise 1 emerald with fortune (ore_drops).
    write_json(os.path.join(DATA, "loot_tables", "blocks", bid + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{
            "type": "minecraft:alternatives",
            "children": [
                {"type": "minecraft:item", "name": f"{MODID}:{bid}", "conditions": [
                    {"condition": "minecraft:match_tool", "predicate": {"enchantments": [
                        {"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}]}}]},
                {"type": "minecraft:item", "name": "minecraft:emerald", "functions": [
                    {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                     "formula": "minecraft:ore_drops"},
                    {"function": "minecraft:explosion_decay"}]}
            ]
        }]}]
    })

def save_item_png(img, name):
    d = os.path.join(ASSETS, "textures", "item"); mkdir(d)
    img.save(os.path.join(d, name + ".png"))

# facing -> base (closed) Y rotation for a door
DOOR_FACING_ROT = {"east": 0, "south": 90, "west": 180, "north": 270}

def gen_door():
    """Generate the single 'fertile_door' (replaces any wooden door). Textures green-tinted from oak_door;
    blockstate/models reuse the vanilla door parents; loot drops once (lower half)."""
    name = sfx("fertile_door")
    with zipfile.ZipFile(JAR) as zf:
        save_png(green_tint(load_vanilla(zf, "oak_door_bottom")), name + "_bottom")
        save_png(green_tint(load_vanilla(zf, "oak_door_top")), name + "_top")
        save_item_png(green_tint(load_vanilla(zf, "oak_door", folder="item")), name)

    # 8 block models (parents are the vanilla door geometry; we just re-skin the top/bottom textures).
    for half in ("bottom", "top"):
        for side in ("left", "right"):
            for op in ("", "_open"):
                mname = f"{name}_{half}_{side}{op}"
                write_json(os.path.join(ASSETS, "models", "block", mname + ".json"),
                           {"parent": f"minecraft:block/door_{half}_{side}{op}",
                            "textures": {"top": tex(name + "_top"), "bottom": tex(name + "_bottom")}})

    # blockstate: 4 facings x {lower,upper} x {left,right} x {closed,open}
    variants = {}
    for half, hmodel in (("lower", "bottom"), ("upper", "top")):
        for hinge in ("left", "right"):
            for is_open in (False, True):
                for facing, base_rot in DOOR_FACING_ROT.items():
                    rot = base_rot
                    if is_open:
                        rot = (base_rot + (90 if hinge == "left" else 270)) % 360
                    model = f"{MODID}:block/{name}_{hmodel}_{hinge}{'_open' if is_open else ''}"
                    val = {"model": model}
                    if rot != 0:
                        val["y"] = rot
                    key = f"facing={facing},half={half},hinge={hinge},open={'true' if is_open else 'false'}"
                    variants[key] = val
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})

    # item model + single-drop loot (only the lower half drops, like vanilla doors)
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{name}"}})
    write_json(os.path.join(DATA, "loot_tables", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": f"{MODID}:{name}"}],
                   "conditions": [
                       {"condition": "minecraft:block_state_property", "block": f"{MODID}:{name}",
                        "properties": {"half": "lower"}},
                       {"condition": "minecraft:survives_explosion"}]}]
    })
    print("generated", name)

def _chorus_flower_models(name, flower_tex, dead_tex, bottom_tex):
    """Author the template_chorus_flower + flower + dead block models (retextured) for `name`.
    Mirrors vanilla block/template_chorus_flower (the bottom faces use the plant texture)."""
    tmpl = name + "_template"
    write_json(os.path.join(ASSETS, "models", "block", tmpl + ".json"), {
        "parent": "minecraft:block/block",
        "textures": {"bottom": tex(bottom_tex), "particle": "#texture"},
        "elements": [
            {"from": [2, 14, 2], "to": [14, 16, 14], "faces": {
                "up":    {"uv": [2, 2, 14, 14], "texture": "#texture"},
                "north": {"uv": [2, 0, 14, 2], "texture": "#bottom"},
                "south": {"uv": [2, 0, 14, 2], "texture": "#bottom"},
                "west":  {"uv": [2, 0, 14, 2], "texture": "#bottom"},
                "east":  {"uv": [2, 0, 14, 2], "texture": "#bottom"}}},
            {"from": [0, 2, 2], "to": [2, 14, 14], "faces": {
                "down":  {"uv": [16, 14, 14, 2], "texture": "#bottom"},
                "up":    {"uv": [0, 2, 2, 14], "texture": "#bottom"},
                "north": {"uv": [14, 2, 16, 14], "texture": "#bottom"},
                "south": {"uv": [0, 2, 2, 14], "texture": "#bottom"},
                "west":  {"uv": [2, 2, 14, 14], "texture": "#texture"}}},
            {"from": [2, 2, 0], "to": [14, 14, 2], "faces": {
                "down":  {"uv": [14, 2, 2, 0], "texture": "#bottom"},
                "up":    {"uv": [2, 0, 14, 2], "texture": "#bottom"},
                "north": {"uv": [2, 2, 14, 14], "texture": "#texture"},
                "west":  {"uv": [0, 2, 2, 14], "texture": "#bottom"},
                "east":  {"uv": [14, 2, 16, 14], "texture": "#bottom"}}},
            {"from": [2, 2, 14], "to": [14, 14, 16], "faces": {
                "down":  {"uv": [14, 16, 2, 14], "texture": "#bottom"},
                "up":    {"uv": [2, 14, 14, 16], "texture": "#bottom"},
                "south": {"uv": [2, 2, 14, 14], "texture": "#texture"},
                "west":  {"uv": [14, 2, 16, 14], "texture": "#bottom"},
                "east":  {"uv": [0, 2, 2, 14], "texture": "#bottom"}}},
            {"from": [14, 2, 2], "to": [16, 14, 14], "faces": {
                "down":  {"uv": [2, 14, 0, 2], "texture": "#bottom"},
                "up":    {"uv": [14, 2, 16, 14], "texture": "#bottom"},
                "north": {"uv": [0, 2, 2, 14], "texture": "#bottom"},
                "south": {"uv": [14, 2, 16, 14], "texture": "#bottom"},
                "east":  {"uv": [2, 2, 14, 14], "texture": "#texture"}}},
            {"from": [2, 0, 2], "to": [14, 14, 14], "faces": {
                "up":    {"uv": [2, 2, 14, 14], "texture": "#bottom"},
                "down":  {"uv": [14, 14, 2, 2], "texture": "#bottom"},
                "north": {"uv": [2, 2, 14, 16], "texture": "#bottom"},
                "south": {"uv": [2, 2, 14, 16], "texture": "#bottom"},
                "west":  {"uv": [2, 2, 14, 16], "texture": "#bottom"},
                "east":  {"uv": [2, 2, 14, 16], "texture": "#bottom"}}},
        ]
    })
    # the live flower model + dead model
    write_json(os.path.join(ASSETS, "models", "block", name + ".json"),
               {"parent": tex(tmpl), "textures": {"texture": tex(flower_tex)}})
    write_json(os.path.join(ASSETS, "models", "block", name + "_dead.json"),
               {"parent": tex(tmpl), "textures": {"texture": tex(dead_tex)}})


def _chorus_plant_models(name, plant_tex):
    """Author the chorus_plant + side/noside connection models (retextured) for `name`."""
    t = {"texture": tex(plant_tex), "inside": tex(plant_tex), "particle": tex(plant_tex)}
    write_json(os.path.join(ASSETS, "models", "block", name + ".json"), {
        "parent": "minecraft:block/block", "textures": dict(t),
        "elements": [
            {"from": [2, 14, 2], "to": [14, 16, 14], "faces": {
                "up":    {"uv": [2, 2, 14, 14], "texture": "#texture", "cullface": "up"},
                "north": {"uv": [2, 0, 14, 2], "texture": "#texture", "cullface": "up"},
                "south": {"uv": [2, 0, 14, 2], "texture": "#texture", "cullface": "up"},
                "west":  {"uv": [2, 0, 14, 2], "texture": "#texture", "cullface": "up"},
                "east":  {"uv": [2, 0, 14, 2], "texture": "#texture", "cullface": "up"}}},
            {"from": [0, 2, 2], "to": [2, 14, 14], "faces": {
                "down":  {"uv": [16, 14, 14, 2], "texture": "#texture", "cullface": "west"},
                "up":    {"uv": [0, 2, 2, 14], "texture": "#texture", "cullface": "west"},
                "north": {"uv": [14, 2, 16, 14], "texture": "#texture", "cullface": "west"},
                "south": {"uv": [0, 2, 2, 14], "texture": "#texture", "cullface": "west"},
                "west":  {"uv": [2, 2, 14, 14], "texture": "#texture", "cullface": "west"}}},
            {"from": [2, 2, 0], "to": [14, 14, 2], "faces": {
                "down":  {"uv": [14, 2, 2, 0], "texture": "#texture", "cullface": "north"},
                "up":    {"uv": [2, 0, 14, 2], "texture": "#texture", "cullface": "north"},
                "north": {"uv": [2, 2, 14, 14], "texture": "#texture", "cullface": "north"},
                "west":  {"uv": [0, 2, 2, 14], "texture": "#texture", "cullface": "north"},
                "east":  {"uv": [14, 2, 16, 14], "texture": "#texture", "cullface": "north"}}},
            {"from": [2, 2, 14], "to": [14, 14, 16], "faces": {
                "down":  {"uv": [14, 16, 2, 14], "texture": "#texture", "cullface": "south"},
                "up":    {"uv": [2, 14, 14, 16], "texture": "#texture", "cullface": "south"},
                "south": {"uv": [2, 2, 14, 14], "texture": "#texture", "cullface": "south"},
                "west":  {"uv": [14, 2, 16, 14], "texture": "#texture", "cullface": "south"},
                "east":  {"uv": [0, 2, 2, 14], "texture": "#texture", "cullface": "south"}}},
            {"from": [14, 2, 2], "to": [16, 14, 14], "faces": {
                "down":  {"uv": [2, 14, 0, 2], "texture": "#texture", "cullface": "east"},
                "up":    {"uv": [14, 2, 16, 14], "texture": "#texture", "cullface": "east"},
                "north": {"uv": [0, 2, 2, 14], "texture": "#texture", "cullface": "east"},
                "south": {"uv": [14, 2, 16, 14], "texture": "#texture", "cullface": "east"},
                "east":  {"uv": [2, 2, 14, 14], "texture": "#texture", "cullface": "east"}}},
            {"from": [2, 0, 2], "to": [14, 2, 14], "faces": {
                "down":  {"uv": [14, 14, 2, 2], "texture": "#texture", "cullface": "down"},
                "north": {"uv": [2, 14, 14, 16], "texture": "#texture", "cullface": "down"},
                "south": {"uv": [2, 14, 14, 16], "texture": "#texture", "cullface": "down"},
                "west":  {"uv": [2, 14, 14, 16], "texture": "#texture", "cullface": "down"},
                "east":  {"uv": [2, 14, 14, 16], "texture": "#texture", "cullface": "down"}}},
            {"from": [2, 2, 2], "to": [14, 14, 14], "faces": {
                "down":  {"uv": [14, 14, 2, 2], "texture": "#inside"},
                "up":    {"uv": [2, 2, 14, 14], "texture": "#inside"},
                "north": {"uv": [2, 2, 14, 14], "texture": "#inside"},
                "south": {"uv": [2, 2, 14, 14], "texture": "#inside"},
                "west":  {"uv": [2, 2, 14, 14], "texture": "#inside"},
                "east":  {"uv": [2, 2, 14, 14], "texture": "#inside"}}},
        ]
    })
    # side connection model
    write_json(os.path.join(ASSETS, "models", "block", name + "_side.json"), {
        "ambientocclusion": False, "textures": dict(t),
        "elements": [{"from": [4, 4, 0], "to": [12, 12, 4], "faces": {
            "down":  {"texture": "#texture"}, "up": {"texture": "#texture"},
            "north": {"texture": "#texture", "cullface": "north"},
            "west":  {"texture": "#texture"}, "east": {"texture": "#texture"}}}]
    })
    # noside (a stub pointing inward) + 3 longer stub variants
    write_json(os.path.join(ASSETS, "models", "block", name + "_noside.json"), {
        "ambientocclusion": False, "textures": dict(t),
        "elements": [{"from": [4, 4, 4], "to": [12, 12, 12], "faces": {"north": {"texture": "#inside"}}}]
    })
    for n, frm in (("1", [4, 4, 3]), ("2", [5, 5, 2]), ("3", [4, 4, 3])):
        to = [12, 12, 4] if n != "2" else [11, 11, 4]
        write_json(os.path.join(ASSETS, "models", "block", name + "_noside" + n + ".json"), {
            "ambientocclusion": False, "textures": dict(t),
            "elements": [
                {"from": [4, 4, 4], "to": [12, 12, 12], "faces": {"north": {"texture": "#inside"}}},
                {"from": frm, "to": to, "faces": {
                    "down":  {"texture": "#texture"}, "up": {"texture": "#texture"},
                    "north": {"texture": "#texture"}, "west": {"texture": "#texture"},
                    "east":  {"texture": "#texture"}}},
            ]
        })


def _chorus_plant_blockstate(name):
    """The multipart chorus_plant blockstate, retextured to `name`'s models (mirrors vanilla)."""
    def m(suffix, **kw):
        d = {"model": tex(name + suffix)}
        d.update(kw)
        return d
    nosides = [m("_noside", weight=2), m("_noside1"), m("_noside2"), m("_noside3")]
    def rotated(rotk, rotv):
        # the 4 noside variants, each with the given rotation + uvlock (order rotates per vanilla)
        order = [("_noside1", {}), ("_noside2", {}), ("_noside3", {}), ("_noside", {"weight": 2})]
        out = []
        for suf, extra in order:
            d = {"model": tex(name + suf), "uvlock": True, rotk: rotv}
            d.update(extra)
            out.append(d)
        return out
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"multipart": [
        {"apply": m("_side"), "when": {"north": "true"}},
        {"apply": m("_side", uvlock=True, y=90), "when": {"east": "true"}},
        {"apply": m("_side", uvlock=True, y=180), "when": {"south": "true"}},
        {"apply": m("_side", uvlock=True, y=270), "when": {"west": "true"}},
        {"apply": m("_side", uvlock=True, x=270), "when": {"up": "true"}},
        {"apply": m("_side", uvlock=True, x=90), "when": {"down": "true"}},
        {"apply": nosides, "when": {"north": "false"}},
        {"apply": rotated("y", 90), "when": {"east": "false"}},
        {"apply": rotated("y", 180), "when": {"south": "false"}},
        {"apply": rotated("y", 270), "when": {"west": "false"}},
        {"apply": rotated("x", 270), "when": {"up": "false"}},
        {"apply": rotated("x", 90), "when": {"down": "false"}},
    ]})


def gen_chorus(gems):
    """fertile_/rich_ chorus plant + flower. Fertile = green-tinted vanilla chorus textures; Rich adds the
    emerald-gem overlay. Blockstates/models mirror vanilla chorus (flower = age variants -> flower/dead model;
    plant = multipart connection models). Fertile flower drops 'fertile_chorus' (a later batch's item); fertile
    plant drops itself; rich variants drop an emerald."""
    with zipfile.ZipFile(JAR) as zf:
        flower = green_tint(load_vanilla(zf, "chorus_flower"))
        dead = green_tint(load_vanilla(zf, "chorus_flower_dead"))
        plant = green_tint(load_vanilla(zf, "chorus_plant"))
    # textures
    save_png(flower, sfx("fertile_chorus_flower")); save_png(dead, sfx("fertile_chorus_flower_dead"))
    save_png(plant, sfx("fertile_chorus_plant"))
    save_png(overlay_onto(flower, gems), sfx("rich_chorus_flower"))
    save_png(overlay_onto(dead, gems), sfx("rich_chorus_flower_dead"))
    save_png(overlay_onto(plant, gems), sfx("rich_chorus_plant"))

    for variant in ("fertile", "rich"):
        fl = sfx(variant + "_chorus_flower")
        pl = sfx(variant + "_chorus_plant")
        # ---- flower: block models, age blockstate, item model ----
        _chorus_flower_models(fl, fl, fl + "_dead", pl)
        write_json(os.path.join(ASSETS, "blockstates", fl + ".json"), {"variants": {
            "age=0": {"model": tex(fl)}, "age=1": {"model": tex(fl)}, "age=2": {"model": tex(fl)},
            "age=3": {"model": tex(fl)}, "age=4": {"model": tex(fl)}, "age=5": {"model": tex(fl + "_dead")},
        } if variant == "fertile" else {"": {"model": tex(fl)}}})  # rich flower has no age property
        write_json(os.path.join(ASSETS, "models", "item", fl + ".json"), {"parent": tex(fl)})
        # ---- plant: connection models, multipart blockstate, item model (item/generated) ----
        _chorus_plant_models(pl, pl)
        _chorus_plant_blockstate(pl)
        # Item icon: use the 3D block model so the creative menu shows a green chorus plant, not a flat square.
        write_json(os.path.join(ASSETS, "models", "item", pl + ".json"), {"parent": tex(pl)})

    # ---- loot ----
    # fertile flower drops itself; fertile plant drops the (later-batch) fertile_chorus item.
    write_loot_self(sfx("fertile_chorus_flower"))
    write_json(os.path.join(DATA, "loot_tables", "blocks", "fertile_chorus_plant.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": f"{MODID}:fertile_chorus"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]
    })
    (write_loot_self if SUFFIX else write_loot_emerald)(sfx("rich_chorus_flower"))
    (write_loot_self if SUFFIX else write_loot_emerald)(sfx("rich_chorus_plant"))
    print("generated chorus (fertile + rich, plant + flower)")


def main():
    with zipfile.ZipFile(JAR) as zf:
        gems = gem_overlay(zf)
        for base, (kind, srcs) in BASES.items():
            fseed, rseed = f"fertile_{base}", f"rich_{base}"   # speck seeds: identical across colours
            fertile, rich = sfx(fseed), sfx(rseed)
            if base == "sculk":
                # Keep sculk ANIMATED (full strip + .mcmeta) so it pulses/glows like vanilla sculk.
                ft = green_tint(load_vanilla(zf, srcs, crop=False))
                save_png(ft, fertile); save_mcmeta(fertile)
                save_png(overlay_strip(ft, gems), rich); save_mcmeta(rich)
                write_cube_all(fertile); write_cube_all(rich)
            elif kind == "leaves":
                ft = animate_specks(green_tint(load_vanilla(zf, srcs)), fseed)
                save_png(ft, fertile); save_anim_mcmeta(fertile)
                save_png(overlay_strip(ft, gems), rich); save_anim_mcmeta(rich)
                write_leaves(fertile); write_leaves(rich)
            elif kind == "all":
                ft = animate_specks(green_tint(load_vanilla(zf, srcs)), fseed)
                save_png(ft, fertile); save_anim_mcmeta(fertile)
                save_png(overlay_strip(ft, gems), rich); save_anim_mcmeta(rich)
                write_cube_all(fertile); write_cube_all(rich)
            else:  # log: side + top (both animated)
                side, top = srcs
                fs = animate_specks(green_tint(load_vanilla(zf, side)), fseed)
                ftp = animate_specks(green_tint(load_vanilla(zf, top)), fseed + "_top")
                save_png(fs, fertile); save_anim_mcmeta(fertile)
                save_png(ftp, fertile + "_top"); save_anim_mcmeta(fertile + "_top")
                save_png(overlay_strip(fs, gems), rich); save_anim_mcmeta(rich)
                save_png(overlay_strip(ftp, gems), rich + "_top"); save_anim_mcmeta(rich + "_top")
                write_log(fertile); write_log(rich)
            if base == "leaves":
                write_loot_leaves(fertile) # fertile leaves also drop the emerald apple
            else:
                write_loot_self(fertile)
            write_loot_self(rich) if SUFFIX else write_loot_emerald(rich)
            print("generated", fertile, "+", rich)
    gen_door()
    gen_chorus(gems)
    gen_catalyst(gems)
    gen_fertile_wood_cobble()
    if SUFFIX:
        return   # a colour run stops here: the items below are one-offs, not part of the recoloured family
    gen_emerald_apple(gems)
    gen_fertile_chorus_item(gems)
    gen_emerald_virus_arrow()
    gen_emerald_tools()
    gen_rich_sheep(gems)

def gen_rich_sheep(gems):
    """The Rich Sheep entity textures: green-tinted sheep body + green-tinted, emerald-flecked fur.
    The body uses entity/sheep/sheep.png; the fur uses entity/sheep/sheep_fur.png with the gem overlay."""
    with zipfile.ZipFile(JAR) as zf:
        body = green_tint(load_vanilla(zf, "sheep/sheep", folder="entity"))
        fur = green_tint(load_vanilla(zf, "sheep/sheep_fur", folder="entity"))
    # Emerald flecks at the texture's native resolution -> crisp gem-studded wool (no stretched smears).
    body = emerald_fleck(body, "rich_sheep_body", density=0.04)   # a sprinkle on the face/legs
    fur = emerald_fleck(fur, "rich_sheep_fur", density=0.11)      # gem-studded wool
    d = os.path.join(ASSETS, "textures", "entity"); mkdir(d)
    body.save(os.path.join(d, "rich_sheep.png"))
    fur.save(os.path.join(d, "rich_sheep_fur.png"))
    print("generated rich_sheep + rich_sheep_fur")

def gen_fertile_wood_cobble():
    """Craftable fertile wood + cobble: fertile_planks (from fertile logs) and fertile_cobblestone (from
    mining fertile stone). Cube textures/models/items/loot here; stairs/slabs reuse the planks texture
    (their JSON is authored separately)."""
    with zipfile.ZipFile(JAR) as zf:
        planks = animate_specks(green_tint(load_vanilla(zf, "oak_planks")), "fertile_planks")
        cobble = animate_specks(green_tint(load_vanilla(zf, "cobblestone")), "fertile_cobblestone")
    save_png(planks, sfx("fertile_planks")); save_anim_mcmeta(sfx("fertile_planks"))
    save_png(cobble, sfx("fertile_cobblestone")); save_anim_mcmeta(sfx("fertile_cobblestone"))
    for bid in (sfx("fertile_planks"), sfx("fertile_cobblestone")):
        write_cube_all(bid)
        write_loot_self(bid)
        print("generated", bid)

def gen_emerald_tools():
    """Emerald tool item sprites: the iron tools, green-tinted. Handheld item models."""
    tools = {"emerald_sword": "iron_sword", "emerald_pickaxe": "iron_pickaxe", "emerald_axe": "iron_axe",
             "emerald_shovel": "iron_shovel", "emerald_hoe": "iron_hoe"}
    with zipfile.ZipFile(JAR) as zf:
        for out, src in tools.items():
            save_item_png(green_tint(load_vanilla(zf, src, folder="item")), out)
            write_json(os.path.join(ASSETS, "models", "item", out + ".json"),
                       {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{MODID}:item/{out}"}})
            print("generated", out)

def gen_emerald_virus_arrow():
    """The Emerald Virus arrow: a green-tinted arrow (item sprite + entity texture)."""
    name = "emerald_virus_arrow"
    with zipfile.ZipFile(JAR) as zf:
        item = green_tint(load_vanilla(zf, "arrow", folder="item"))
        ent = green_tint(load_vanilla(zf, "projectiles/arrow", folder="entity"))
    save_item_png(item, name)
    d = os.path.join(ASSETS, "textures", "entity"); mkdir(d)
    ent.save(os.path.join(d, name + ".png"))
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{name}"}})
    print("generated", name)

def gen_emerald_apple(gems):
    """The emerald apple item: a green-tinted apple with the emerald-gem overlay."""
    name = "emerald_apple"
    with zipfile.ZipFile(JAR) as zf:
        img = overlay_onto(green_tint(load_vanilla(zf, "apple", folder="item")), gems)
    save_item_png(img, name)
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{name}"}})
    print("generated", name)

def gen_fertile_chorus_item(gems):
    """The Fertile Chorus food item: a green-tinted chorus fruit with the emerald-gem overlay."""
    name = "fertile_chorus"
    with zipfile.ZipFile(JAR) as zf:
        img = overlay_onto(green_tint(load_vanilla(zf, "chorus_fruit", folder="item")), gems)
    save_item_png(img, name)
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{name}"}})
    print("generated", name)

def gen_catalyst(gems):
    """fertile_sculk_catalyst + rich_sculk_catalyst: green-tinted sculk-catalyst skins (top/bottom/side),
    a cube_bottom_top model, item model and self-drop loot. Rich adds the emerald-gem overlay."""
    with zipfile.ZipFile(JAR) as zf:
        top = green_tint(load_vanilla(zf, "sculk_catalyst_top"))
        bottom = green_tint(load_vanilla(zf, "sculk_catalyst_bottom"))
        side = green_tint(load_vanilla(zf, "sculk_catalyst_side"))
    for variant, gemize in (("fertile", False), ("rich", True)):
        name = sfx(variant + "_sculk_catalyst")
        t, b, s = top, bottom, side
        if gemize:
            t, b, s = overlay_onto(top, gems), overlay_onto(bottom, gems), overlay_onto(side, gems)
        save_png(t, name + "_top"); save_png(b, name + "_bottom"); save_png(s, name + "_side")
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"),
                   {"variants": {"": {"model": tex(name)}}})
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"),
                   {"parent": "minecraft:block/cube_bottom_top",
                    "textures": {"top": tex(name + "_top"), "bottom": tex(name + "_bottom"),
                                 "side": tex(name + "_side")}})
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": f"{MODID}:block/{name}"})
        write_loot_self(name)
        print("generated", name)

if __name__ == "__main__":
    main()
