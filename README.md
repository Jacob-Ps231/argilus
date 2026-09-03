# Argilus

A clay golem that tends a farm on its own. It harvests what is ripe, replants it,
picks up what falls, and empties itself into a chest it remembers. Right click it
to see what it is carrying.

Deliberately simpler than what exists elsewhere: no hunger, no lifespan, no
sleep, no third parties. One golem, a handful of behaviours done properly.

## Summoning

Place a **carved pumpkin** or a **jack o'lantern** on top of a **block of clay**.

Shearing a pumpkin that is already sitting on the clay works too, as does a
dispenser placing the head. Every route the vanilla golems accept, this one
accepts.

Each golem is summoned with one of six clay finishes, drawn at random.

## What it does

| Behaviour | Detail |
| --- | --- |
| Harvest | any fully grown crop within its radius |
| Replant | using a seed taken from the crop's own drops |
| Pumpkins and melons | only fruit attached to a stem, and melons in slices, as a bare hand gets them |
| Sweet berries | picked without breaking the bush, which regrows as usual |
| Nether wart | harvested and replanted like any crop, on anything that supports it |
| Collect | walks to items dropped nearby and picks them up |
| Deposit | into a chest or barrel it remembers, moving on to another when one fills |
| Sow | bare ground beside a tile already in production, and only with a seed to hand |
| Bone meal | taken from the chest during a deposit, never on a trip of its own |

Right click it to open its inventory. Anything can be taken out or put in, which
is the quickest way to hand it bone meal or a stack of seeds.

It never tramples farmland, it never despawns, and it works whether or not a
player is nearby, as long as the chunks are loaded. Berry bushes and cacti do
not hurt it, so it can walk into a patch and clear the middle of it. Killing one
returns everything it was carrying, plus a little clay.

Ground it will prepare: dirt beside farmland, tilled and sown in one action, and
anything in `#minecraft:supports_nether_wart` beside a tile already carrying
wart. It leaves the tiles a melon or pumpkin stem needs for its fruit alone.

## Configuration

`config/argilus.json`, created on first launch. Values are clamped on read, so a
mistake cannot make the golem scan a huge volume every tick.

| Key | Default | Range |
| --- | --- | --- |
| `radius` | 12 | 4 to 24 |
| `harvestIntervalTicks` | 20 | 1 to 200 |
| `scanIntervalTicks` | 40 | 20 to 400 |
| `inventoryRows` | 2 | 1 to 3 |
| `depositIdleTicks` | 100 | 20 to 2000 |
| `collectRadius` | 7 | 1 to 24 |

The inventory is counted in rows of nine because that is what the screen behind
the right click can display. Shrinking it while a golem is carrying more than
fits drops the surplus at its feet rather than destroying it.

## Modded crops

Compatibility comes from being generic rather than from per-mod code, so there
is no hard dependency on anything. A modded crop that extends `CropBlock` and
drops a seed that places it back is handled without this mod knowing it exists.

Verified against Farmer's Delight Refabricated: cabbage, onion and rice all work
untouched.

A crop the golem cannot put back is left standing rather than harvested. A
player who has no seed for a patch does not flatten it either, and the golem
should not be the worse farmer of the two. The tile is remembered so the trip is
not repeated, and the note lifts as soon as the golem carries a seed that would
replant it.

Farmer's Delight tomatoes are the case in point. Their vine is picked by hand
and survives, but nothing it drops puts the vine back, and tomato seeds plant a
different block. The golem therefore leaves a tomato patch entirely alone, which
is the outcome you want: harvest it yourself and the vines keep producing.

Sweet berry bushes and nether wart are matched the same way, by block type, so a
modded plant extending either is handled too. Anything outside those three
families is invisible to the golem, which is the right outcome for what the
generic rule cannot handle.

## Requirements

Minecraft 26.2, Fabric Loader 0.19.3 or later, Fabric API, Java 25.

## Deposit containers

Chests, trapped chests and barrels, through the `argilus:deposit_containers`
block tag. A datapack can widen it without touching the mod.

## Bugs and sources

Report anything odd at
[github.com/Jacob-Ps231/argilus/issues](https://github.com/Jacob-Ps231/argilus/issues).
A description of what the golem did, and what you expected instead, is usually
enough — most of the behaviour above was corrected that way.

## Licence

MIT.
