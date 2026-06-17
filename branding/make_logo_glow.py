"""
Animate the TVFB logo so its vivid green (the emerald letters and frame) pulses
with a subtle glow + bloom halo. Exports two looping GIFs:

  logo_glow.gif       full size, for the gallery/feature image (< 5 MB)
  logo_glow_icon.gif  small, for the project icon slot (< 256 KiB)

The background is left untouched; only green pixels brighten and a soft blurred
green halo blooms over them, oscillating once per loop.
"""
import os, math
import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "logo.png")

FRAME_MS = 80                 # base per-frame delay
GREEN_LO, GREEN_HI = 30, 70   # greenness ramp (g - max(r,b)) that counts as "emerald"
LIFT = 0.28                   # how far green pixels shift toward bright green at the peak
BLOOM = 0.28                  # bloom-halo strength at the peak
GLOW_RGB = np.array([150, 255, 165], np.float32)

def build(size, frames, colors, out_name):
    base = Image.open(SRC).convert("RGB").resize((size, size), Image.LANCZOS)
    arr = np.asarray(base).astype(np.float32)
    r, g, b = arr[..., 0], arr[..., 1], arr[..., 2]
    greenness = g - np.maximum(r, b)
    mask = np.clip((greenness - GREEN_LO) / (GREEN_HI - GREEN_LO), 0.0, 1.0)

    glow_seed = (mask[..., None] * GLOW_RGB).astype(np.uint8)
    glow_blur = np.asarray(
        Image.fromarray(glow_seed).filter(ImageFilter.GaussianBlur(size * 0.014))
    ).astype(np.float32)

    m3 = mask[..., None]
    rgba = []
    for i in range(frames):
        t = 0.5 - 0.5 * math.cos(2 * math.pi * i / frames)   # 0 -> 1 -> 0, smooth
        lift = m3 * (LIFT * t)
        frame = arr * (1 - lift) + GLOW_RGB * lift
        frame = frame + glow_blur * (BLOOM * t)
        rgba.append(Image.fromarray(np.clip(frame, 0, 255).astype(np.uint8)))

    pal = rgba[frames // 2].quantize(colors=colors, method=Image.MEDIANCUT)
    gframes = [f.quantize(palette=pal, dither=Image.NONE) for f in rgba]

    out = os.path.join(HERE, out_name)
    # keep the loop duration constant regardless of frame count
    gframes[0].save(out, save_all=True, append_images=gframes[1:],
                    duration=round(FRAME_MS * 24 / frames), loop=0, optimize=True)
    kib = os.path.getsize(out) / 1024
    print("%-20s %dpx %2d frames %3d colors -> %.1f KiB" % (out_name, size, frames, colors, kib))
    return kib

build(512, 24, 256, "logo_glow.gif")          # gallery / feature image
build(256, 12,  64, "logo_glow_icon.gif")     # project icon (must be < 256 KiB)
