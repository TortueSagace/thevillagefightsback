#!/usr/bin/env python
"""Generate all Limbo-raider textures: recolored mob skins + emissive glows, animated
forgotten powder, and animated spawn-egg icons. Run from the .vanilla_tex dir."""
import os, math
from PIL import Image

SRC = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(SRC)
ENT = os.path.join(ROOT, "src/main/resources/assets/thevillagefightsback/textures/entity")
ITEM = os.path.join(ROOT, "src/main/resources/assets/thevillagefightsback/textures/item")
os.makedirs(ENT, exist_ok=True); os.makedirs(ITEM, exist_ok=True)

def lum(r, g, b):
    return 0.299 * r + 0.587 * g + 0.114 * b

# ---- mob skins: ghostly grayscale-green body + green emissive glow ----------------------
def make_mob(src_name, out_name):
    im = Image.open(os.path.join(SRC, src_name)).convert("RGBA")
    w, h = im.size
    px = im.load()
    base = Image.new("RGBA", (w, h), (0, 0, 0, 0)); bp = base.load()
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0)); gp = glow.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            L = lum(r, g, b) / 255.0
            # base: desaturated, darkened, faint cool-green ghost tint
            d = L ** 1.15 * 0.82          # darken midtones for a spectral look
            bp[x, y] = (int(255 * d * 0.80), int(255 * d * 0.95), int(255 * d * 0.86), a)
            # emissive glow: brighter areas glow green; dark areas barely glow
            gI = (L ** 1.6) * 0.85
            gp[x, y] = (int(55 * gI), int(235 * gI), int(120 * gI), a)
    base.save(os.path.join(ENT, out_name + ".png"))
    glow.save(os.path.join(ENT, out_name + "_glow.png"))
    print("mob", out_name, im.size)

for src, out in [("pillager.png", "limbo_pillager"), ("vindicator.png", "limbo_vindicator"),
                 ("evoker.png", "limbo_evoker"), ("ravager.png", "limbo_ravager"),
                 ("vex.png", "limbo_vex")]:
    make_mob(src, out)

# ---- animated item: 6-frame vertical strip + mcmeta (matches the limbo-gear shimmer) ----
MCMETA = '{"animation": {"frametime": 5, "interpolate": true}}'

def shimmer_frames(base_rgba, n=6):
    """Return n frames of base_rgba with a pulsing additive green sheen."""
    w, h = base_rgba.size
    bp = base_rgba.load()
    frames = []
    for f in range(n):
        amt = 0.5 + 0.5 * math.sin(2 * math.pi * f / n)   # 0..1 pulse
        fr = Image.new("RGBA", (w, h), (0, 0, 0, 0)); fp = fr.load()
        for y in range(h):
            for x in range(w):
                r, g, b, a = bp[x, y]
                if a == 0:
                    fp[x, y] = (0, 0, 0, 0); continue
                # add a green sheen scaled by pixel brightness and the pulse
                L = lum(r, g, b) / 255.0
                add = amt * (0.35 + 0.4 * L)
                fp[x, y] = (min(255, int(r + 40 * add)), min(255, int(g + 120 * add)),
                            min(255, int(b + 70 * add)), a)
        frames.append(fr)
    return frames

def write_strip(frames, path):
    w, h = frames[0].size
    strip = Image.new("RGBA", (w, h * len(frames)), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        strip.paste(fr, (0, i * h))
    strip.save(path)
    with open(path + ".mcmeta", "w") as fh:
        fh.write(MCMETA)

# forgotten powder: animate the existing icon
fp_src = Image.open(os.path.join(ITEM, "forgotten_powder.png")).convert("RGBA")
write_strip(shimmer_frames(fp_src), os.path.join(ITEM, "forgotten_powder.png"))
print("animated forgotten_powder")

# ---- spawn-egg icons: tint the vanilla egg template per mob, then shimmer-animate -------
egg = Image.open(os.path.join(SRC, "spawn_egg.png")).convert("RGBA")
ovl = Image.open(os.path.join(SRC, "spawn_egg_overlay.png")).convert("RGBA")

def tint(layer, color):
    w, h = layer.size; lp = layer.load()
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0)); op = out.load()
    cr, cg, cb = color
    for y in range(h):
        for x in range(w):
            r, g, b, a = lp[x, y]
            if a == 0: continue
            op[x, y] = (r * cr // 255, g * cg // 255, b * cb // 255, a)
    return out

EGGS = {
    "limbo_pillager_spawn":   ((40, 55, 48), (150, 225, 175)),
    "limbo_vindicator_spawn": ((50, 62, 50), (170, 235, 150)),
    "limbo_evoker_spawn":     ((34, 46, 56), (140, 235, 205)),
    "limbo_ravager_spawn":    ((28, 34, 30), (120, 200, 140)),
    "limbo_vex_spawn":        ((46, 62, 72), (185, 245, 222)),
}
for name, (primary, secondary) in EGGS.items():
    composed = tint(egg, primary)
    composed.alpha_composite(tint(ovl, secondary))
    write_strip(shimmer_frames(composed), os.path.join(ITEM, name + ".png"))
    print("egg", name)

print("DONE")
