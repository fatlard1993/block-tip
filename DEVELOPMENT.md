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

## API for other mods

Everything a mod can call is in `api/BlockTipApi.java`. Compile against Block Tip and guard every call on it being present.

### Boss Bars

A boss bar and this card live in the same corner of the screen. Left alone, a dragon gets named twice in the same place by two things that have clearly never met.

So whenever a bar is up, the card moves to the next row of that stack and takes the bar's width, which reads as one more line of the same thing rather than somebody else's window over the top of it. That is two questions, not one, and the answers differ:

- **Is a bar on screen at all?** Decides where the card sits. True far more often than you would think - a village, a raid, a timer somebody set - and this was the case that went wrong first: a card that only checked whether it was *looking at* a boss sat squarely across the bar every other time.
- **Does the bar belong to what I am looking at?** Decides what the card says. The health goes, because the bar is a health bar drawn larger and more precisely than a card could. And where that leaves nothing to add - a vanilla dragon, whose name and health are the whole card - it does not appear at all. A modded boss still gets one, because which mod a boss came from is a thing the bar never says.

Neither question is answerable in general. A boss bar is a field inside whatever owns it, with no tag and no central list; only the ones made by `/bossbar` can be found from outside. So mods say so themselves:

```java
// this creature draws its own bar
BlockTipApi.bossBar("your-mod:your_boss");

// this player has a bar of mine up right now, whatever they are looking at
BlockTipApi.bossBarCheck(player -> myBars.isShowing(player));
```

### Adding A Line

A card names a thing. Some things also have a fact that cannot be seen by looking at them, and those are exactly the ones nobody learns: a crafter that keeps a template looks like a crafter, a piston set to push three looks like a piston.

Any mod can add one line, compiled against Block Tip and guarded on it being present:

```java
// true wherever the block stands
BlockTipApi.line("minecraft:crafter", "Keeps one of each - the pattern stays");

// or worked out from the block in front of you
BlockTipApi.describe((level, pos, state, player) -> ...);

// The card's title, for a block whose registered name is a category: return null to keep it
BlockTipApi.name((level, pos, state, player) -> ...);

// or about whatever is walking around
BlockTipApi.describeEntity((entity, player) -> ...);

// or as a picture, where a picture says it better
BlockTipApi.illustrate((level, pos, state, player) -> new BlockTipApi.Tip("Keeps a template", "minecraft:bread"));

// the picture for a block that has no item of its own
BlockTipApi.icon("your-mod:worldgen_only_block", "minecraft:oak_leaves");

// the card's title for a creature whose own name is wrong: return null to keep it
BlockTipApi.nameEntity((entity, player) -> ...);

// how far a plant of yours has grown, 0 to 1, for the fill along the bottom: negative to say nothing
BlockTipApi.growth((level, pos, state, player) -> ...);
```

An entity that is really a block drawing itself - a hidden mob borrowing a renderer, a display entity - can carry the scoreboard tag `block_tip:stand_in` (`BlockTipApi.STAND_IN`). Looking at it then names the block it stands in, so the costume never shows through on the card.

The illustrated form draws an item at the head of the detail line, in the same column as the block's own picture. [Crafter Template](https://github.com/fatlard1993/crafter-template) uses it to show what a crafter is loaded to make: a picture of the loaf beats nine ingredients the player has to solve in their head, and it is the same width whatever the recipe. The picture belongs to the line that starts the row: it is read off the winning tip rather than kept in a field of its own, so it cannot be drawn at the head of somebody else's sentence.

The entity form is for the same silence in a thing that moves. [Player Trade](https://github.com/fatlard1993/player-trade) uses it to say *"Sneak-click to trade"* while you are looking at somebody: the gesture is that mod's only front door, and nothing else in the game hints at it. An entity line joins whatever the card already says about the creature - an animal's food, what it gives - rather than replacing it, since they answer different questions and there is only ever the one row. Where several mods have a line for the same creature, the first registered is the one shown.

The green fill along the bottom of a card is read off a block's age property, which is how nearly every crop in the game says how far along it is. A plant that keeps its progress somewhere else - a block entity, a clock of its own, a record spread over the several blocks it is made of - has no age to read, so the crop with the most worth saying was the one that said nothing. `growth` hands the card a fraction instead. [Hemp Craft](https://github.com/fatlard1993/hemp-craft) answers it out of the plant's own record, so a stalk halfway up fills the bar the same as the root does, it being the same plant. The same number decides the tick or cross at the end of the name, so a plant that is ready reads as ready the way wheat does.

A mod compiling against this should declare the version it needs as `breaks`, not only `suggests`:

```json
"breaks": { "block-tip": "<1.1.0" }
```

`suggests` is advisory and Fabric does not act on it, and `FabricLoader.isModLoaded` answers whether Block Tip is present, not which one. Without `breaks`, a mod calling a method an older Block Tip does not have fails at registration with a `NoSuchMethodError` the loader turns into a refusal to start, naming your mod rather than the mismatch.

One line each, deliberately. The card stays readable because it is small; a mod that wants a paragraph wants a book.

Several mods can have something to say about the same block, and the card shows as many of their lines as its width will hold, separated by a dot. Priority decides the order, so what falls off the end of a full line is the least important thing on it. Keep a line short enough to share.

### What It Will Not Say

The card reads block entities: how full a chest is, the number a comparator would give, whether a furnace has run dry, what a spawner makes. Every switch above belongs to the person looking, and none to the person looked at - so on a server where somebody else's chest is not your business, a claim or protection mod can close that:

```java
BlockTipApi.inspection((level, pos, state, player) -> claims.mayOpen(player, pos));
```

Refusing leaves the block named, because a name is what anybody standing there can already see. What it withholds is everything the card would otherwise have read out of the inside of it.

## Runtime notes

- One raycast per player four times a second (every 5 ticks), and a packet only when the answer changes. Standing still and staring at a wall sends nothing. The break bar is the one thing sent every tick, and only while the player is mining.
- Names go to the client as translation keys, so the client renders them in its own language. The facts under the name are the server's own words; a mod's translation key only survives while it is the only fact on the line, since two facts joined by a dot are no longer a key anything can look up.
- The detail line is packed against a measured width and collection stops at six facts (`MOST_LINES`), so the card cannot grow into a panel.
- The silk-touch and shears marks are read from the block's own loot table, asking "does the block itself come back", not "does silk touch change the drop" (true of stone and every ore). Each block is asked once and remembered until data packs reload.
- Preferences are stored per player in the world's saved data, as the list of players who opted out plus their hidden block ids (ids rather than blocks, so a mod removed for a while does not empty the list).
