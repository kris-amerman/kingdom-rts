# Structurize spike (S-023)

> **Decision (owner, after this report): not adopted.** We won't use Structurize, BlockUI or Domum Ornamentum. Kingdom RTS stays fully distinct from MineColonies and its libraries (CLAUDE.md, Independence), and templates, placement and the nation management interface stay our own. See docs/design.md, Nation management interface. This report is kept as the record of the spike.

Should Kingdom RTS build its templates, block placement and build tool on **Structurize** (ldtteam, the library behind MineColonies' build tool), or keep its own? The owner is open to relicensing under GPL-3.0 if Structurize is the better tool, so this report is about technical fit, cost and risk. Researched in October 2026 against Structurize's `release/1.21` branch (head `5fa751d`, 30 Sep 2026) and published builds on the ldtteam maven, with a hands-on test on a throwaway copy of the project (not kept; see "Reproducing the hands-on test" at the end).

## Summary
- **It fits well where it matters most.**
  - **Its server-side placement** is a clean API a builder can drive one block at a time from its own inventory.
  - **Its 22 placement handlers** cover doors, beds, fluids, fire, double plants, flower pots, banners, containers with contents, falling blocks and dripstone.
  - **Its build tool** has a hook that lets a mod turn "place this blueprint" into its own commission.
  - **Hands-on, it rebuilt our storage shed exactly,** and its required-items count matched ours exactly.
- **The costs:**
  - **Two more required mods,** BlockUI (a GUI library) and Domum Ornamentum (a decorative-block content mod).
  - **The GPL-3.0 licence** for all three.
  - **Every 1.21.1 build is labelled beta or snapshot,** and the three mods' versions must match. A mismatched pair crashed the dedicated server at load.
  - **Placing a block costs about 110 µs** through Structurize, against 8 µs for our bulk `finish`. That's fine at builder speeds, but worth watching.
- **Recommendation: adopt it,** in steps (see the end), if the owner accepts Domum Ornamentum as a second dependency alongside the GPL licence. The build tool alone (preview, move, rotate, mirror, pack browser, multiplayer sync) would be a large project to build ourselves, and it's the centrepiece of the Direction's build tools.

## Findings

### Releases, dependencies and size
- **Licence:** GPL-3.0 for Structurize, BlockUI and Domum Ornamentum (all ldtteam; `gh api repos/ldtteam/...`).
- **Required dependencies,** declared `type="required"` in Structurize's `neoforge.mods.toml`:
  - **BlockUI,** a GUI library;
  - **Domum Ornamentum,** which adds the Architect's Cutter and many decorative "framed" blocks, craftable in survival. That's content, not just a library, so it brushes against CLAUDE.md's "no gameplay or content mods".
- **Published builds** on the ldtteam maven (`https://ldtteam.jfrog.io/ldtteam/modding/`, group `com.ldtteam`):
  - **Structurize:** 82 builds for 1.21.1, all `-beta` or `-snapshot`.
  - **Maven metadata** lists `1.21.1-1.0.746-beta` as "latest". A newer series `1.0.8xx-1.21.1-snapshot` also exists (`1.0.835`).
  - **The repo is active** (pushed 11 Oct 2026). Structurize itself is built against NeoForge 21.1.84; we're on 21.1.252, and it loaded fine.
- **Jar sizes:** Structurize 976 KB, Domum Ornamentum 1060 KB, BlockUI 452 KB, about **2.5 MB** in total.
- **Version pinning matters.**
  - The first try (`structurize 1.21.1-1.0.746-beta` with `blockui 1.21.1-1.0.182-beta`) **crashed the dedicated (GameTest) server at mod load**. BlockUI's config setup loaded a client-only class (`Screen`). Structurize's source pins BlockUI `1.0.191-1.21.1-snapshot`, so the pair I picked didn't match.
  - The matched snapshot set (`structurize 1.0.835`, `blockui 1.0.212`, `domum-ornamentum 1.0.204`, all `-1.21.1-snapshot`) started cleanly, and all 124 of our GameTests still passed alongside it.
  - Adopting means pinning a known-good trio and re-checking it on every update.

### Blueprints and packs
- **Format:** `.blueprint` (NBT), read and written by `BlueprintUtil` (`readBlueprintFromNBT`, `writeBlueprintToNBT`, `createBlueprint` from a region of the world, which is what the scan tool does). Data fixers upgrade older blueprints.
- **Packs:** a folder with a `pack.json` (format version, name, the mods it needs).
  - **Where they come from:** mods ship packs inside their jar under `blueprints/<modid>/` (`ServerStructurePackLoader`), and players add packs to a `blueprints` folder in the game directory. Server packs sync to clients.
  - A pack that lists a missing mod isn't loaded. So **MineColonies' style packs need MineColonies** (their buildings use its hut blocks), and we can't borrow them directly; the format, tools and pack browser are what we'd gain. A pack of our own buildings would list `kingdomrts`.
- **Substitution blocks** (Structurize's placeholders: "keep what's here", "any solid block", fluid substitution) let a template leave terrain alone or accept any filler. That suits building on uneven ground.
- **Our vanilla `.nbt` templates** aren't read directly. Converting means scanning the built structure in-game (the spike's `createBlueprint` did that from a built shed in one call).

### Placement (what a builder would use)
- **`StructurePlacer`** walks a blueprint with a configurable iterator (default, Hilbert, inward circle, and more) in phases: solid blocks, weak solids, water clearing, the rest, then entities (`PlaceStructureOperation` shows the canonical order).
- **`executeStructureStep(level, changes, fromPos, operation, iterate, entities)`** does at most `handler.getStepsPerCall()` blocks per call and returns where to resume. That's resumable and bounded, the shape our "explicit steps" principle wants.
- **`IStructureHandler`** is the survival integration point a builder implements:
  - `getInventory()` (an `IItemHandler`, which could wrap the site stock);
  - `hasRequiredItems` and `consume`;
  - `isStackFree`;
  - `allowReplace`;
  - `getStepsPerCall` and `getMaxBlocksCheckedPerCall`;
  - `prePlacementLogic`;
  - `triggerSuccess`;
  - `onCompletion`.
  - It all runs server-side, with no GUI.
- **Placement handlers** (`PlacementHandlers`, 22 built in, and mods can register more) handle placing each kind of block correctly and say what it costs (`getRequiredItems`): doors, beds, double plants, fluids, fire, flower pots, banners, containers (with their contents), falling blocks, dripstone, grass paths, substitution blocks.
- **Operations** also include `BLOCK_REMOVAL` (which in survival reports `BREAK_BLOCK`, so a builder can break it like a player), `WATER_REMOVAL` and `GET_RES_REQUIREMENTS`.
- **Hands-on:**
  1. Our shed was built with `BuildingOps.finish`, scanned with `createBlueprint`, and the site cleared.
  2. **Required items:** with a survival-style handler (`isCreative()` and `isStackFree()` false), `GET_RES_REQUIREMENTS` gave exactly our list: 25 cobblestone, 25 oak slabs, 22 planks, 8 logs, a door, a chest, a torch. A creative handler reports nothing, since everything is free.
  3. **Placement,** one block per call, rebuilt the shed exactly: our `BuildingRules.remaining` found 0 missing and 0 in the way, door halves and wall torch included. It took 86 calls.
- **Cost:** about **100–112 µs per call** (one block, two runs), against **8 µs per block** for our bulk `finish`.
  - The difference is the iterator and the handlers: checks, required items, block entities.
  - At builder speeds (say one block per builder every 10 ticks), 100 builders would add about **1 ms per tick**. That's inside our budget (5 ms at 100 units), but S-025 should put builders in the stress test either way.

### The build tool and hooking our commissions
- **The client tool:** preview a blueprint in the world, move, rotate and mirror it, browse packs and categories, then confirm. That's what the Direction describes for our build tools.
- **The hook:** `ISurvivalBlueprintHandler`, registered with `SurvivalBlueprintHandlers.registerHandler`.
  - In survival, confirming a placement offers the matching handlers (`canHandle`, client-side), then calls `handle(blueprint, packId, path, clientPack, level, player, pos, rotationMirror)` on the server (`BlueprintPlacementHandling`).
  - Our handler would call `BuildingOps.commission` (the faction operation, so AI parity holds: AI factions call the same operation without the tool), instead of anything being placed instantly.
- **Not tried hands-on:** driving the build tool's UI needs the client. With the dependencies below added, `./gradlew runClient` and the build tool from the creative tab would show it. Hooking it is the follow-up story's job.

### What else it adds
- **Structurize:** the scan tool, shape tool, tag tool and build tool (creative tab items), placeholder and substitution blocks, and commands.
- **Domum Ornamentum:** the Architect's Cutter and decorative framed blocks (survival content), plus their creative tab and recipes.
- **BlockUI:** a GUI framework, no content.
- None of them bring gameplay systems (no colonies, no jobs). Domum Ornamentum is decorative content in the world, though.

## Options

| | 1. Adopt fully | 2. Build tool and blueprints only | 3. Keep our own |
|---|---|---|---|
| **What we use** | Blueprints and packs, `StructurePlacer` with a builder `IStructureHandler`, the build tool with an `ISurvivalBlueprintHandler` | Blueprints and packs and the build tool; our own `BuildingRules` placement for builders | Our vanilla-template code; borrow ideas (handlers, phases, substitution blocks) |
| **What changes here** | `BuildingTemplates` loads Structurize blueprints; `BuildingOps.remaining` and `finish`, and S-025's builder, drive the placer; `BuildingType` points at pack and blueprint | `BuildingTemplates` reads blueprints into our `Blueprint`; our placement grows its own handlers over time | Nothing now; a preview tool, packs and authoring later |
| **We gain** | 22 placement handlers, required items, survival removal, a preview tool, packs, a scan tool, substitution blocks | A preview tool, packs, a scan tool | Full control, no dependencies, our licence |
| **We take on** | GPL; BlockUI and Domum Ornamentum; beta builds and version pinning; about 110 µs per block | The same dependencies and licence, for less of the benefit | Building the build tool ourselves (client rendering, UI, sync), and every placement edge case |
| **Effort** | Medium: adoption story plus S-025 on their placer plus the build tool hook | Medium, and the handlers are duplicated later anyway | Low now, high later |

Option 2 carries all of option 1's costs while throwing away its best server-side piece, so it's the weakest. The real choice is between 1 and 3.

## Recommendation
**Adopt Structurize (option 1)**, if the owner accepts Domum Ornamentum as a dependency alongside the GPL. The build tool is the main way a player shapes a settlement in our Direction, and Structurize already has it, with a hook made for exactly our "the placement is the commission" design. Its placer also matches our principles: resumable steps, a bounded amount of work per call, and survival inventories.

**Mitigations:**
- **Pin one known-good Structurize, BlockUI and Domum Ornamentum trio in one place,** and add a GameTest-server start to CI so a bad pairing fails the build (the dedicated-server crash above would have).
- **Keep everything behind our own operations** (`BuildingOps`, `BuildingTemplates`), so AI factions and our rules never depend on the tool, and a swap stays possible.
- **Builders in the stress test with S-025;** budget about 110 µs per placed block.

**Proposed follow-up stories, if adopted:**
1. **Adopt Structurize** (before S-025):
   - relicense Kingdom RTS as GPL-3.0 (LICENSE and `mod_license`);
   - add the pinned dependencies and update CLAUDE.md's dependency note;
   - convert the storage shed into a `kingdomrts` pack (`blueprints/kingdomrts/...`);
   - `BuildingType` names a pack blueprint;
   - `BuildingTemplates` loads it;
   - `remaining`, `finish` and `demolish` work from it, keeping our matching rule;
   - the existing building tests pass on the new engine.
2. **S-025 Builder job, on `StructurePlacer`:** a builder `IStructureHandler` over the site stock, with the supply lifecycle as planned.
3. **Build tool commissions:** an `ISurvivalBlueprintHandler` that turns build-tool placements into `BuildingOps.commission`, with our claim and overlap rules, and refusals shown to the player.

**If not adopted:** keep our own, and treat the handler list and phases above as a checklist for S-025's placement.

## Reproducing the hands-on test
1. **Add to `build.gradle`:**
   - a repository: `maven { url = 'https://ldtteam.jfrog.io/ldtteam/modding/' }`;
   - dependencies: `implementation "com.ldtteam:structurize:1.0.835-1.21.1-snapshot"`, `implementation "com.ldtteam:blockui:1.0.212-1.21.1-snapshot"`, `implementation "com.ldtteam:domum-ornamentum:1.0.204-1.21.1-snapshot"`.
2. **A GameTest that:**
   1. commissions and finishes the storage shed (`BuildingOps.commission`, `finish`);
   2. scans it with `BlueprintUtil.createBlueprint(level, corner, false, sizeX, sizeY, sizeZ, "shed", Optional.empty())`;
   3. clears the footprint;
   4. counts requirements with a `CreativeStructureHandler` subclass whose `isCreative()` and `isStackFree()` return false, running `executeStructureStep(..., Operation.GET_RES_REQUIREMENTS, () -> placer.getIterator().increment((info, pos, h) -> false), false)` to `FINISHED`;
   5. places it with a one-step-per-call handler through the three block phases from `PlaceStructureOperation`;
   6. checks the result with our `BuildingRules.remaining`.
   - Times come from `System.nanoTime()` around each call.
