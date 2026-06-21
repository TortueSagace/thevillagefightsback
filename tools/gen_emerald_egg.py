"""The Emerald Egg item: a green-tinted vanilla egg with the emerald-gem overlay, matching the emerald apple's
look. Thrown, it inflicts the Emerald Infection; emerald chickens lay it."""
import os
import zipfile

from gen_fertile_blocks import (load_vanilla, green_tint, overlay_onto, gem_overlay, save_item_png,
                                write_json, ASSETS, JAR, MODID)


def gen():
    name = "emerald_egg"
    with zipfile.ZipFile(JAR) as zf:
        gems = gem_overlay(zf)
        img = overlay_onto(green_tint(load_vanilla(zf, "egg", folder="item")), gems)
    save_item_png(img, name)
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MODID}:item/{name}"}})
    print("generated", name)


if __name__ == "__main__":
    gen()
