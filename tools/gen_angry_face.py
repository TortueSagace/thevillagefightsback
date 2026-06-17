"""
Generates the two "angry villager face" overlay textures used by the client-only angry-face render
layers (client/AngryFaceClient + AngryVillagerFaceLayer + AngryGuardFaceLayer).

The face is the FRONT-of-head face lifted straight from the "Angry Villager" player skin
(minecraftskins.com/skin/23380520) -- ONLY the 8x8 head-front face, base + hat layers composited.
It is tan villager skin with black angled (angry) brows, bright green eyes, a black nose bridge, and
a brown nose. The 8x8 face is embedded below so this is reproducible without the original skin file.

  - entity/angry_guard_face.png    : 64x64, laid on the vanilla HUMANOID (Steve) head UV. The head is an
        8x8x8 box at texOffs(0,0) so its front face is u=[8,16), v=[8,16). The 8x8 face drops in directly.

  - entity/angry_villager_face.png : 64x64, laid on the vanilla VILLAGER head UV. The villager head is an
        8x10x8 box at texOffs(0,0) so its front face is u=[8,16), v=[8,18) (8 wide, 10 TALL). The square
        8x8 face is PADDED to 10 tall: top-aligned (so the eyes land where a villager's eyes sit) with the
        two extra rows below filled with skin (the chin). The villager model's own 3D nose sticks out in
        front and is the same tan, so it reads as the villager nose; only the face area is replaced.

Everything outside the head-front face stays fully transparent so the real skin shows underneath.
Re-runnable (overwrites). Uses Pillow.
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # villager-arms-mod/
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback", "textures", "entity")

# The 8x8 head-front face from the Angry Villager skin (base + hat composited), top row first.
FACE = [
    [(189, 139, 113, 255), (189, 139, 113, 255), (189, 139, 113, 255), (189, 139, 113, 255), (189, 139, 113, 255), (189, 139, 113, 255), (189, 139, 113, 255), (181, 122, 103, 255)],
    [(189, 139, 113, 255), (181, 123, 103, 255), (189, 139, 113, 255), (178, 122, 98, 255), (178, 122, 98, 255), (178, 122, 98, 255), (189, 139, 113, 255), (181, 122, 103, 255)],
    [(189, 139, 113, 255), (0, 0, 0, 255), (181, 123, 103, 255), (178, 122, 98, 255), (178, 122, 98, 255), (178, 122, 98, 255), (0, 0, 0, 255), (181, 122, 103, 255)],
    [(181, 123, 103, 255), (178, 122, 98, 255), (0, 0, 0, 255), (178, 122, 98, 255), (178, 122, 98, 255), (0, 0, 0, 255), (180, 122, 101, 255), (181, 122, 103, 255)],
    [(189, 139, 113, 255), (255, 255, 255, 255), (4, 200, 0, 255), (0, 0, 0, 255), (0, 0, 0, 255), (4, 200, 0, 255), (255, 255, 255, 255), (190, 136, 108, 255)],
    [(189, 139, 113, 255), (178, 122, 98, 255), (183, 130, 114, 255), (144, 94, 67, 255), (144, 94, 67, 255), (190, 136, 108, 255), (178, 122, 98, 255), (189, 139, 113, 255)],
    [(189, 139, 113, 255), (178, 122, 98, 255), (119, 66, 53, 255), (144, 94, 67, 255), (144, 94, 67, 255), (119, 66, 53, 255), (178, 122, 98, 255), (189, 139, 113, 255)],
    [(189, 139, 113, 255), (178, 122, 98, 255), (178, 122, 98, 255), (144, 94, 67, 255), (144, 94, 67, 255), (178, 122, 98, 255), (178, 122, 98, 255), (189, 139, 113, 255)],
]


def mkdir(p):
    os.makedirs(p, exist_ok=True)


def blank():
    return Image.new("RGBA", (64, 64), (0, 0, 0, 0))


def build_guard():
    """Humanoid head front: u=[8,16), v=[8,16). The 8x8 face drops straight in."""
    img = blank()
    for y in range(8):
        for x in range(8):
            img.putpixel((8 + x, 8 + y), FACE[y][x])
    img.save(os.path.join(TEX, "angry_guard_face.png"))


def build_villager():
    """Villager head front: u=[8,16), v=[8,18) (10 tall). Top-align the 8x8 face; pad the 2 extra rows
    below with skin (the chin), copied from the face's bottom row so it blends seamlessly."""
    img = blank()
    for y in range(8):
        for x in range(8):
            img.putpixel((8 + x, 8 + y), FACE[y][x])
    # pad the two extra villager rows (v=16,17) with the face's bottom-row skin -> a tan chin
    for y in (8, 9):
        for x in range(8):
            img.putpixel((8 + x, 8 + y), FACE[7][x])
    img.save(os.path.join(TEX, "angry_villager_face.png"))


if __name__ == "__main__":
    mkdir(TEX)
    build_guard()
    build_villager()
    print("wrote angry_villager_face.png + angry_guard_face.png (64x64) to %s" % TEX)
