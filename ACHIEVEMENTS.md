# The Village Fights Back — Achievements & Roadmap

A running log of what the mod does (the "achievements" — features shipped) and where it's going (the
roadmap). Grouped by system, not by commit. Update this when a **big** update lands — see the maintenance note
at the bottom.

> Minecraft Forge 1.20.1 (Java 17) · modid `thevillagefightsback` · package `com.example.villagergunners`

---

## ✅ Shipped

### 1. Armed villages (the original premise)
- Naturally-generated villagers wield revolvers; guards wield miniguns; iron golems carry rocket launchers.
- They fight any hostile mob. Ally-immunity so they never hurt each other.
- Golems: point-blank melee, wind-charge "pogo" launch, rocket at apex.
- Desperation AI: swarmed defenders lob a grenade and flee; reinforcement flares rally idle allies.
- Balance: villager guns nerfed to 1 shot/sec, flat 8 damage.

### 2. The Village Brain Node (evolution system)
- A Village Brain Node block under the bell drives a **per-village** evolution level (independent economy).
- Right-click upgrade screen (GUI) shows villager/guard/golem counts and level.
- **Staged defenders** (stages 1→4) for villagers, guards, golems — with creative spawn eggs.
- Daily sunset reinforcements scale with the village's stage.
- **Emerald gear**: armour + full tool set (tier between iron and diamond), in our creative tab.
- **Emerald knights** (stage-3 guards): mace / crossbow / Alien Slasher; death explosion.
- **Gigachad golems**: gold re-skin, 200 HP, rockets.
- **Immunity** effect/potion + a brain node immunity aura covering villagers, guards, and golems.

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

### 5. The Unleashed BrainNode (boss)
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
- **The Limbo expansion:**
  - **Green Limbo Portal** — light a fake-obsidian frame for a green portal; teleports at **2× nether
    compression** (coordinate_scale 16); never spawns inside the central cube (raised above it).
  - **Void-fall chain** — falling out of the world drops you into Limbo's top, then the End's top; only an
    End void-fall is fatal.
  - **Floating bedrock cubes** of varying size, generated on demand, sparsely encrusted with **Forgotten
    Glowstone** (vivid green, obsidian-tough, drops Forgotten Powder); big cubes hold an ancient-debris **nest**.
  - **Limbo Eggs** — black/white dragon eggs in the nests that hatch (on destruction unless Silk Touch, or
    rarely on their own) into a **Limbo Phantom**: a 3× bedrock-phasing phantom (white vines, glowing eyes)
    that grabs prey and hauls it through bedrock; drops **Limbo Membrane**.
  - **Dimensional drift** — 2% of Limbo chunks mirror their overworld twin and 1% their nether twin, live and
    two-way (blocks, biomes, container contents); living entities don't cross. Bounded copy budget; configurable.
  - **Limbo gear** — Membrane → Ingot → (+ a 1%-village-chest Smithing Template) smith netherite into **Limbo
    armour** (full knockback immunity) and the **Limbo tool set** (pickaxe that mines bedrock + reinforced
    deepslate; axe/shovel/hoe that out-mine netherite).
  - **Full-set bonuses** — immunity (incl. fire + fall), harmful-effect clearing, phantom neutrality,
    bedrock phasing (walk through bedrock like the phantom), and a keybound (G) green **banish beam** on a
    30s HUD cooldown.
  - **Volatile Limbo Potion** — a thrown, area banish. (The old Limbo Arrow was removed.)
  - An 8-node **advancement tree** for the Limbo journey.

### Tooling
- Built-in per-subsystem profiler (`/gbtyw profile`) + spark integration for finding lag.

---

## 🗺️ Roadmap

### Proposed next (needs your steer)
- More Limbo natives / a Limbo mini-boss.
- Limbo ambience: custom sounds + music.
- Wider config surface (cube rarity, phantom spawn rates); drift rates are already in config.
- More in-game advancements as the Limbo grows (these need your approval before they go in).

---

## 🔧 Maintenance
Update this file when a **big** update ships (a new system, dimension, boss, or gear line), not for small
tweaks. The doc can be updated freely; adding new IN-GAME advancements needs the user's approval first.
