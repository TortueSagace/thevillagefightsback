"""
Generates the two "angry villager face" overlay textures used by the client-only angry-face render
layers (see client/AngryFaceClient + AngryVillagerFaceLayer + AngryGuardFaceLayer):

  - entity/angry_villager_face.png : 64x64 RGBA, laid out on the VANILLA VILLAGER head UV.
        The villager head is an 8x10x8 box at texOffs(0,0), so its FRONT face occupies
        u=[8,16), v=[8,18) (8 wide, 10 tall). ONLY that face is painted; everything else is
        fully transparent (alpha 0) so the real villager skin shows through underneath.

  - entity/angry_guard_face.png    : 64x64 RGBA, laid out on the VANILLA HUMANOID (Steve) head UV.
        The humanoid head is an 8x8x8 box at texOffs(0,0), so its FRONT face occupies
        u=[8,16), v=[8,16) (8 wide, 8 tall). Used for the Guard Villagers guard, which renders
        with a standard HumanoidModel.

The art is ORIGINAL pixel work inspired by the recognizable "angry villager" look (it is NOT a copy
of any bundled meme image): the big villager nose, a heavy unibrow furrowed down into a sharp V, small
narrowed red-tinged eyes under the brow, and a downturned scowling mouth. Drawn only over the face so
the overlay reads as an angry expression painted onto whatever head is underneath.

Re-runnable (overwrites). Uses Pillow (see gen_alien_smasher.py / gen_alien_eradicator.py for precedent).
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # villager-arms-mod/
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "thevillagefightsback", "textures", "entity")

# Palette (RGBA). Kept dark/saturated so the scowl reads clearly over either skin tone.
BROW = (38, 22, 14, 255)        # heavy dark unibrow
BROW_HI = (74, 48, 30, 255)     # slight highlight on the brow ridge
EYE = (120, 18, 18, 255)        # narrowed, red-tinged angry eye
EYE_DARK = (60, 8, 8, 255)      # eye shadow / inner corner
NOSE = (150, 96, 70, 255)       # the big villager nose (warm skin shadow)
NOSE_DARK = (110, 66, 46, 255)  # underside of the nose
MOUTH = (52, 26, 22, 255)       # downturned scowl line


def mkdir(p):
    os.makedirs(p, exist_ok=True)


def blank(w=64, h=64):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def put(img, x, y, c, ox, oy):
    """Put a pixel relative to the face-front origin (ox, oy) on the texture."""
    px, py = ox + x, oy + y
    if 0 <= px < img.width and 0 <= py < img.height:
        img.putpixel((px, py), c)


def paint_face(img, ox, oy, face_h):
    """
    Paint the angry expression into an 8-wide x face_h-tall front-face cell whose top-left texel is
    (ox, oy). Local coords: x in [0,8), y in [0,face_h). The face is drawn relative to the face cell so
    the same routine works for the 10-tall villager face and the 8-tall guard face (we just nudge the
    features up a touch on the shorter guard head).
    """
    # vertical anchors as a fraction of the face height so features sit sensibly on both heads
    brow_y = max(1, round(face_h * 0.30))   # top of the furrowed brow
    eye_y = brow_y + 1                       # narrowed eyes just under the brow
    nose_y = eye_y + 1                       # nose bridge starts under the eyes
    mouth_y = min(face_h - 1, nose_y + 3)    # scowl near the bottom

    # --- heavy unibrow furrowed into a sharp V (angled down toward the centre) ---
    # left half slopes down-right, right half slopes down-left, meeting low in the middle = furrow.
    brow_left = [(0, brow_y), (1, brow_y), (2, brow_y + 1), (3, brow_y + 1)]
    brow_right = [(4, brow_y + 1), (5, brow_y + 1), (6, brow_y), (7, brow_y)]
    for (x, y) in brow_left + brow_right:
        put(img, x, y, BROW, ox, oy)
    # thicken the brow one row up at the outer ends (heavy ridge) with a faint highlight
    for (x, y) in [(0, brow_y - 1), (1, brow_y - 1), (6, brow_y - 1), (7, brow_y - 1)]:
        put(img, x, y, BROW_HI, ox, oy)
    # the centre furrow dip (the lowest point of the V)
    put(img, 3, brow_y + 2, BROW, ox, oy)
    put(img, 4, brow_y + 2, BROW, ox, oy)

    # --- small narrowed, red-tinged eyes tucked under the brow ---
    put(img, 1, eye_y + 1, EYE, ox, oy)
    put(img, 2, eye_y + 1, EYE_DARK, ox, oy)
    put(img, 5, eye_y + 1, EYE_DARK, ox, oy)
    put(img, 6, eye_y + 1, EYE, ox, oy)

    # --- the big villager nose: a 2-wide column down the centre, widening at the tip ---
    for y in range(nose_y, mouth_y):
        put(img, 3, y, NOSE, ox, oy)
        put(img, 4, y, NOSE, ox, oy)
    # nostril/underside shading at the bottom of the nose
    put(img, 3, mouth_y - 1, NOSE_DARK, ox, oy)
    put(img, 4, mouth_y - 1, NOSE_DARK, ox, oy)

    # --- downturned scowling mouth (corners higher than the centre) ---
    mouth_cells = [(2, mouth_y), (3, mouth_y + 0), (4, mouth_y + 0), (5, mouth_y),
                   (1, mouth_y - 1), (6, mouth_y - 1)]
    for (x, y) in mouth_cells:
        put(img, x, y, MOUTH, ox, oy)


def build_villager():
    img = blank()
    # villager head front face: u=[8,16), v=[8,18) -> 8 wide, 10 tall
    paint_face(img, ox=8, oy=8, face_h=10)
    img.save(os.path.join(TEX, "angry_villager_face.png"))


def build_guard():
    img = blank()
    # humanoid (Steve) head front face: u=[8,16), v=[8,16) -> 8 wide, 8 tall
    paint_face(img, ox=8, oy=8, face_h=8)
    img.save(os.path.join(TEX, "angry_guard_face.png"))


if __name__ == "__main__":
    mkdir(TEX)
    build_villager()
    build_guard()
    print("wrote angry_villager_face.png + angry_guard_face.png (64x64) to %s" % TEX)
