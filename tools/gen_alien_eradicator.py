"""
Generates the vivid-green item textures for the Alien Eradicator + the Emerald Virus Arrow II by
recoloring their base counterparts (the Alien Slasher and the Emerald Virus Arrow):
  - alien_eradicator.png:     a MORE VIVID, saturated green version of alien_slasher.png.
  - emerald_virus_arrow_2.png: a greener version of emerald_virus_arrow.png.
Both keep their original shape/shading and per-pixel alpha; we only shift the hue toward green and
push saturation/value so the new items read as a stronger ("Virus II") variant. The enchant glint
(isFoil) still animates over the top in-game, so no .mcmeta is needed.
Re-runnable (overwrites). Uses Pillow (see gen_mace_wind_charge.py for precedent).
"""
import colorsys
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # villager-arms-mod/
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback", "textures", "item")

# Target hue for "vivid green" in HSV (0..1). 0.33 == pure green (120 degrees).
GREEN_HUE = 0.33


def vivid_green(src_name, dst_name, sat_floor, sat_boost, val_boost):
    """Load src_name, drag every visible pixel toward a vivid green, save as dst_name."""
    img = Image.open(os.path.join(TEX, src_name)).convert("RGBA")
    w, h = img.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for x in range(w):
        for y in range(h):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue  # keep transparency exactly
            hh, ss, vv = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            # Force the hue to green; lift saturation (with a floor so near-grey pixels still go green)
            # and value so the blade/arrow looks brighter and more "alive" than the base item.
            hh = GREEN_HUE
            ss = min(1.0, max(sat_floor, ss * sat_boost))
            vv = min(1.0, vv * val_boost)
            nr, ng, nb = colorsys.hsv_to_rgb(hh, ss, vv)
            out.putpixel((x, y), (int(nr * 255), int(ng * 255), int(nb * 255), a))
    out.save(os.path.join(TEX, dst_name))
    print("wrote", dst_name)


if __name__ == "__main__":
    os.makedirs(TEX, exist_ok=True)
    # The slasher is already green-ish; push it hard so the eradicator is clearly MORE vivid.
    vivid_green("alien_slasher.png", "alien_eradicator.png", sat_floor=0.85, sat_boost=1.6, val_boost=1.12)
    # The arrow is already an emerald arrow; give the II arrow a punchier, brighter green.
    vivid_green("emerald_virus_arrow.png", "emerald_virus_arrow_2.png", sat_floor=0.7, sat_boost=1.5, val_boost=1.1)
    print("done ->", TEX)
