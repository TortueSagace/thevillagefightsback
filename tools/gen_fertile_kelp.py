"""Generate the "fertile kelp" block (the fertile variant of kelp): the vanilla kelp_plant texture green-tinted
with the animated fertile specks, glowing like the rest of the fertile family. Writes the texture + mcmeta, the
blockstate, the cross block model (cutout), the item model and a self-drop loot table. Java registration is done
separately. Re-runnable (overwrites)."""
import os
import zipfile

import gen_fertile_blocks as g  # noqa: F401 (kept for parity with the other single-block generators)
from gen_fertile_blocks import (load_vanilla, green_tint, animate_specks, save_png, save_anim_mcmeta,
                                write_json, write_loot_self, tex, ASSETS, JAR)


def gen():
    with zipfile.ZipFile(JAR) as zf:
        kelp = load_vanilla(zf, "kelp_plant")  # the body segment (no flower) -- a 16x16 cross texture
    strip = animate_specks(green_tint(kelp), "fertile_kelp")
    save_png(strip, "fertile_kelp")
    save_anim_mcmeta("fertile_kelp")

    write_json(os.path.join(ASSETS, "blockstates", "fertile_kelp.json"),
               {"variants": {"": {"model": tex("fertile_kelp")}}})
    write_json(os.path.join(ASSETS, "models", "block", "fertile_kelp.json"), {
        "parent": "minecraft:block/cross",
        "render_type": "minecraft:cutout",
        "textures": {"cross": tex("fertile_kelp")},
    })
    write_json(os.path.join(ASSETS, "models", "item", "fertile_kelp.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": tex("fertile_kelp")}})
    write_loot_self("fertile_kelp")
    print("fertile_kelp assets written")


if __name__ == "__main__":
    gen()
