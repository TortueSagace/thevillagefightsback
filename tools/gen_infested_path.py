"""Generate the lime "infested path" block (v1.0.3 mob infection): a glowing, animated salt-and-pepper path laid
below an emerald-converted unit. Reuses the fertile-block pipeline (green tint + animated specks) but with a
brighter LIME speck palette, on the vanilla dirt-path top/side textures. Also writes the blockstate, the path-shaped
block model, the item model and a self-drop loot table."""
import os
import zipfile

import gen_fertile_blocks as g
from gen_fertile_blocks import (load_vanilla, green_tint, animate_specks, save_png, save_anim_mcmeta,
                                write_json, write_loot_self, tex, ASSETS, JAR, MODID)

# Lime salt & pepper (brighter / more yellow-green than the fertile blocks' mint green).
g.SALT = (180, 255, 70)
g.SALT_DIM = (74, 150, 34)
g.PEPPER = (26, 80, 12)


def gen():
    with zipfile.ZipFile(JAR) as zf:
        top = load_vanilla(zf, "dirt_path_top")
        side = load_vanilla(zf, "dirt_path_side")
    for img, name in ((top, "infested_path_top"), (side, "infested_path_side")):
        strip = animate_specks(green_tint(img), name)
        save_png(strip, name)
        save_anim_mcmeta(name)

    write_json(os.path.join(ASSETS, "blockstates", "infested_path.json"),
               {"variants": {"": {"model": tex("infested_path")}}})
    write_json(os.path.join(ASSETS, "models", "block", "infested_path.json"), {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "minecraft:block/dirt",
            "top": tex("infested_path_top"),
            "side": tex("infested_path_side"),
            "bottom": "minecraft:block/dirt",
        },
        "elements": [{
            "from": [0, 0, 0], "to": [16, 15, 16],
            "faces": {
                "down": {"texture": "#bottom", "cullface": "down"},
                "up": {"texture": "#top", "cullface": "up"},
                "north": {"texture": "#side", "cullface": "north"},
                "south": {"texture": "#side", "cullface": "south"},
                "west": {"texture": "#side", "cullface": "west"},
                "east": {"texture": "#side", "cullface": "east"},
            },
        }],
    })
    write_json(os.path.join(ASSETS, "models", "item", "infested_path.json"),
               {"parent": tex("infested_path")})
    write_loot_self("infested_path")
    print("infested_path: textures + blockstate + model + item + loot written")


if __name__ == "__main__":
    gen()
