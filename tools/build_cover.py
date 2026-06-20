"""
Mod cover (1:1): a helmeted villager bust over the animated Brain Node.
  - Big split face: LEFT half normal villager, RIGHT half emeraldger (glowing eyes).
  - Limbo helmet EQUIPPED on the head (fitted to head width, covering the forehead),
    spikes ending just before the top edge.
  - Background: animated upper face of the Village Brain Node.
Outputs promo/cover.png (1024), promo/cover.gif (512), tools/_cover_tmp previews.
"""
import os
from PIL import Image

BASE = os.path.join("src","main","resources","assets","thevillagefightsback","textures")
PROMO= "promo"; TMP = os.path.join("tools","_cover_tmp")
os.makedirs(PROMO, exist_ok=True); os.makedirs(TMP, exist_ok=True)
C = 1024

def strip_frame(path, f):
    im = Image.open(path).convert("RGBA"); w = im.width; n = im.height//w
    return im.crop((0,(f%n)*w, w,(f%n)*w+w))
def up(im,s): return im.resize((im.width*s, im.height*s), Image.NEAREST)
def at(comp,pos): l=Image.new("RGBA",(C,C),(0,0,0,0)); l.alpha_composite(comp,pos); return l

# --- split face (8x10): left vanilla villager, right emeraldger+eyes ---
R = (8,8,16,18)
vv = Image.open(os.path.join("tools","villager_face_src.png")).convert("RGBA").crop(R)
eg = Image.open(os.path.join(BASE,"entity","emeraldger.png")).convert("RGBA")
egeyes = Image.open(os.path.join(BASE,"entity","emeraldger_eyes.png")).convert("RGBA")
ef = eg.crop(R); ef.alpha_composite(egeyes.crop(R))
FACE = Image.new("RGBA",(8,10),(0,0,0,0))
FACE.alpha_composite(vv.crop((0,0,4,10)),(0,0))
FACE.alpha_composite(ef.crop((4,0,8,10)),(4,0))

P_BRAIN=os.path.join(BASE,"block","village_brain_node_top.png")
P_HELM =os.path.join(BASE,"item","limbo_helmet.png")

# vignette
def make_vig():
    import math; n=96; v=Image.new("L",(n,n),0); vp=v.load()
    cx=cy=(n-1)/2; md=math.hypot(cx,cy)
    for y in range(n):
        for x in range(n):
            d=math.hypot(x-cx,y-cy)/md; a=max(0.0,(d-0.55)/0.45); vp[x,y]=int(min(150,a*a*150))
    return v.resize((C,C),Image.BILINEAR)
VIG=make_vig()
VIG_LAYER=Image.merge("RGBA",(*Image.new("RGB",(C,C),(2,8,5)).split(),VIG))

# --- layout params (tuned so the helmet dome width == head width: an equipped fit) ---
SFACE = 68           # face px scale (8x10 -> 544 x 680); head width 544
FACE_CX = 512
FACE_TOP = 294       # face top y; rows 0-4 (forehead) sit under the helmet
SHELM = 54           # helmet px scale (dome 10 native -> 540 wide ~= head)
HELM_CX = 512
HELM_TOP = 40        # spikes start just below the top edge; rim lands ~y634

def compose(brain_f, helm_f):
    cv=Image.new("RGBA",(C,C),(0,0,0,255))
    bg=up(strip_frame(P_BRAIN,brain_f), C//16)
    bg=Image.blend(bg, Image.new("RGBA",(C,C),(6,12,8,255)), 0.42)
    cv.alpha_composite(bg); cv.alpha_composite(VIG_LAYER)
    face=up(FACE,SFACE)
    cv.alpha_composite(at(face,(FACE_CX-face.width//2, FACE_TOP)))
    helm=up(strip_frame(P_HELM,helm_f), SHELM)
    cv.alpha_composite(at(helm,(HELM_CX-helm.width//2, HELM_TOP)))
    return cv.convert("RGB")

# --- outputs: static PNG (icon candidate), animated GIF (gallery), preview strip ---
# 32 GIF frames @ 50ms == in-game timing: brain advances 1 frame/tick, helmet every 5 ticks.
T = 32
def frame(t): return compose(t % 32, (t//5) % 6)

frame(8).save(os.path.join(PROMO,"cover.png"))                 # static, spikes lit, mid brain-pulse

frames = [frame(t).resize((512,512), Image.NEAREST) for t in range(T)]
frames[0].save(os.path.join(PROMO,"cover.gif"), save_all=True, append_images=frames[1:],
               duration=50, loop=0, optimize=True, disposal=2)

keys = [0, 8, 16, 24]
grid = Image.new("RGB",(960,960),(0,0,0))
for i,t in enumerate(keys):
    grid.paste(frame(t).resize((480,480), Image.NEAREST), ((i%2)*480,(i//2)*480))
grid.save(os.path.join(TMP,"cover_strip.png"))

print("wrote", os.path.join(PROMO,"cover.png"), "(1024 static)")
print("wrote", os.path.join(PROMO,"cover.gif"), "(512,", T, "frames)")
print("wrote", os.path.join(TMP,"cover_strip.png"), "(preview)")
