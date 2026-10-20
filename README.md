# Kingdom RTS

A NeoForge mod for Minecraft 1.21.1: a long-lived, real-time kingdom game. Found and grow settlements, put your people to work, trade and negotiate with rival factions, and defend and capture territory. AI-led factions play by the same rules you do.

It's early in development. What exists so far: settlements with claims and capture, factions, units with data-driven occupations, workplaces and lumberjacks, buildings commissioned from templates with construction sites, and builders. The design and plans are in [`docs/design.md`](docs/design.md) and [`docs/backlog.md`](docs/backlog.md).

## Building and running
- `./gradlew runClient`: the dev client
- `./gradlew build`: compile and run the unit tests
- `./gradlew runGameTestServer`: run the GameTests headless
- `./gradlew runStressTest`: the performance stress test (see [`docs/perf.md`](docs/perf.md))

Requires Java 21. The mod is standalone: it doesn't depend on other gameplay mods.

## Influences
Kingdom RTS is its own design, but it owes a lot to games and mods we admire:
- **Polytopia:** empire-building and strategy, starting money, and relationships between factions.
- **MineColonies:** settlements run by workers, buildings built from templates, and lessons about keeping settlement systems quicker and more fun than doing the work by hand.
- **Civilization:** growing borders.
- **Cities: Skylines:** zoning and demand-driven growth.
- **Crusader Kings, Anno, Manor Lords:** inspiration for relationships, production and settlement life.
- **Create:** a planned add-on for machines and factories.

More detail is in the Influences section of [`docs/design.md`](docs/design.md).

## Mapping names
This project uses the official mapping names from Mojang for methods and fields in the Minecraft codebase. These names are covered by a specific license, which all modders should be aware of. For the latest license text, refer to the mapping file itself, or the reference copy here: https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

## Resources
- NeoForge documentation: https://docs.neoforged.net/
- NeoForged Discord: https://discord.neoforged.net/
