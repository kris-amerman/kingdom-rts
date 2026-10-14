# Performance

The mod should cope with hundreds of workers across a world. This file says how we measure that, the budget we hold ourselves to, how unit code should be written to stay cheap, and what we've measured so far.

## Budget
Our code (units' whole ticks plus town hall capture checks) must stay under:

| Units | Our ms per tick |
|---|---|
| 100 | 5 |
| 200 | 10 |

A server tick has 50 ms in total, and vanilla, other mods and players need most of it. The stress test fails if a budget is exceeded (`PerfReport.Budget`). Other sizes are only reported.

## How it's measured
- **`Perf`** (`util/Perf.java`) times the hot paths. Each section is also a profiler section, so `/debug start` / `/debug stop` and Spark show them in any run:
  - `kingdomrts:unit`: a unit's whole server tick, including vanilla movement, AI goals and pathfinding;
  - `kingdomrts:job`, `kingdomrts:job_seeking`: inside a unit's tick;
  - `kingdomrts:capture`: a town hall's capture check.
  - "Our ms per tick" = unit + capture; jobs run inside units' ticks, so they aren't added again.
  - Not recording, a section costs only the profiler push and pop. The timers run only during the stress test.
- **The stress test** (`gametest/StressScenario.java`, `StressGameTests.java`) builds towns of 10 units each: 8 lumberjacks working from their huts in a forest of small oaks, a soldier at the hall, and a settler looking for work it won't find. It runs 50, 100, 200 and 400 units. Each size warms up for 200 ticks, then records 1200 ticks. It also checks that the lumberjacks actually felled trees, so an idle town can't pass by being cheap.
  - The GameTest server ticks as fast as it can, so the whole run takes under a minute.
  - Its tests use their own namespace, `kingdomrts_stress`, so the normal GameTest run never includes them.

## Running it
- `./gradlew runStressTest`. The report goes to `run/kingdomrts-stress/report.md` (and `report.json`), and is logged.
- **When:** before merging anything that adds per-unit work, and now and then otherwise. It also runs weekly on GitHub Actions, or by hand from the Actions tab ("Stress test"), with the report uploaded as an artifact. Shared runners are noisy, so their numbers show trends; local runs are the reference.
- When a run changes things noticeably, add a row to Results below.
- **Digging into a cost:** in `runClient`, `/debug start`, play, then `/debug stop`, and look for the `kingdomrts:` sections in the profile it writes. Spark gives a better view, if installed in the dev run.

## Writing unit code that stays cheap
Every bit of per-unit work belongs to one of these classes. New code should say in a comment which one it's in.

1. **Every tick:** constant-time only: a goal's or job step's tick, comparing a few numbers, moving towards a target. No world scans, no entity searches, no pathfinding.
2. **Periodic, staggered:** every N ticks, offset by the unit's UUID so units don't all do it on the same tick. For example, job seeking every 200 ticks and item pickup every 10.
3. **Once per job cycle, spread over ticks:** a bounded amount of work per tick, resumable. For example, the tree search scans 128 columns a tick, and canopy clearing scans 512 blocks a tick.
4. **On player action:** panels and commands. It can be a little heavier, but it's still bounded (e.g. a capped candidate list).

Also:
- Never load chunks from unit code: check `isLoaded` first (as `LumberjackJob.kindAt` does).
- Bound every search: a radius, a count, or a maximum, like `TreeRules.MAX_TREE_LOGS`.

## What costs what (from the baseline)
- **About 10 µs per unit per tick.** Most of it is vanilla's own entity tick (movement, AI goals, navigation); our jobs are about a sixth.
- **Job seeking and capture are negligible.**
- **The 400-unit run's worst tick** is about 30 ms. That's a spike, not the average; it's worth looking at if it grows.

## Watch list
Where cost is most likely to grow:
- **Pathfinding:** `WalkToStep` repaths once a second while walking, and every walk starts with a path check of up to a few stand spots. Builders and couriers will walk more.
- **Entity searches:** item pickup (every 10 ticks), `CollectStep` (every 10 ticks for 5 s after a tree), capture (a box around each hall).
- **Settlement-wide loops:** job seeking goes through a settlement's workplaces; panels go through its units.

## Levers for later
Not now; only once a measurement points at one of them.
- A global budget of path computations per tick, with the rest queued.
- Longer repath intervals on long walks.
- Cheaper ticks for units far from any player (vanilla already stops entities in chunks that aren't entity-ticking).
- Per-settlement caches (e.g. of open workplaces).
- Unloaded settlements: units in unloaded chunks don't tick at all. Whether settlements keep producing while unloaded is a design question (see docs/design.md, Open questions).

## Results
Our ms per tick (µs per unit in brackets), from local runs. Server ms/tick is the whole tick in the stress world.

| Date | Commit | Machine | 50 | 100 | 200 | 400 | Notes |
|---|---|---|---|---|---|---|---|
| 2026-10-13 | S-021 branch | Apple Silicon, 8 cores, Java 21 | 0.50 (10) | 1.01 (10) | 1.94 (10) | 4.53 (11) | Baseline. Three runs varied by about 15%. |
| 2026-10-14 | d721b4d | GitHub Actions, 4 cores, Linux | 0.50 (10) | 0.90 (9) | 1.96 (10) | 4.87 (12) | First CI run: within about 10% of local. Worst tick 44 ms at 400 units. |
