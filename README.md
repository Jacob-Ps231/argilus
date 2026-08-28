# Argilus

A clay golem that tends a farm on its own. It harvests what is ripe, replants it,
picks up what falls, and empties itself into a chest it remembers.

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
| Pumpkins and melons | only fruit attached to a stem, melons whole rather than sliced |
| Collect | walks to items dropped nearby and picks them up |
| Deposit | into a chest or barrel it remembers, moving on to another when one fills |
| Till | bare dirt beside farmland, but only when it has a seed to sow at once |
| Bone meal | taken from the chest during a deposit, never on a trip of its own |

It never tramples farmland, and it works whether or not a player is nearby, as
long as the chunks are loaded.

## Configuration

`config/argilus.json`, created on first launch. Values are clamped on read, so a
mistake cannot make the golem scan a huge volume every tick.

| Key | Default | Range |
| --- | --- | --- |
| `radius` | 12 | 4 to 24 |
| `harvestIntervalTicks` | 20 | 1 to 200 |
| `scanIntervalTicks` | 40 | 20 to 400 |
| `inventorySize` | 18 | 1 to 27 |
| `depositIdleTicks` | 100 | 20 to 2000 |
| `collectRadius` | 7 | 1 to 24 |

Shrinking `inventorySize` while a golem is carrying more than fits drops the
surplus at its feet rather than destroying it.

## Modded crops

Compatibility comes from being generic rather than from per-mod code, so there
is no hard dependency on anything. A modded crop that extends `CropBlock` and
drops a seed that places it back is handled without this mod knowing it exists.

Verified against Farmer's Delight Refabricated: cabbage, onion and rice all work
untouched.

**Known limit.** A crop that drops no seed cannot be replanted, so the golem
harvests it and leaves the tile bare. Farmer's Delight tomatoes are the case in
point, since their seeds have to be crafted. Keep such a patch outside the
radius, or replant it by hand.

Crops that are not `CropBlock` are invisible to the golem, which is the right
outcome for anything the generic rule cannot handle.

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
