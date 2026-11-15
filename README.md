# Practice HUD (Fabric, Minecraft 1.21.11)

Client-side HUD and practice utilities. Nothing is sent to servers and nothing modifies gameplay; it only reads what your client already knows and draws it on screen. Press **Right Shift** in game to open the settings screen (rebindable under Options > Controls > Practice HUD).

## Build

Requirements: JDK 21, internet access for Gradle (Fabric Maven + Mojang).

```
gradle wrapper --gradle-version 9.2.1   # once, if there is no gradlew yet
./gradlew build                          # gradlew.bat build on Windows
```

The jar is `build/libs/practice-hud-1.0.0.jar`. Put it in `.minecraft/mods` with Fabric Loader 0.18+ and Fabric API for 1.21.11. To test in a dev client run `./gradlew runClient`.

> This project was written without access to the Fabric Maven repository, so it has **not been compiled yet**. Yarn method names shift between builds; if `./gradlew build` reports a missing symbol, it is almost always a one-word rename. Versions are pinned in `gradle.properties` (Yarn `1.21.11+build.4`, Loader `0.18.4`, Fabric API `0.141.3+1.21.11`); adjust them to the newest on https://fabricmc.net/develop if Gradle says one does not exist.

## Modules (all off by default; toggle in the GUI)

| Category | Module | What it does |
|---|---|---|
| Crystal & Respawn | Totem Status | Totem count, offhand ready/empty |
| | Respawn Anchor Meter | Charge level and blast size of the anchor you look at |
| Combat | Custom Crosshair | Shape, size, gap, thickness, colour, dynamic spread, recolours over a target in reach (replaces the vanilla crosshair) |
| | Target Tracking Ring | Static ring at screen centre |
| | Combat Distance Counter | Distance to the entity under the crosshair (64 blocks) |
| | Sprint Indicator | Sprint/sneak state and speed |
| | Guard Timing Indicator | Weapon attack charge, shield-disabled state |
| | Shield Status | Shield durability and blocking |
| | Ability Cooldown Bar | Cooldown of the held item |
| | Netherite Combat HUD | Health, armour durability, potion effects |
| | Entity Information | Name, health bar, effects of the targeted entity |
| Mace & Wind | Critical Hit Timing Bar | Whether a hit would crit now |
| | Mace Height Calculator | Fall distance, 1.5-block minimum, bonus damage |
| | Wind Burst Combo Trainer | Step-by-step prompts |
| | Jump Velocity Gauge | Vertical speed |
| | Elytra HUD | Pitch, speed, durability while gliding |
| Minecart & Redstone | Minecart Speedometer | Speed/heading of the 3 nearest carts |
| | Inventory Rail Counter | Rails, minecarts, redstone |
| Utility | Throw Landing Calculator | Predicted landing point for pearls, snowballs, eggs, splash/lingering potions |
| | Interaction Distance | Distance to the targeted block/entity |

Settings tab: crosshair shape/size/gap/thickness/dynamic, ring radius, and which hotbar slot each of four **Quick Slot** keys selects (bind the keys under Controls).

## Not in this first version

These need mixins or world-space rendering, which are the most version-fragile parts of the 1.21.11 render rewrite, so they were left for a second pass once the base builds on your machine:

- World-space overlays: crystal placement outline, obsidian grid, blast radius circle, drawn trajectory arcs, bounding box colours, movement particle trail, TNT cart/enclosure guides, container markers, reach line
- Mixins: nameplate customizer, hit particles, arm/item offsets, camera shake, sensitivity curves, entity scale, auto-sort button, spear attack visualizer

## Notes

- Distance counter and Entity Information use a line-of-sight-free ray test, so they can show mobs behind walls.
- A module that throws an exception turns itself off and logs the error instead of crashing the game.
- Config lives in `config/practicehud.json`.
