"""
Generate the three X-ray block textures from their Fake counterparts.

An X-ray block is a Fake block whose warm yellow growth has been recoloured to a cold, glowing blue, so the
two families read apart instantly on a wall: yellow means "you are blind inside me", blue means "you can see
out". The geometry is untouched, only the colour, so an X-ray gate still looks like the same masonry.

    fake_cobblestone.png  -> x_ray_cobblestone.png   (olive moss  -> blue glow)
    fake_obsidian.png     -> x_ray_obsidian.png      (yellow vein -> blue glow)
    vanilla bedrock       -> x_ray_bedrock.png       (no vein of its own, so it BORROWS the obsidian's)

Fake bedrock wears the untouched vanilla bedrock texture, which is the whole point of it: it is a disguise.
That leaves its X-ray sibling with nothing to recolour, so the veins are stamped on using the mask lifted
from fake_obsidian. The family then shares one vein shape across all three blocks.

Run from the repo root:  python tools/gen_xray_blocks.py
"""

import os
import zipfile

from PIL import Image

BLOCKS = "src/main/resources/assets/thevillagefightsback/textures/block"

# The vanilla jar ForgeGradle already unpacked for the 1.20.1 dev run; only bedrock.png is read out of it.
CLIENT_EXTRA = os.path.expanduser(
    "~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar")
BEDROCK_IN_JAR = "assets/minecraft/textures/block/bedrock.png"


def is_warm(px):
    """A pixel belonging to the yellow growth rather than to the stone around it.

    Both source textures are grey or purple-black stone, so 'warm' separates cleanly: the growth has red and
    green well above blue, while cobblestone's grey has all three within a few points of each other and
    obsidian's purple actually has blue on top.
    """
    r, g, b, a = px
    return a > 0 and r > b + 30 and g > b + 20


def to_glow(px):
    """Map a warm pixel to the matching cold blue, keeping its brightness so the texture keeps its shading.

    Luminance in, blue out. Holding the luminance is what stops the recolour flattening the veins into one
    solid shape: the light and dark parts of the original growth stay light and dark, just blue.
    """
    r, g, b, a = px
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    return (
        int(min(255, lum * 0.22)),
        int(min(255, lum * 0.78)),
        int(min(255, lum * 1.35 + 40)),   # blue runs past the original brightness -- this is the "glow"
        a,
    )


def recolour(src_name, dst_name):
    """Recolour one texture in place of its Fake twin, and return the mask of what was changed."""
    src = Image.open(os.path.join(BLOCKS, src_name)).convert("RGBA")
    out = Image.new("RGBA", src.size)
    mask = Image.new("L", src.size, 0)
    changed = 0
    for y in range(src.height):
        for x in range(src.width):
            px = src.getpixel((x, y))
            if is_warm(px):
                out.putpixel((x, y), to_glow(px))
                mask.putpixel((x, y), 255)
                changed += 1
            else:
                out.putpixel((x, y), px)
    out.save(os.path.join(BLOCKS, dst_name))
    print(f"  {dst_name:24s} {changed:3d} px recoloured")
    return mask


def stamp_bedrock(vein_mask, vein_source):
    """Bedrock has no growth of its own, so paint the obsidian's veins onto the vanilla texture."""
    with zipfile.ZipFile(CLIENT_EXTRA) as z, z.open(BEDROCK_IN_JAR) as f:
        base = Image.open(f).convert("RGBA")
    # The vanilla texture is animated in some packs; take only the top 16x16 frame.
    base = base.crop((0, 0, 16, 16))
    out = base.copy()
    painted = 0
    for y in range(16):
        for x in range(16):
            if vein_mask.getpixel((x, y)) == 0:
                continue
            # Reuse the recoloured obsidian vein pixel, but darken it slightly so it sits on bedrock's
            # lighter grey the way it sat on obsidian's near-black, instead of blowing out.
            r, g, b, a = vein_source.getpixel((x, y))
            out.putpixel((x, y), (int(r * 0.85), int(g * 0.85), int(b * 0.92), 255))
            painted += 1
    out.save(os.path.join(BLOCKS, "x_ray_bedrock.png"))
    print(f"  x_ray_bedrock.png        {painted:3d} px veined onto vanilla bedrock")


def main():
    print("X-ray block textures:")
    recolour("fake_cobblestone.png", "x_ray_cobblestone.png")
    obs_mask = recolour("fake_obsidian.png", "x_ray_obsidian.png")
    stamp_bedrock(obs_mask, Image.open(os.path.join(BLOCKS, "x_ray_obsidian.png")).convert("RGBA"))
    print("done")


if __name__ == "__main__":
    main()
