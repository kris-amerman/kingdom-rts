# Performance

The mod should cope with hundreds of workers across a world, and its interface should never cost a player frames. This file says how we measure that, the budgets we hold ourselves to, how unit and interface code should be written to stay cheap, and what we've measured so far.

## Budget
Our code (units' whole ticks plus town hall capture checks) must stay under:

| Units | Our ms per tick |
|---|---|
| 100 | 5 |
| 200 | 10 |

A server tick has 50 ms in total, and vanilla, other mods and players need most of it. The stress test fails if a budget is exceeded (`PerfReport.Budget`). Other sizes are only reported.

### Interface budget
The nation management interface has budgets of its own (S-044):

| What | Budget | Checked by |
|---|---|---|
| Server: a placement check | 200 µs per call | the stress test's request benchmark |
| Server: the unfinished sites around a player (32 sheds) | 1 ms per call | the stress test's request benchmark |
| Server: the Build catalog | 1 ms per call | the stress test's request benchmark |
| Server: the borders around a player | 1 ms per call | the stress test's request benchmark |
| Network: the biggest allowed building's blueprint | 160 KB | `PayloadSizeTest` |
| Network: the most unfinished buildings sent at once | 64 KB | `PayloadSizeTest` |
| Network: the most borders sent at once (32 settlements, 1024 chunks) | 16 KB | `PayloadSizeTest` |
| Client: drawing a preview and the sites around it | 1 ms per frame | the F3 line, by hand |
| Client: the borders view | 0.1 ms per frame drawing, 0.1 ms per tick checking the ground | `/kingdomrts perf` (client section), or `kingdomrts:borders_render` and `kingdomrts:borders_tick` in the Shift+F3 pie chart. First live run (S-036, one settlement): 0.052 ms a frame, 0.084 ms a tick; with the underground curtain and two settlements, 0.049 ms a frame, 0.090 ms a tick |

A placement check is asked up to ten times a second while a player moves a ghost; the rest are asked much less often. The client budget is checked by hand on the dev machine, with a preview up among 30 or so unfinished sheds, since the stress test has no client.

## How it's measured
- **`Perf`** (`util/Perf.java`) times the hot paths. Each section is also a profiler section, so `/debug start` / `/debug stop` and Spark show them in any run:
  - `kingdomrts:unit`: a unit's whole server tick, including vanilla movement, AI goals and pathfinding;
  - `kingdomrts:job`, `kingdomrts:job_seeking`: inside a unit's tick;
  - `kingdomrts:capture`: a town hall's capture check.
  - `kingdomrts:nation_request`: answering a request from a player's nation interface (every payload handler is wrapped in it, in `NationPayloads`).
  - "Our ms per tick" = unit + capture + nation requests; jobs run inside units' ticks, so they aren't added again.
  - Not recording, a section costs only the profiler push and pop. The timers run only during the stress test.
- **The stress test** (`gametest/StressScenario.java`, `StressGameTests.java`) builds towns of 10 units each: 8 lumberjacks working from their huts in a forest of small oaks, a soldier at the hall, and a builder building a storage shed from a stocked site. It runs 50, 100, 200 and 400 units. Each size warms up for 200 ticks, then records 1200 ticks. It also checks that the lumberjacks actually felled trees and that at least half the sheds got built, so an idle town can't pass by being cheap.
  - The GameTest server ticks as fast as it can, so the whole run takes under a minute.
  - Its tests use their own namespace, `kingdomrts_stress`, so the normal GameTest run never includes them.
  - **The request benchmark** (`interfaceRequests`) builds a town with 32 unfinished sheds around a player standing at its hall, then times each request the interface asks most (a placement check at a free spot, the unfinished sites, the catalog, the borders) over 500 calls after 200 to warm up. It writes a table to the report and fails over the interface budget.
- **Client sections** (`Perf.ClientSection`): `kingdomrts:preview_tick`, `kingdomrts:preview_render` and `kingdomrts:sites_render`. They're profiler names only, never timed: find them in the F3 pie chart (Shift+F3, under `gameRenderer`), in `/debug` profiles, or in Spark's client profiler.
- **The F3 line:** with F3 open while previewing, the left side shows `Kingdom RTS preview: <n> ghost + <n> site blocks, <avg> ms/frame (max <max>)`, over the last 60 frames.
- **Payload sizes:** `PayloadSizeTest` (JUnit) encodes the biggest payloads and checks them against their budgets.

## Running it
- `./gradlew runStressTest`. The report goes to `run/kingdomrts-stress/report.md` (and `report.json`), and is logged.
- **When:** before merging anything that adds per-unit work, and now and then otherwise. It also runs weekly on GitHub Actions, or by hand from the Actions tab ("Stress test"), with the report uploaded as an artifact. Shared runners are noisy, so their numbers show trends; local runs are the reference.
- When a run changes things noticeably, add a row to Results below.
- **In a live world (`/kingdomrts perf`, op only):** to measure real play, or to hand over numbers.
  - `/kingdomrts perf start`, play, then `/kingdomrts perf stop`. This writes `<game dir>/kingdomrts-perf/perf-<date>-<time>.md` and `.json` (in dev runs, `run/kingdomrts-perf/`), in the stress report's format, with a summary in chat.
  - The client of the player who ran `start` measures its frames meanwhile. A few ticks after `stop`, its numbers are added to the report as a "Client" section: frame rate, frame times (average, 95th percentile, worst), and the building preview's drawing time and blocks drawn. From the server console, there's no client section.
  - `/kingdomrts perf log on [seconds]` writes one summary line to the game log every 10 seconds (or as given) until `/kingdomrts perf log off`, such as `kingdomrts perf (last 10 s): 37 units, our 0.42 ms/tick (unit 0.39, ...), server 4.10 ms/tick, worst 9.8 ms`. Search `latest.log` for `kingdomrts perf`. The interval counts server ticks, so a paused singleplayer game logs later. `/kingdomrts perf log` on its own says whether it's on.
  - Both can run together. With neither on, nothing is recorded, and only the profiler push and pop run.
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

## Writing interface code that stays cheap
The nation interface runs on both sides: requests on the server, screens and previews on the client. It follows these rules:

1. **Idle costs nothing.** Every per-tick or per-frame hook returns at once when its feature is off (as `BuildPreviewClient` does with no preview).
2. **The server answers bounded questions from settlement data.** Requests never scan the world, and every answer is capped by range, count or size (`NationRequests.SITES_RANGE`, `MAX_SITES`, `Blueprint.MAX_BLOCKS`). Handlers are cost class 4, "on player action".
3. **Ask when something changed, not on a timer.** Clients ask when something moved or happened, with a slow recheck as the safety net. The server rate-limits every repeatable request per player (`CHECK_INTERVAL_TICKS`, `SITES_INTERVAL_TICKS`) and drops the excess quietly.
4. **Send each thing once.** Blueprints go once per kind and without air. The client keeps what it was sent (answers by spot, blueprints by kind). Once lists get long, send changes rather than whole lists.
5. **Work out per tick or per change, draw per frame.** Anything that looks at the world or allocates goes in the tick or in the change handler (`stillToShow` per tick, the turned blocks per rotation). A frame only draws prepared lists, so its cost is in proportion to the blocks drawn, and that number is capped.
6. **Escalate in order, when a number says so:** cap, then cache per tick, then bake a mesh (`client/BakedMesh`, used by previews since S-046, when a live report showed per-frame drawing over budget). Don't build it before a measurement asks for it.
7. **Measure before optimising,** with the tools above, and give every new kind of request a budget and a place in the benchmark.

## What costs what (from the baseline)
- **About 10 µs per unit per tick.** Most of it is vanilla's own entity tick (movement, AI goals, navigation); our jobs are about a sixth.
- **Job seeking and capture are negligible.**
- **The 400-unit run's worst tick** is about 30 ms. That's a spike, not the average; it's worth looking at if it grows.

## Watch list
Where cost is most likely to grow:
- **Pathfinding:** `WalkToStep` repaths once a second while walking, and every walk starts with a path check of up to a few stand spots. Builders and couriers will walk more.
- **Entity searches:** item pickup (every 10 ticks), `CollectStep` (every 10 ticks for 5 s after a tree), capture (a box around each hall).
- **Settlement-wide loops:** job seeking goes through a settlement's workplaces; panels go through its units.
- **Drawing previews:** the ghost and each unfinished site are baked into meshes (S-046), so a frame only draws them. Rebuilds happen when the ghost turns or a site's blocks change, and show as a one-frame cost (watch the F3 line's max). Chests and other entity-style blocks are still drawn each frame.
- **Placement checks:** the most frequent request, asked as a ghost moves. Its cost grows with a settlement's buildings, workplaces and storage, which it checks for overlap one by one.

## Levers for later
Not now; only once a measurement points at one of them.
- A global budget of path computations per tick, with the rest queued.
- Longer repath intervals on long walks.
- Cheaper ticks for units far from any player (vanilla already stops entities in chunks that aren't entity-ticking).
- Per-settlement caches (e.g. of open workplaces).
- A spatial index of a settlement's buildings, if placement checks grow with big settlements.
- Unloaded settlements: units in unloaded chunks don't tick at all. Whether settlements keep producing while unloaded is a design question (see docs/design.md, Open questions).

## Results
Our ms per tick (µs per unit in brackets), from local runs. Server ms/tick is the whole tick in the stress world.

| Date | Commit | Machine | 50 | 100 | 200 | 400 | Notes |
|---|---|---|---|---|---|---|---|
| 2026-10-13 | S-021 branch | Apple Silicon, 8 cores, Java 21 | 0.50 (10) | 1.01 (10) | 1.94 (10) | 4.53 (11) | Baseline. Three runs varied by about 15%. |
| 2026-10-14 | d721b4d | GitHub Actions, 4 cores, Linux | 0.50 (10) | 0.90 (9) | 1.96 (10) | 4.87 (12) | First CI run: within about 10% of local. Worst tick 44 ms at 400 units. |
| 2026-10-19 | S-025 branch | Apple Silicon, 8 cores, Java 21 | 0.54 (11) | 1.03 (10) | 2.11 (11) | 4.98 (12) | Builders join the scenario: each town's idle settler is now a builder building a stocked shed. All sheds built in every size. Worst ticks 10, 17 and 39 ms at 100, 200 and 400. |
| 2026-10-20 | S-030 branch | Apple Silicon, 8 cores, Java 21 | 0.52 (10) | 1.03 (10) | 2.08 (10) | 5.57 (14) | Builders clear their sites first. Run back to back with main (0.52, 0.99, 1.99, 4.77): within noise up to 200, about 15% more at 400, mostly job time. An earlier run on a loaded machine (load 13) read about 2.5 times higher; rerun when the machine is busy. |
| 2026-10-20 | S-031 branch | Apple Silicon, 8 cores, Java 21 | 0.75 (15) | 0.90 (9) | 1.85 (9) | 4.89 (12) | Builders now fetch the shed's materials from a settlement storage by the hall instead of a stocked site. All sheds built in every size. Back to back with main (0.52, 1.02, 2.11, 5.54): within noise; the 50-unit run was the first and noisiest. |
| 2026-10-21 | S-044 branch | Apple Silicon, 8 cores, Java 21 | 0.91 (18) | 0.90 (9) | 2.63 (13) | 5.04 (13) | First request benchmark: placement check 25 µs, unfinished sites (32 sheds) 34 µs, catalog 6 µs per call. Back to back with main (0.47, 0.87, 2.30, 4.90): within noise. The load average rose from 2.7 to 6.8 over the runs, and an earlier branch run on it read up to 70% higher. |
| 2026-10-24 | S-036 branch | Apple Silicon, 8 cores, Java 21 | 0.63 (13) | 0.95 (10) | 1.91 (10) | 5.35 (13) | Borders join the request benchmark: 16 µs per call (placement check 25, unfinished sites 46, catalog 4). Nothing per unit changed; load average about 4. |
