"""
Generates the Limbo content assets for "The Village Fights Back":
  - The Limbo Sage villager PROFESSION overlay (textures/entity/villager/profession/limbo_sage.png):
    a dark/black recolor of the vanilla cleric (priest) robe, with a faint limbo-green trim.
  - Copies the vanilla profession_level badge textures (stone/iron/gold/emerald/diamond) into OUR
    namespace, since the renderer builds the badge path in the profession's namespace.
  - The Limbo BLOCK face: an animated 16 x (16*FRAMES) vertical strip in the limbo_ingot green palette,
    with a sweeping green glow across frames, plus its .png.mcmeta (frametime 4).
Re-runnable (overwrites). Pillow only; no Java here.
"""
import os, zipfile
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # villager-arms-mod/
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback")
CLIENT_JAR = (r"C:\Users\Administrator\AppData\Roaming\ModrinthApp\meta\libraries\net\minecraft"
              r"\client\1.20.1-20230612.114412\client-1.20.1-20230612.114412-extra.jar")

# The limbo green palette (sampled from limbo_ingot.png): dark base + bright highlight.
DARK = (12, 30, 20)        # near-black green base for the robe
GREEN_TRIM = (23, 130, 56) # faint limbo-green trim
GREEN_GLOW = (106, 193, 132)


def mkdir(p):
    os.makedirs(p, exist_ok=True)


def darken_to_limbo(img):
    """Recolor the vanilla cleric robe to a black/very-dark robe with a faint limbo-green tint.
    Keeps the original alpha (the robe shape); pushes every opaque pixel toward DARK, with the
    brighter source pixels picking up a touch of limbo green as trim."""
    img = img.convert("RGBA")
    w, h = img.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y in range(h):
        for x in range(w):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0  # 0..1 source brightness
            # base: very dark; brighter source areas lift slightly toward the green trim (the "highlights")
            t = lum ** 1.6  # bias dark so most of the robe is near-black
            nr = int(DARK[0] + (GREEN_TRIM[0] - DARK[0]) * t)
            ng = int(DARK[1] + (GREEN_TRIM[1] - DARK[1]) * t)
            nb = int(DARK[2] + (GREEN_TRIM[2] - DARK[2]) * t)
            out.putpixel((x, y), (nr, ng, nb, a))
    return out


def gen_profession_overlay(zf, dst_dir):
    mkdir(dst_dir)
    with zf.open("assets/minecraft/textures/entity/villager/profession/cleric.png") as fh:
        cleric = Image.open(fh).convert("RGBA")
    sage = darken_to_limbo(cleric)
    sage.save(os.path.join(dst_dir, "limbo_sage.png"))
    print("wrote", os.path.join(dst_dir, "limbo_sage.png"), sage.size)


def copy_profession_levels(zf, dst_dir):
    mkdir(dst_dir)
    for name in ("stone", "iron", "gold", "emerald", "diamond"):
        src = "assets/minecraft/textures/entity/villager/profession_level/%s.png" % name
        with zf.open(src) as fh:
            img = Image.open(fh).convert("RGBA")
        img.save(os.path.join(dst_dir, name + ".png"))
    print("copied 5 profession_level badges ->", dst_dir)


def gen_limbo_block(zf, dst_dir):
    """Animated dark-limbo block face: a tileable dark-green stone-ish face with a vertical green glow
    band that sweeps top->bottom across the frames, in the limbo_ingot palette."""
    mkdir(dst_dir)
    SIZE = 16
    FRAMES = 6
    # A static base face: dark limbo green with subtle per-pixel speckle (deterministic, tileable).
    base = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 255))
    for y in range(SIZE):
        for x in range(SIZE):
            # deterministic pseudo-speckle so the face has texture but no seams
            n = ((x * 7 + y * 13) ^ (x * y * 3)) & 7
            shade = 0.78 + n * 0.03
            r = int(DARK[0] * shade) + 4
            g = int(DARK[1] * shade) + 6
            b = int(DARK[2] * shade) + 4
            base.putpixel((x, y), (min(r, 40), min(g, 70), min(b, 48), 255))
    # faint border so blocks read as separate cubes
    for i in range(SIZE):
        for (px, py) in ((i, 0), (i, SIZE - 1), (0, i), (SIZE - 1, i)):
            r, g, b, a = base.getpixel((px, py))
            base.putpixel((px, py), (max(r - 4, 0), max(g - 6, 0), max(b - 4, 0), 255))

    # UNIFORM breathing pulse: the WHOLE face brightens and dims together every frame. This is
    # direction-agnostic, so all six cube faces animate identically (no sweeping band that would point
    # different ways per face). A static per-pixel speckle keeps spatial texture; only the brightness pulses.
    PULSE = [0.22, 0.42, 0.66, 0.66, 0.42, 0.22]  # symmetric dim -> bright -> dim over the 6 frames
    strip = Image.new("RGBA", (SIZE, SIZE * FRAMES), (0, 0, 0, 255))
    for f in range(FRAMES):
        frame = base.copy()
        k = PULSE[f]
        for y in range(SIZE):
            for x in range(SIZE):
                r, g, b, a = frame.getpixel((x, y))
                sp = ((x * 7 + y * 13) ^ (x * y * 3)) & 7  # static spatial variation (temporally uniform)
                kk = min(1.0, k * (0.75 + sp * 0.04))
                nr = int(r + (GREEN_GLOW[0] - r) * kk)
                ng = int(g + (GREEN_GLOW[1] - g) * kk)
                nb = int(b + (GREEN_GLOW[2] - b) * kk)
                frame.putpixel((x, y), (nr, ng, nb, 255))
        strip.paste(frame, (0, f * SIZE))
    out = os.path.join(dst_dir, "limbo_block.png")
    strip.save(out)
    with open(out + ".mcmeta", "w", encoding="utf-8") as fh:
        fh.write('{\n  "animation": {\n    "frametime": 4,\n    "interpolate": true\n  }\n}\n')
    print("wrote", out, strip.size, "+ .mcmeta")


def main():
    with zipfile.ZipFile(CLIENT_JAR) as zf:
        gen_profession_overlay(zf, os.path.join(ASSETS, "textures", "entity", "villager", "profession"))
        copy_profession_levels(zf, os.path.join(ASSETS, "textures", "entity", "villager", "profession_level"))
        gen_limbo_block(zf, os.path.join(ASSETS, "textures", "block"))


if __name__ == "__main__":
    main()
