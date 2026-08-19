"""
Finish the four colour sets: the nine blocks gen_colour_sets.py does not cover.

gen_fertile_blocks.py generates 41 of the family's 50 blocks. The other nine are either hand-authored JSON
(stairs, slab, fence, iron bars, grass, planks stairs/slab) or live in their own small generators (kelp,
infested path). Rather than duplicate those generators, this copies the GREEN json and rewrites every
reference to carry the colour suffix -- the models for stairs/slab/fence already point at fertile_stone, and a
suffixed fertile_stone_cyan exists, so the copy just works.

Textures are handled two ways:
  * kelp and the infested path go through the same tint-and-speck pipeline as the rest of the family, so they
    match it exactly.
  * fertile_grass and fertile_iron_bars are HAND-DRAWN art with no vanilla source to re-tint, so they are
    hue-rotated from the green original instead. Rotating hue (rather than re-tinting) keeps the artist's
    shading and alpha intact and only moves the colour.

Usage:  python tools/gen_colour_extras.py     (run AFTER gen_colour_sets.py)
"""
import colorsys
import json
import os
import re
import shutil
import zipfile

import gen_fertile_blocks as g
from gen_fertile_blocks import (ASSETS, DATA, JAR, animate_specks, green_tint, load_vanilla,
                                save_anim_mcmeta, save_png)
from gen_colour_sets import COLOURS

# The nine the main generator misses.
COPY_ONLY = ["fertile_stairs", "fertile_slab", "fertile_fence", "fertile_iron_bars", "fertile_grass",
             "fertile_planks_stairs", "fertile_planks_slab", "fertile_kelp", "infested_path"]
# Of those, the two whose textures are hand-drawn rather than generated.
HUE_SHIFT = {"fertile_grass": "fertile_grass", "fertile_iron_bars": "fertile_iron_bars"}
# Target hues, in turns, matched to the tints in gen_colour_sets.COLOURS.
HUES = {"_cyan": 0.50, "_purple": 0.78, "_orange": 0.07, "_yellow": 0.15}


def family_names():
    """Every green block in the family -- what a reference is allowed to be suffixed."""
    bs = os.path.join(ASSETS, "blockstates")
    out = set()
    for f in os.listdir(bs):
        if not f.endswith(".json"):
            continue
        n = f[:-5]
        if any(n.endswith(c) for c in HUES):
            continue
        if n.startswith("fertile_") or n.startswith("rich_") or n == "infested_path":
            out.add(n)
    return out


def retarget(obj, names, suffix):
    """Append the suffix to every reference that names a block in the family."""
    if isinstance(obj, dict):
        return {k: retarget(v, names, suffix) for k, v in obj.items()}
    if isinstance(obj, list):
        return [retarget(v, names, suffix) for v in obj]
    if isinstance(obj, str):
        def sub(m):
            pre, name = m.group(1), m.group(2)
            return pre + name + suffix if name in names else m.group(0)
        return re.sub(r"(thevillagefightsback:(?:block/|item/)?)([a-z0-9_]+)", sub, obj)
    return obj


def copy_json(rel_dir, name, names, suffix):
    src = os.path.join(rel_dir, name + ".json")
    if not os.path.exists(src):
        return False
    with open(src, encoding="utf-8") as f:
        data = json.load(f)
    with open(os.path.join(rel_dir, name + suffix + ".json"), "w", encoding="utf-8") as f:
        json.dump(retarget(data, names, suffix), f, indent=2)
        f.write("\n")
    return True


def hue_shift(name, suffix, hue):
    """Rotate the green art to the target hue, leaving lightness, saturation and alpha alone."""
    from PIL import Image
    d = os.path.join(ASSETS, "textures", "block")
    img = Image.open(os.path.join(d, name + ".png")).convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, gg, b, a = px[x, y]
            if a == 0:
                continue
            _, l, s = colorsys.rgb_to_hls(r / 255.0, gg / 255.0, b / 255.0)
            nr, ng, nb = colorsys.hls_to_rgb(hue, l, s)
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    img.save(os.path.join(d, name + suffix + ".png"))
    meta = os.path.join(d, name + ".png.mcmeta")
    if os.path.exists(meta):
        shutil.copyfile(meta, os.path.join(d, name + suffix + ".png.mcmeta"))


def gen_textures(suffix):
    """kelp + the infested path's two faces, through the family's own pipeline."""
    with zipfile.ZipFile(JAR) as zf:
        kelp = load_vanilla(zf, "kelp_plant")
        top = load_vanilla(zf, "dirt_path_top")
        side = load_vanilla(zf, "dirt_path_side")
    for img, seed in ((kelp, "fertile_kelp"), (top, "infested_path_top"), (side, "infested_path_side")):
        strip = animate_specks(green_tint(img), seed)   # seeded off the GREEN name: identical speck layout
        save_png(strip, seed + suffix)
        save_anim_mcmeta(seed + suffix)


def main():
    names = family_names()
    saved = (g.GREEN, g.SALT, g.SALT_DIM, g.PEPPER, g.GREEN_LIFT, g.GREEN_GAMMA)
    try:
        for suffix, tint, salt, salt_dim, pepper, lift, gamma in COLOURS:
            g.GREEN, g.SALT, g.SALT_DIM, g.PEPPER = tint, salt, salt_dim, pepper
            g.GREEN_LIFT, g.GREEN_GAMMA = lift, gamma
            gen_textures(suffix)
            for name in HUE_SHIFT:
                hue_shift(name, suffix, HUES[suffix])
            n = 0
            for name in COPY_ONLY:
                copy_json(os.path.join(ASSETS, "blockstates"), name, names, suffix)
                copy_json(os.path.join(ASSETS, "models", "item"), name, names, suffix)
                copy_json(os.path.join(DATA, "loot_tables", "blocks"), name, names, suffix)
                # a family may own several block models (stairs -> _inner/_outer, fence -> _post/_side, ...)
                md = os.path.join(ASSETS, "models", "block")
                for f in os.listdir(md):
                    if not f.endswith(".json"):
                        continue
                    stem = f[:-5]
                    if stem == name or stem.startswith(name + "_"):
                        if any(stem.endswith(c) for c in HUES):
                            continue
                        if copy_json(md, stem, names, suffix):
                            n += 1
            print("%-8s extras: %d block models + 9 families" % (suffix.lstrip("_"), n))
    finally:
        (g.GREEN, g.SALT, g.SALT_DIM, g.PEPPER, g.GREEN_LIFT, g.GREEN_GAMMA) = saved


if __name__ == "__main__":
    main()
