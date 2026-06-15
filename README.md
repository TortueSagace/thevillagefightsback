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
