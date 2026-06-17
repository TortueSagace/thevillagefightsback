"""
Render the Village Brain Node block as an inventory-style isometric cube and
animate its glowing top into a transparent GIF + a static PNG, for use as the
Modrinth / CurseForge project icon. Crisp (NEAREST) scaling keeps the edges
hard so GIF 1-bit transparency stays clean.
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets",
                   "thevillagefightsback", "textures", "block")

top_strip = Image.open(os.path.join(TEX, "village_brain_node_top.png")).convert("RGBA")   # 16 x 512
side      = Image.open(os.path.join(TEX, "village_brain_node_side1.png")).convert("RGBA") # 16 x 16

FR = 16
FRAMES = top_strip.height // FR   # 32
S = 256                           # upscaled face size (16 -> 256, crisp 16x)

def shade(img, f):
    r, g, b, a = img.split()
    lut = [int(min(255, v * f)) for v in range(256)]
    return Image.merge("RGBA", (r.point(lut), g.point(lut), b.point(lut), a))

side_up    = side.resize((S, S), Image.NEAREST)
left_face  = shade(side_up, 0.62)   # Minecraft-ish darker left face
right_face = shade(side_up, 0.82)   # lighter right face

# --- isometric (2:1) cube geometry ---
a = 250; r = a // 2; h = a          # diamond half-width, rise, side height
W = 2 * a; H = 2 * r + h            # 500 x 500
PAD = 8
CW, CH = W + 2 * PAD, H + 2 * PAD
ox, oy = PAD, PAD

Top    = (ox + a,     oy + 0)
Right  = (ox + 2 * a, oy + r)
Bottom = (ox + a,     oy + 2 * r)
Left   = (ox + 0,     oy + r)
def down(p): return (p[0], p[1] + h)

def affine_coeffs(P0, P1, P2, s):
    """PIL AFFINE maps out(x,y) -> in(a*x+b*y+c, d*x+e*y+f). P0->src(0,0), P1->src(s,0), P2->src(0,s)."""
    Ux, Uy = P1[0] - P0[0], P1[1] - P0[1]
    Vx, Vy = P2[0] - P0[0], P2[1] - P0[1]
    det = Ux * Vy - Vx * Uy
    m11, m12 = Vy / det, -Vx / det
    m21, m22 = -Uy / det, Ux / det
    return (s * m11, s * m12, s * (-m11 * P0[0] - m12 * P0[1]),
            s * m21, s * m22, s * (-m21 * P0[0] - m22 * P0[1]))

def place(face, P0, P1, P2):
    return face.transform((CW, CH), Image.AFFINE, affine_coeffs(P0, P1, P2, S),
                          resample=Image.NEAREST)

# side faces are identical every frame -> render once
left_layer  = place(left_face,  Left,   Bottom, down(Left))
right_layer = place(right_face, Bottom, Right,  down(Bottom))

frames = []
for i in range(FRAMES):
    top_f = top_strip.crop((0, i * FR, FR, (i + 1) * FR)).resize((S, S), Image.NEAREST)
    top_layer = place(top_f, Top, Right, Left)
    canvas = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
    for layer in (left_layer, right_layer, top_layer):   # top drawn last
        canvas = Image.alpha_composite(canvas, layer)
    cube = canvas.crop(canvas.getbbox())
    final = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    final.paste(cube, ((512 - cube.width) // 2, (512 - cube.height) // 2), cube)
    frames.append(final)

# --- static PNG: the brightest (most glowing) frame ---
def brightness(im):
    return sum(im.convert("RGB").resize((1, 1), Image.BOX).getpixel((0, 0)))
best = max(range(FRAMES), key=lambda i: brightness(frames[i]))
frames[best].save(os.path.join(HERE, "brain_node_icon.png"))
print("static PNG: frame", best)

# --- animated GIF with a shared palette + clean 1-bit transparency ---
strip = Image.new("RGB", (512, 512 * FRAMES))
for i, f in enumerate(frames):
    strip.paste(f.convert("RGB"), (0, 512 * i))
pal = strip.quantize(colors=255, method=Image.MEDIANCUT)

gif_frames = []
for f in frames:
    p = f.convert("RGB").quantize(palette=pal, dither=Image.NONE)
    transparent = f.split()[3].point(lambda v: 255 if v < 128 else 0)
    p.paste(255, transparent)
    p.info["transparency"] = 255
    gif_frames.append(p)

gif_frames[0].save(os.path.join(HERE, "brain_node_icon.gif"),
                   save_all=True, append_images=gif_frames[1:],
                   duration=50, loop=0, disposal=2, transparency=255, optimize=False)
print("frames:", FRAMES, "-> brain_node_icon.gif (%dms/frame)" % 50)
