# The Village Fights Back -- Fabric 1.20.1

This branch (`fabric-1.20.1`) is the Fabric build of the mod, ported from the Forge 1.20.1 1.6.0 release on
`main`. Same mod id, same namespace, same content: a world saved by one build opens in the other.

Requires **Fabric API** and **Guard Villagers (Fabric/Quilt)** (mod id `guardvillagers`, tested against
`2.0.9-1.20.1`). MCA Reborn is optional, as on Forge.

## How the port is built

The mod is 384 files and ~44k lines, and only 169 of them ever touched Forge. Rather than rewrite those against
Fabric API, this branch carries a compat layer in `com.example.villagefightsback.platform` that reimplements the
Forge surface the mod actually uses. Gameplay files change by import only, so the branch stays a near-mechanical
rebase of `main` and a new Forge-side feature ports over cheaply.

| Piece | Stands in for | Backed by |
|---|---|---|
| `EventBus` + `SubscribeEvent` + `ModEventHandlers` | Forge's event bus and `@Mod.EventBusSubscriber` | reflection over a hand-listed set of handler classes -- Fabric has no classpath scan |
| `platform.event.*` (34 classes) | the Forge events the mod listens to | Fabric API callbacks (`ModEventSources`) and 16 mixins |
| `registry.DeferredRegister` / `RegistryObject` | Forge's | immediate `Registry.register` |
| `config.ModConfigSpec` | `ForgeConfigSpec` | a commented-TOML reader/writer producing the same file |
| `network.ModNetwork` | `SimpleChannel` | one named Fabric play channel per packet |
| `PersistentData` | `Entity.getPersistentData()` | a mixin adding the tag to `Entity`, saved under `ForgeData` |

**Adding a new event handler class:** list it in `ModEventHandlers`, or it will never fire. `EventBus.register`
throws on a class with no handler methods, so a stale entry fails loudly at startup.

**Sources of truth for firing:** `platform.event.ModEventSources` (common), `ModEventSourcesClient` (client), and
`com.example.villagefightsback.mixin`. Every cancellable event's firing site acts on the cancellation -- if that
ever stops being true, the mod goes wrong quietly rather than loudly.

## Where this build differs from the Forge one

Everything is ported; these are the places the mechanism underneath is not identical.

- **Loot chances are constants.** The three village-chest extras were global loot modifiers with their odds in
  datapack json. They are extra loot pools now (`ModLoot`), and the odds are constants in code. A datapack that
  wants different odds has to override the loot table.
- **Brewing matches more containers.** Forge's NBT-exact ingredients only matched the drinkable potion; vanilla's
  `addMix` also matches its splash and lingering forms, so an emerald apple brewed into a splash awkward potion
  gives a splash virus directly. That is how every vanilla potion already behaves.
- **Creative tab order is registration order.** Forge lets a tab declare which one it sits after; 1.20.1 vanilla
  has no such call. The main tab registers first, so the order is unchanged anyway.
- **Uncurable effects are uncurable per effect, not per instance.** Forge attaches a curative-item list to each
  effect instance; here the effect itself is marked (`UncurableEffects`) and a `removeAllEffects` mixin puts it
  back. Every instance the mod creates is marked, so the behaviour matches.
- **`LivingEquipmentChangeEvent.getFrom()` is empty.** Vanilla hands over what a slot changed *to* but not what it
  held before. The mod's one handler reads the slot and the new stack.
- **`ExplosionEvent.Detonate` has no entity list.** It fires from `finalizeExplosion`, by which point vanilla has
  already dealt its entity damage. The block list is live and editable, which is what the mod uses.
- **`BlockEvent.EntityPlaceEvent` covers item placement.** Blocks placed from an item, by a player or a dispenser
  -- every case the mod cares about. Forge also fires it for falling blocks and a few worldgen paths.
- **`EntityJoinLevelEvent` is server-side.** Forge fires it on both sides. The 10 handlers were checked and none
  needed the client firing.
- **The mace enchantments use a vanilla category.** Forge lets a mod add an `EnchantmentCategory`; the enum cannot
  be extended here, so the three enchantments override `canEnchant` to demand an actual mace instead. That is the
  method the enchanting table, the anvil and the commands all consult.
- **The `ALLY_PASSABLE` path type is spliced into an enum.** `BlockPathTypes` gains a constant through its own
  compiler-generated constructor, and the values array is patched with `Unsafe`. Only the splice can fail, and
  nothing depends on it: passability is decided by the per-mob malus map, which is keyed by the object.
- **No Minotaur.** Whichever version of it you take drags Gson 2.9.1 onto the buildscript classpath, and 2.9.1
  predates record support, so Loom's parse of Mojang's version manifest dies before Minecraft is set up. The
  Fabric jar is uploaded by hand; `build.gradle` records the metadata to use.
- **`gradlew runServer` does not start.** Guard Villagers is a mandatory dependency and Loom cannot remap it into
  Mojang names -- its `GuardEntity.setChargingCrossbow` collides with the Mojang name of the `CrossbowAttackMob`
  method it inherits, and tinyremapper fails the whole remap batch. Boot-test the real jar instead (below), which
  tests the shipped artifact rather than a dev-remapped one.

## Boot test

```
gradlew build
```

then, in a scratch directory:

1. `curl -L -o fabric-server-launch.jar https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.15.11/1.1.2/server/jar`
2. `echo eula=true > eula.txt`
3. Drop into `mods/`: this build's jar, `fabric-api-0.92.2+1.20.1.jar`, `guardvillagers-2.0.9-1.20.1.jar`
4. `java -Xmx3G -jar fabric-server-launch.jar --nogui`

Judge the boot on the `Done (Ns)!` line plus the recipe and advancement counts, and check that the Limbo
dimension registered and no mixin failed to apply. Then in-game: `/thevillagefightsback config` should list every
key (which proves the TOML shim round-trips), and `/summon thevillagefightsback:general_darius` should work.
