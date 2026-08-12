"""
Generate General Darius's skin: a HUMAN commander, so he reads as one of the village's people rather than as a
villager (and so he does not stand out as the only big-nosed figure in a town of MCA Reborn humans).

Darius used to render on a villager-nosed model with the vanilla zombie-villager texture recoloured tan. That
made him unmistakably a VILLAGER, which looks wrong next to MCA's human villagers. He now renders on the plain
player model, so his skin has to be a PLAYER-layout texture.

We start from the vanilla wide player skin (already the exact layout the player model expects, with proper
shading baked in) and recolour only its CLOTHING by hue: the cyan shirt becomes a deep military green and the
blue trousers a dark slate, giving a general's uniform. Skin, hair, eyes and mouth are matched by hue too and
left untouched, so the face stays a normal human face.

Output: src/main/resources/assets/thevillagefightsback/textures/entity/general_darius.png  (64x64)
"""
import colorsys
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback",
                   "textures", "entity", "general_darius.png")
CLIENT_JAR = (r"C:\Users\Administrator\AppData\Roaming\ModrinthApp\meta\libraries\net\minecraft"
              r"\client\1.20.1-20230612.114412\client-1.20.1-20230612.114412-extra.jar")
SRC = "assets/minecraft/textures/entity/player/wide/steve.png"

# Hue windows of the vanilla skin we retarget, in degrees, with the hue/saturation we move them to.
# The shirt sits around cyan (180) and the trousers around blue (240); skin and hair are warm (< 60) and are
# deliberately not listed, so they survive untouched.
# (hue window, target hue, target saturation, value multiplier). The value multiplier is what turns the
# trousers into dark uniform trousers rather than a pale mint: the vanilla shading is kept, just dimmed.
SHIRT = ((150.0, 200.0), 125.0, 0.72, 0.92)   # deep military green
TROUSERS = ((200.0, 260.0), 145.0, 0.35, 0.55) # dark slate with a green cast, so it reads as one outfit


def retarget(img):
    """Recolour the uniform by hue, keeping the vanilla shading (value) exactly as it is."""
    img = img.convert("RGBA")
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            hh, ss, vv = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if ss < 0.15:
                continue  # near-grey: leave it (eye whites, boots, shading)
            deg = hh * 360.0
            for (lo, hi), hue, sat, vmul in (SHIRT, TROUSERS):
                if lo <= deg < hi:
                    nr, ng, nb = colorsys.hsv_to_rgb(hue / 360.0, sat, min(1.0, vv * vmul))
                    px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
                    break
    return img


def main():
    with zipfile.ZipFile(CLIENT_JAR) as jar, jar.open(SRC) as f:
        base = Image.open(f).copy()
    out = retarget(base)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT)
    print("wrote", OUT, out.size)


if __name__ == "__main__":
    main()
