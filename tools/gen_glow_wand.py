"""The Glow Wand item: a green-tinted blaze rod with the emerald-gem overlay (same treatment as the emerald
apple). Right-clicking it makes every unit of this mod nearby glow for 3 seconds."""
import os
import zipfile

from gen_fertile_blocks import (load_vanilla, green_tint, overlay_onto, gem_overlay, save_item_png,
                                write_json, ASSETS, JAR, MODID)


def gen():
    name = "glow_wand"
    with zipfile.ZipFile(JAR) as zf:
        gems = gem_overlay(zf)
        img = overlay_onto(green_tint(load_vanilla(zf, "blaze_rod", folder="item")), gems)
    save_item_png(img, name)
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{name}"}})
    print("generated", name)


if __name__ == "__main__":
    gen()
