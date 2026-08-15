"""
Generate the Obliteration Furnace's block textures from the vanilla blast furnace.

The furnace is the blast furnace recoloured into the Obliterator's violet, animation and all, so the two read
as one system: the weapon marks, the furnace erases, and they share a palette nobody has to be told about.

The recolour is a LUMINANCE REMAP, not a hue rotation. The blast furnace is mostly neutral greys with a few
warm accents, and rotating hue on a grey does nothing at all -- the block would have come out unchanged except
for the fire. Reading each pixel's brightness and looking that up in a violet ramp repaints the whole thing
while keeping every bit of its shading and rivet detail.

Two things get special treatment:

  * THE FIRE, on blast_furnace_front_on. Vanilla's is orange, and orange is the one part of this texture that
    is NOT neutral, so the same warm test the Obliterator's trace uses picks it out and sends it to a hot violet
    ramp instead of the cold structural one. Otherwise the fire would have gone the same purple as the casing
    and the furnace would look unlit while burning.
  * ITS ANIMATION. The source is a 16x32 two-frame strip with its own mcmeta (interpolate, frametime 10). The
    strip is recoloured frame by frame and the mcmeta copied verbatim, so the fire keeps flickering at exactly
    vanilla's cadence rather than being reinvented.

Output: textures/block/obliteration_furnace_{front,front_on,side,top}.png (+ the front_on mcmeta)
"""
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback",
                       "textures", "block")
CLIENT_EXTRA = os.path.expanduser(
    "~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar")

# Cold ramp: the casing. Dark violet through to a pale amethyst highlight.
COLD = [(10, 5, 18), (28, 14, 46), (48, 24, 78), (72, 34, 116), (100, 50, 158),
        (134, 82, 196), (170, 124, 226), (206, 168, 255), (232, 210, 255)]
# Hot ramp: the fire. Bright enough to read as flame against the cold casing.
HOT = [(60, 10, 90), (120, 20, 170), (180, 40, 230), (220, 110, 255), (245, 190, 255), (255, 240, 255)]

SOURCES = {
    "blast_furnace_front": "obliteration_furnace_front",
    "blast_furnace_front_on": "obliteration_furnace_front_on",
    "blast_furnace_side": "obliteration_furnace_side",
    "blast_furnace_top": "obliteration_furnace_top",
}


def ramp(value, table):
    """Pick from a colour ramp by 0..255 brightness."""
    idx = min(len(table) - 1, max(0, int(value / 256.0 * len(table))))
    return table[idx]


def recolour(img, allow_fire):
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sp, op = img.convert("RGBA").load(), out.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = sp[x, y]
            if a == 0:
                continue
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            # The fire is the only strongly warm thing on these textures; the casing is neutral grey.
            if allow_fire and r > b + 40 and r > 90:
                c = ramp(lum, HOT)
            else:
                c = ramp(lum, COLD)
            op[x, y] = (c[0], c[1], c[2], a)
    return out


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    with zipfile.ZipFile(CLIENT_EXTRA) as z:
        for src, dst in SOURCES.items():
            path = f"assets/minecraft/textures/block/{src}.png"
            with z.open(path) as fh:
                img = Image.open(fh).convert("RGBA")
            fire = src.endswith("_on")
            recolour(img, fire).save(os.path.join(OUT_DIR, dst + ".png"))
            # Carry the animation across verbatim so the flicker keeps vanilla's cadence.
            try:
                meta = z.read(path + ".mcmeta").decode("utf-8")
                with open(os.path.join(OUT_DIR, dst + ".png.mcmeta"), "w",
                          encoding="utf-8", newline="\n") as f:
                    f.write(meta if meta.endswith("\n") else meta + "\n")
            except KeyError:
                pass
            print("wrote", dst, img.size, "(fire ramp)" if fire else "")


if __name__ == "__main__":
    main()
