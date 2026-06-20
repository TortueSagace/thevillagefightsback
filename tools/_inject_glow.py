"""One-off: add the animated EmeraldGlowLayer to each of the 18 emerald-mob renderers (NOT the village
Emeraldger). Inserts `this.addLayer(new EmeraldGlowLayer<>(this, "<base>"));` right after the eyes-layer block."""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLIENT = os.path.join(ROOT, "src", "main", "java", "com", "example", "villagefightsback", "client")

RENDERERS = {
    "EmeraldCowRenderer": "emerald_cow", "EmeraldChickenRenderer": "emerald_chicken",
    "EmeraldCatRenderer": "emerald_cat", "EmeraldEndermiteRenderer": "emerald_endermite",
    "EmeraldWolfRenderer": "emerald_wolf", "EmeraldHorseRenderer": "emerald_horse",
    "EmeraldPigRenderer": "emerald_pig", "EmeraldSalmonRenderer": "emerald_salmon",
    "EmeraldBatRenderer": "emerald_bat", "EmeraldZombieRenderer": "emerald_zombie",
    "EmeraldSkeletonRenderer": "emerald_skeleton", "EmeraldCreeperRenderer": "emerald_creeper",
    "EmeraldEndermanRenderer": "emerald_enderman", "EmeraldIronGolemRenderer": "emerald_iron_golem",
    "EmeraldPhantomRenderer": "emerald_phantom", "EmeraldWitherRenderer": "emerald_wither",
    "EmeraldGuardianRenderer": "emerald_guardian", "EmeraldElderGuardianRenderer": "emerald_elder_guardian",
}

# Matches the whole eyes addLayer block (anonymous EyesLayer) so we can splice the glow layer right after it.
EYES_BLOCK = re.compile(
    r"(this\.addLayer\(new EyesLayer<>\(this\) \{.*?\}\);\n)", re.DOTALL)


def inject(cls, base):
    path = os.path.join(CLIENT, cls + ".java")
    with open(path, "r", encoding="utf-8") as fh:
        src = fh.read()
    if "EmeraldGlowLayer" in src:
        return f"{cls}: already has glow"
    m = EYES_BLOCK.search(src)
    if not m:
        return f"{cls}: EYES BLOCK NOT FOUND -- skipped"
    indent = re.match(r"\s*", src[src.rfind("\n", 0, m.start()) + 1:]).group(0)
    add = f'{indent}this.addLayer(new EmeraldGlowLayer<>(this, "{base}"));\n'
    src = src[:m.end()] + add + src[m.end():]
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(src)
    return f"{cls}: glow added ({base})"


for cls, base in RENDERERS.items():
    print(inject(cls, base))
