# Block Tip - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Key Files

| File | Responsibility |
|------|---------------|
| `Main.java` | Entry point; the tick that asks what each player is looking at, and the mods menu page |
| `Sighted.java` | The raycast, and reducing a block or creature to a picture and a name |
| `TipHud.java` | The card, and only sending it when the answer changes |
| `VanillaTips.java` | The facts vanilla keeps to itself, crop growth, the spawn mark and a player head's owner |
| `Breeding.java` | What an animal breeds on, probed once per species |
| `Interactions.java` | What an animal gives you and what to hold for it |
| `ModNames.java` | Which mod a block or creature came from |
| `BreakProgress.java` | How far through the block each player is, for the break bar |
| `mixin/DestroyProgressMixin.java` | Reads the mining fraction as vanilla computes it |
| `Advice.java` | What you should be holding, and whether what you hold will do |
| `Drops.java` | What breaking it actually gives you, asked of the loot table |
| `BossBars.java` | Whether the game is already drawing a bar for this creature |
| `TipPreferences.java` | Who opted out and what they hid, kept in saved data |
| `TipCommand.java` | `/blocktip`, and the per-player list of blocks not to name |
| `api/BlockTipApi.java` | What other mods call to add lines, names, pictures and boss bars |

## Building

Block Tip builds against Pandorical's live source, not a published artifact: `settings.gradle` includes `../pandorical`. Check both out side by side or the build fails before it starts.

```bash
./gradlew build
```

The built jar will be in `build/libs/`.
