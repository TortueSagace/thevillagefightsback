"""
Generate General Darius's skin: a LIVING villager-guard texture, so he renders with a proper villager face
(the iconic nose + brow) instead of being a faceless player model or a green zombie.

We start from the vanilla zombie-villager base texture (which lays out perfectly on the ZombieVillagerModel that
Darius is rendered with -- same nose/head UVs) and recolor only its GREEN ZOMBIE SKIN to a warm villager tan by
swapping the red/green channels on skin pixels (green-dominant). The robe and dark facial features (brow/eyes)
are left as-is, so he keeps a normal villager's face, just alive and tan rather than undead green.

Output: src/main/resources/assets/thevillagefightsback/textures/entity/general_darius.png  (64x64)
"""
import os
import zipfile
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback",
                   "textures", "entity", "general_darius.png")
CLIENT_JAR = (r"C:\Users\Administrator\AppData\Roaming\ModrinthApp\meta\libraries\net\minecraft"
              r"\client\1.20.1-20230612.114412\client-1.20.1-20230612.114412-extra.jar")
SRC = "assets/minecraft/textures/entity/zombie_villager/zombie_villager.png"


def to_living(img):
    """Recolor the zombie's green skin to villager tan; leave robe / dark features untouched."""
    img = img.convert("RGBA")
    w, h = img.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y in range(h):
        for x in range(w):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue
            # zombie skin is green-dominant: swap red<->green to turn it into a warm tan, then warm it slightly.
            if g > r + 4:
                nr, ng, nb = g, r, b
                nr = min(255, nr + 14)  # a touch warmer/brighter so it reads as healthy skin
            else:
                nr, ng, nb = r, g, b    # robe + dark brow/eyes: keep
            out.putpixel((x, y), (nr, ng, nb, a))
    return out


def main():
    with zipfile.ZipFile(CLIENT_JAR) as zf:
        with zf.open(SRC) as fh:
            base = Image.open(fh).convert("RGBA")
    skin = to_living(base)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    skin.save(OUT)
    print("wrote", os.path.normpath(OUT), skin.size)


if __name__ == "__main__":
    main()
