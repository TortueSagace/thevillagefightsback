"""
The EMERALD PIPELINE for v1.0.3 "mob infection".

Reuses the rich-sheep recipe from gen_fertile_blocks.py (green_tint + emerald_fleck) and adds the two new
emerald-pipeline steps so every emerald mob matches the Emeraldger / fertile-block family:

  1. BODY  = vanilla entity texture -> green_tint -> emerald_fleck   (baked into <mob>.png; static, reliable)
  2. GLOW  = an EMISSIVE, animated salt/pepper speckle strip (<mob>_glow.png + N frames stacked vertically).
             Entity textures do NOT animate via .mcmeta (only atlas sprites do), so this strip is sampled
             frame-by-frame at runtime by a reusable emissive render layer (EmeraldSpeckleLayer) -- the layer
             reads gameTime to pick the frame, giving the same breathing green glow as fertile blocks.
  3. EYES  = an EMISSIVE overlay of the Emeraldger's vertical green eyes (<mob>_eyes.png), hand-placed per mob
             (endermite = 1 eye). Drawn fullbright by an EyesLayer, exactly like the Emeraldger.

Run:  python tools/gen_emerald_mobs.py
Re-runnable (overwrites). Vanilla source textures come from the client "extra" jar (same as gen_fertile_blocks).
"""
import os, zipfile
from PIL import Image

# Reuse the trusted recipe functions + constants from the fertile-block generator (no main() runs on import).
from gen_fertile_blocks import (green_tint, emerald_fleck, animate_specks, load_vanilla, JAR, ASSETS, mkdir,
                                 SALT, SALT_DIM, PEPPER, gem_overlay)

ENT = os.path.join(ASSETS, "textures", "entity")


def overlay_gems(img, gems16):
    """Stud img with the EXACT rich-block emerald-gem texture: the 16x16 emerald-ore crystal overlay tiled
    across img, keeping a gem pixel ONLY where img is opaque (so gems never bleed off the model). Baked into the
    body, so the emissive eyes/glow layers always draw on top -- i.e. the gems sit BELOW the eyes layer."""
    out = img.convert("RGBA").copy()
    px = out.load()
    gp = gems16.load()
    gw, gh = gems16.size
    w, h = out.size
    for y in range(h):
        for x in range(w):
            if px[x, y][3] < 250:
                continue
            gr, gg, gb, ga = gp[x % gw, y % gh]
            if ga > 0:
                px[x, y] = (gr, gg, gb, px[x, y][3])
    return out

# mob id -> vanilla entity texture sub-path under assets/minecraft/textures/entity/ (1.20.1).
# Order/keys are the v1.0.3 emerald roster (sheep handled separately: it has body + fur).
MOB_TEXTURES = {
    "emerald_cow":        "cow/cow",
    "emerald_chicken":    "chicken",
    "emerald_cat":        "cat/tabby",
    "emerald_endermite":  "endermite",
    "emerald_wolf":       "wolf/wolf",
    "emerald_horse":      "horse/horse_brown",
    "emerald_pig":        "pig/pig",
    "emerald_salmon":     "fish/salmon",
    "emerald_bat":        "bat",
    "emerald_zombie":     "zombie/zombie",
    "emerald_skeleton":   "skeleton/skeleton",
    "emerald_creeper":    "creeper/creeper",
    "emerald_enderman":   "enderman/enderman",
    "emerald_iron_golem": "iron_golem/iron_golem",
    "emerald_phantom":    "phantom",
    "emerald_wither":     "wither/wither",
    "emerald_guardian":       "guardian",
    "emerald_elder_guardian": "guardian_elder",
}

# Per-mob FLECK density (sparser on big/!humanoid skins so it reads as flecks, not noise).
FLECK_DENSITY = {
    "emerald_iron_golem": 0.04, "emerald_wither": 0.04, "emerald_enderman": 0.05,
    "emerald_horse": 0.05, "emerald_phantom": 0.06,
    "emerald_guardian": 0.05, "emerald_elder_guardian": 0.05,
}
DEFAULT_DENSITY = 0.08

# Mobs that get a SINGLE central glowing eye instead of the Emeraldger's two vertical eyes (used by the
# upcoming eyes step / the render layer). Everyone else gets the paired vertical eyes.
ONE_EYE = {"emerald_endermite", "emerald_guardian", "emerald_elder_guardian"}

# Bright emissive emerald eye colour (matches the Emeraldger eyes). Rendered fullbright by an EyesLayer.
EYE = (90, 255, 130, 255)

# Front-of-FACE eye-band rectangle per mob (x0,y0,x1,y1) on the vanilla texture. The detector recolours the
# darkest/most-saturated pixels found here (the real eyes) -- so the glow always lands on the face. Tuned to the
# eye ROW so it won't grab nostrils/mouths. Mobs omitted here get eyes added in the screenshot-verified pass.
HEAD_RECT = {
    "emerald_sheep":        (3, 9, 11, 12),
    "emerald_cow":          (3, 9, 11, 13),
    "emerald_pig":          (3, 9, 11, 12),
    "emerald_chicken":      (2, 3, 9, 8),
    "emerald_cat":          (0, 3, 10, 8),
    "emerald_wolf":         (0, 3, 11, 8),
    "emerald_horse":        (0, 13, 9, 19),
    "emerald_bat":          (1, 1, 11, 6),
    "emerald_zombie":       (8, 10, 16, 12),
    "emerald_skeleton":     (8, 10, 16, 12),
    "emerald_enderman":     (8, 10, 16, 12),
    "emerald_creeper":      (8, 9, 16, 12),
    "emerald_endermite":    (2, 2, 8, 5),      # 1 eye
    "emerald_iron_golem":   (40, 22, 76, 34),
    "emerald_phantom":      (4, 4, 20, 9),
    "emerald_wither":       (8, 8, 16, 12),
    "emerald_guardian":     (4, 4, 14, 12),    # 1 big central eye
    "emerald_elder_guardian": (4, 4, 14, 12),  # 1 big central eye
}

def detect_eyes(vimg, rect, n):
    """Return up to n eye pixel positions in `rect` of the VANILLA image: the darkest (or, for coloured eyes,
    most-saturated) opaque pixels, merged into n clusters by x. Empty if the rect is too flat (no clear eyes)."""
    x0, y0, x1, y1 = rect
    px = vimg.convert("RGBA").load()
    w, h = vimg.size
    cands = []
    for y in range(max(0, y0), min(h, y1)):
        for x in range(max(0, x0), min(w, x1)):
            r, g, b, a = px[x, y]
            if a < 200:
                continue
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            sat = max(r, g, b) - min(r, g, b)
            cands.append((255 - lum + 0.6 * sat, x, y))
    if not cands:
        return []
    cands.sort(reverse=True)
    best = cands[0][0]
    if best < 40:                 # nothing clearly eye-like in this rect -> skip rather than misplace
        return []
    keep = [c for c in cands if c[0] >= best * 0.72][:8]   # the eye cluster(s)
    keep.sort(key=lambda c: c[1])                          # by x (left -> right)
    if n == 1:
        xs = [c[1] for c in keep]; ys = [c[2] for c in keep]
        return [(round(sum(xs) / len(xs)), round(sum(ys) / len(ys)))]
    # n==2: split into a left and right group on the x gap, take each group's darkest pixel
    midx = (keep[0][1] + keep[-1][1]) / 2.0
    left = [c for c in keep if c[1] <= midx] or [keep[0]]
    right = [c for c in keep if c[1] > midx] or [keep[-1]]
    return [(max(left)[1], max(left)[2]), (max(right)[1], max(right)[2])]

def gen_eyes(zf, mob, subpath):
    rect = HEAD_RECT.get(mob)
    if rect is None:
        return False
    vimg = load_vanilla(zf, subpath, folder="entity")
    n = 1 if mob in ONE_EYE else 2
    eyes = detect_eyes(vimg, rect, n)
    if not eyes:
        print("  eyes:", mob, "-> none detected (left for screenshot pass)")
        return False
    overlay = Image.new("RGBA", vimg.size, (0, 0, 0, 0))
    op = overlay.load()
    for (x, y) in eyes:
        op[x, y] = EYE
        if y + 1 < vimg.size[1]:
            op[x, y + 1] = EYE        # 1x2 vertical eye, like the Emeraldger
    mkdir(ENT)
    overlay.save(os.path.join(ENT, mob + "_eyes.png"))
    print("  eyes:", mob, "->", eyes)
    return True

def gen_body(zf, mob, subpath, gems16):
    img = green_tint(load_vanilla(zf, subpath, folder="entity"))
    img = emerald_fleck(img, mob, density=FLECK_DENSITY.get(mob, DEFAULT_DENSITY))
    img = overlay_gems(img, gems16)  # stud with the rich-block emerald-gem texture (below the eyes overlay)
    mkdir(ENT)
    img.save(os.path.join(ENT, mob + ".png"))
    print("  body:", mob, img.size)

def gen_sheep(zf, gems16):
    """Rename rich sheep -> emerald sheep: green body + green emerald-flecked, gem-studded fur (as before)."""
    body = overlay_gems(emerald_fleck(green_tint(load_vanilla(zf, "sheep/sheep", folder="entity")), "emerald_sheep_body", 0.04), gems16)
    fur = overlay_gems(emerald_fleck(green_tint(load_vanilla(zf, "sheep/sheep_fur", folder="entity")), "emerald_sheep_fur", 0.11), gems16)
    mkdir(ENT)
    body.save(os.path.join(ENT, "emerald_sheep.png"))
    fur.save(os.path.join(ENT, "emerald_sheep_fur.png"))
    print("  body: emerald_sheep + emerald_sheep_fur", body.size)

def gen_gigachad(gems16):
    """Emerald Gigachad Golem shell: the full pipeline applied to OUR gold_golem.png (the gigachad's gold skin),
    not a vanilla texture. The emerald defender golem reuses emerald_iron_golem.png for its body."""
    src = os.path.join(ENT, "gold_golem.png")
    if not os.path.exists(src):
        print("  !! gold_golem.png missing -> skipping emerald_gigachad_golem")
        return
    img = overlay_gems(emerald_fleck(green_tint(Image.open(src).convert("RGBA")), "emerald_gigachad_golem", 0.05), gems16)
    img.save(os.path.join(ENT, "emerald_gigachad_golem.png"))
    print("  body: emerald_gigachad_golem", img.size)

def main():
    with zipfile.ZipFile(JAR) as zf:
        gems16 = gem_overlay(zf)  # the rich-block emerald-gem crystals (emerald_ore minus stone)
        gen_sheep(zf, gems16)
        gen_eyes(zf, "emerald_sheep", "sheep/sheep")
        for mob, sub in MOB_TEXTURES.items():
            try:
                gen_body(zf, mob, sub, gems16)
                gen_eyes(zf, mob, sub)
            except KeyError as e:
                print("  !! missing vanilla texture for", mob, "->", sub, e)
    gen_gigachad(gems16)
    print("done. (animated emissive glow layer comes next.)")

if __name__ == "__main__":
    main()
