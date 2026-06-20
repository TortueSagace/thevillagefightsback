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
                                 SALT, SALT_DIM, PEPPER)

ENT = os.path.join(ASSETS, "textures", "entity")

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
}

# Per-mob FLECK density (sparser on big/!humanoid skins so it reads as flecks, not noise).
FLECK_DENSITY = {
    "emerald_iron_golem": 0.04, "emerald_wither": 0.04, "emerald_enderman": 0.05,
    "emerald_horse": 0.05, "emerald_phantom": 0.06,
}
DEFAULT_DENSITY = 0.08

def gen_body(zf, mob, subpath):
    img = green_tint(load_vanilla(zf, subpath, folder="entity"))
    img = emerald_fleck(img, mob, density=FLECK_DENSITY.get(mob, DEFAULT_DENSITY))
    mkdir(ENT)
    img.save(os.path.join(ENT, mob + ".png"))
    print("  body:", mob, img.size)

def gen_sheep(zf):
    """Rename rich sheep -> emerald sheep: green body + green emerald-flecked fur (as before)."""
    body = emerald_fleck(green_tint(load_vanilla(zf, "sheep/sheep", folder="entity")), "emerald_sheep_body", 0.04)
    fur = emerald_fleck(green_tint(load_vanilla(zf, "sheep/sheep_fur", folder="entity")), "emerald_sheep_fur", 0.11)
    mkdir(ENT)
    body.save(os.path.join(ENT, "emerald_sheep.png"))
    fur.save(os.path.join(ENT, "emerald_sheep_fur.png"))
    print("  body: emerald_sheep + emerald_sheep_fur", body.size)

def main():
    with zipfile.ZipFile(JAR) as zf:
        gen_sheep(zf)
        for mob, sub in MOB_TEXTURES.items():
            try:
                gen_body(zf, mob, sub)
            except KeyError as e:
                print("  !! missing vanilla texture for", mob, "->", sub, e)
    print("done. (eyes + animated emissive glow are added in the next step, per-mob.)")

if __name__ == "__main__":
    main()
