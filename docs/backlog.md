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

## Current: Settlements MVP

### S-004 Territory messages
- **Status:** in review | **Branch:** feature/territory-messages | **PR:** #9 | **Depends on:** S-001
- **Goal:** players can tell when they cross into or out of a settlement's claim.
- **Scope:** "Entering Oakhaven (Dev)" / "Leaving Oakhaven" in the action bar, checked only when a player changes chunk (no scanning).
- **Out of scope:** drawing claim borders in the world.
- **Test:**
  - GameTest (if a mock player works for this): moving a player across a claim edge produces one enter message and one leave message.
  - Manual: walk in and out of a claim and check how the action-bar text looks.
- **Notes:**
  - Uses NeoForge's `EntityEvent.EnteringSection`, which only fires when an entity enters a new 16×16×16 section. The handler skips height-only moves and compares the claim of the old chunk with the new one, so there's no per-player state and no scanning.
  - The message choice is a pure function, `TerritoryMessages.message`, covered by the unit tests in `TerritoryMessagesTest`. Moving straight from one claim into another says "Entering …".
  - GameTest `crossingClaimEdgeShowsMessages` uses a new `TestSupport.recordingPlayer`, a fake player that records its action-bar messages. It checks for exactly one "Entering" on the way in, nothing while moving inside the claim, and one "Leaving" on the way out. A sabotage check (messages disconnected) fails it.
  - Not covered: no message on login or respawn inside a claim; dimension changes are untested.

## Later (not yet stories)
- **Unit/occupation refactor:** a generic `Unit` entity with occupation as data, before a second occupation exists. Also adds units belonging to a settlement and the civilian transfer on capture.
- **Lumberjack job:** needs claims (S-001) and storage (S-003).
- **Builder job:** needs storage and a structure layout format. Decide whether the MVP builder takes from storage or uses the on-site builder + courier model (open in design.md).
- **Cleanup:** remove the NeoForge template leftovers (`example_block`, `example_item`, the example `Config` options and the example creative tab).
- **Capture UI later:** faction identity (colour/emblem/banner), a custom-drawn bar, town hall and flag models.
- **Parked ideas:** allied factions sum their presence at capture; player-set unit stances (hold vs pursue); a detection range separate from attack range (ranged mobs currently out-range units).

## Done

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
