# The Village Fights Back -- Achievements & Roadmap

A running log of what the mod does (the "achievements" -- features shipped) and where it's going (the
roadmap). Grouped by system, not by commit. Update this when a **big** update lands -- see the maintenance note
at the bottom.

> Minecraft Forge 1.20.1 (Java 17) · modid `thevillagefightsback` · package `com.example.villagefightsback`

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
- **Staged defenders** (stages 1→4) for villagers, guards, golems -- with creative spawn eggs.
- Daily sunset reinforcements scale with the village's stage.
- **Emerald gear**: armour + full tool set (tier between iron and diamond), in our creative tab.
- **Emerald knights** (stage-3 guards): mace / crossbow / Alien Slasher; death explosion.
- **Gigachad golems**: gold re-skin, 200 HP, rockets.
- **Immunity** effect/potion + a brain node immunity aura covering villagers, guards, and golems.
- **Advancements** (Brain Node tab): **A very bad speech** (land an emerald egg on a target), plus an organic
  branch -- **Emerald party** (10 emerald units around a village), **Overpopulation** (reach 50), and
  **I'm not kidding anymore** (witness the birth of an emerald wither).

### 3. Walls & defensive structures
- Deterministic wall planner that follows the village footprint (concave), with builders, gates, loopholes,
  and scaffolding -- scaled to village size.
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
- **General Darius** -- a boss-grade upgrade of the stage-4 guard, marched ONLY by a **stage-4** brain node's
  reflex (costs 64 gems, refunded when he returns home) when a heavy threat appears: an enemy with >= 100 max
  HP, or a player who has killed >= 3 villagers/guards in 3 minutes. Wields the **Alien Smasher** and the full
  **Limbo set** (incl. the elytra chestplate), so he gets every full-set advantage (fire/fall immunity,
  harmful-effect clearing, contact soft-block breaking, and -- below 50% HP -- the **banish beam** on a 30s
  cooldown). Limbo-banish immune like the Vindictive Village Spirit; has a boss bar and the name "Darius". He
  **locks onto the single biggest threat** and ignores all others (retaliating only against whoever hits him),
  hunts it anywhere, then picks the next biggest; with none left he walks home and despawns. **Hires** nearby
  idle village guards into a follow-squad, **double-jumps** (no ceiling -- he breaks through), pillars out of
  pits with cheap scaffolds, and uses **custom pathfinding** that routes around the village's own walls while
  plowing straight through generic terrain (broken on contact).

### 6. Native weapons (JEG and the mace mod fully removed)
- Native **bullet**, **rocket**, and **fire grenade** -- the mod no longer depends on Just Enough Guns.
- Native **Mace** + **Wind Charge** + the three mace enchantments (**Density**, **Breach**, **Wind Burst**,
  with enchanted books) -- a faithful clone of the vanilla 1.20.1 mace, so the mod no longer depends on the
  mace backport mod. The mace's smash (fall-scaled bonus, area knockback, Wind Burst self-launch, no self
  fall damage) is reproduced natively.
- **Rocket Launcher** polish: a real muzzle hole on the firing face; aiming shows the small back face shifted
  clear of the crosshair (barrel pointing at the target); fires from the bow-style aim, consuming one Rocket
  in survival; gigachad / vindictive gigachad drop the launcher (100%) + 0-2 rockets.

### 7. The Limbo dimension
- A dark void dimension with a 22×22×22 bedrock holding-cell at spawn (always-loaded).
- **Limbo Potion** (drink → banished), **Limbo Gravel** return-markers (fall like gravel, name-tagged),
  **Limbo Bed** (sleep → return) -- and trying to sleep in ANY bed while in the Limbo returns you the same way;
  banish FX (black spirals + soul + portal swirl).
- **The Limbo expansion:**
  - **Green Limbo Portal** -- light a fake-obsidian frame for a green portal; teleports at **2× nether
    compression** (coordinate_scale 16); never spawns inside the central cube (raised above it).
  - **Void-fall chain** -- falling out of the world drops you into Limbo's top, then the End's top; only an
    End void-fall is fatal.
  - **Floating bedrock cubes** of varying size, generated on demand; their OUTER surface is covered in
    **Forgotten Glowstone** clumps (vivid green, obsidian-tough, drops Forgotten Powder -- placed on the outside
    like vines, the bedrock shell left intact); big cubes hold a **nest** built entirely of ancient debris.
  - **Limbo Eggs** -- black/white dragon eggs in the nests that hatch (on destruction unless Silk Touch, or
    rarely on their own) into a **Limbo Phantom**: a 3× bedrock-phasing phantom (white vines, glowing eyes)
    that grabs prey and hauls it through bedrock; drops **Limbo Membrane**.
  - **Dimensional drift** -- 2% of Limbo chunks mirror their overworld twin and 1% their nether twin, live and
    two-way (blocks, biomes, container contents); living entities don't cross. Bounded copy budget; configurable.
  - **Limbo gear** -- Membrane → Ingot → (+ a 1%-village-chest Smithing Template) smith netherite into **Limbo
    armour** (full knockback immunity) and the **Limbo tool set** (pickaxe that mines bedrock + reinforced
    deepslate; axe/shovel/hoe that out-mine netherite).
  - **Full-set bonuses** -- immunity (incl. fire + fall), harmful-effect clearing, phantom neutrality,
    bedrock phasing (walk through bedrock like the phantom), and a keybound (G) green **banish beam** on a
    30s HUD cooldown.
  - **Volatile Limbo Potion** -- a thrown, area banish. (The old Limbo Arrow was removed.)
  - **Limbo Sage** -- a new base villager profession whose job site is the Village Brain Node itself (one
    sage per village). A black-robed cleric who sells tiered, expensive limbo goods (useless blocks up to
    randomly-enchanted limbo tools, never limbo armour) and hurls a Volatile Limbo Potion at enemies above
    20 HP on a 60s cooldown.
  - **Limbo Block** -- 9 limbo ingots, bedrock-tough, animated green, and a valid beacon base.
  - A 9-node **advancement tree** for the Limbo journey (added **Dark deal**: buy from the Limbo Sage).

### 8. Expanded arsenal
- **Alien Slasher / Alien Eradicator split**: the Slasher now always inflicts Emerald Virus I; the new
  **Alien Eradicator** (a vivid-green Slasher) inflicts Virus II. Stage-4 villagers, Emeraldgers, and stage-4
  guards wield the Eradicator; vindictive guards keep the plain Slasher.
- **Emerald Virus Potion II** (brewed I + glowstone, plus splash) and a **level-II Emerald Virus Arrow**.
- New 9-ingredient Slasher recipe (netherite axe + Emerald Virus Potion I + fire charge + echo shard + ghast
  tear + fertile chorus corners); the Eradicator uses the same recipe with the Virus II potion. Fertile
  chorus is edible.
- **Alien Smasher** -- a Limbo-forged mace (Mace + Limbo Ingot at the smithing table) carrying the guard mace
  loadout (Density 3 / Wind Burst 3 / Fire Aspect 2) and further enchantable. Every hit blasts three
  TNT-sized holes straight down under the target (y, y-2, y-4), setting them on fire, with no creature
  damage -- carving a fiery pit the enemy falls into.
- **Emerald Hammer**: the 3x3 now orients to the actual mined face, works in creative, and costs one
  durability per 3x3 (not nine).
- **Drops**: villagers/guards have a 5% chance to drop their used weapon on death; guards also drop 1-5 wind
  charges. The native mace + wind charges + mace-enchant books are sold by the **Weaponsmith** (not craftable).

### 9. Village law (Hero / Enemy of the Village)
- Donating emeralds to the brain grants/extends **Hero of the Village** for X minutes (X = emeralds given),
  in any game mode. Placing the brain/bell no longer grants it.
- The brain's discovery banner now fires the first time **any** player comes near a node, not just on placement.
- **Chest/barrel protection**: placing one in a brain's range donates it to the village; a non-hero who opens
  a village chest is warned (10s cooldown); taking an item brands them an **Enemy of the Village** for 30s
  with a red "stealing from the village!" broadcast.
- The village treats a player as hostile **iff** they hold Enemy of the Village, driven by effect
  add/remove/expire events (rally on add, disengage on end) rather than a per-tick verifier.
- Hero and Enemy of the Village both survive milk and the mod's Immunity; Enemy of the Village still clears
  on death.
- **Angry faces**: while a villager or guard is fighting (detected from the synced arm-swing), the "angry
  villager" face (tan skin, black angry brows, bright green eyes) is overlaid on its head.

### Tooling
- Built-in per-subsystem profiler (`/gbtyw profile`) + spark integration for finding lag.

---

## 🗺️ Roadmap

### Proposed next (needs your steer)
- A Limbo mini-boss (the Limbo Sage is now the first Limbo native).
- Limbo ambience: custom sounds + music.
- Wider config surface (cube rarity, phantom spawn rates); drift rates are already in config.
- More in-game advancements as the Limbo grows (these need your approval before they go in).

---

## 🔧 Maintenance
Update this file when a **big** update ships (a new system, dimension, boss, or gear line), not for small
tweaks. The doc can be updated freely; adding new IN-GAME advancements needs the user's approval first.
