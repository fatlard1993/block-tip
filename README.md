# Block Tip

A Minecraft Fabric mod. Names what you are looking at, for players who are still learning to read.

## What This Mod Does

Look at a block. Its picture and its name appear just above your hotbar, where vanilla already writes the name of whatever you are holding, so nobody has to be taught where to look.

That is the whole mod.

## Why It Is This Small

The suite it belongs to is thirty-nine mods deep, and a lot of that content goes undiscovered because nothing in the world says what anything is. The players it is aimed at are children sounding words out, so the picture is the point and the word is there to be learned from.

Which means what it deliberately does **not** show: tool tiers, harvest levels, mod names, block states, machine progress, container contents. Every one of those is a line between a child and the answer to "what is that".

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
- **It costs almost nothing.** One raycast per player four times a second, and a packet only when the answer changes. Standing still and staring at a wall sends nothing at all.
- **Spectators get no card.** Looking through walls would name whatever is behind them.
- **Reach is six blocks**, a little past arm's length, so it answers before you arrive.

## Pandorical

Block Tip runs server-side, and Pandorical is a hard dependency (`fabric.mod.json`): the server will not load this mod without it. The card is a Pandorical HUD, which is the reason there is nothing to install on a client that already has Pandorical.

No Block Tip jar is needed on a client.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Key Files

| File | Responsibility |
|------|---------------|
| `Main.java` | Entry point; the tick that asks what each player is looking at |
| `Sighted.java` | The raycast, and reducing a block to a picture and a name |
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
