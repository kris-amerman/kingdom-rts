# Kingdom RTS

A NeoForge mod for Minecraft: a long-lived, real-time kingdom-management game. The player founds and grows settlements, and defends and captures territory against rival factions. Standalone, with no dependency on other gameplay mods.

## Stack
- Minecraft 1.21.1, NeoForge 21.1.x, ModDevGradle, Java 21

## Commands
- `./gradlew runClient` launches the dev client (the main way to verify changes in-game)
- `./gradlew runServer` runs a dedicated dev server
- `./gradlew build` compiles and checks the project

## MVP systems
The owner will name the system to work on in each session. Work only on that system.

### Capture
- Every settlement has a capture point at its town hall, with a control meter from 1 to 100 (100 = fully held by the owner).
- Units with a military occupation within a set radius of the point count as their faction's presence. Other units don't, even if they belong to the same faction or settlement. Mobs never take part in capture.
- Only units of a faction hostile to the owner count as attackers. Units of a non-hostile faction count as neither attackers nor defenders. Whether allied units count as defenders is open.
- Majority rule: each hostile faction is compared with the defenders on its own; hostile factions don't combine. When the strongest hostile faction outnumbers the defenders, the meter drains steadily over time. When defenders outnumber it, it regenerates toward 100. When both sides are present and equal, it holds. With no attackers present, it regenerates.
- Capture is never instant. It requires sustained presence, so a quick raid-and-retreat can't flip a settlement.
- When a drain would take the meter below 1, the settlement changes owner (see Settlements) to the strongest hostile faction present; a tie for strongest holds the meter at 1. The meter starts at 1 for the new owner, who must keep the majority to raise it.
- A neutral settlement isn't captured. A player claims it by interacting with it, and it becomes theirs immediately. The meter and unit presence only apply to owned settlements.

### Factions
- A faction is a side that owns settlements and units - effectively a nation. The player has a faction. Rival factions are AI-controlled.
- Factions are not hostile to each other by default. A faction can declare war on another, which makes the two hostile. Other relationships, such as neutrality or a formal alliance, are possible.
- A player starts without a faction. A newly placed town hall is neutral. Claiming a neutral settlement while factionless founds a faction, and the claimer becomes its owner.

### Settlements
- A settlement is a city built around a town hall, owned by a faction, or unowned (neutral).
- Claims the chunks around it. Claimed chunks belong to the settlement's owner.
- Knows which units and storage containers belong to it.
- On capture, the settlement and its claimed chunks pass to the new owner, who also gains access to its storage containers. Whether any units change allegiance along with it - and if so, which ones - is still open; don't assume all units automatically flip owners, especially units with a military occupation.
- A settlement has its own happiness, based on its constituents. If a settlement's happiness drops below a threshold, its units can revolt - stopping work, turning on the settlement's leadership, or breaking away to form an independent hostile faction.

### Units
- A unit is the mod's term for an NPC/AI-controlled entity with its own identity - something tracked individually, unlike a generic mob that spawns, wanders, and despawns without being tracked.
- A unit can have a faction (the side it belongs to), a settlement (the city it's tied to), a home (a specific place of residence, independent of its settlement), an inventory (item slots, the same idea as a player's), and an occupation (its role and capabilities). All of these are independent of each other - a unit can have any combination, including an occupation without a settlement, a settlement without a home, or no occupation at all.
- A unit has hunger and happiness. How these get implemented is open.
- Adding a new kind of unit should be possible as new data, not new code for every variant.

MVP occupations:
- A unit with a military occupation has combat stats (movement speed, attack damage, attack range; health comes from the underlying entity system). MVP behavior: holds position near a set point, attacking hostile mobs and units of hostile factions that come within range, and returning to post afterward. Never attacks units of its own faction, or of any faction that isn't hostile to its own. Counts as its faction's presence at a capture point.
- A unit with a non-military occupation (a job) may work for a settlement. The MVP jobs below are settlement jobs, but a unit having a job shouldn't be assumed to always require one - unattached working units (a mercenary, a traveling merchant, and the like) should stay possible later. MVP jobs:
  - **Lumberjack**: finds a tree within the settlement's claimed chunks, walks to it, chops down the whole tree, picks up the logs, carries them back and puts them in a settlement storage container, then repeats.
  - **Builder**: is assigned a structure to build at a site. It takes the required materials from settlement storage, walks to the site, and places blocks until the structure matches its predefined layout. Materials are used up as blocks are placed. Several builders can work on one structure at once.
- Stats and job definitions live in data rather than hardcoded per unit type, so a new occupation can be added as a data entry rather than new code. This also keeps the door open to letting others add unit types without forking the mod.
- Pacing is a design goal for jobs: a player should be able to grow several settlements in a reasonable time without tedious material supply. Build speed and costs will need playtesting, so keep them easy to change.

## Working notes
- The owner is rusty on Java. Briefly explain unfamiliar Java or NeoForge concepts as they come up.
- NeoForge APIs changed a lot between versions. Check against the actual 1.21.1 sources (External Libraries) or docs.neoforged.net for 1.21.1 rather than relying on memory.
- Dependencies: avoid other gameplay/content mods - depending on one means its systems and design decisions become part of this mod's, which "standalone" is meant to rule out. A well-established library mod that solves a narrow technical problem (rendering, animation, and the like) and carries no gameplay content of its own is fine to use when it's clearly the standard tool for that problem.
- After a change, run `./gradlew build` and say what to check in-game via `runClient`.
- Do not commit or push. Version control is handled by the owner.