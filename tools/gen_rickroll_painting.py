"""
Build the animated "Rickroll" painting texture from the source clip.

Minecraft animates an atlas sprite from a VERTICAL STRIP: one square frame stacked under the next, with
a .mcmeta beside it saying how long each frame lasts. So this centre-crops the 4:3 source to a square,
samples it down to a manageable frame count, and stacks the result.

Two knobs and why they are where they are:
  FRAME  32, not the 16 a 1x1 vanilla painting uses. Painting sprites are not required to be 16px --
         the renderer just maps the sprite across the block face -- and at 16px Rick is an unreadable
         smudge. 32 is legible and still cheap.
  COUNT  40 of the source's 159 frames, at FRAMETIME 3 ticks each. That is 120 ticks, i.e. 6 seconds,
         which is about the length of the original clip. The full 159 frames would make a strip eight
         times taller for motion nobody can see at this size.

The inventory icon is the SAME animated strip, not a still frame. Item sprites are stitched into the
block atlas exactly like painting sprites are, so they animate from a .mcmeta the same way -- which means
the item in the creative menu and in your hand plays the clip too.

Outputs:
  assets/thevillagefightsback/textures/painting/rickroll.png        (32 x 32*COUNT strip)
  assets/thevillagefightsback/textures/painting/rickroll.png.mcmeta
  assets/thevillagefightsback/textures/item/rickroll_painting.png   (the same strip)
  assets/thevillagefightsback/textures/item/rickroll_painting.png.mcmeta
"""
import json
import os

from PIL import Image, ImageSequence

SOURCE = r"rickroll.webp"

HERE = os.path.dirname(__file__)
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "thevillagefightsback")
OUT_TEX = os.path.join(ASSETS, "textures", "painting", "rickroll.png")
OUT_META = OUT_TEX + ".mcmeta"
OUT_ICON = os.path.join(ASSETS, "textures", "item", "rickroll_painting.png")
OUT_ICON_META = OUT_ICON + ".mcmeta"

FRAME = 32
COUNT = 40
FRAMETIME = 3


def square(frame):
    """Centre-crop to a square, then scale to FRAME. Cropping beats squashing: Rick stays proportioned."""
    img = frame.convert("RGBA")
    w, h = img.size
    side = min(w, h)
    left = (w - side) // 2
    top = (h - side) // 2
    return img.crop((left, top, left + side, top + side)).resize((FRAME, FRAME), Image.LANCZOS)


def main():
    src = Image.open(SOURCE)
    frames = [f.copy() for f in ImageSequence.Iterator(src)]
    total = len(frames)

    picks = [frames[min(total - 1, round(i * (total - 1) / (COUNT - 1)))] for i in range(COUNT)]

    strip = Image.new("RGBA", (FRAME, FRAME * COUNT), (0, 0, 0, 0))
    for i, f in enumerate(picks):
        strip.paste(square(f), (0, i * FRAME))

    os.makedirs(os.path.dirname(os.path.normpath(OUT_TEX)), exist_ok=True)
    strip.save(os.path.normpath(OUT_TEX))

    meta = {"animation": {"frametime": FRAMETIME}}
    with open(os.path.normpath(OUT_META), "w", encoding="utf-8") as fh:
        json.dump(meta, fh, indent=2)
        fh.write("\n")

    # The icon is the same strip and the same timing, so the item animates in the inventory too.
    os.makedirs(os.path.dirname(os.path.normpath(OUT_ICON)), exist_ok=True)
    strip.save(os.path.normpath(OUT_ICON))
    with open(os.path.normpath(OUT_ICON_META), "w", encoding="utf-8") as fh:
        json.dump(meta, fh, indent=2)
        fh.write("\n")

    print("strip  %s (%dx%d, %d frames @ %d ticks)"
          % (os.path.normpath(OUT_TEX), FRAME, FRAME * COUNT, COUNT, FRAMETIME))
    print("mcmeta %s" % os.path.normpath(OUT_META))
    print("icon   %s (animated, same strip)" % os.path.normpath(OUT_ICON))
    print("icon   %s" % os.path.normpath(OUT_ICON_META))


if __name__ == "__main__":
    main()
