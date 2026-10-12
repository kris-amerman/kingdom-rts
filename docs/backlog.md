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

### S-013 Unit info panel
- **Status:** in review | **Branch:** feature/unit-info-panel | **PR:** - | **Depends on:** S-008
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

### S-009 Lumberjack job
- **Status:** planned | **Branch:** feature/lumberjack | **PR:** - | **Depends on:** S-007, S-008
- **Goal:** a lumberjack unit fells trees in its settlement's claim and stores the logs in settlement storage, without supervision.
- **Scope:**
  - **Job steps:** a small framework that runs explicit steps (CLAUDE.md), with a timeout per step and recovery (give up, pick again, back off). The lumberjack's steps: find a tree in the claim, walk to it, fell the whole tree, collect the drops, walk to storage, deposit, repeat.
  - **Tree search:** spread across ticks over the claimed chunks, with no per-tick world scan.
  - **Storage:** units of the owning faction can use settlement storage (left for later in S-003).
  - **Data:** a `lumberjack` occupation type, with tuning such as search radius and chop time.
- **Out of scope:** replanting (maybe later), tools wearing out, payment and gold.
- **Test:**
  - Unit tests: the step and recovery logic as pure functions where possible, and tree-shape detection.
  - GameTests: fells a tree and its logs end up in storage; recovers when its tree disappears mid-walk; idles without stalling or crashing when there's no storage or no tree.
  - Manual: watch one work.
- **Notes:** may split in two (job framework, then lumberjack) if it gets big.

### S-010 Building templates and sites
- **Status:** planned | **Branch:** feature/building-sites | **PR:** - | **Depends on:** S-001
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
- **Status:** planned | **Branch:** feature/builder | **PR:** - | **Depends on:** S-009, S-010
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
- **Status:** planned | **Branch:** feature/unit-needs | **PR:** - | **Depends on:** S-009, S-011
- **Goal:** units have hunger and happiness, drawn from real things in the world.
- **Scope:** first a short design note in docs/design.md, then the simplest version (likely hunger from food in settlement storage, happiness from housing).
- **Out of scope:** revolts, taxes.
- **Test:** decided with the design.
- **Notes:** deliberately placed after the jobs. With no food production, housing or economy yet, needs would have to be abstract counters, against the "constraints from the world" principle.

## Later (not yet stories)
- **Capture UI later:** faction identity (colour/emblem/banner), a custom-drawn bar, town hall and flag models.
- **Parked ideas:** allied factions sum their presence at capture; player-set unit stances (hold vs pursue); a detection range separate from attack range (ranged mobs currently out-range units).

## Done

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
