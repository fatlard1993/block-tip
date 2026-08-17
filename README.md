# Block Tip

A Minecraft Fabric mod. Names what you are looking at.

## What This Mod Does

Look at anything. Its picture and its name appear just above your hotbar, where vanilla already writes the name of whatever you are holding, so nobody has to be taught where to look.

Blocks and creatures both. A creature is named by its display name, so a villager who has been given one is introduced by it.

That is the whole mod.

## Why It Is This Small

The suite it belongs to is forty mods deep and most of that content is unlabelled: the game will happily show you a block it has never mentioned by name. "What is that" is a question everybody asks, whether the answer is a word they have not read before or a block they have not seen before.

So the answer is a picture and a word, and nothing else. Not tool tiers, harvest levels, mod names, block states, machine progress or container contents. Every one of those is a line between the question and the answer, and the ones who need answering most are the ones with the least patience for lines.

## Turning It Off

| Command | Effect |
|---------|--------|
| `/blocktip` | Flip it, whichever way it currently is |
| `/blocktip on` | On, definitely |
| `/blocktip off` | Off, definitely |

Bare toggle first, because that is what someone types when the tips are in the way and they want them gone now. The explicit forms exist so that telling a child "type slash blocktip on" cannot end with it off.

It is on by default and the preference is stored per player, as the list of people who opted **out**. A feature you have to switch on is a feature for people who already knew about it, which is the opposite of the point here.

## Details Worth Knowing

- **Names arrive in your own language.** The server sends a translation key, not words, so the client says it in whatever language that client is set to.
- **Blocks with no item still get named.** Fire, portals, the top half of a door: those show the name with no picture, which beats a picture of the wrong thing.
- **Creatures show their pick item.** A spawn egg for a mob, the boat for a boat: whatever middle-click would hand you, which is the picture of the thing by definition.
- **Whichever is nearer wins.** A cow standing in front of a wall names the cow, the same rule your eyes are using.
- **It costs almost nothing.** One raycast per player four times a second, and a packet only when the answer changes. Standing still and staring at a wall sends nothing at all.
- **Spectators get no card.** Looking through walls would name whatever is behind them.
- **Reach is six blocks**, a little past arm's length, so it answers before you arrive.

## What It Says About Vanilla

A handful of facts the game tracks and never shows, chosen because not knowing each one costs you something:

| Looking at | It says |
|------------|---------|
| Any block you could stand on | **Dark enough for mobs to appear here**, when block light is zero |
| Wheat, carrots, potatoes | **Ready to harvest** or **Still growing** |
| Redstone dust | **Carrying 12 of 15** |
| Waxed copper | **Waxed - it will not weather** |
| A note block | **Bass, note 7 of 24** |
| A hive | **3 inside, honey 4 of 5** |
| Farmland | **Watered**, or **Dry - needs water within four blocks** |

The spawn light is the one worth having. Mobs need block light of exactly zero, which nobody can check by eye: a torch two blocks too far leaves a square that looks lit and spawns creepers all night.

It is checked last, because it is true of most of the world and would otherwise drown out everything specific.

## Adding A Line

A card names a thing. Some things also have a fact that cannot be seen by looking at them, and those are exactly the ones nobody learns: a crafter that keeps a template looks like a crafter, a piston set to push three looks like a piston.

Any mod can add one line, compiled against Block Tip and guarded on it being present:

```java
// true wherever the block stands
BlockTipApi.line("minecraft:crafter", "Keeps one of each - the pattern stays");

// or worked out from the block in front of you
BlockTipApi.describe((level, pos, state, player) -> ...);
```

One line, deliberately. The card stays readable because it is small; a mod that wants a paragraph wants a book.

## Pandorical

Block Tip runs server-side, and Pandorical is a hard dependency (`fabric.mod.json`): the server will not load this mod without it. The card is a Pandorical HUD, which is the reason there is nothing to install on a client that already has Pandorical.

No Block Tip jar is needed on a client.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Key Files

| File | Responsibility |
|------|---------------|
| `Main.java` | Entry point; the tick that asks what each player is looking at |
| `Sighted.java` | The raycast, and reducing a block or creature to a picture and a name |
| `TipHud.java` | The card, and only sending it when the answer changes |
| `TipPreferences.java` | Who opted out, kept in saved data |
| `TipCommand.java` | `/blocktip` |

## Building

Block Tip builds against Pandorical's live source, not a published artifact: `settings.gradle` includes `../pandorical`. Check both out side by side or the build fails before it starts.

```bash
./gradlew build
```

The built jar will be in `build/libs/`.

## License

MIT, see [LICENSE](LICENSE).
