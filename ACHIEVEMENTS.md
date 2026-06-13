# Go Back To Your World — Achievements & Roadmap

A running log of what the mod does (the "achievements" — features shipped) and where it's going (the
roadmap). Grouped by system, not by commit. Update this when a **big** update lands — see the maintenance note
at the bottom.

> Minecraft Forge 1.20.1 (Java 17) · modid `gobacktoyourworld` · package `com.example.villagergunners`

---

## ✅ Shipped

### 1. Armed villages (the original premise)
- Naturally-generated villagers wield revolvers; guards wield miniguns; iron golems carry rocket launchers.
- They fight any hostile mob. Ally-immunity so they never hurt each other.
- Golems: point-blank melee, wind-charge "pogo" launch, rocket at apex.
- Desperation AI: swarmed defenders lob a grenade and flee; reinforcement flares rally idle allies.
- Balance: villager guns nerfed to 1 shot/sec, flat 8 damage.

### 2. The Village Gravemind (evolution system)
- A Village Gravemind block under the bell drives a **per-village** evolution level (independent economy).
- Right-click upgrade screen (GUI) shows villager/guard/golem counts and level.
- **Staged defenders** (stages 1→4) for villagers, guards, golems — with creative spawn eggs.
- Daily sunset reinforcements scale with the village's stage.
- **Emerald gear**: armour + full tool set (tier between iron and diamond), in our creative tab.
- **Emerald knights** (stage-3 guards): mace / crossbow / Alien Slasher; death explosion.
- **Gigachad golems**: gold re-skin, 200 HP, rockets.
- **Immunity** effect/potion + a gravemind immunity aura covering villagers, guards, and golems.

### 3. Walls & defensive structures
- Deterministic wall planner that follows the village footprint (concave), with builders, gates, loopholes,
  and scaffolding — scaled to village size.
- **Wall defenders**: turret-creatures that fire real bullets / rockets, raised above the gates at level 2.
- **Reinforced glass** (ancient-debris-tough, light-transparent) domes; **protecting glass** (one-way for
  projectiles); **fake cobblestone / fake obsidian** smart-door gates (allies phase, enemies don't).
- **Blue TNT** builds a house block-by-block; **Green TNT** builds tower variants.

### 4. The Emerald Virus / infection
- A green "fertile/rich" infection that slowly converts the world (10× slower than the source), with a
  block cap, fertile/rich sculk catalysts, and a full **Fertile/Rich block family** (dirt, sand, stone, log,
  deepslate, netherrack, end stone, sculk, leaves, stairs/slab/fence/door).
- **Emerald Apple**, **Emerald Virus potion + arrows**.
- **Emeraldger**: the stage-4 virus warrior (scales with the host's HP, total immunity, glowing green eyes).

### 5. The Unleashed Gravemind (boss)
- Erupts when a village's last villager dies. Green sonic-boom **banish waves**, slam attack, screen shake.
- **Infected faction** (boss + vindictive guards + vindictive gigachads): never fight each other, absorb 90%
  explosion damage; creatures slain by a direct boss hit become vindictive minions (capped).
- Performance-tuned (pathfinding throttled after profiling).

### 6. Native weapons (JEG fully removed)
- Native **bullet**, **rocket**, and **fire grenade** — the mod no longer depends on Just Enough Guns.

### 7. The Limbo dimension
- A dark void dimension with a 22×22×22 bedrock holding-cell at spawn (always-loaded).
- **Limbo Potion** (drink → banished), **Limbo Gravel** return-markers (fall like gravel, name-tagged),
  **Limbo Bed** (sleep → return), banish FX (black spirals + soul + portal swirl).
- **Limbo expansion (this update):**
  - **Green Limbo Portal** — light a fake-obsidian frame → a green portal; teleports at **2× nether
    compression** (coordinate_scale 16); never spawns inside the central cube (raised above it).
  - **Void-fall chain** — falling out of the world drops you into Limbo's top, then the End's top; only an
    End void-fall is fatal.
  - **Forgotten Glowstone** (vivid green, obsidian-tough, drops Forgotten Powder) encrusting the cubes.
  - **Floating bedrock cubes** of varying size generated on demand across the Limbo.
  - **Limbo Phantom** — a 3× bedrock-phasing phantom (white vines, glowing eyes) that grabs prey and hauls
    it through bedrock; drops **Limbo Membrane**.
  - **Limbo gear chain** — Membrane → Ingot → (+ a 1%-village-chest Smithing Template) smith netherite into
    **Limbo armour** (full knockback immunity) and a **Limbo Pickaxe** (mines bedrock + reinforced deepslate).
  - **Volatile Limbo Potion** — a thrown, area banish.

### Tooling
- Built-in per-subsystem profiler (`/gbtyw profile`) + spark integration for finding lag.

---

## 🗺️ Roadmap

### In progress (Limbo expansion, current work)
- **Limbo eggs** — black/white dragon eggs in the nests; hatch on destruction (unless Silk Touch) or rarely
  on their own, into a Limbo Phantom.
- **Dimensional drift** — 2% of Limbo chunks mirror their overworld twin, 1% their nether twin (live two-way
  block + biome sync; living entities don't cross).
- **Limbo gear set bonuses** — full set grants phantom-neutrality, immunity, fire + fall immunity, and a
  keybound green-beam banish ability with an on-screen cooldown.

### Proposed next (needs your steer)
- In-game **advancement tree** for the Limbo journey (enter Limbo, smith Limbo gear, survive the void-fall, …).
- More Limbo natives / a Limbo mini-boss.
- Limbo ambience: custom sounds + music.
- Config surface for the drift rates and cube rarity (partly in place).

---

## 🔧 Maintenance
Update this file when a **big** update ships (a new system, dimension, boss, or gear line) — not for small
tweaks. **Always get the user's approval before editing this list.**
