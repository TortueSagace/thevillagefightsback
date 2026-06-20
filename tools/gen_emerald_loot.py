"""Generate the EMERALD mob loot tables (v1.0.3): each emerald unit drops its normal variant's loot PLUS one
guaranteed emerald. We copy the vanilla entity loot table out of the client jar and append an emerald pool, writing
it to the emerald entity's default loot-table id (thevillagefightsback:entities/emerald_<mob>). Mobs whose vanilla
form has no loot table (endermite, wither -> code-based nether star) just get the emerald pool; code-based drops are
still produced by the inherited entity class."""
import json
import os
import zipfile

from gen_fertile_blocks import JAR, DATA

MOBS = ["cow", "chicken", "cat", "endermite", "wolf", "horse", "pig", "salmon", "bat", "zombie", "skeleton",
        "creeper", "enderman", "iron_golem", "phantom", "wither", "guardian", "elder_guardian"]

EMERALD_POOL = {
    "rolls": 1,
    "entries": [{"type": "minecraft:item", "name": "minecraft:emerald"}],
}


def vanilla_table(zf, mob):
    path = f"data/minecraft/loot_tables/entities/{mob}.json"
    try:
        with zf.open(path) as fh:
            return json.load(fh)
    except KeyError:
        return None


def main():
    out_dir = os.path.join(DATA, "loot_tables", "entities")
    os.makedirs(out_dir, exist_ok=True)
    with zipfile.ZipFile(JAR) as zf:
        for mob in MOBS:
            table = vanilla_table(zf, mob)
            if table is None:
                table = {"type": "minecraft:entity", "pools": []}
            table.setdefault("pools", [])
            table["pools"].append(dict(EMERALD_POOL))
            with open(os.path.join(out_dir, f"emerald_{mob}.json"), "w", encoding="utf-8") as fh:
                json.dump(table, fh, indent=2)
            print(f"  emerald_{mob}: {'copied vanilla + emerald' if vanilla_table(zf, mob) else 'emerald only'}")
    print(f"emerald loot tables written for {len(MOBS)} mobs")


if __name__ == "__main__":
    main()
