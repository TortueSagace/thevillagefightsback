"""Every asset a model or blockstate names must exist. Run it before every build.

Black-and-purple in game means one reference did not resolve, and Minecraft says nothing about it: it
logs at DEBUG, falls back to the missing-texture checker and carries on. That is how a whole family of
coloured blocks shipped broken twice -- once as "village path renders as a missing block", once as a
player report that the cyan fertile log had a black-and-purple top.

Both times the cause was the same and it is worth stating plainly, because the naming reads the other
way round to everyone who meets it: **in the coloured families the colour goes LAST**. The texture is
`fertile_log_top_cyan.png`, never `fertile_log_cyan_top.png`; `fertile_chorus_flower_dead_cyan.png`,
never `fertile_chorus_flower_cyan_dead.png`. A generator that builds a name by inserting the colour
before the suffix produces something that looks completely reasonable and does not exist.

Checks, in the order a resource pack resolves them:
  1. every `textures` value in every model file resolves to a real .png
  2. every `parent` that is ours resolves to a real model
  3. every `model` named by a blockstate variant or multipart resolves to a real model

Vanilla (`minecraft:`) references are skipped -- they resolve against the client jar, not this tree.

Usage:  python tools/audit_assets.py [repo_root]
Exit code 0 when everything resolves, 1 otherwise, so it can gate a build.
"""
import io
import json
import os
import sys
import glob

NS = "thevillagefightsback"


def audit(root):
    assets = os.path.join(root, "src/main/resources/assets", NS)
    models_dir = os.path.join(assets, "models")
    blockstates_dir = os.path.join(assets, "blockstates")

    textures = set()
    for f in glob.glob(os.path.join(assets, "textures", "**", "*.png"), recursive=True):
        rel = os.path.relpath(f, os.path.join(assets, "textures")).replace("\\", "/")[:-4]
        textures.add("%s:%s" % (NS, rel))

    models = set()
    for f in glob.glob(os.path.join(models_dir, "**", "*.json"), recursive=True):
        rel = os.path.relpath(f, models_dir).replace("\\", "/")[:-5]
        models.add("%s:%s" % (NS, rel))

    problems = []

    def ours(ref):
        return isinstance(ref, str) and ref.startswith(NS + ":")

    for f in glob.glob(os.path.join(models_dir, "**", "*.json"), recursive=True):
        name = os.path.relpath(f, root).replace("\\", "/")
        try:
            j = json.load(io.open(f, encoding="utf-8"))
        except Exception as e:
            problems.append((name, "unparseable: %s" % e))
            continue
        for slot, ref in (j.get("textures") or {}).items():
            # "#slot" is a reference to another slot in the same model, not a file.
            if not isinstance(ref, str) or ref.startswith("#"):
                continue
            rid = ref if ":" in ref else "minecraft:" + ref
            if ours(rid) and rid not in textures:
                problems.append((name, "texture %s -> %s" % (slot, ref)))
        if ours(j.get("parent")) and j["parent"] not in models:
            problems.append((name, "parent -> %s" % j["parent"]))

    for f in glob.glob(os.path.join(blockstates_dir, "*.json")):
        name = os.path.relpath(f, root).replace("\\", "/")
        try:
            j = json.load(io.open(f, encoding="utf-8"))
        except Exception as e:
            problems.append((name, "unparseable: %s" % e))
            continue
        refs = []
        for variant in (j.get("variants") or {}).values():
            for v in (variant if isinstance(variant, list) else [variant]):
                if isinstance(v, dict) and "model" in v:
                    refs.append(v["model"])
        for part in (j.get("multipart") or []):
            apply = part.get("apply")
            for v in (apply if isinstance(apply, list) else [apply]):
                if isinstance(v, dict) and "model" in v:
                    refs.append(v["model"])
        for r in refs:
            if ours(r) and r not in models:
                problems.append((name, "model -> %s" % r))

    print("%s: %d textures, %d models" % (os.path.basename(root), len(textures), len(models)))
    for where, what in problems:
        print("  MISSING  %s   %s" % (where, what))
    if problems:
        print("%d unresolved reference(s)." % len(problems))
    else:
        print("all references resolve.")
    return len(problems)


if __name__ == "__main__":
    here = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    sys.exit(1 if audit(here) else 0)
