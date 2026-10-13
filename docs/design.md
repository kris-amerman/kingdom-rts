# Kingdom RTS - design

The fuller design behind CLAUDE.md: goals, decisions, reasoning and open questions. CLAUDE.md holds only what constrains current work.

## Vision
A long-lived, real-time kingdom sim in Minecraft. You grow a small settlement into a nation and share the world with other factions, AI or player, that follow the same rules you do. Polytopia supplies the empire-building and strategy. The depth Polytopia lacks comes from persistence, real trade, and relationships between factions that go beyond conquest. AI-led factions are the main reason this is its own mod and not MineColonies.

## Tenets
- Faction parity: any faction, AI or player-led, has the same capabilities and rules. Rules act on factions and units, not players. AI is a controller on the one real simulation.
- If everything is makeable and the world is open, no rule makes trading raw materials strictly necessary without artificial scarcity. Most strategy games drive competition with unevenly distributed resources, so this mod does too.
- Autarky should be a losing strategy, or at least very hard to sustain.
- Resources are physical. No abstract counters.
- Prefer constraints from the world over arbitrary rules. MineColonies tried climate-limited crops, and players just grew whatever worked or got food elsewhere. A restriction only matters if there is no workaround.
- Settlement systems must be easier and more efficient than hand labor without trivializing the game. If they are slow or annoying, players bypass them (MineColonies' weak point). If they are too strong, the game inflates and gets boring. Balance is the core challenge.
- There should be several ways to do things, so play doesn't get repetitive.
- Real simulation, not faked. Measure before optimizing.

## Settlements and territory
- A player founds a settlement with a starter camp item (see Progression), whose build tool is used to build the town hall. Town halls are built only through the build tool, like every other building, so a player's settlement is claimed the moment it's founded. Placing a bare town hall block would bypass the claim, collision and space rules, and since the hall can't be broken, it could be used as an unbreakable wall. The town hall block is the core block inside the town hall structure, used to claim and manage the settlement, like MineColonies' hut blocks.
- Unclaimed settlements are only the naturally generated ones, which come later. They already have buildings, including a town hall, and claiming one doesn't spawn a starter camp.
- Every settlement starts with a small claim, and a town hall can't be built in another faction's territory. Until the build tool exists, the MVP places the town hall directly as a block, which starts unclaimed and is claimed by interacting with it. This is a temporary stand-in. A settlement exists once its town hall is built and claimed, given enough valid space (chunk count and any buffer from other settlements to be decided). Size doesn't matter.
- Territory is the union of a settlement's chunk claims. Claims must touch existing ones and can't overlap another faction's.
- There is no world border or fixed play area pushing factions together. It was considered and dropped as anti-Minecraft. AI faction emergence handles proximity instead.
- Claim expansion is open, and the MVP has only the starting claim. Ideas so far: (1) the border grows on its own with population and buildings, free, like Civ; (2) free but gated claiming, limited by capacity, contiguity and maybe a maximum distance from the hall, with time as the cost; (3) buildings extend the border by their own chunk. Avoid the MineColonies pain of building something just to claim land, which scatters outposts. A goods cost for claiming is doubtful, because it is an arbitrary rule.
- Storage containers belonging to a settlement can't be opened or broken by other factions until capture, so nothing can be looted beforehand. Allied access may come later. Capture hands the containers to the new owner.
- On capture, civilians (units without a military occupation) pass to the new owner. Military units keep their faction and stay hostile. Naturally generated settlements may start with civilians later; for now they have no units.

## Progression
**Starting out.** A player crafts or finds a starter camp item and places it, similar to MineColonies' supply camp (there's no supply ship for now). The camp comes with 5 beds, some starting gold, a builder (like Polytopia's starter unit, with a quirky line such as "it was cramped in there"), and a tool, something like a wand, for choosing where the town hall goes. The tool will be very important but hasn't been designed yet. Starting gold works like Polytopia's stars: just enough to get things running, with no debt modeling for now.

**First buildings.** The player commissions the builder to build each one:
- Wood: a lumber hut.
- Wood crafts: a woodworker's hut, making wood tools, decorative wood blocks, and anything crafted from just wood.
- Food: a hunter's hut or a small garden with a gardener.
- Ore and stone: a mining hut.

**The first economy.** Workers buy what they need from each other:
- The lumberjack buys axes from the woodworker, who buys logs from the lumberjack.
- The builder buys lumber from the lumberjack and parts from the woodworker.
- The gardener buys hoes, the hunter buys swords, and the miner buys pickaxes, all from the woodworker.
- Units buy coal from the miner or wood from the lumberjack to cook food from the garden or hunter.

Reaching this loop is the first achievement: a basic settlement economy.

**After that.** The player can build whatever they want and follow supply and demand. Likely next steps: residences (happiness is tied to accommodation; early ones can be basic tents or huts), scaling production (mines, lumber huts, farms, hunters, fishers), building a food and resource surplus, and basic protection by hiring a mercenary or building a guard outpost.

Open: the starter camp recipe and loot. The tool is covered under Build tools, and gold under Money and commissions.

## Build tools
The build tool is the core UX for shaping a settlement, and it will work very much like MineColonies' build tool. Structurize, the library behind MineColonies' tool, is the likely base if its license and design fit (to verify: license, NeoForge 1.21.1 support, whether its operations can run without its GUI, and whether it counts as an acceptable library dependency).

- **Placing buildings.** Pick a building, style and level from a catalog, see a ghost preview in the world, then move, rotate, mirror and adjust its height. You can step away and walk around the preview before confirming. Confirming the placement is the commission, with no separate "build building" step. Commissioning costs gold, not a crafted hut block. The builder lives on site until the job is finished, and a courier delivers the materials there. Placement is only allowed inside your own claim and can't collide with existing buildings. Builders clear and level the site.
- **Parity.** The tools are a front end over faction operations: commission a building here, demolish this, zone this lot, lay this road, terraform this, claim this. AI factions use the same operations under the same rules (own claim only, no collisions, same costs). How an AI decides where to build is its own business: a predetermined layout, zoning, or its own judgment. It doesn't imitate the player's preview flow. The details wait until AI factions are designed.
- **Styles and packs.** Buildings, styles and levels are data, so the community can ship building packs. Styles give AI factions a visual identity. Packs are also how custom Create contraptions and factories would arrive later.
- **Demolish tool.** Instantly destroys one of your own buildings with a TNT-style animation, with no builder involved. It's for city planning, not strategy, so it never works on another faction's buildings. It leaves some rubble and part of the materials in barrels, never everything. Units who lived or worked there become homeless or jobless. It probably costs some gold (to be tuned).
- **Zoning tool.** Zone lots as residential, commercial, industrial, agricultural, municipal or defensive, like Cities: Skylines. Buildings could then emerge in zoned lots based on supply and demand, so the player doesn't have to place every building by hand. This may be how automatic commissioning works, with a setting to turn it off for players who want to hand-design. Units pay for private zoned buildings, and the treasury pays for municipal and defensive ones (see Money and commissions).
- **Infrastructure tool.** Drag walls, roads, bridges and retaining walls, and terraform, with automatic curves and smooth shaping. The player can also terraform directly, probably for some gold (to be tuned). Roads are cosmetic for now, and might later give faster movement or help unit pathing. Zoning doesn't require roads, unless requiring them turns out to make zoning much simpler to build.
- **Claim management.** Possibly part of the tool if it becomes the main UI for managing a faction, otherwise a separate UI.

Open: how zoning reads supply and demand. Exact costs for demolition, infrastructure and terraforming. Whether players can scan their own builds into templates, which would keep "hand-built structures aren't buildings" while giving back creative freedom.

## Money and commissions
This is modeled on how it works in real life and in Cities: Skylines: the state pays for public works and earns from private ones, and private buildings are paid for by whoever will use them.

- **Gold is physical.** It's coin kept in a faction treasury at the town hall, so capturing a settlement means seizing its treasury. New coin enters through minting gold ore, which gives gold ore a real use. Gold still feels like Polytopia's stars: small amounts, just enough to get things going, with no debt for now.
- **Contract model.** A commission pays the builder. The builder buys materials from producers (lumber from the lumberjack, parts from the woodworker), and they're delivered to the site by a courier, who may charge. The builder keeps the rest as a wage. Gold moves through the settlement instead of disappearing. Paying in full at commission is the simplest version. Staged payments or refunds on cancel can come later.
- **Who pays.** The treasury pays for buildings the player places by hand and for public ones (municipal, defensive). In zoned residential, commercial, industrial and agricultural lots, the units who will live or work there pay from their own savings, once there's demand and they can afford it. A settlement grows only as fast as its people can afford, which is a natural limit. The state can subsidize a lot to speed it up.
- **Income.** A small tax on every sale between units, which grows with the economy and needs no busywork. A permit fee when a zoned lot gets built. Trade with other factions. Possibly rent, since the faction owns the claimed land and could lease lots. Tariffs and tribute could come later with relationships. A higher tax rate brings in more gold but lowers happiness, which ties into revolts.
- **The loop.** Treasury spending becomes wages. Wages become purchases and savings. Savings fund private buildings. Taxes and fees return part of it to the treasury.
- **Getting started.** A new settlement's units have no savings, so zoned lots stay empty at first. The player hand-places the first buildings from starting gold, the wages give units money, and zoning becomes useful once there's an economy to fund it.

### How money flows
All numbers below are illustrative.

**Where gold sits.**
- The faction treasury, at the town hall.
- Each unit's purse.
- Goods themselves: what producers hold in stock is wealth that hasn't been sold yet.

**How gold enters the faction.**
- Starting gold from the starter camp.
- The mint, turning gold ore into coin.
- Selling goods to other factions.

**How gold leaves the faction.**
- Buying goods from other factions.
- Tribute.
- Demolition and terraforming fees (these could destroy the coin rather than paying a unit, so they work as sinks).
- Coins lost when a settlement is captured.

**How gold moves inside the faction.**
- Treasury to units: commissions for hand-placed and public buildings. Possibly wages for units who work for the state, such as guards and couriers on public jobs, and subsidies for zoned lots.
- Unit to unit: purchases of tools, materials and food, a builder paying producers and the courier, and private commissions in zoned lots.
- Units to treasury: the sales tax on each trade between units, permit fees when a zoned lot gets built, and possibly rent on leased lots.

**A worked example: the first lumber hut.**
1. The player commissions a lumber hut for 20 gold. The treasury goes from 50 to 30, and the builder's purse goes to 20.
2. The builder buys 10 gold of logs from the lumberjack and 4 gold of parts from the woodworker. The courier charges 1 gold to deliver them. A 10% sales tax returns about 1.5 gold to the treasury.
3. The builder builds and keeps the last 5 as a wage.
4. The builder spends wages on food from the gardener. The lumberjack spends on an axe from the woodworker. The woodworker spends on logs from the lumberjack. Each sale pays a little tax.
5. In the end the treasury has spent 20 and recovered a few coins in tax, and the remaining 18 or so gold is spread across unit purses. Some goes on daily needs, and some is saved toward a private building in a zoned lot.

**Why the loop holds together.** Every unit's income is someone else's purchase, and the treasury takes a small cut of each trade. While units keep spending, gold circulates and the treasury slowly refills. What the treasury lacks has to come from outside: minting, or selling to other factions. That is where trade and scarce materials create pressure.

**Balance risks to watch.**
- **Hoarding:** if units save everything, circulation stops and tax income stalls. Daily needs such as food, rent and upkeep keep money moving.
- **Inflation:** if the mint is too cheap, coin floods in and prices stop meaning anything. Minting should be limited by gold ore, which is geography, plus labor.
- **Deflation:** if sinks outpace sources, nothing gets built. Sinks should stay small and optional.
- **Treasury lock:** a treasury can run dry with no buildings that earn. Starting gold, a minimum tax income, or early foreign trade should always offer a way out.
- **Prices:** goods need a price. The simplest version is a fixed base price per good set in data. The fuller version lets prices respond to stock, which faction-to-faction trade already assumes.

Open: whether state workers draw a wage from the treasury, what the daily needs are (food, rent, upkeep), how prices are set inside a settlement, and whether sinks destroy coin or pass it to units.

## Factions and relationships
- A faction can be led by a player or by AI. Its members are its players and its units. Hostility is a war state between two factions.
- Relationships are modeled on Polytopia's tribe relations:
    - Two factions are at war, neutral, or at peace. Peace is a treaty one side offers and the other accepts. During peace, hostile actions aren't allowed. Breaking a treaty leaves a permanent negative opinion. (In Polytopia war lapses back to neutral when nobody attacks for a turn; here war is declared.)
    - Each AI faction has a mood toward every other faction, from horrible to great. Mood sets how aggressive it is and how likely it is to accept peace. Players can see it, and Polytopia shows extremes visually (flames for horrible, a halo for great).
    - Mood comes from opinions, each tied to something observable and shown with its reason. Positive: shares friends or enemies (Polytopia's "wise"), hasn't attacked recently ("peaceful"), has an embassy or similar tie ("diplomatic"), has more units ("powerful"), is at war with the strongest other faction ("brave"). Negative: recently attacked ("violent"), has military near or in its territory ("threatening"), shows fewer units than it has ("weak"), made peace with its enemy, fought its ally or broke a treaty ("foolish"), has borders touching its own ("intrusive"), is close to winning ("dominating"). A faction it knows nothing about has no opinions yet.
    - Polytopia's embassies are buildings in another tribe's capital that pay both sides each turn, more during peace, and are destroyed by an attack. A trade post or envoy could play the same role here.
- Weak factions invite attack, so allies, tribute and defenses matter. Prestige goods make natural gifts and tribute.

## AI factions and pacing
- The proximity problem: in an open world with no border, factions could spread out and never meet, so there would be no competition, trade or diplomacy. Instead of forcing a border, AI factions appear near players. They show up gradually near where a player has settled, once that settlement passes a development threshold, up to a configurable cap, so players always have neighbors to trade with, ally with or fight. Ideas: rumors before an arrival, an in-world reason (migrants or a splinter group), and placing them where they hold materials the player lacks.
- An AI faction starts the same way a player's settlement does, from a starter camp, and grows by the same rules. AI factions aren't created at world generation. Commands create and delete them, and deleting removes the leader and units but not structures.
- Growth limits come from food, housing, space, defense burden, travel distance and unrest, so size and count stay manageable.
- Performance and smarter units: units should seem smart while staying cheap. Jobs are explicit steps, a stuck unit notices and recovers, thinking is spread across ticks instead of every tick, pathfinding is budgeted, and shared state lives at the settlement level. How to handle many simulated settlements at once (a cap, or something else) is open. Clustering factions near players shares loaded chunks. Parked ideas: fast-forward tasks, bodiless units, detail levels for distant settlements, region-graph pathing, a dormant mode.

## Economy
**Why materials are uneven.** In an open world where everything is makeable, nothing makes material trade necessary without artificial scarcity. Games get hard necessity from unevenly distributed natural endowments, so a few custom materials are spread unevenly and needed by every faction. Other sources of dependence: exclusion (territory closed to others without a treaty or war), security, competition (AI factions trade with each other, so a faction that doesn't falls behind), and sunk specialization.

**Categories.** Sustenance (vanilla food, universal), Sustainment (ongoing upkeep), Capability (unlocks tiers of buildings and units, including military), Prestige (keeps larger settlements happy). What a shortage does is open. It could be severe enough to kill a settlement, to push factions toward trade or capture. Hand-mining is allowed, and geography is what creates the need. Custom materials must not be sustainable with vanilla automation (no bone meal, kelp or fish).

| Category | Candidates | Notes |
|---|---|---|
| Sustainment | Medicinal herb, fertilizer (from nitrate) | Herb: disease pressure scales with settlement size, and medicine prevents outbreaks and heals wounded soldiers faster. Fertilizer: settlement farms keep a safe baseline yield, and fertilizer lets them run intensively and support more people. Fertility declines gradually if supply stops. Worker-run farms only. |
| Capability | Tin, saltpeter | Tin gates the Bronze Age tier. Nitrate beds (white crusts, finite) are processed into fertilizer or saltpeter, a farm-versus-war choice. |
| Prestige | Tea or coffee (small worker productivity bonus), spice, silk plus rare dye | Consumed by civic buildings. Happiness could depend on the variety of goods. Demand scales with population and tier. Silk plus dye is a two-input textile chain. |

Suggested first set: medicinal herb, tin, and one prestige good. Uncertain: ironwood, which was meant for custom ship and siege entities, an approach we've moved away from. Considered and not picked: pitch or tar, wine, lime or mortar, fodder, guano, amber, sea pearls, star-iron, live animals.

**Monuments.** Late-game structures that need large amounts of several rare goods and a long build time. They answer what keeps a mature nation interested.

**Materials in the world.** Light additive worldgen: seed-based regions (large, independent of vanilla biomes) plus per-material features such as deposits and wild plants. No terrain or biome changes. Seed-deterministic, so a seed preview can show them. To verify: compatibility with terrain overhaul mods, features only appearing in newly generated chunks (an existing world would need a command to seed them), custom growth rules, NeoForge hooks.

**Specialization.** Permanent faction-level branch choices at development thresholds, like Polytopia's city level-up rewards. They apply to the whole faction, not a settlement, so a big faction can't cover everything. AI factions choose through the same system, which gives them different roles. Show what a branch gives up before committing.

**Trade.** Moves real goods, so a faction can only sell what it holds, and prices respond to stock. AI factions trade with each other too. AI factions could act as market makers with standing offers and needs, so supply isn't left to chance.

## Workplaces and supply
**Workplaces.** Every settlement job works from a workplace building of its kind, as in MineColonies: a lumberjack works from a lumberjack hut. You build the workplace first, then hire into it. A workplace can employ several workers (unlike MineColonies' usual one), perhaps more at higher building levels. The workplace holds its workers' stock (what they produce, and their tools), and its panel holds the job's settings: work area, replanting, which wood to fell, and so on. Workplaces come from the build tool like every building; until it exists they're placed directly. Military units are exempt for now. Barracks or guard towers may come later. Huts hire automatically by default: unemployed settlers fill their openings, nearest hut first, and a player can switch a hut to manual hiring. Worker skills and matching units to jobs that suit them, as in MineColonies, are deferred.

**Work areas.** A worker stays within an area around its workplace (and inside the claim), configurable from the workplace panel, so it doesn't walk across the city for one tree. A toggle on the panel or the build tool could show the area's edges.

**Supply (open).** Each workplace type has a base demand defined in data, its wanted stock (a lumberjack hut: one axe in use and a couple of spares). In MineColonies, couriers deliver what workers request, and with few couriers everything waits on them: workers spend much of their time idle and the player steps in to help. Ideas to avoid that:
- Workers fill gaps themselves, trying the cheapest source first: their own workplace's stock, then the nearest settlement storage, then other workplaces' surplus offered for sale (each workplace a small shop), paying in gold once money exists.
- A market building where producers drop surplus and anyone buys, so buyers walk to one place instead of many.
- Couriers as an optional job that moves bulk surplus to markets or storage ahead of demand. They make things faster but never block work.
- Shortages shown as visible demand (on the workplace panel, perhaps a settlement overview), so the player can see what's missing and why.

## Units
- **Population: wanderers (open).** A settlement grows as new units arrive now and then as wanderers: unemployed and unhoused, they then find jobs through automatic hiring, and homes once housing exists. It's a slow trickle whose rate scales with the settlement's capacity (housing, jobs, perhaps happiness and attractiveness). The formula is open; Cities: Skylines' demand for residents and workers is one place to look. Buildings such as an Inn draw travellers more often, a way to speed up growth. Citizens have names, health, hunger and an inventory, and are vulnerable to hostile mobs and units.
- Units, occupations, buildings and materials are data, so the community and modpacks can add their own.
- Special units come from vanilla capabilities: a unit that converts others to its faction, a cloaked unit, archers with special bows and enchantment huts, mounted knights modeled as one unit on horseback.
- The faction skin set is accepted and not yet wired in.

## War and blocks
- Why blocks aren't protected: with unbreakable walls, a defender could box in the capture point and never be captured. Settlements are mostly builder-made, so losing blocks isn't losing creative work. War carries a destructive cost, which gives a reason to avoid it. Hard permission systems also leak (explosions, pistons, fluids, other mods).
- For the MVP, anyone can place and break blocks except the town hall and settlement storage. The rest of this section is for much later.
- Damaging another faction's claimed territory at peace could build toward a threshold that starts a war, with a warning partway. Block values come from a few tiers (a torch barely counts, a diamond block counts a lot), unlisted blocks including other mods' count as ordinary, the build-up fades over time and is counted per faction, and at war there is no extra penalty. Open-border treaties or alliances could allow access.
- A builder would repair a damaged building back to its template when told to, maybe with an auto-repair option. Templates could mark critical blocks (the workstation), and a building could stop working when those or too much of it are missing.
- AI military units will need to be able to break through walls (parity). Declaring war may need a cost or warning.

## Create (add-on, later)
- The goal: units operating Create contraptions, Create-built factories feeding settlements, and eventually cannons, ground vehicles, ships and aircraft. Start with the basic non-Create versions of each.
- Units don't operate machines the way players do. A station block exposes simple commands (forward, left, right, fire, load) as redstone signals and reads status back (heading, speed, ammo, damage). Designers wire it into their machine, and units ride seats and board only while docked. The station checks itself when the machine is commissioned. Direct per-mod adapters cover what signals can't, such as aiming a cannon mount. Station blocks could come from an Engineering workshop tied to a branch and a capability material.
- Aiming: community tools already solve cannon ballistics (angles from muzzle speed, gravity and drag, leading moving targets, holding fire until on target, correcting as it goes), and Create Big Cannons mounts accept target angles. Ours adds crew skill as error, fires only at hostile factions, and stands down when its data is stale. Scope grows from fixed batteries to ground vehicles, then ships and aircraft.
- To check with quick experiments: whether Aeronautics bodies and Big Cannons aiming can be driven by non-players. Check licenses before reusing any code.

Sources: [CC:CBC](https://modrinth.com/mod/cc-cbc), [Create Big Cannons: Ballistic Computer](https://www.modpackindex.com/mod/178434/create-big-cannons-ballistic-computer), [auto-targeting add-on](https://modrinth.com/project/NvmY9lVQ), [CCTurret](https://github.com/ColbyJack1134/CCTurret).

## Open questions
- The starter camp recipe and loot. Build tool details and whether Structurize fits (see Build tools).
- Money details: tax rate, permit fees, whether lots are leased, payment timing, subsidies, how the mint works, state wages, daily needs, prices inside a settlement, and sinks (see How money flows).
- How severe material shortages are.
- Workplaces: how many workers each can employ (building level?), and how worker supply works (markets, shops, couriers; see Workplaces and supply).
- Population: how fast wanderers arrive and what capacity means (housing, jobs, happiness?); see Units.
- Surrender: does it end hostility, transfer units, settle the war? Military units stay hostile after capture until then.
- How claims expand. What naturally generated settlements look like (later).
- What happens when a faction damages another's territory at peace, and how repairs are triggered.
- How revolt ends (for example the settlement flips unclaimed).
- Branch details, and faction elimination.
- Whether the player sees AI-to-AI opinions.
- Regional material generation (to verify).
- Whether AI factions field Create machines at first.
- How new AI settlements pick sites.
- The name (faction or nation, Kingdom RTS).