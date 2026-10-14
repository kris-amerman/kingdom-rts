# Backlog

Planned work, one story per PR. CLAUDE.md says what the game should do and docs/design.md says why; this file tracks what we're building next and what was decided along the way, so details aren't lost between sessions.

## How to use this
- **One story = one atomic PR.** Keep stories small enough to review in one sitting.
- **Status:** `planned` → `in progress` → `in review` → `done`. Done stories move to the Done section with their PR number.
- **Update the story as you go:** when work starts (status, branch), when scope changes (say what moved and why), and when it ships (PR number, anything left over).
- **Decisions** worth remembering go in the story's Notes, and anything that changes the game's rules goes into CLAUDE.md as well.
- **New ideas** that aren't ready to be stories go under Later.

Story template:
```
### S-000 Title
- **Status:** planned | **Branch:** feature/... | **PR:** - | **Depends on:** -
- **Goal:** one sentence: what's true when this is done.
- **Scope:** what's in.
- **Out of scope:** what's deliberately not in.
- **Test:** automated tests added (unit tests, GameTests) + a short manual checklist (client-side visuals only).
- **Notes:** decisions, open questions.
```

## Current: Units MVP

### S-022 In-game stress and perf commands
- **Status:** planned | **Branch:** feature/perf-commands | **PR:** - | **Depends on:** S-021
- **Goal:** the stress scenario and live `Perf` numbers are available in a real world, for watching and profiling with Spark.
- **Scope:** `/kingdomrts stress <units>` builds `StressScenario` near the player (and `stress clear` removes it); `/kingdomrts perf start|stop` records and prints the same table as the stress report. Op only.
- **Out of scope:** optimisation.
- **Test:** GameTests via `TestSupport.runCommand`: the commands build and clear a small scenario, and perf prints a table. Manual: watching a town at work.
- **Notes:** `StressScenario` would move out of the `gametest` package.

### S-010 Building templates and sites
- **Status:** in progress | **Branch:** feature/building-sites | **PR:** - | **Depends on:** S-001
- **Goal:** a settlement can have a building commissioned at a site from a template.
- **Scope:**
  - **Templates:** vanilla structure NBT under `data/<ns>/kingdomrts/building/`, plus a small JSON definition (template, display name).
  - **Commission:** a faction operation records a building site (template, position, rotation, settlement). It's refused outside your own claim or when it overlaps another building. Command: `/kingdomrts building commission`.
  - **Remaining work:** a site can report which blocks are missing and the materials they need.
- **Out of scope:** the build tool UI and ghost preview (Direction), gold, damage repair.
- **Test:**
  - Unit test: the missing-block and materials calculations.
  - GameTests: commissioning is refused outside the claim; remaining work is correct after some blocks are placed by hand.
- **Notes:** -

### S-011 Builder job
- **Status:** planned | **Branch:** feature/builder | **PR:** - | **Depends on:** S-010, S-014, S-015
- **Goal:** builders take materials from settlement storage and build a commissioned site until it matches its template.
- **Scope:**
  - Uses the S-009 job steps: take materials, walk to the site, place blocks, repeat.
  - Materials are used up as blocks are placed.
  - Several builders can work one site without placing the same block twice.
  - Placement speed is set in data.
- **Out of scope:** the courier, gold, clearing and levelling the site, repair.
- **Test:**
  - GameTests: one builder completes a small template; two builders finish without duplicate placements; a builder waits without crashing when materials are missing.
  - Manual: watch it build.
- **Notes:** decided: the MVP builder takes materials from settlement storage, as CLAUDE.md's MVP says. The on-site builder + courier and payment model stays in Direction.

### S-012 Unit needs (design first)
- **Status:** planned | **Branch:** feature/unit-needs | **PR:** - | **Depends on:** S-011, S-014
- **Goal:** units have hunger and happiness, drawn from real things in the world.
- **Scope:** first a short design note in docs/design.md, then the simplest version (likely hunger from food in settlement storage, happiness from housing).
- **Out of scope:** revolts, taxes.
- **Test:** decided with the design.
- **Notes:** deliberately placed after the jobs. With no food production, housing or economy yet, needs would have to be abstract counters, against the "constraints from the world" principle.

## Later (not yet stories)
- **Work-area view:** a toggle on the hut panel or the build tool that shows a workplace's work area.
- **Military buildings:** barracks or guard towers for soldiers (exempt from workplaces for now).
- **Wanderers (population growth):** unemployed, unhoused units arrive in settlements now and then, at a rate scaled to the settlement's capacity, with buildings like an Inn speeding it up (design.md, Population).
- **Worker skills:** MineColonies-style skills and strengths, and matching units to jobs that suit them. Deferred by the owner to keep things simple.
- **Worker supply:** base demand per workplace, with workers fetching what they need from their workplace, storage, markets or other workplaces' surplus, and couriers as a helper rather than a bottleneck (design.md, Workplaces and supply).
- **Capture UI later:** faction identity (colour/emblem/banner), a custom-drawn bar, town hall and flag models.
- **Parked ideas:** allied factions sum their presence at capture; player-set unit stances (hold vs pursue); a detection range separate from attack range (ranged mobs currently out-range units).

## Done

### S-020 spawnunit defaults to no occupation
- **Status:** done | **Branch:** feature/spawnunit-default | **PR:** #23 | **Depends on:** -
- **Goal:** `/kingdomrts spawnunit` with no occupation given spawns a unit with no occupation (the owner noticed it defaults to soldier).
- **Scope:** change the default in `KingdomRtsCommands.spawnOptions` from `Occupation.SOLDIER` to none. `occupation kingdomrts:soldier` still gives a soldier, and `occupation none` stays as an alias.
- **Out of scope:** other command changes.
- **Test:** GameTests via `TestSupport.runCommand`: plain `spawnunit` has no occupation; `occupation kingdomrts:soldier` gives a soldier. Check the existing tests and docs that rely on the old default.
- **Notes:**
  - Done as scoped: the default is now null (no occupation). `occupation none` still works, and a job id is still refused ("is a job").
  - The existing tests already spelled out `occupation none` or an id, so none relied on the old default.
  - **Sabotage check:** putting the soldier default back fails `spawnCommandDefaultsToNoOccupation`.

### S-021 Stress testing
- **Status:** done | **Branch:** feature/stress-test | **PR:** #22 | **Depends on:** S-014
- **Goal:** we know what our systems cost per tick as the number of workers grows, and catch regressions before they settle in.
- **Scope:**
  - **`Perf`** times the hot paths (`kingdomrts:unit` (a unit's whole tick), `job`, `job_seeking`, `capture`), as profiler sections that also add up their time while recording. Recording is off outside the stress test.
  - **A headless stress run,** `./gradlew runStressTest`: towns of 10 units (8 lumberjacks with huts in a forest, a soldier, a job seeker) at 50, 100, 200 and 400 units. Each size warms up for 200 ticks and records 1200.
  - **The report:** `run/kingdomrts-stress/report.md` and `.json`.
  - **The budget fails the run:** 5 ms/tick at 100 units, 10 ms at 200 (`PerfReport.Budget`).
  - **CI:** a weekly and manual GitHub workflow (`stress.yml`) uploads the report.
  - **`docs/perf.md`:** the budget, how to run and read it, cost classes for unit code, a watch list, levers for later, and the results.
- **Out of scope:** in-game stress and perf commands (S-022); optimising anything.
- **Test:** unit tests: `PerfReportTest` (per-tick maths, the budget, sizes without a limit, no ticks); `PerfTest` (nothing counted while not recording, nested sections, restarting). The stress run itself: every size completes, the lumberjacks really work, the budget holds. The normal GameTests (86) don't include it. No manual checks (nothing visual).
- **Notes:**
  - **Separate namespace:** the stress tests use the `kingdomrts_stress` namespace (with a copy of the platform template). NeoForge only registers tests whose template namespace is enabled, so `runGameTestServer` and `/test runall` skip them, and `runStressTest` runs only them.
  - **Each size is built in its own place,** so no leftovers from one size affect the next. Their chunks are forced, so they tick as if a player were near.
  - **The GameTest server ticks as fast as it can,** so the run takes under a minute.
  - **Baseline:** about 10 µs per unit per tick (1.0 ms at 100 units, 1.9 at 200, 4.5 at 400). That's well under budget. Most of it is vanilla's entity tick; our jobs are about a sixth. Job seeking and capture are negligible.
  - **Sabotage checks:**
    - an 80 µs busy-wait per job tick fails the 100- and 200-unit budgets;
    - lumberjacks that never work fail every size's "really worked" check.
  - **Unloaded settlements** (should they keep producing?) added to design.md's open questions.

### S-016 Lumberjack hut settings
- **Status:** done | **Branch:** feature/lumberjack-settings | **PR:** #21 | **Depends on:** S-014
- **Goal:** the hut panel controls replanting and which wood the lumberjack fells.
- **Scope:**
  - **Replanting** (on/off, on by default): after clearing a felled tree's canopy, it plants a sapling of the tree's wood where the trunk stood, from its inventory (usually one the canopy just dropped).
  - **Wood types:** each known wood can be set to Fell or Skip; skipped woods are left standing.
  - **Hut panel:** a "Job settings..." button on the overview (lumberjack huts only) opens a third page: "Replant felled trees" On/Off, then each wood with its log icon and a Fell/Skip button, paged like the workers page. Workers and Stock now share one row on the overview.
- **Out of scope:** tree farms, bone meal, 2x2 saplings for big trees, fetching saplings from the hut to replant.
- **Test:** unit tests: the wood data decodes (full and short form); the settings survive saving, and huts saved before them replant and fell everything; the settings page rows; picking the sapling slot. GameTests: a sapling is planted where the tree stood; with replanting off none is; a skipped wood is left standing while another is felled; the panel's settings page, its buttons, and that the hut remembers them. Manual: the settings page's look.
- **Notes:**
  - **Wood types are data:** a NeoForge data map on blocks, `kingdomrts:tree_wood` (`data/kingdomrts/data_maps/block/tree_wood.json`), maps each trunk log to the sapling that replants it (`"minecraft:oak_log": { "sapling": "minecraft:oak_sapling" }`, or just the sapling id). The 8 vanilla overworld woods are listed. A data map attaches data to existing registry entries, so other mods or datapacks can add their woods without code.
  - A tree's wood is the block of its lowest log. Unlisted woods (other mods' trees with no entry) are still felled, but can't be skipped or replanted.
  - **Skip list, not a whitelist mode:** the hut stores the woods it skips (`Workplace.skippedWood`), so new woods are felled by default. A whitelist is the same thing with every other wood switched to Skip.
  - **Cheap filtering:** the column scan drops skipped woods before checking the tree, so skipped trees cost no flood fill.
  - **Replanting only plants what it carries.** If the canopy dropped no sapling and it has none of that wood, the spot stays empty. Oak and spruce canopies almost always drop one; jungle less often. Fetching saplings from the hut is a possible follow-up.
  - **One sapling only:** dark oak (and giant spruce or jungle) need four saplings to grow, so those don't grow back.
  - **Azalea trees** have oak logs, so they're replanted as oak.
  - `collectsTreeDropsForTheHut` turns replanting off, so the sapling it checks for isn't planted instead.
  - **Flakes:** GameTests run 3 times in a row without flakes.
  - **Sabotage checks:** never planting (the replant test fails); ignoring the replant setting (the no-replant and drops tests fail); ignoring the wood filter (the skip test fails).

### S-014 Lumberjack works from its hut
- **Status:** done | **Branch:** feature/lumberjack-hut | **PR:** #20 | **Depends on:** S-017
- **Goal:** a lumberjack works around its hut, brings its logs back to the hut's stock, and takes its axes from there.
- **Scope:**
  - **Work area:** a radius around the hut, intersected with the claim. It is set in the hut panel and capped by a maximum in data.
  - It picks up tree drops (logs, saplings, sticks, apples), not only logs, and gathers the drops around a felled tree.
  - It deposits what it collected into the hut's stock after each tree or when full, and takes axes from the stock.
  - Without room in the stock it keeps its logs and waits.
- **Out of scope:** replanting and wood filters (S-016), showing the work area, buying tools.
- **Test:** GameTests: trees outside the work area are ignored; logs and other drops end up in the hut's stock; it takes an axe from the stock; a full stock makes it wait; it clears a felled tree's canopy but leaves neighbouring canopies standing; it fells several trees before going back. Manual: the work-area setting in the panel. Checked by the owner, with the full loop.
- **Notes:**
  - Replaces the earlier "Lumberjack hauls logs to storage": logs now go to the hut, not settlement storage.
  - **The loop** (revised after owner feedback):
    1. Find a tree in the work area; walk beside it; fell it.
    2. Clear its canopy (below).
    3. Collect anything wanted still lying within 8 blocks, for up to 5 s.
    4. Repeat. It goes back to the hut only when it should deposit (pure rule `LumberjackJob.shouldDeposit`: fewer than 2 empty slots, or no room for logs), when there are no trees left and it's carrying something, or when it has no axe and the hut's stock has one. Walking back after every tree wasted most of its time.
    5. At the hut: deposit everything except axes (`Stock.insert` tops up stacks, then fills empty slots), and take the stock's best axe if it has none.
    6. If the stock was full and it still has no room for logs, it waits and tries again.
  - **Clearing the canopy (owner feedback: most drops were missed, because leaves take minutes to decay):**
    - **How vanilla works:** every natural leaf stores its distance to the nearest log. After a log is removed, distances update over a few ticks, and a leaf with no log within 6 (distance 7) decays, but only on a random tick, which is the slow part.
    - **What the lumberjack does** (`ClearCanopyStep`): waits 10 ticks for the distances to settle, then decays every such leaf around the tree itself. Drops come from the leaf's own loot table, so saplings, sticks and apples arrive at vanilla's chances, straight into its inventory. It clears 4 per tick, so the canopy falls away over a second or two. The pure check is `isOrphanedLeaf`.
    - **Dense forests:** leaves still within 6 of another tree's log keep a lower distance and stay, so canopies that touch are handled by vanilla's own rule. No search through the connected forest is needed.
    - **Bounded work:** it scans only the felled tree's bounds plus vanilla's 6-block leaf range (`TreeRules.canopyBox`), 512 blocks per tick, once per tree.
  - **Work area:**
    - `Workplace.workRadius` is saved per workplace. Its starting value and maximum come from the workplace data (`work_radius` 16 and `max_work_radius` 48 for the lumberjack hut), and data with a starting radius above the maximum is rejected.
    - The hut panel has a "Work area" row with −/+ buttons, in steps of 4 down to a minimum of 4. Being on the workplace, it's ready for every job.
    - The search keeps only claim chunks that reach into the area (`WorkArea.touchesChunk`), then only columns within the radius (`WorkArea.contains`), measured across the ground.
  - **Drops:** lumberjacks pick up logs, saplings, sticks and apples, and nothing else. The saplings are kept for replanting (S-016).
  - **Hut panel layout (owner feedback: the panel ran off the screen):** it's now two pages of one fixed, vanilla-sized panel, like MineColonies' hut windows.
    - **Overview:** details, the work area with −/+, and buttons for automatic hiring, "Workers (n/m)…" and Stock.
    - **Workers:** one paged list (6 rows a page) of "Working here" with Fire and "Looking for work" with Hire, then Back and paging.
    - The current page (`WorkplaceInfo.view`) is sent by the server, so hiring, firing and the live refresh keep the player on the workers page.
    - Settlers looking for work are sent in full (up to 64) and paged by the shared `WorkplaceInfo.rows()`.
    - Text isn't scaled down: Minecraft's pixel font blurs when shrunk.
  - **Profiler sections** (`kingdomrts:job`, `kingdomrts:job_seeking`, `kingdomrts:capture`) show our share of the tick in Minecraft's profiler (`/debug start`/`/debug stop`) and tools like Spark.
  - Existing lumberjack GameTests now count logs carried plus logs in the hut's stock. "Doesn't chop with no room" became "waits when it and its hut are full".
  - **Flakes:** GameTests run 3 times in a row without flakes.
  - **Sabotage checks:** no depositing; logs-only pickup; not taking the hut's axe; no work-area filtering; skipping the canopy step; clearing leaves whatever their distance (the shared-canopy test); depositing after every tree. Each fails the matching tests.
    - The work-area GameTest can only reliably catch both filters disabled together. Whether the far tree's chunk touches the area depends on where the test sits on the chunk grid. The column filter on its own is covered by `WorkAreaTest`.

### S-019 Job seeking
- **Status:** done | **Branch:** feature/job-seeking | **PR:** #19 | **Depends on:** S-017
- **Goal:** unemployed settlers take jobs at huts with openings by themselves.
- **Scope:**
  - **Automatic hiring:** a toggle per hut, on by default (as in MineColonies). Firing someone from the panel switches the hut to manual, so the unit isn't re-hired straight away.
  - **Seeking:** an unemployed settler checks about every 10 seconds (staggered per unit) for the nearest hut in its settlement with room and automatic hiring on, and is hired there. It only looks at its settlement's few workplaces, so it's cheap.
- **Out of scope:** worker skills and job preferences (deferred).
- **Test:**
  - Unit test: choosing the workplace.
  - GameTests:
    - an opening is filled automatically;
    - a manual hut isn't filled;
    - firing from the panel switches the hut to manual;
    - the nearest hut wins;
    - soldiers and outsiders don't seek jobs.
  - Manual: the toggle in the panel. Checked by the owner.
- **Notes:**
  - **Data:** `Workplace.autoHire` is saved, and defaults to on, including for huts saved before this existed.
  - **Panel:** an "Automatic hiring: On/Off" button with a tooltip. Its Fire buttons switch the hut to manual before firing.
  - **Choosing (pure, `WorkplaceRules.chooseWorkplace`):** the nearest workplace with room and automatic hiring on.
  - **Seeking (`WorkplaceOps.seekJob`):** runs from `Unit.aiStep` for units with no occupation and a settlement, every `JOB_SEEK_INTERVAL_TICKS` (200), offset by each unit's UUID so checks are spread across ticks. It only looks at its settlement's own workplaces.
  - Soldiers don't seek (they have an occupation), and neither do units with no settlement.
  - **Live panel (owner feedback):** an open hut panel didn't show hires made by automatic hiring until it was reopened. `WorkplaceMenu.broadcastChanges` now checks once a second whether the panel is out of date: the workers, the toggle, or the set of settlers looking for work. If so, it reopens in place (no cursor jump). Settlers are compared as a set, not in order, so ones wandering about don't make it refresh constantly.
  - **Tests:** GameTests run 3 times in a row without flakes. Job seeking now runs during every test with unemployed settlers near a hut, and none of the existing tests depended on them staying unemployed. GameTests for the live panel: it updates when someone is hired automatically, and stays put when settlers only move about (the nearest and farthest swap places). Sabotage checks (ignoring the auto-hire flag; no seeking; no live refresh; comparing settlers in order) fail the matching tests.

### S-017 Hut panel
- **Status:** done | **Branch:** feature/hut-panel | **PR:** #18 | **Depends on:** S-018
- **Goal:** right-clicking a workplace opens a panel to see and manage it, like MineColonies' hut screens.
- **Scope:**
  - Right-clicking the hut opens the panel instead of the stock. Only the owning faction (and creative operators) can open it.
  - **It shows:** the workplace's name, settlement, job, workers N/capacity, and the list of workers, each with a Fire button.
  - **Hiring:** the settlement's unemployed settlers are listed with Hire buttons, nearest first, with simple paging.
  - **Stock:** a button opens the stock (the chest screen), which has a Back button to the panel.
- **Out of scope:** automatic hiring (S-019), job settings (S-014, S-016), building levels.
- **Test:** GameTests with real button clicks: right-clicking opens the panel; Hire and Fire work; candidates leave out soldiers, the employed, and other settlements' units; the Stock button opens the chest; outsiders are refused. Manual: the panel's look. Checked by the owner.
- **Notes:**
  - Candidates are loaded units only, since an unloaded settler's occupation isn't known.
  - **Built as:** `WorkplaceMenu` + `WorkplaceScreen`, following the town hall menu.
    - The server sends a `WorkplaceInfo` snapshot: type, settlement, job, capacity, workers, and the current page of candidates.
    - Buttons go through `clickMenuButton` ids (Stock, previous/next page, Hire *i*, Fire *i*). After a hire or fire, the panel simply reopens with a fresh snapshot.
  - **Opening:** right-clicking a workplace opens the panel instead of the stock (`SettlementContainerBlock.open` is now a hook that `WorkplaceBlock` overrides). The panel closes if the hut is gone, the player walks away, or they lose access.
  - **Firing an unloaded worker:** `WorkplaceOps.fire(level, workplace, id)` takes it off the list, and the unit loses its job when it loads.
  - **The Hire button** is disabled when the hut is full. The server refuses too, with a message.
  - **Owner feedback:**
    - **Back button:** the stock now opens as `WorkplaceStockMenu` (vanilla's chest menu under its own menu type), whose screen adds a Back button.
    - **Cursor jump:** paging, hiring and firing moved the cursor to the middle of the panel. Each refresh reopens the menu, and by default the server first tells the client to close the old screen, which re-centres the mouse. `SwitchingMenuProvider` opens menus in place instead (NeoForge's `shouldTriggerClientSideContainerClosingOnOpen`). It's used for refreshes and for the panel ↔ stock switch, and GameTests check that no close packet is sent.
  - **Other changes:** the occupation display name moved to `client/DisplayNames`, shared by the unit and hut panels. `TestSupport.lastOpenedScreen` rebuilds the client's copy of any opened menu for tests.
  - GameTests added:
    - the panel's details, with candidates nearest first (no workers, soldiers or outsiders);
    - the Hire and Fire buttons, with the panel refreshing;
    - the Hire button respects capacity;
    - firing an unloaded worker;
    - paging;
    - the Stock button opens the chest;
    - outsiders can't open it.

    Sabotage checks (candidates include the employed; firing an unloaded worker doesn't update the list; switching closes the screen first) fail the matching tests.

### S-018 Unit names
- **Status:** done | **Branch:** feature/unit-names | **PR:** #17 | **Depends on:** S-015
- **Goal:** every unit has a name.
- **Scope:**
  - Name pools as data: `data/<ns>/kingdomrts/unit_names/*.json` with `first` and `last` lists, merged across files (loaded like settlement names).
  - New units get a random first and last name; units saved without one get one when they load.
  - The name shows when looked at (vanilla's behaviour for named mobs, the owner's choice), and in the unit and hut panels.
  - `spawnunit` stops setting the "Red soldier" testing tag and reports the new unit's name.
- **Out of scope:** renaming units.
- **Test:** unit tests for the name picker; GameTests: a spawned unit has a name from the pools, keeps it through save and load, and the spawn command reports it. Manual: the name shows when looking at a unit. Checked by the owner.
- **Notes:**
  - `UnitNames` is a datapack reload listener like `SettlementNames`, reading `data/<ns>/kingdomrts/unit_names/*.json`. Its pure `pick(first, last, random)` is unit-tested. The default pools have 60 first and 60 last names, and two units can share a name.
  - The name is vanilla's custom name: saved with the unit, shown when looked at (`CustomNameVisible` off). Units get one in `setup`, and units saved before names existed get one when they load.
  - Units spawned earlier with the "Red soldier" testing tag keep it, since it's already a custom name.
  - The unit panel's title and a workplace's worker list already used the unit's name, so they show it with no changes.
  - Sabotage check (no name given) fails the naming GameTest.

### S-015 Workplaces
- **Status:** done | **Branch:** feature/workplaces | **PR:** #16 | **Depends on:** S-009
- **Goal:** a unit gets a job by being hired at a workplace building of that job's kind (CLAUDE.md, Workplaces).
- **Scope:**
  - **The lumberjack hut**, a workplace block placed directly for now (like the town hall). It can only be placed inside your own claim, belongs to that settlement (which records it), and has a stock inventory protected like settlement storage. Right-clicking opens the stock.
  - **Workplace data:** `data/<ns>/kingdomrts/workplace/<block>.json` gives the occupation its workers get and the worker capacity (default 1).
  - **Hiring:** `WorkplaceOps.hire` gives an unemployed settler of the hut's settlement the hut's occupation, up to capacity. Firing, leaving the settlement, death, removing the hut, or the settlement dissolving end the job. Units that were unloaded catch up when they load.
  - **Commands:** `/kingdomrts unit hire <units> <workplace>` and `unit fire <units>`. `spawnunit` no longer hands out jobs, only soldiers or no occupation.
  - Soldiers are exempt.
- **Out of scope:** the hut panel (S-017), building levels, work areas (S-014), the build tool, worker supply.
- **Test:**
  - Unit tests: the hiring rule, and the workplace entry's defaults and validation.
  - GameTests:
    - the hut is recorded on its settlement, with placement only in your own claim;
    - hiring up to capacity;
    - only unemployed settlers can be hired (not outsiders or soldiers);
    - firing and leaving the settlement end the job;
    - removing the hut unemploys its workers, including one that was unloaded;
    - a dead worker leaves its workplace;
    - the hut and its workers pass with the settlement on capture;
    - other factions can't break the hut;
    - the hire, fire and spawn commands, run for real.
  - The lumberjack GameTests now hire their lumberjack at a hut.
  - Manual: placing the hut (look, creative tab, refused outside your claim), opening its stock.
- **Notes:**
  - **Split:** the hut panel (worker list, hire and fire buttons, stock button) is its own story, S-017, to keep this PR small. Until then, hiring is by command. The owner's feedback on this story also added unit names (S-018) and job seeking (S-019).
  - **Shared container block:** storage and workplaces share `SettlementContainerBlock` (own-claim placement, owner-only access, explosion-proof, no automation). Subclasses only say how they're recorded and what they're called. `WorkplaceBlockEntity` extends the storage block entity for the stock. `SettlementOps.ownClaimProblem` is the shared placement rule.
  - **Workers live on the settlement:** they're recorded in `SettlementData` (`Workplace`: position, type, workers with the name they had when hired), not on the hut's block entity, so they're known even while the hut's chunk is unloaded. The unit stores its workplace position.
  - **Pure rule:** `WorkplaceRules.hireProblem`. A unit belongs to the workplace's settlement, has no occupation yet (this also rules out soldiers), and the workplace has room.
  - **Catch-up** (`WorkplaceOps.catchUp`, run with the settlement catch-up when a unit loads or its settlement changes hands or dissolves): a unit whose workplace no longer lists it loses its job.
  - **Losing a job:** a unit that loses its job goes back to having no occupation and keeping near its post. Its job goals are removed.
  - **Hut look:** fletching-table textures, as a placeholder.
  - **Sabotage checks** that fail the matching tests: ignoring capacity; no catch-up on load.

### S-009 Lumberjack fells trees
- **Status:** done | **Branch:** feature/lumberjack | **PR:** #15 | **Depends on:** S-007, S-008
- **Goal:** a lumberjack unit fells natural trees in its settlement's claim and keeps the logs, without supervision.
- **Scope:**
  - **Job steps:** a small framework that runs explicit steps (CLAUDE.md), with a time limit per step and recovery chosen by the job.
    - The lumberjack's steps: find a tree in the claim, walk next to it, fell the whole tree, repeat.
  - **Tree search:** spread across ticks over the claimed chunks, with no per-tick world scan.
  - **What counts as a tree:** connected logs with natural (non-persistent) leaves touching them, so player-built logs are left alone.
  - **Breaking:** each log takes as long as it would for a player with the same tool. It uses its own best axe, which wears out. Reach is unlimited: it fells the whole tree from the ground.
  - **Pathfinding:** it walks to a standable spot beside the trunk, notices when it's stuck and gets itself out.
  - **Data:** a `lumberjack` occupation type (`movement_speed`).
  - **Pickup:** lumberjacks only pick up logs.
- **Out of scope:** workplaces (S-015), work areas and depositing (S-014), replanting and wood filters (S-016), taking tools from stock, payment and gold.
- **Test:**
  - Unit tests: the step runner (order, time limits, stopping), tree detection, break times.
  - GameTests:
    - fells a tree and keeps its logs;
    - leaves player-built logs alone;
    - recovers when its tree disappears on the way;
    - ignores trees outside its claim;
    - doesn't chop with no room;
    - idles without a settlement;
    - break time matches vanilla's for a player;
    - felling by hand takes a player's time;
    - uses and wears its axe;
    - carries on by hand when the axe breaks;
    - reaches a tree open on one side only;
    - gets down from a tree top;
    - climbs out of a pit;
    - skips a tree it can't reach.
  - Manual: watch one work; crack animation, axe in hand, behaviour in a real forest. Checked by the owner.
- **Notes:**
  - **Split:** the original S-009 (fell trees and haul logs to storage) was split, to keep PRs small. After the owner's feedback, the follow-ups became workplaces (S-015), working from the hut (S-014) and hut settings (S-016).
  - **Job framework (`unit/job`):** a `Job` hands out `JobStep`s. `JobRunner` ticks the current step, fails it when it runs past its time limit, and asks the job for the next step (passing how the last one went). `JobGoal` runs it as an AI goal. Shared steps: `WalkToStep`, `WaitStep`.
  - **Search:** a heightmap lookup per claim column (the highest non-leaf block), 128 columns per tick, skipping unloaded chunks. Candidate logs are then checked nearest first with `TreeRules`, 4 per tick. A 3×3 claim takes about 20 ticks.
  - **Tree rule (pure, `TreeRules`):** logs connected in all 26 directions (so branches count), at most 256 of them, and at least 3 natural leaves touching. Leaves placed by players are persistent, so they never count.
  - **Breaking (owner feedback):** breaking was instant at first.
    - **Timing:** `BreakTime` copies vanilla's player formula (tool speed, Efficiency via the `MINING_EFFICIENCY` attribute, `BLOCK_BREAK_SPEED`, the ÷30 or ÷100 harvest rule). Units now have the two mining attributes, so enchanted tools work. Oak takes 60 ticks by hand, 30 with a wooden axe, 10 with an iron one. A GameTest checks it against vanilla's own numbers for a player. `chop_ticks` was removed from the data.
    - **Felling:** highest log first, with vanilla's crack animation and arm swings.
    - **Axes:** while chopping it holds its fastest axe from its inventory, which takes durability per log. It switches to the next axe or bare hands when one breaks, and puts the axe back in its slot afterwards. The held axe always drops if the unit dies.
    - **Drops:** they go straight into the inventory, so logs can't get stuck in the canopy.
  - **Pathfinding (owner feedback: it skipped trees, got stuck, and once got stranded on a tree):**
    - **Stand spot:** it walks to a standable spot within 2 blocks of the trunk (solid ground that isn't part of a tree, room for its body), preferring one it has a full path to (checking at most 8). Aiming at the log itself is what led it up trees.
    - **Exact spot:** it paths to the spot itself (accuracy 0). Vanilla's `moveTo(x, y, z)` happily stops a block short, which left units just outside reach until the stuck handling nudged them, so walks took about 15 s instead of 4.
    - **Recovery ladder** (in `WalkToStep`):
      1. re-plan once a second;
      2. no progress for 3 s, or standing on a tree: step to a random nearby spot (twice);
      3. still stuck: hop to a safe spot within 8 blocks (the owner chose short teleports as the last resort). It never moves closer to the target, so it can't hop over a wall to a tree it couldn't walk to.
      4. still stuck: fail, and the job skips that tree for a minute.
    - **Restriction:** workers no longer keep within 6 blocks of their post, since that's for soldiers and idle units.
  - **Recovery:**
    - a tree it can't reach is skipped for a minute;
    - a tree that disappears on the way is replaced by the next one;
    - with no settlement, no room or no trees, it waits and checks again.
  - `Occupation.wantsToPickUp` lets each occupation filter what it picks up (lumberjack: logs only).
  - **Flakes:** GameTests run 3 times in a row without flakes.
  - **Sabotage checks** that fail the matching tests:
    - logs without leaves count as trees;
    - chopping with no room;
    - instant breaking;
    - ignoring the axe;
    - no teleport fallback (both the pit and tree-top tests fail);
    - walking at the log instead of a stand spot (the pit test fails).

### S-013 Unit info panel
- **Status:** done | **Branch:** feature/unit-info-panel | **PR:** #14 | **Depends on:** S-008
- **Goal:** right-clicking a unit shows its details, in full to its own faction and in part to everyone else.
- **Scope:**
  - **Everyone sees:** name, faction, occupation, health / max health, and the occupation's basic stats (attack damage, attack range, speed).
  - **Members of the unit's faction also see:** settlement, home, post and the inventory, and can move items in and out of it.
  - **Factionless units:** nobody gets the full view. Only the owner sees everything; there's no creative-operator exception.
  - **The server decides what to send:** hidden information never reaches a limited viewer's client.
  - The access rule is a pure function (full or limited).
- **Out of scope:** giving orders, renaming, changing occupation, allied access (open).
- **Test:**
  - Unit tests: the access rule.
  - GameTest: the server-side builder gives a member the full data and an outsider the limited set (no inventory).
  - Manual: how the screen looks for each viewer. Checked by the owner.
- **Notes:**
  - **Scope change (owner feedback on the first version):**
    - The panel now looks like a vanilla container screen: the unit drawn on the left (following the mouse, as the player does in their inventory), label/value rows beside it, the unit's inventory row ("Carrying"), and the player's own inventory below. It's drawn with fills that copy vanilla's panel and slot look (`client/GuiDraw`).
    - Units pick up items on the ground near them when they have room (`Unit` is now an `InventoryCarrier`, like villagers). It checks every 10 ticks instead of vanilla's every tick, and it respects the mobGriefing game rule like other mobs. It may need rules later so units don't collect trash.
  - **Scope change:** owners can now give and take items, which was planned for later. Clicking and shift-clicking work as in a chest. The server re-checks access every tick while the panel is open, and closes it if the player leaves the faction or the unit changes faction (e.g. on capture).
  - Occupation names are translated (`occupation.<namespace>.<name>`), falling back to the capitalised id for datapack occupations without a translation.
  - Values too long for their row are cut short with "...", and hovering shows them in full.
  - **Built as:** `Unit.mobInteract` opens `UnitMenu` (a menu, like the town hall's), which draws as `UnitScreen`.
    - `UnitInfo` is the snapshot sent when the panel opens: the basics for everyone, plus `Details` (settlement, home, post) only for the full view.
    - `UnitInfo.accessFor` is the pure access rule: full only when the viewer's faction is the unit's faction.
    - Health stays live through a `ContainerData`; the rest is a snapshot taken when the panel opens.
  - **Keeping the inventory private:** for a limited viewer the menu has no slots at all, so vanilla's menu sync never sends them the items. It isn't just hidden by the screen.
  - The panel closes if the unit dies or the player walks more than 8 blocks away.
  - Right-clicking always opens the panel, so items used on mobs (such as name tags) no longer work on units. Revisit if that matters.
  - **Test player upgrade:** `TestSupport.recordingPlayer` now records every packet sent to it instead of sending it. Vanilla's mock player can't receive NeoForge's packets, such as opening a menu with extra data. Tests can now check what actually reaches a player's client.
  - GameTests added: own faction sees everything, while an outsider and a factionless player see only the basics; nobody gets the full view of a factionless unit. Both check the panel the client builds from the packet that was sent, and that no packet carries the unit's items to an outsider. Unit tests: `UnitInfoTest` (6), covering the access rule and the snapshot surviving the trip to the client. Pickup GameTests: a unit picks up a nearby item; a full unit leaves it on the ground. GameTest `ownerCanGiveAndTakeItems`: an owner moves items in and out with real clicks and shift-clicks, and the panel stops being valid once they leave the faction. Sabotage checks (everyone gets the full view; no pickup; no access re-check) fail the matching GameTests.

### S-008 Units belong to settlements
- **Status:** done | **Branch:** feature/settlement-units | **PR:** #13 | **Depends on:** S-007
- **Goal:** a settlement knows its units, and capture hands its civilians to the new owner.
- **Scope:**
  - `Settlement` gets a saved set of unit UUIDs, like its storage set, kept up to date by `SettlementOps.setUnitSettlement` / `unitRemoved`. A unit is removed when it dies.
  - `/kingdomrts unit join <units> <settlement>`, `unit leave <units>` and `unit info <units>`, using entity selectors.
  - **On capture:** non-military units of the settlement switch to the new owner's faction; military units keep their faction (CLAUDE.md).
  - Units in unloaded chunks catch up when they next load.
  - `/kingdomrts settlement here` and `list` show the unit count.
- **Out of scope:** assigning homes automatically, population growth.
- **Test:**
  - GameTests: on capture, a civilian flips to the new owner and a soldier keeps its faction; a unit's death removes it from the settlement; a unit loaded after a capture catches up; the `unit join|leave|info` and `settlement here` commands, run for real.
  - Manual: none. There are no client-side visuals in this story.
- **Notes:**
  - **Scope change:** settlement membership is set with `/kingdomrts unit join|leave` (entity selectors) instead of a `settlement <name>` option on `spawnunit`. Another option would have doubled the spawn command's tree, and join/leave also works on units that already exist. `unit info` was added so a manual test can see factions change, since name tags are set once at spawn.
  - **Catching up:** each settlement counts its owner changes, and each unit remembers the count it has seen. When a unit loads (`Unit.onAddedToLevel`) with an older count, a non-military unit takes the current owner's faction. This handles several captures in a row while the unit was away, and it needs no list of pending changes. Loaded units are updated at capture time. A unit whose settlement was dissolved while it was away drops the settlement when it loads.
  - **The capture rule** is a pure function, `CaptureRules.factionAfterCapture` (military keeps its faction, everyone else passes), covered by unit tests. It applies to every non-military unit of the settlement, whatever its faction, as CLAUDE.md says.
  - `Occupation.countsAtCapture()` was renamed `isMilitary()`, since it now decides both capture presence and who keeps their faction.
  - **Removal:** a unit leaves its settlement's list only when it's destroyed (killed or discarded), not when its chunk unloads.
  - GameTests added: capture hands civilians to the new owner; unloaded units catch up after a capture; a dead unit leaves the settlement; dissolving releases units. Unit tests: two for `factionAfterCapture`. Sabotage checks (no catch-up on load; civilians keep their faction on capture; `unit join` doing nothing) fail the matching tests.
  - New `TestSupport.runCommand`: runs a real command as an operator standing at a spot in the test, and returns everything it printed. Commands are tested this way from now on, instead of in manual checklists.

### S-007 Generic Unit with data-driven occupations
- **Status:** done | **Branch:** feature/unit-occupations | **PR:** #12 | **Depends on:** -
- **Goal:** one `Unit` entity replaces `MilitaryUnit`. What a unit does comes from its occupation, a data entry. Military is the first occupation, with unchanged behaviour.
- **Scope:**
  - **Unit fields**, each optional and independent (CLAUDE.md): faction (moved from `MilitaryUnit`), settlement id (stored only; S-008 gives it meaning), home (`BlockPos`), inventory (a saved 9-slot container), occupation id.
  - **Occupation registry:** the datapack registry `kingdomrts:occupation` replaces `military_stats`. An entry has a `type` (only `military` for now) and that type's settings; for military, the current four stats. `soldier.json` moves over. Reuse the `MilitaryStats` pattern (codec + `DataPackRegistryEvent`).
  - **Behaviour from the occupation:** its goals (`HoldPostAttackGoal`, `HostileInRangeTargetGoal`, the post restriction), its targeting rules, and whether the unit counts at capture (`TownHallBlockEntity`).
  - `spawnunit` takes `occupation <id>`, defaulting to `kingdomrts:soldier`.
  - The entity id becomes `kingdomrts:unit`, with the renderer and lang updated.
- **Out of scope:** settlement membership logic, jobs, hunger and happiness.
- **Test:**
  - Port `CombatGameTests` and the capture GameTests without changing what they check; they are the safety net.
  - New GameTests: a unit with no occupation, or a non-military one, doesn't count at capture; inventory, home and occupation survive a save/load round trip.
  - Unit test: occupation JSON decodes, and bad entries are rejected.
  - Manual: spawn units with each command form and check the name tags; soldiers still fight and hold their post. Checked by the owner.
- **Notes:**
  - Old dev-world `military_unit` entities are dropped by the id change.
  - **Occupation JSON:** `data/<ns>/kingdomrts/occupation/<name>.json`, e.g. `{ "type": "kingdomrts:military", "max_health": 24, ... }`. `"type"` picks the code (`OccupationType`); the other fields are that type's settings. Types are a fixed list in code for now (only military); they can become a NeoForge registry if other mods need to add types.
  - **How an occupation drives a unit:** `Occupation.applyTo(unit)` sets the stats and adds the occupation's AI goals through `Unit.addOccupationGoal`, so they can be swapped out if the occupation changes. Every unit has the basic goals (float, stroll near its post, look around). Military adds hold-post attack, return to post, and target selection, at the same priorities as before.
  - **Capture:** counts units whose occupation `countsAtCapture()` (military only).
  - **Targeting:** soldiers target any unit of a hostile faction, not only soldiers. Hostile mobs target every unit, like villagers.
  - **Inventory:** 9 slots, saved slot by slot (vanilla `SimpleContainer`'s own save packs items into the first slots, which the round-trip test caught). It drops when the unit dies.
  - **Command:** `/kingdomrts spawnunit [occupation <id>|none] [townhall <pos>] [faction <name>]`, with occupation ids tab-completed from the datapacks. The name tag shows faction and occupation.
  - **Non-military coverage:** there's no non-military occupation type yet, so "doesn't count at capture" is tested with no occupation and with an id that doesn't exist. S-009's lumberjack adds the real non-military case.
  - GameTests added: units without a military occupation don't count; a soldier gets its stats from its occupation entry; a unit survives save and load (faction, settlement, home, post, occupation, inventory); inventory drops on death. Unit tests: `OccupationCodecTest` (5). A sabotage check (every unit counts at capture) fails two of the GameTests.

### S-006 Template cleanup
- **Status:** done | **Branch:** feature/template-cleanup | **PR:** #11 | **Depends on:** -
- **Goal:** none of the NeoForge MDK example code is left.
- **Scope:**
  - Remove `example_block`, `example_item` and the BUILDING_BLOCKS `addCreative` hook.
  - Remove `Config.java`, its registration and logging in `KingdomRts`, and the config screen in `KingdomRtsClient`.
  - Keep one creative tab, renamed `kingdomrts:main`, with the town hall as its icon, holding the town hall and storage block (and later the unit spawn egg).
  - Remove leftover `en_us.json` entries.
- **Out of scope:** any gameplay change.
- **Test:**
  - The existing unit tests and GameTests still pass.
  - Manual: the creative tab looks right and holds the town hall and storage block. Checked by the owner.
- **Notes:**
  - Also removed the MDK's "hello" log lines (common setup, server starting, client setup), which made the mod class register itself on the game event bus for no reason.
  - The tab's title is now "Kingdom RTS" (was "Kingdom RTS Tab"). Old dev worlds lose any `example_block` placed in them.
  - Left alone: the commented-out examples in `build.gradle` and `neoforge.mods.toml`, which are build/metadata boilerplate rather than mod code.
  - No new tests: nothing gameplay-related changed. The 28 unit tests and 20 GameTests still pass.

### S-004 Territory messages
- **Status:** done | **Branch:** feature/territory-messages | **PR:** #9 | **Depends on:** S-001
- **Goal:** players can tell when they cross into or out of a settlement's claim.
- **Scope:** "Entering Oakhaven (Dev)" / "Leaving Oakhaven" in the action bar, checked only when a player changes chunk (no scanning).
- **Out of scope:** drawing claim borders in the world.
- **Test:**
  - Unit tests: `TerritoryMessagesTest` (4). GameTest: crossing a claim edge gives one enter and one leave message.
  - Manual: walk in and out of a claim and check how the action-bar text looks. Checked by the owner.
- **Notes:**
  - Uses NeoForge's `EntityEvent.EnteringSection`, which only fires when an entity enters a new 16×16×16 section. The handler skips height-only moves and compares the claim of the old chunk with the new one, so there's no per-player state and no scanning.
  - The message choice is a pure function, `TerritoryMessages.message`, covered by the unit tests in `TerritoryMessagesTest`. Moving straight from one claim into another says "Entering …".
  - GameTest `crossingClaimEdgeShowsMessages` uses a new `TestSupport.recordingPlayer`, a fake player that records its action-bar messages. It checks for exactly one "Entering" on the way in, nothing while moving inside the claim, and one "Leaving" on the way out. A sabotage check (messages disconnected) fails it.
  - Not covered: no message on login or respawn inside a claim; dimension changes are untested.

### S-003 Settlement Storage block
- **Status:** done | **Branch:** feature/settlement-storage | **PR:** #8 | **Depends on:** S-001
- **Goal:** a settlement has storage that other factions can't open, break or drain until they capture it.
- **Scope:**
  - **Block:** a chest-like block with a single-chest inventory.
  - **Placement:** only inside your own faction's claim. It then belongs to that settlement and is recorded in the settlement's storage list. Placement anywhere else is refused, with a reason.
  - **Opening:** only the owner faction's members (later also its units). Others get "This storage belongs to <settlement>".
  - **Breaking:** the owner's members can break it (it drops its contents). It's unbreakable for everyone else, explosion-proof, and pistons can't move it.
  - **Automation:** no item access from hoppers or pipes for now.
  - **Capture:** access follows the settlement's current owner.
- **Out of scope:** same-faction automation, allied access, units using storage (comes with jobs).
- **Test:**
  - GameTests: placement inside vs. outside your claim; a hopper underneath pulls nothing; it survives an explosion; access checks for the owner's members vs. another faction (mock players), before and after a capture.
  - Manual: another faction's player sees the "belongs to" message; contents persist after save and reload. Owner checked: creative tab entry, barrel look, chest screen, refused placement outside the claim.
- **Notes:**
  - Custom block chosen over registering vanilla chests, because hoppers and pipes would leak from vanilla ones.
  - CLAUDE.md said storage "can't be broken" in one place, but the owner decided the owning faction can break its own. Reworded in this story.
  - Built as `SettlementStorageBlock` + `SettlementStorageBlockEntity`:
    - **Automation:** the block entity is a `WorldlyContainer` that offers no slots on any side, so hoppers are shut out. It registers no item-handler capability, and NeoForge only adds those automatically for vanilla block-entity types, so pipe mods can't see it.
    - **Access:** members of the owning faction, or creative-mode operators. Storage that belongs to no settlement (placed with `/setblock`, or its settlement dissolved) is an ordinary chest.
    - **The settlement's list:** each settlement records its storage positions (saved), so it "knows its storage containers".
    - **Shared code:** `SettlementOps.registerStorage` is used by both hand placement and the tests.
  - **Known quirk:** the client can't see who owns a storage block, so another faction's player trying to mine it may see cracks briefly before the server refuses. Placement refused by the server can likewise flash for a moment.
  - GameTests added:
    - placement only in your own claim, and refused outside any claim;
    - hoppers can't pull from or push into it;
    - owned storage survives an explosion, contents intact;
    - access follows the owner, before and after a capture;
    - only the owner can break it (contents drop, and it leaves the settlement's list).

    A sabotage check fails the matching tests.
  - Test isolation fix: `TestSupport.found` now dissolves leftover settlements in a test's spot first. Otherwise one test failing partway made later tests fail with "Too close to …", hiding the real failure.

### S-002 Town hall protection
- **Status:** done | **Branch:** feature/town-hall-protection | **PR:** #7 | **Depends on:** S-001
- **Goal:** a claimed town hall can't be destroyed in survival.
- **Scope:**
  - A claimed hall can't be mined by anyone in survival; creative-mode operators still can (testing).
  - Claimed halls are explosion-proof (bedrock-level resistance).
  - Protection lives on the block itself (break progress, explosion resistance) rather than in event cancelling, which leaks.
  - Unclaimed halls stay breakable, so a bare hall block can't be used as an unbreakable wall (design.md names this exploit).
- **Out of scope:** general block protection (CLAUDE.md: anyone can place and break other blocks), damage done at peace.
- **Test:**
  - GameTests: a claimed hall survives an explosion next to it; a survival mock player can't break a claimed hall but can break an unclaimed one; removing a hall still dissolves its settlement (already covered).
  - Manual: in survival, mining a claimed hall makes no progress; a creative operator can break it, which dissolves the settlement. Both checked by the owner, along with `/test runall` passing in-game.
- **Notes:**
  - Pistons already can't push the hall, because vanilla refuses to push blocks with block entities.
  - "Claimed" lives in a `claimed` block-state property, so clients know too (no mining cracks on a claimed hall). `TownHallBlockEntity` keeps it in step with its settlement, including halls claimed before this property existed.
  - Guards on `TownHallBlock`:
    - `getDestroyProgress` = 0 (client feedback);
    - `onDestroyedByPlayer` refuses (the real server check, which also stops creative players who aren't operators);
    - `getExplosionResistance` = bedrock's;
    - `canEntityDestroy` = false (wither, dragon, ravagers).
  - GameTests added: claimed hall survives an explosion; unclaimed hall is destroyed by one; a non-operator player can't break a claimed hall (zero mining progress); a player can break an unclaimed hall. A sabotage check (guards off) fails the two claimed-hall tests.
  - `TestSupport.mockPlayer` wraps vanilla's mock-player helper, which is marked deprecated for removal in a later Minecraft version but has no 1.21.1 replacement.

### S-005 Test setup
- **Status:** done | **Branch:** feature/test-setup | **PR:** #6 | **Depends on:** S-001
- **Goal:** features are checked by automated tests (unit tests + GameTests, run in CI) instead of step-by-step manual testing.
- **Scope:**
  - JUnit 5 through ModDevGradle's `unitTest` (runs with the game loaded), run by `./gradlew build`.
  - The capture decision is pulled out of `TownHallBlockEntity.serverTick` into a pure `CaptureRules`.
  - Unit tests: `CaptureRulesTest`, `NameRulesTest`, `SettlementOpsTest`, `FactionDataTest` (24 tests).
  - GameTests in the `gametest` package, on a shared `platform` template generated by `scripts/gen_platform_nbt.py`, with `TestSupport` helpers.
  - First 9 GameTests: capture flip, hold on equal, defender regeneration, tie holds at 1, units at war fight, units at peace don't, peace ends a fight, overlapping claim refused, hall removal dissolves.
  - CI runs `build` and `runGameTestServer`.
- **Out of scope:** client UI tests; staging commands; tests for hold-position wandering and hostile-mob combat (add when that code changes).
- **Test:**
  - `./gradlew build` and `./gradlew runGameTestServer` both pass.
  - Sabotaging the tie rule makes both the unit test and the GameTest fail.
  - Manual: `/test runall` in a new creative world shows 9 passing.
- **Notes:**
  - GameTest coordinates: the framework puts a structure block at y = 0, so the template's floor is at y = 1 and things stand at y = 2 (`TestSupport.FLOOR_Y`).
  - Factions and settlements are world-wide saved data, so every test that founds a settlement has its own batch (claims are bigger than the spacing between tests) and removes its hall at the end.
  - Run `/test` in a dedicated test world: test factions stay behind.
  - The headless server runs ticks as fast as it can, so the full suite takes about 2-3 seconds.
  - In-game, use `/test runall` or `/test run <name>`. `/test runall <class>` finds nothing, because our test names have no class prefix (that's what lets them share one template). Revisit if the suite grows to dozens of tests: class prefixes plus a generated template per class.

### S-001 Settlement record
- **Status:** done | **Branch:** feature/settlement-record | **PR:** #5 | **Depends on:** -
- **Goal:** claiming a town hall founds a named settlement with a 3×3 chunk claim, which passes to the new owner on capture.
- **Scope:**
  - `Settlement` + `SettlementData` (saved data with a chunk → settlement index), with ownership moved off the town hall.
  - `SettlementOps.found` (a faction operation, used by both Claim and the command), refused when the claim overlaps another settlement.
  - Names from `data/kingdomrts/settlement_names/`, with the shared `util/NameRules`.
  - Capture transfers the settlement; the boss bar, alerts and panel show the settlement name.
  - `/kingdomrts settlement list|here|rename|found`.
  - Removing a hall dissolves its settlement.
- **Out of scope:** hall protection (S-002), storage (S-003), territory messages (S-004), units belonging to a settlement.
- **Test:** see PR #5.
- **Notes:**
  - "Removing a hall dissolves its settlement" moved here from S-002, because halls are still breakable in this PR.
  - Old saves: halls load unclaimed.

- Unit prototype, factions, capture meter UI: PRs #1-#4 (before this backlog existed).
