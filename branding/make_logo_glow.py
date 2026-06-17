"""
Animate the TVFB logo so its vivid green (the emerald letters and frame) pulses
with a glow + bloom halo, and export a looping GIF kept under 5 MB.

The background is left untouched; only green pixels brighten and a blurred green
halo blooms over them, oscillating once per loop.
"""
import os, math
import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "logo.png")

SIZE = 512          # output square size (logo is 2048; 512 is plenty and keeps the GIF small)
FRAMES = 24         # frames per pulse loop
FRAME_MS = 80       # ~1.9 s loop
GREEN_LO, GREEN_HI = 30, 70   # greenness ramp (g - max(r,b)) that counts as "emerald"
LIFT = 0.28         # how far green pixels shift toward bright green at the peak
BLOOM = 0.28        # bloom-halo strength at the peak

base = Image.open(SRC).convert("RGB").resize((SIZE, SIZE), Image.LANCZOS)
arr = np.asarray(base).astype(np.float32)
r, g, b = arr[..., 0], arr[..., 1], arr[..., 2]

# soft 0..1 mask of the vivid green (high green vs red/blue), ignoring the pale background
greenness = g - np.maximum(r, b)
mask = np.clip((greenness - GREEN_LO) / (GREEN_HI - GREEN_LO), 0.0, 1.0)

# pre-blurred bright-green bloom layer, shaped by the mask
GLOW_RGB = np.array([150, 255, 165], np.float32)
glow_seed = (mask[..., None] * GLOW_RGB).astype(np.uint8)
glow_blur = np.asarray(
    Image.fromarray(glow_seed).filter(ImageFilter.GaussianBlur(SIZE * 0.014))
).astype(np.float32)

m3 = mask[..., None]
frames = []
for i in range(FRAMES):
    t = 0.5 - 0.5 * math.cos(2 * math.pi * i / FRAMES)   # 0 -> 1 -> 0, smooth
    lift = m3 * (LIFT * t)
    frame = arr * (1 - lift) + GLOW_RGB * lift            # green pixels glow brighter
    frame = frame + glow_blur * (BLOOM * t)               # additive bloom halo
    frames.append(Image.fromarray(np.clip(frame, 0, 255).astype(np.uint8)))

# shared palette built from the brightest (peak) frame so glow colors are represented
peak = frames[FRAMES // 2]
pal = peak.quantize(colors=256, method=Image.MEDIANCUT)
gframes = [f.quantize(palette=pal, dither=Image.NONE) for f in frames]

out = os.path.join(HERE, "logo_glow.gif")
gframes[0].save(out, save_all=True, append_images=gframes[1:],
                duration=FRAME_MS, loop=0, optimize=True)
print("frames:", FRAMES, "size:", SIZE, "MB:", round(os.path.getsize(out) / 1e6, 2))
