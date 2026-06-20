from PIL import Image
import os
BASE = os.path.join("src","main","resources","assets","thevillagefightsback","textures")
OUT = os.path.join("tools","_cover_tmp"); os.makedirs(OUT, exist_ok=True)
items = {
 "emeraldger_gui": os.path.join(BASE,"gui","emeraldger_face.png"),
 "emeraldger_ent": os.path.join(BASE,"entity","emeraldger.png"),
 "emeraldger_eyes":os.path.join(BASE,"entity","emeraldger_eyes.png"),
 "limbo_layer_1":  os.path.join(BASE,"models","armor","limbo_layer_1.png"),
 "limbo_spikes":   os.path.join(BASE,"entity","limbo_helmet_spikes.png"),
}
def up(im,s): return im.resize((im.width*s, im.height*s), Image.NEAREST)
for name,p in items.items():
    if not os.path.exists(p): print(name,"MISSING"); continue
    im = Image.open(p).convert("RGBA")
    print(f"{name:16s} size={im.width}x{im.height} bbox={im.getbbox()}")
    s = max(1, 384//max(im.width,im.height))
    up(im,s).save(os.path.join(OUT,name+".png"))
print("done")
