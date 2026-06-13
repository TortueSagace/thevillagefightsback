"""
Generates the Fertile/Rich block assets for "Go Back To Your World":
  - Fertile X = the vanilla block texture hue-shifted toward green (still recognizable).
  - Rich X    = Fertile X with emerald gems overlaid (gems = emerald_ore minus stone).
Writes textures + blockstates + block/item models + loot tables. Java registration is done
separately. Re-runnable (overwrites). Source textures come from the vanilla client "extra" jar.
"""
import json, os, zipfile
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # villager-arms-mod/
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "gobacktoyourworld")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "gobacktoyourworld")
MODID = "gobacktoyourworld"
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
    """The emerald crystals of emerald ore as a transparent overlay: emerald_ore where it differs
    from plain stone, transparent elsewhere. Scaled to 16x16 (vanilla native size)."""
    ore = load_vanilla(zf, "emerald_ore")
    stone = load_vanilla(zf, "stone").resize(ore.size)
    op, sp = ore.load(), stone.load()
    w, h = ore.size
    overlay = Image.new("RGBA", ore.size, (0, 0, 0, 0))
    od = overlay.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = op[x, y]
            sr, sg, sb, _ = sp[x, y]
            if abs(r - sr) + abs(g - sg) + abs(b - sb) > 24:  # a crystal pixel
                od[x, y] = (r, g, b, 255)
    return overlay

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
    name = "fertile_door"
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

def main():
    with zipfile.ZipFile(JAR) as zf:
        gems = gem_overlay(zf)
        for base, (kind, srcs) in BASES.items():
            fertile, rich = f"fertile_{base}", f"rich_{base}"
            if base == "sculk":
                # Keep sculk ANIMATED (full strip + .mcmeta) so it pulses/glows like vanilla sculk.
                ft = green_tint(load_vanilla(zf, srcs, crop=False))
                save_png(ft, fertile); save_mcmeta(fertile)
                save_png(overlay_strip(ft, gems), rich); save_mcmeta(rich)
                write_cube_all(fertile); write_cube_all(rich)
            elif kind == "leaves":
                ft = green_tint(load_vanilla(zf, srcs))
                save_png(ft, fertile)
                save_png(overlay_onto(ft, gems), rich)
                write_leaves(fertile); write_leaves(rich)
            elif kind == "all":
                ft = green_tint(load_vanilla(zf, srcs))
                save_png(ft, fertile)
                save_png(overlay_onto(ft, gems), rich)
                write_cube_all(fertile); write_cube_all(rich)
            else:  # log: side + top
                side, top = srcs
                fs, ftp = green_tint(load_vanilla(zf, side)), green_tint(load_vanilla(zf, top))
                save_png(fs, fertile); save_png(ftp, fertile + "_top")
                save_png(overlay_onto(fs, gems), rich); save_png(overlay_onto(ftp, gems), rich + "_top")
                write_log(fertile); write_log(rich)
            if base == "leaves":
                write_loot_leaves(fertile) # fertile leaves also drop the emerald apple
            else:
                write_loot_self(fertile)
            write_loot_emerald(rich)
            print("generated", fertile, "+", rich)
    gen_door()
    gen_catalyst(gems)
    gen_emerald_apple(gems)
    gen_emerald_virus_arrow()
    gen_emerald_tools()
    gen_fertile_wood_cobble()

def gen_fertile_wood_cobble():
    """Craftable fertile wood + cobble: fertile_planks (from fertile logs) and fertile_cobblestone (from
    mining fertile stone). Cube textures/models/items/loot here; stairs/slabs reuse the planks texture
    (their JSON is authored separately)."""
    with zipfile.ZipFile(JAR) as zf:
        planks = green_tint(load_vanilla(zf, "oak_planks"))
        cobble = green_tint(load_vanilla(zf, "cobblestone"))
    save_png(planks, "fertile_planks")
    save_png(cobble, "fertile_cobblestone")
    for bid in ("fertile_planks", "fertile_cobblestone"):
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

def gen_catalyst(gems):
    """fertile_sculk_catalyst + rich_sculk_catalyst: green-tinted sculk-catalyst skins (top/bottom/side),
    a cube_bottom_top model, item model and self-drop loot. Rich adds the emerald-gem overlay."""
    with zipfile.ZipFile(JAR) as zf:
        top = green_tint(load_vanilla(zf, "sculk_catalyst_top"))
        bottom = green_tint(load_vanilla(zf, "sculk_catalyst_bottom"))
        side = green_tint(load_vanilla(zf, "sculk_catalyst_side"))
    for variant, gemize in (("fertile", False), ("rich", True)):
        name = variant + "_sculk_catalyst"
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
