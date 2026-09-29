# Kingdom RTS

A NeoForge mod for Minecraft: a long-lived, real-time kingdom-management game. The player founds and grows settlements, and defends and captures territory against rival factions. Standalone, with no dependency on other gameplay mods.

## Stack
- Minecraft 1.21.1, NeoForge 21.1.x, ModDevGradle, Java 21

## Commands
- `./gradlew runClient` launches the dev client (the main way to verify changes in-game)
- `./gradlew runServer` runs a dedicated dev server
- `./gradlew build` compiles and checks the project

## Terms
- **Faction**: a side that owns settlements and units. The player is a faction; rivals are AI-controlled factions.
- **Settlement**: a town built around a town hall, owned by one faction or unowned (neutral).
- **Unit**: any entity belonging to a faction, such as guards and workers.

## MVP systems
The owner will name the system to work on in each session. Work only on that system.

### Capture
- Every settlement has a capture point at its town hall, with a control meter from 0 to 100 (100 = fully held by the owner).
- Units of each faction within a set radius of the point count as that faction's presence.
- When an attacking faction's presence exceeds the owner's by a margin, the meter drains steadily over time. Otherwise it regenerates toward 100.
- Capture is never instant. It requires sustained presence, so a quick raid-and-retreat can't flip a settlement.
- When the meter reaches 0, the settlement changes owner (see Settlement).
- Presence is counted per faction and doesn't depend on unit type.

### Settlement
- Founded by placing a town hall block. Owned by a faction, or unowned.
- Claims the chunks around it. Claimed chunks belong to the settlement's owner.
- Knows which workers, guards and storage containers belong to it.
- On capture, the settlement, its claimed chunks and its workers pass to the new owner, who can also open its storage containers.

### Guards
- A custom unit belonging to a settlement's faction.
- Stays near its settlement, attacks units of hostile factions that come within range, then returns to its post.
- Never attacks units of its own faction.
- Counts as defender presence at its settlement's capture point.

### Workers
A custom unit belonging to a settlement, with one job. MVP jobs:
- **Lumberjack**: finds a tree within the settlement's claimed chunks, walks to it, chops down the whole tree, picks up the logs, carries them back and puts them in a settlement storage container, then repeats.
- **Builder**: is assigned a structure to build at a site. It takes the required materials from settlement storage, walks to the site, and places blocks until the structure matches its predefined layout. Materials are used up as blocks are placed. Several builders can work on one structure at once.

Pacing is a design goal: a player should be able to grow several settlements in a reasonable time without tedious material supply. Build speed and costs will need playtesting, so keep them easy to change.

### Villages and exploration
- Neutral settlements generate naturally in the world.
- The player starts as the owner of one settlement. Other settlements and rival factions' locations are unknown at the start.
- Each faction tracks which chunks it has explored. Settlements in chunks a faction hasn't explored are unknown to it.
- Neutral settlements can be captured with the same capture mechanic, which is how the player expands.

## Working notes
- The owner is rusty on Java. Briefly explain unfamiliar Java or NeoForge concepts as they come up.
- NeoForge APIs changed a lot between versions. Check against the actual 1.21.1 sources (External Libraries) or docs.neoforged.net for 1.21.1 rather than relying on memory.
- After a change, run `./gradlew build` and say what to check in-game via `runClient`.
- Do not commit or push. Version control is handled by the owner.