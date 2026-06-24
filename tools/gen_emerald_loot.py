"""Generate the EMERALD mob loot tables: each emerald unit drops its normal variant's loot (we copy the vanilla
entity loot table out of the client jar to the emerald entity's id, thevillagefightsback:entities/emerald_<mob>).
The emerald GEM is no longer a loot-table pool -- it is dropped in code with a chance proportional to the unit's
max HP (see EmeraldVirusEvents.onEmeraldGemDrop), which a static loot table can't express. Mobs whose vanilla form
has no loot table (endermite, wither) get an empty table; their code-based drops still come from the entity class."""
import json
import os
import zipfile

from gen_fertile_blocks import JAR, DATA

MOBS = ["cow", "chicken", "cat", "endermite", "wolf", "horse", "pig", "salmon", "bat", "zombie", "skeleton",
        "creeper", "enderman", "iron_golem", "phantom", "wither", "guardian", "elder_guardian"]


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
            had_vanilla = table is not None
            if table is None:
                table = {"type": "minecraft:entity", "pools": []}
            table.setdefault("pools", [])
            # The emerald gem is dropped in code (HP-proportional), NOT here -- these tables are just the
            # vanilla variant's normal loot.
            with open(os.path.join(out_dir, f"emerald_{mob}.json"), "w", encoding="utf-8") as fh:
                json.dump(table, fh, indent=2)
            print(f"  emerald_{mob}: {'copied vanilla loot' if had_vanilla else 'empty (code drops only)'}")
    print(f"emerald loot tables written for {len(MOBS)} mobs")


if __name__ == "__main__":
    main()
