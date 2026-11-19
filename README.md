# The Village Fights Back

A Minecraft **Forge 1.20.1** mod that arms villagers and iron golems with guns from
[Just Enough Guns](https://www.curseforge.com/minecraft/mc-mods/just-enough-guns) (JEG) so they fight
back against any hostile mob -- including infection-style invaders such as *From Another World* -- and
tell them to *go back to their world*.

> The name is a deliberate counter to the **From Another World** infection mod.

## What it does

- **Every villager** is armed. When it sees a hostile mob (within **30 blocks** and with a direct
  line of sight) it shoots it with a **revolver**; if the mob is within **5 blocks** it switches to a
  **flamethrower**. Villagers hold their ground and fight instead of fleeing, and **wear iron boots**.
- **Village iron golems** carry a **rocket launcher**. They hold at range and fire only when **no
  villager or other iron golem is within 5 blocks of the target** (so the splash never hits allies),
  backing away to keep a safe standoff. Player-built golems are left alone.
- **"Hostile" is mod-agnostic** -- anything implementing Minecraft's `Enemy` interface is a target, so
  modded monsters are covered automatically with no per-mod code.
- **Retaliation:** hit a villager and it turns hostile toward you for a few seconds, then reverts.
- Mobs aim with perfect precision and fire via JEG's own projectile API; the guns shown in their
  hands are a decorative client-side render layer (the mobs don't actually hold the item, which keeps
  JEG from hijacking their AI).

## Dependencies (required at runtime)

This mod integrates with JEG and its own dependencies. Install alongside:

- Just Enough Guns `0.13.2` (`jeg`)
- MrCrayfish's Framework `0.8.0+` (`framework`)
- GeckoLib `4.8.3+` (`geckolib`)

## Building

ForgeGradle 6 / Gradle 8.8 / **JDK 17** (the build is pinned to JDK 17 in `gradle.properties`).

The three dependency jars above are **not** committed (they aren't ours to redistribute). Drop them
into `libs/` before building:

```
libs/JustEnoughGuns-0.13.2-1.20.1.jar
libs/framework-forge-1.20.1-0.8.0.jar
libs/geckolib-forge-1.20.1-4.8.3.jar
```

Then:

```
./gradlew build
```

The mod jar is produced at `build/libs/thevillagefightsback-1.0.0.jar`.

## Modules

The mod is split into independent modules, in the same spirit as Quark: each module owns a slice of the mod's
content *and* the event handlers that drive it. Turning a module off removes both, so it costs nothing at runtime
rather than running and returning early. There is no loader-side "Half A" trick and no soft dependency: the
switches are plain config.

The switches sit at the top of `config/thevillagefightsback-common.toml`:

```toml
[modules.limbo]
    # Limbo: the dimension, its portals, drift, raids, the sage, limbo gear and books.
    enabled = true
```

| Module | Category | Owns |
| --- | --- | --- |
| `core` | Management | Config, commands, capabilities, entity attribute plumbing, profiler. **Always on.** |
| `brain_node` | Management | The Village Brain Node: four stages, upgrades, node blocks (factory, barracks, lure tower, dead node), its guards, bounty, spawns, achievements. |
| `village_defense` | Defense | Guard weapons and kill effects, defenders, walls and wall repair, house building, founding, expansion, chest protection, graves, raids. |
| `emerald_army` | Creatures | Emerald creatures and conversion, population, the emerald virus, block/mob infection, organic mass. |
| `weapons` | Combat | Alien gear, maces, rockets and launcher, wind charges, fire grenades, shields, the obliterator, air strikes. |
| `limbo` | World | The Limbo dimension, portals and chunk portals, drift, limbo raids and the sage, limbo gear, books, beds, gravel. |
| `darius` | Combat | General Darius, his squad, his special effect and reversals, the statue. |
| `village_spirit` | Combat | The Vindictive Village Spirit and the memorial scythe that calls it. |
| `world_mode` | Management | Reputation and hero/enemy standing, difficulty and brutal modes, spawn rules, starting kit, survival aids. |
| `client` | Client | HUD, discovery ticker, key binds, limbo sky and bedrock phase, reversed-gravity flip, screen shake, tooltips. |

Some modules declare a requirement -- `village_defense`, `emerald_army`, `darius` and `village_spirit` all need
`brain_node`. Turning a requirement off turns its dependents off too, and says so in the log.

### Changing a module

Either edit the file, use the in-game config screen, or use commands:

```
/thevillagefightsback modules                     # list every module, grouped by category, with its state
/thevillagefightsback config module_limbo false   # write the toggle (op only)
```

**A restart is required.** Content is registered while the game loads, long before a world exists, so a mid-session
toggle writes the file, logs a warning, and leaves the running session on the state it booted with. The config
screen marks a module whose file value is waiting for that restart with a `*`.

### Config screen

The gear button on the title screen (left of the language button, where Quark keeps its own) opens the mod's
config GUI; the Mods list also offers it under "Config". Categories are the TOML sections -- Modules first, then
`limbo`, `explosions`, `features`, `multipliers`, `values` -- and every entry is the same value
`/thevillagefightsback config` reads and writes, so the two can never drift apart. Booleans are toggle buttons,
numbers and lists are text fields (lists comma-separated). Changes apply to the running game immediately; the file
is written once, when the screen closes.

### How it works

- `modules/Module` -- one module: id, category, default state, its config line, and the handler classes it owns.
- `modules/ModuleManager` -- resolves the state at load, registers only the enabled modules' handlers, and warns on
  reload that a restart is needed. It reads `[modules.*]` straight out of the TOML, because NeoForge fires the
  registry events *before* it loads configs (`CommonModLoader.begin()`), so a `ModConfigSpec` value cannot answer
  "is this module on?" during registration.
- `modules/Modules` -- the module table, plus content ownership: `ownerOf(name)` maps a registry entry to its
  module by name, so `limbo_*` belongs to `limbo` and so on. Unclaimed names stay with `core` (always on), which
  means a missed rule leaves content in place rather than deleting it.
- `modules/ModuleRegistry` -- a `DeferredRegister` that skips entries owned by a disabled module. Registration
  sites just use it instead of `DeferredRegister.create(...)`.

### Adding a module

1. Write a nested `Module` subclass in `Modules` and declare its handlers with `handlers(...)` (game bus),
   `modHandlers(...)`, or the `client*` variants for client-only classes.
2. Add it to `Modules.ALL`, and to `ownerOf(...)` if it owns content whose names do not already imply it.
3. Register its handler classes there instead of annotating them: `@EventBusSubscriber` is deliberately gone from
   this mod, because the loader's annotation scan cannot be made conditional and would re-register a disabled
   module's handlers.
