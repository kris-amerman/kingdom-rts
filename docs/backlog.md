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

### S-023 Structurize spike
- **Status:** in review | **Branch:** feature/structurize-spike | **PR:** #29 | **Depends on:** S-011
- **Goal:** decide whether to build on Structurize (ldtteam) or keep our own templates and placement.
- **Scope:** a time-boxed look at its licence, API, build tool, 1.21.1 support, size and cost, with a hands-on test on a throwaway copy (not kept; the report says how to reproduce it). **The report is `docs/structurize.md`.**
- **Out of scope:** adopting it.
- **Test:** none (a decision document). The hands-on test was a GameTest on the throwaway copy.
- **Notes:**
  - **Owner, before the spike:** open to relicensing Kingdom RTS as GPL-3.0 if Structurize is the better tool.
  - **Findings in brief:**
    - **Licence and dependencies:** GPL-3.0. It requires BlockUI (GUI library) and Domum Ornamentum (decorative-block content). About 2.5 MB of jars.
    - **Builds:** all 1.21.1 builds are beta or snapshot. A mismatched Structurize/BlockUI pair crashed the dedicated server at load; the matched snapshot trio worked, with all our GameTests passing alongside.
    - **Placement:** server-side, resumable, a bounded number of blocks per call, driven by a survival `IStructureHandler` (inventory, required items, consume). 22 placement handlers.
    - **Hands-on:** it rebuilt our shed exactly, with required items matching ours exactly. About 110 µs per placed block, against 8 µs for our bulk `finish`; roughly 1 ms per tick for 100 builders.
    - **The build tool** has a hook (`ISurvivalBlueprintHandler`) that can turn a placement into `BuildingOps.commission`.
  - **The report recommended adopting** (if Domum Ornamentum was acceptable too).
  - **Owner's decision:** we won't use Structurize, BlockUI or Domum Ornamentum. The mod stays fully distinct from MineColonies and its libraries (a new standing policy in CLAUDE.md, Independence), and the spike's costs point the same way. Recorded in design.md (Nation management interface). `docs/structurize.md` stays as the record of the spike.
  - **Follow-ups from the decision:**
    - **The "build tool" becomes the nation management interface** (working name): the player's main way of shaping and running their nation, opened by a hotkey for now, with actions that don't depend on how they're triggered (stories S-032 to S-036).
    - **Placement stays our own:** S-025 is reworked from vanilla's block rules and split into S-025, S-030 and S-031.
    - **Docs:** CLAUDE.md, design.md and the README describe features in our own terms, with an Influences section crediting MineColonies, Polytopia, Civilization, Cities: Skylines and others. Done stories stay as they are.
    - **Later:** code comments that mention MineColonies or Structurize (`WorkplaceScreen`, `BuildingTemplates`) are reworded in the next code PR.

### S-025 Builders place blocks
- **Status:** planned | **Branch:** feature/builders-place | **PR:** - | **Depends on:** S-024
- **Goal:** assigned builders build their site from its stock until it matches its template.
- **Scope:**
  - **Placing** missing blocks from the site stock, a block at a time, at a speed set in the builder's occupation data. Each block placed uses up its materials.
  - **Build order,** from vanilla's own rules (not from any other mod):
    - bottom layer first;
    - a block is only placed once vanilla says it can stay there (`BlockState.canSurvive`), otherwise it waits. That one rule covers torches, lanterns, ladders, buttons, levers, signs, vines, carpets, rails, pressure plates and plants on soil;
    - falling blocks (sand, gravel, concrete powder, anvils) only once something is under them (`FallingBlock.isFree` on the block below).
  - **Two-block things** (doors and tall plants by `DoubleBlockHalf`, beds by `BedPart`): both halves go down in the same step, costing one item, so the first half doesn't pop off.
  - **Shapes and connections** (stairs' corners, fences, walls, panes, redstone, paired chests) come from vanilla's neighbour updates. We place the template's facing, axis and half, and the matching rule already ignores the rest.
  - **Costs,** a pure rule in `BuildingRules`, unit-tested:
    - a double slab costs 2 slabs, and a wall torch costs a torch (done);
    - crops cost their seeds (`asItem`);
    - a potted plant costs a flower pot plus the plant;
    - grass blocks, farmland and paths cost dirt;
    - snow layers, candles and sea pickles cost one per layer or piece;
    - a water source costs a water bucket, with the empty bucket returned to the site stock.
  - **What builders don't place:** lava, flowing fluids, fire, portals and piston heads are listed as "not placeable", as now.
  - **Block entities** (chests, barrels, furnaces, signs, banners) are placed empty and plain; a template never gives away items or text.
  - **Waterlogged blocks** are placed dry.
  - **Several builders on one site** never place the same block twice (reservations in memory).
  - **When nothing's left,** the site is completed (`BuildingOps.completeIfDone`), and its builders return to the pool.
  - **Performance:** the stress scenario gains a builder and a site per town, and the stress test is run before merging.
- **Out of scope:**
  - clearing what's in the way (S-030);
  - fetching materials (S-031); for now the player fills the site stock;
  - couriers, gold, levelling, repair.
- **Test:**
  - **Unit tests:** costs and build order (rules).
  - **GameTests:**
    - one builder finishes the shed from a stocked site;
    - two builders finish it without duplicates;
    - a builder with an empty stock waits without crashing, then carries on when materials are added;
    - doors, beds, torches and falling blocks end up right.
  - **Manual:** watch it build.
- **Notes:**
  - **Placement stays our own** (S-023 decision). The awkward blocks above come from vanilla's block classes.
  - The site's list of outstanding materials is the demand signal markets can read later.

### S-030 Builders clear the site
- **Status:** planned | **Branch:** feature/builders-clear | **PR:** - | **Depends on:** S-025
- **Goal:** builders clear what's in the way inside a site before building there.
- **Scope:**
  - Builders break blocks inside the footprint where the template wants air or another block, taking as long as a player would with their best tool (`BreakTime`).
  - The drops go into the site stock.
  - Grass, snow and the like are cleared too.
  - No levelling or filling below the site.
- **Out of scope:** terraforming and levelling.
- **Test:** GameTests: a site with dirt and a tree in the way gets cleared and then built; the drops end up in the site stock. Manual: watch it.
- **Notes:** -

### S-031 Builders fetch materials
- **Status:** planned | **Branch:** feature/builders-fetch | **PR:** - | **Depends on:** S-025
- **Goal:** builders fetch what their site is missing from settlement storage.
- **Scope:**
  - At most one builder per site fetches at a time; the others keep building from the site stock.
  - A fetch takes the missing materials not yet on site, in build order, as much as it can carry, from settlement storage (later from shops), and deposits them into the site stock.
  - What can't be found is skipped; builders build what they can, then wait.
  - Leftover materials go back to storage when the site is finished.
- **Out of scope:** couriers, gold and shops.
- **Test:** GameTests: a builder fetches from storage and the site gets built; two builders on a site have only one fetcher at a time; missing materials make them wait, then carry on. Manual: watch it.
- **Notes:** the supply lifecycle recommended to the owner while planning S-011 (design.md, Builders and construction sites).

### S-032 Nation management interface: opening it
- **Status:** planned | **Branch:** feature/nation-interface | **PR:** - | **Depends on:** S-024
- **Goal:** a player can open the nation management interface, and its actions run on the server as faction operations, however they're triggered.
- **Scope:**
  - **Opening it:** a rebindable hotkey, N by default, in a "Kingdom RTS" category under Controls (vanilla doesn't use N). Also `/kingdomrts nation`, which opens the same screen. No item.
  - **A first screen:** your faction and settlements, and the actions Build, Demolish and Borders (filled in by the next stories).
  - **A request channel** from the screens to the server: the server checks the player belongs to the faction and runs the same faction operations as the commands. How a request is triggered (a key, a later command view or free camera) never matters to the server.
  - Players without a faction see a short "join or found a faction first" screen.
- **Out of scope:** the actions themselves (S-033 to S-036), the name, a command view or free camera.
- **Test:** GameTests for the request handling (accepted, refused for outsiders and factionless players) and the command. Manual: the key and the screen.
- **Notes:** the hotkey is the owner's lean (no item); the command is for testing and anyone who hasn't set a key.

### S-033 Nation interface: building preview
- **Status:** planned | **Branch:** feature/building-preview | **PR:** - | **Depends on:** S-032
- **Goal:** a player can pick a building from the catalog and see it as a ghost in the world, moving and turning it.
- **Scope:**
  - **Build** lists the building catalog, sent from the server (building data isn't on the client).
  - Picking one shows it as a translucent ghost where the player is looking. Keys nudge it sideways and up or down, and rotate it.
  - The template's blocks are sent on demand, within the existing size cap.
  - Our own ghost renderer, using vanilla's block rendering.
- **Out of scope:** confirming (S-034), mirroring, styles and levels.
- **Test:** unit tests for the placement maths (reusing `BuildingRules.place` and `footprint`); a GameTest for the catalog and template payloads. Manual: the ghost and its controls.
- **Notes:** -

### S-034 Nation interface: commission from the preview
- **Status:** planned | **Branch:** feature/preview-commission | **PR:** - | **Depends on:** S-033
- **Goal:** confirming a preview commissions the building, under the same rules as the command.
- **Scope:**
  - Confirming sends a commission request; the server runs `BuildingOps.commission`, so every claim and overlap rule applies.
  - Refusals are shown to the player ("outside your claim", "overlaps…").
  - The ghost is tinted where the spot can't work, from a rate-limited check on the server, which stays the authority.
- **Out of scope:** gold costs.
- **Test:** GameTests for commissioning through the request (success, outside the claim, overlapping, another faction's claim). Manual: the tint and messages.
- **Notes:** -

### S-035 Nation interface: demolish
- **Status:** planned | **Branch:** feature/interface-demolish | **PR:** - | **Depends on:** S-032
- **Goal:** a player can demolish their own buildings from the interface.
- **Scope:**
  - **Demolish** shows your settlement's buildings as outlines.
  - Pick one by looking at it, confirm, and the server runs `BuildingOps.demolish`.
  - Other factions' buildings can't be picked or demolished.
- **Out of scope:** the explosion effect, rubble and materials returned, gold costs.
- **Test:** GameTests for the request (own building demolished, another faction's refused). Manual: outlines and picking.
- **Notes:** -

### S-036 Nation interface: borders view
- **Status:** planned | **Branch:** feature/borders-view | **PR:** - | **Depends on:** S-032
- **Goal:** a player can toggle a view of settlement borders around them.
- **Scope:**
  - A toggle that draws claim borders near the player, coloured per faction.
  - The server sends nearby claims when the view is switched on, and when claims change.
- **Out of scope:** claim management, faction colours and emblems beyond a simple colour.
- **Test:** a GameTest for what's sent (nearby claims, owners). Manual: the drawn borders.
- **Notes:** -

### S-026 Lumberjack hut building
- **Status:** planned | **Branch:** feature/lumberjack-hut-building | **PR:** - | **Depends on:** S-031, S-034
- **Goal:** a lumberjack hut is a real building, commissioned from a template, built by builders, and managed from its hut block.
- **Scope:**
  - **Core blocks from data** (the general part, reused by later functional buildings): a building's data can name its core block, e.g. `"core": "kingdomrts:lumberjack_hut"`.
    - The core is part of the template.
    - Commissioning places it at once, at its turned spot. It replaces the construction site block for that building: there's no separate site block, and the core holds the site's stock.
    - Clicking the core shows the site panel while the building is built, and its own panel (the hut panel) once built.
    - Templates for buildings with a core must contain it exactly once; plain buildings keep using the site block.
  - **The workplace starts employing once its building is built.** While under construction it's recorded as a workplace-to-be: no hiring, and it isn't offered to job seekers.
  - **The building's stock:** what's left in the site stock when it's finished stays, as the hut's starting stock (e.g. spare axes).
  - **A lumberjack hut template** (small, wooden) and its building definition.
  - **Demolishing** the building removes its hut block too, so the workplace goes and its workers lose their jobs (existing workplace code).
- **Out of scope:** other huts, the town hall (S-027), homes, repair.
- **Test:**
  - **Unit tests:** a template's core (missing, once, several), and the building definition with and without a core.
  - **GameTests:**
    - commissioning places the hut block at its turned spot, and it shows the site panel;
    - nobody is hired while it's under construction;
    - once finished, the hut panel shows and automatic hiring fills it;
    - demolishing it fires its worker;
    - a placed hut block (the stand-in) still works as before.
  - **Manual:** the hut template's look; the panels switching.
- **Notes:**
  - Owner: homes need their own residence core (a later story, with housing).
  - Placed hut blocks, the stand-in until the nation management interface builds huts, keep working. Whether they stay after that is open.

### S-027 Town hall building
- **Status:** planned | **Branch:** feature/town-hall-building | **PR:** - | **Depends on:** S-026
- **Goal:** a settlement can be founded by commissioning a town hall building, which claims the land at once and is built like any other building.
- **Scope:**
  - **A town hall template and definition,** with the town hall block as its core (S-026's core-from-data).
  - **Founding by commission** (`BuildingOps`/`SettlementOps`): commissioning a town hall outside any claim founds the settlement then and there, as CLAUDE.md says (claimed the moment it's founded). That means the starting claim, the space and buffer rules, and no town hall in another faction's territory. The hall building is then the settlement's first construction site.
  - **The command:** `building commission` accepts a town hall outside a claim (every other building still needs your own claim).
  - **While the hall is being built:** the claim and capture rules apply. Whether the hall panel's other features wait until it's finished is decided in the story.
  - **Demolish:** a settlement's town hall can't be demolished, just as the hall block can't be broken.
- **Out of scope:** the starter camp (Direction) and who builds the first hall, naturally generated settlements, founding through the interface (a later story).
- **Test:**
  - **GameTests:**
    - commissioning a hall outside any claim founds a settlement, with the claim, and places the hall block;
    - it's refused too close to another settlement or in another faction's land;
    - finishing it makes it an ordinary built building;
    - demolishing it is refused;
    - a placed town hall block (the stand-in) still works.
  - **Manual:** the hall template's look.
- **Notes:**
  - **Open, for the owner:** who builds the first town hall. The settlement has no builders until it's founded. In the Direction, the starter camp brings a builder; until then, a settlement's builders can be hired at the half-built hall, or `building finish` is used.
  - The placed town hall block stays as the stand-in until the interface and starter camp can found settlements (CLAUDE.md).

### S-012 Unit needs (design first)
- **Status:** planned | **Branch:** feature/unit-needs | **PR:** - | **Depends on:** S-031, S-014
- **Goal:** units have hunger and happiness, drawn from real things in the world.
- **Scope:** first a short design note in docs/design.md, then the simplest version (likely hunger from food in settlement storage, happiness from housing).
- **Out of scope:** revolts, taxes.
- **Test:** decided with the design.
- **Notes:** deliberately placed after the jobs. With no food production, housing or economy yet, needs would have to be abstract counters, against the "constraints from the world" principle.

## Later (not yet stories)
- **Work-area view:** a toggle on the hut panel or in the nation management interface that shows a workplace's work area.
- **Military buildings:** barracks or guard towers for soldiers (exempt from workplaces for now).
- **Wanderers (population growth):** unemployed, unhoused units arrive in settlements now and then, at a rate scaled to the settlement's capacity, with buildings like an Inn speeding it up (design.md, Population).
- **Worker skills:** per-unit skills and strengths, and matching units to jobs that suit them. Deferred by the owner to keep things simple.
- **Worker supply:** base demand per workplace, with workers fetching what they need from their workplace, storage, markets or other workplaces' surplus, and couriers as a helper rather than a bottleneck (design.md, Workplaces and supply).
- **Capture UI later:** faction identity (colour/emblem/banner), a custom-drawn bar, town hall and flag models.
- **Parked ideas:** allied factions sum their presence at capture; player-set unit stances (hold vs pursue); a detection range separate from attack range (ranged mobs currently out-range units).

## Done

### S-024 Builders
- **Status:** done | **Branch:** feature/builders | **PR:** #28 | **Depends on:** S-011
- **Goal:** a settlement has builders, hired at its town hall and assigned to construction sites.
- **Scope:**
  - **The builder occupation** (`kingdomrts:builder`: `BuilderOccupation`, data in `occupation/builder.json`). Builders have no workplace.
  - **Town hall panel, Builders page** (owners only): the settlement's builders with what each is working on and Fire; its unemployed settlers with Hire. The overview's bottom row is now [Population (n)] [Builders (n)].
  - **Site panel, Builders page:** the builders on this site with Unassign; the settlement's idle builders with Assign (hidden once the site is full). A site takes up to `max_builders` (building data, default 2; the right number is open). The overview gets a "Builders (n/max)…" button.
  - **Commands:** `unit hire <units> builder`; `unit fire` also fires builders; `building assign|unassign <pos> <units>`.
  - **Where builders go** (`BuilderJob`): assigned builders walk to their site and wait within 4 blocks of its site block. Unassigned builders go back to their post, the town hall, and wait within 4 blocks. A builder whose site is finished, cancelled or demolished is back in the pool.
- **Out of scope:** building anything (S-025).
- **Test:**
  - **Unit tests:** `BuilderRulesTest` (hiring and assigning refusals); a building's builders surviving saving and being freed when finished; `max_builders` optional, and at least 1.
  - **GameTests (`BuilderGameTests`, 7):**
    - hiring and firing at the hall panel;
    - outsiders don't get the builders page;
    - assigning at the site panel up to the limit, with busy builders not offered elsewhere;
    - a builder walking to its site and back to the hall when the site is cancelled;
    - finishing a site freeing its builders;
    - a builder that dies or leaves being taken off its site;
    - the commands.
  - **Manual:** the panels.
- **Notes:**
  - **Decisions:**
    - idle builders' post is the town hall, so the pool gathers there;
    - only idle builders can be assigned (no moving a busy builder between sites from the panel; unassign first);
    - `max_builders` defaults to 2.
  - **Owner, for later:** the cap on builders per site should come from some level, perhaps a builders' guild's level, rather than a fixed number per building. Undecided; `max_builders` is a placeholder until then.
  - **Assignments live on the building** (`Building.builders`, UUID to name), so they're known while builders are unloaded. A builder fired while unloaded loses the job when it next loads (`SettlementOps.catchUp`). A builder that leaves its settlement loses the job too.
  - **Bug found by a sabotage check:**
    - finishing a building wrote back the caller's copy of the building record, which could be out of date (from before builders were assigned), so changes made since were lost;
    - `SettlementData.finishBuilding`, `assignBuilder` and `unassignBuilder` now work on the settlement's current record.
  - **Stress:** builders add a small per-unit job (a short wait, and a look through the settlement's buildings when a step ends). The stress run is unchanged and within budget; builders join the scenario in S-025.
  - **Flakes:** GameTests ran 3 times in a row without flakes.
  - **Sabotage checks:**
    - no `max_builders` check (limit test);
    - finishing keeping its builders (finish test, after the fix above);
    - outsiders getting builders (outsider tests);
    - the job ignoring its site (walk test).
    - Each fails the matching test.

### S-029 Walk diagnostics for idling lumberjacks
- **Status:** done | **Branch:** feature/lumberjack-reach | **PR:** #27 | **Depends on:** S-014
- **Goal:** the next time a lumberjack idles while a tree stands, the debug log says why.
- **Scope:** logging only, no behaviour changes (owner's call: no fixes on a guess).
  - `WalkToStep` logs when a walk starts (from where, to which spot, and whether the first path reaches) and when it gives up: no spot to stand, or stuck with no way out, with where it got to and the closest it came.
  - The felling step logs when the lumberjack ends up too far from the trunk to keep chopping.
  - All at debug level, logged only at those moments.
- **Out of scope:** fixing the idling (a follow-up story once the logs show the cause).
- **Test:** `LumberjackReachGameTests`: real vanilla trees (by the game's own tree generator) at 10, 18, 26 and 34 blocks from the hut, a fancy oak, and one with the town hall in the way. Each must start being felled within 30 s. They pass today and guard walking against regressions.
- **Notes:**
  - **Owner report:** lumberjacks sometimes wait by the hut for minutes with a tree standing, then eventually fell it.
  - **The log** (19 Oct session): the same tree, at (118, 64, 34), "couldn't reach" three times, a minute apart. Each failure skips the tree for 60 s; with nothing else to fell it waits at the hut. Just before that, "can't replant at (122, 64, 40), too far": it had ended up more than 6 blocks from the tree it had just felled.
  - **Ruled out so far:**
    - **Lack of standing room:** the save shows flat open grass.
    - **The 16-block pathfinding range:** the reach tests start felling within 6 to 13 s at every distance.
    - **A small forest:** a temporary scenario with 10 vanilla trees and an idle unit had no failed walks.
  - **Next time it happens:** the "gave up walking" and "stopped felling" lines in `run/logs/debug.log`.
  - **Ideas held back** until the cause is known: "close enough counts", and retrying a lone tree sooner.

### S-028 Town hall panel: population
- **Status:** done | **Branch:** feature/town-hall-population | **PR:** #26 | **Depends on:** S-011
- **Goal:** a settlement's owners can open its town hall and scroll through everyone who lives there.
- **Scope:**
  - **The town hall panel is redone** as the same fixed vanilla-style panel as the hut and site panels (`GuiDraw`, `SwitchingMenuProvider`, a snapshot plus a once-a-second live refresh), with two pages.
    - **Overview:** settlement, owner, live control meter, claim, and population count, with the Claim button for an unclaimed hall and a "Population (n)…" button for the owning faction's members.
    - **Population:** each citizen's name with their job title ("Lumberjack", "Soldier", "Unemployed"), one per line, 9 a page, sorted by name, with Back and paging.
  - **The settlement remembers its units** (`Resident`: name and occupation), so units that aren't loaded are listed as last seen. It's refreshed when a unit joins, is hired or fired, and whenever it loads (`SettlementOps.refreshResident`).
  - **Owners only:** other factions see the overview without the population, and can't open the page.
- **Out of scope:** opening a unit's panel from the list, hiring builders (S-024 adds a Builders page here), happiness and needs (S-012), a settlement-wide management menu or map, filtering and sorting.
- **Test:**
  - **Unit tests (`ResidentTest`):** residents survive saving; updates only touch the settlement's own units; units saved before residents existed load as unknown.
  - **GameTests (`TownHallPanelGameTests`, 5):**
    - everyone listed by name with their job (and not a faction unit outside the settlement);
    - unloaded units listed as last seen;
    - paging and Back;
    - the open page updating when someone is hired or joins;
    - outsiders don't get the population.
  - **Manual:** the panels' look.
- **Notes:**
  - **Owner feedback after a first version:**
    - no View button to open a unit's panel from the list (a first version had one, view only from the hall, with Back);
    - no workplace or "away" marker: just each citizen and their job title.
    - The unit panel is unchanged from main.
  - **The settlement save format changed:** units are now saved with their name and occupation. Units saved in the old format (just a UUID) show as "Unknown settler" until they next load.
  - **No stress run needed:** residents are only refreshed on hire, fire, joining and loading, and the panel's work only happens while it's open.
  - **Flakes:** GameTests ran 3 times in a row without flakes.
  - **Sabotage checks:** residents never refreshed (unloaded-units test), outsiders getting the population (outsider test), and no live refresh (live test) each fail the matching test.

### S-011 Construction sites
- **Status:** done | **Branch:** feature/construction-sites | **PR:** #25 | **Depends on:** S-010
- **Goal:** a commissioned building has a construction site on site that holds its materials and shows its progress, until it's finished.
- **Scope:**
  - **The construction site block** (`kingdomrts:construction_site`, no item): commissioning puts it inside the building, on a spot the template leaves clear. It's a floor spot near the middle (`BuildingRules.siteSpot`). A building with no clear spot is refused. Its stock is protected like the settlement's other containers.
  - **The site panel** (right-click the site block): progress (blocks placed of the total), blocks in the way, and materials still needed with how many are already on site. A Materials page lists them all; a Stock button opens the site's stock.
  - **Finishing:** once nothing is left to do, the building is marked built and its site block is taken away; leftover stock drops. This happens through `building finish`, or when the open panel notices (e.g. built by hand), and builders will do it in S-025. `building list` shows built buildings; `cancel` and `finish` refuse them.
  - **Cancelling:** breaking the site block (owner only) cancels the site, and so does `building cancel` (which also removes the block).
  - **Demolishing** (`BuildingOps.demolish`, command `/kingdomrts building demolish <pos>`), for sites and built buildings:
    - the settlement forgets the building, so its ground is free and it's no longer listed;
    - a site's block goes and its stock drops;
    - every block still matching the template is removed at once, with no drops. Other blocks stay.
    - It refuses if the site isn't loaded, so a building is never half removed.
- **Out of scope:** builders (S-024), the work loop (S-025), core blocks for functional buildings (S-026), repair.
- **Test:**
  - **Unit tests:** where the site goes (`siteSpot`: a floor spot near the middle, floor beats floating, none without air), a building with and without a site surviving saving (including finished ones and older saves), and the panel's materials ordering.
  - **GameTests (`BuildingSiteGameTests`, 8):**
    - the site block placed on commission (and in a turned building);
    - finishing marks the building built, takes the site block away and drops leftovers;
    - an open panel completes a building finished by hand;
    - an outsider can't open or break the site, and the owner breaking it cancels;
    - cancelling removes the site block;
    - the panel's progress and on-site counts; live refresh; the materials page, paging, Stock and Back;
    - demolishing a built building (its blocks gone, a block put inside it stays, no longer listed, its ground free again) and a site (its block and the floor gone, its stock dropped).
  - **`BuildingGameTests`:** `remaining` on a built building, `list` showing it built, and `cancel` refusing it.
  - **Manual:** the panel's look.
- **Notes:**
  - **Owner's design (while planning S-011):**
    - There's no builder's hut. A settlement has a pool of builders, hired at the town hall for now.
    - Builders are assigned to construction sites and set up shop there until the building is done, with materials pooled on site.
    - Builders always fetch materials themselves (couriers are on hold), later buying from shops.
  - **Core blocks, decided after a first version:** S-011 first put a generic "building core" block in every template. The owner questioned what a core is for in a building that does nothing. Settled:
    - a temporary construction site block for every building;
    - functional buildings (huts, the town hall, homes with a residence core, markets) have their own block as a lasting core (S-026);
    - plain buildings have no block once finished.
    - Per-building core blocks can't come from data anyway: blocks are only registered at startup.
  - **Buildings keep their footprint until they're removed,** even when broken by hand (owner's call after an in-game report; a "count only the blocks still there" rule was considered and dropped). This is like MineColonies, where a damaged building stays until its hut is removed.
    - One difference: MineColonies doesn't track decorations once built, while we track every building. So plain buildings need a way out: `building demolish` now, the build tool's demolish later (instant, or a builder taking it apart).
    - Demolish sabotage checks: not forgetting the building, not removing its blocks, and removing every block in the footprint each fail the demolish tests.
  - **The site spot is air in the template,** so the site block never stands where a block is wanted. `remaining` skips it, so it's neither "in the way" nor counted.
  - **The open panel checks once a second** whether the site is finished, by recomputing the remaining work. That's bounded by the 32k-block template cap, and only runs while someone is looking. Builders will complete sites themselves in S-025.
  - The old "Builder job" story is split into S-011, S-024, S-023 (Structurize spike), S-025 and S-026.
  - **Flakes:** GameTests ran 3 times in a row without flakes.
  - **Sabotage checks:**
    - not placing the site block fails most site tests;
    - breaking without cancelling fails the break test;
    - never completing fails the finish tests.
    - Removing the site block before marking the building built turned out harmless: the settlement forgets it and finishing writes it back. So that order isn't a hidden dependency.

### S-010 Building templates and sites
- **Status:** done | **Branch:** feature/building-sites | **PR:** #24 | **Depends on:** S-001
- **Goal:** a settlement can have a building commissioned at a site from a template.
- **Scope:**
  - **Templates:**
    - vanilla structure files at `data/<ns>/structure/<path>.nbt`, loaded through the level's structure manager;
    - a definition per building in the `kingdomrts:building` datapack registry (`data/<ns>/kingdomrts/building/<name>.json`: `structure` and `name`);
    - the sample is `kingdomrts:storage_shed`.
  - **Commission** (`BuildingOps.commission`, a faction operation): records a site (type, template corner, rotation, footprint) on the settlement. It's refused if:
    - it isn't in a settlement's claim, or it's another faction's claim;
    - it doesn't fit inside one claim, or not within build height;
    - it overlaps another building, or covers the town hall, a workplace or a storage block.
  - **Remaining work** (`BuildingOps.remaining`): blocks missing, blocks in the way, and the materials needed. It never loads chunks.
  - **Commands:** `/kingdomrts building commission <type> <pos> [<rotation>] [faction <name>]`, `list`, `remaining <pos>`, `cancel <pos>`, and `finish <pos>` (a cheat that builds the site instantly, for testing).
- **Out of scope:** the build tool UI and ghost preview (Direction), gold, damage repair, builders (S-011).
- **Test:**
  - **Unit tests:**
    - `BuildingRulesTest`: placing and turning, footprints, matching, remaining work, costs, unplaceable blocks;
    - `BlueprintTest`: reading templates, rejecting broken or huge ones, the building definition;
    - `BuildingTest`: a site survives saving.
  - **GameTests (`BuildingGameTests`, 8):**
    - commissioning, and refusals: outside any claim, someone else's, partly outside, over the hall, overlapping;
    - remaining work on a fresh site and after some blocks are placed by hand;
    - `finish` (with an obstruction cleared), a rotated building's door facing the new way, `cancel`, `list`.
  - **Manual:** the shed's look and its rotations, via `finish`.
- **Notes:**
  - **A block matches** if it's the same block facing the same way: facing, axis, half, hinge, attach face, rotation, slab type, rail shape and bed part must match (owner's call). Other properties are ignored (waterlogged, open, powered, leaf distance, stair shape, chest pairing), so opening a door doesn't make a building unfinished or, later, damaged.
  - **Rotation:** a building turns about its footprint's middle, so a square one stays over the same ground. `<pos>` is the template's corner before turning.
  - **Air in a template means "must be clear":** a solid block there is an obstruction, and so is grass or snow. Structure voids mean "don't care".
  - **Costs:** a door, bed or tall plant costs one item (on its lower half or foot), a double slab costs two, and a wall torch costs a torch. Blocks with no item (fire, fluids) are listed as unplaceable.
  - **Reading templates:** vanilla keeps a template's blocks private, so `Blueprint.read` parses the template's saved NBT (the public structure-file format). No access transformer is needed.
    - Blueprints are cached per structure and cleared on datapack reload.
    - Templates over 32k blocks are rejected.
    - All template access goes through `BuildingTemplates`, so Structurize (design.md) could replace it later.
  - **`finish`** sets blocks without shape updates while placing (so a door's lower half doesn't pop off before its upper half is there), then notifies neighbours. Block entity contents (a chest's items) aren't placed.
  - **The sample shed** was generated with a small script. To re-author it in-game:
    1. Build it.
    2. Save it with a structure block named `kingdomrts:buildings/storage_shed`.
    3. Copy the file from `run/world/generated/kingdomrts/structure/` into `src/main/resources/data/kingdomrts/structure/buildings/`.
  - **Test fix:** the local GameTest world (`run/world`) keeps factions between runs, so a command that looked a test faction up by name could find a leftover one with the same name. `TestSupport.faction` names now end with a per-run suffix. CI starts fresh, so it never showed up there.
  - **No stress run needed:** nothing per unit or per tick was added.
  - **Follow-ups:**
    - a workplace block in a template becomes a workplace when built;
    - an outline or preview of a site;
    - damage detection (built buildings compared with their template);
    - evaluating Structurize for the build tool.

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
