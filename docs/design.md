# Kingdom RTS - design

The fuller design behind CLAUDE.md: goals, decisions, reasoning and open questions. CLAUDE.md holds only what constrains current work.

## Vision
A long-lived, real-time kingdom sim in Minecraft. You grow a small settlement into a nation and share the world with other factions, AI or player, that follow the same rules you do. Polytopia supplies the empire-building and strategy. The depth Polytopia lacks comes from persistence, real trade, and relationships between factions that go beyond conquest. AI-led factions that play by the same rules as players are what most sets it apart.

## Tenets
- Faction parity: any faction, AI or player-led, has the same capabilities and rules. Rules act on factions and units, not players. AI is a controller on the one real simulation.
- If everything is makeable and the world is open, no rule makes trading raw materials strictly necessary without artificial scarcity. Most strategy games drive competition with unevenly distributed resources, so this mod does too.
- Autarky should be a losing strategy, or at least very hard to sustain.
- Resources are physical. No abstract counters.
- Prefer constraints from the world over arbitrary rules. A restriction only matters if there is no workaround: limits players can simply route around (grow something else, get it elsewhere) don't constrain anything.
- Settlement systems must be easier and more efficient than hand labor without trivializing the game. If they are slow or annoying, players bypass them and do the work by hand. If they are too strong, the game inflates and gets boring. Balance is the core challenge.
- There should be several ways to do things, so play doesn't get repetitive.
- Real simulation, not faked. Measure before optimizing.

## Settlements and territory
- A player founds a settlement with a starter camp item (see Progression), and builds the town hall through the nation management interface. Town halls are built only through the interface, like every other building, so a player's settlement is claimed the moment it's founded. Placing a bare town hall block would bypass the claim, collision and space rules, and since the hall can't be broken, it could be used as an unbreakable wall. The town hall block is the core block inside the town hall structure, used to claim and manage the settlement.
- Unclaimed settlements are only the naturally generated ones, which come later. They already have buildings, including a town hall, and claiming one doesn't spawn a starter camp.
- Every settlement starts with a small claim, and a town hall can't be built in another faction's territory. Until the interface can build town halls, the MVP places the town hall directly as a block, which starts unclaimed and is claimed by interacting with it. This is a temporary stand-in. A settlement exists once its town hall is built and claimed, given enough valid space (chunk count and any buffer from other settlements to be decided). Size doesn't matter.
- Territory is the union of a settlement's chunk claims. Claims must touch existing ones and can't overlap another faction's.
- There is no world border or fixed play area pushing factions together. It was considered and dropped as anti-Minecraft. AI faction emergence handles proximity instead.
- Claim expansion is open, and the MVP has only the starting claim. Ideas so far: (1) the border grows on its own with population and buildings, free, like Civ; (2) free but gated claiming, limited by capacity, contiguity and maybe a maximum distance from the hall, with time as the cost; (3) buildings extend the border by their own chunk. Avoid making players build something just to claim land, which scatters outposts. A goods cost for claiming is doubtful, because it is an arbitrary rule.
- Storage containers belonging to a settlement can't be opened or broken by other factions until capture, so nothing can be looted beforehand. Allied access may come later. Capture hands the containers to the new owner.
- On capture, civilians (units without a military occupation) pass to the new owner. Military units keep their faction and stay hostile. Naturally generated settlements may start with civilians later; for now they have no units.

## Progression
**Starting out.** A player crafts or finds a starter camp item and places it, and a small camp unfolds: 5 beds, some starting gold, and a builder (like Polytopia's starter unit, with a quirky line such as "it was cramped in there"). The player then chooses where the town hall goes in the nation management interface. Starting gold works like Polytopia's stars: just enough to get things running, with no debt modeling for now.

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

Open: the starter camp recipe and loot. The interface is covered under Nation management interface, and gold under Money and commissions.

## Nation management interface
The player's main way of shaping and running their nation (the name is to be decided). Most of it isn't designed yet; it grows from a small first version.

- **How it's reached (open).** For now a rebindable hotkey opens it (N, for nation), with a command that opens the same thing; there's no item to craft or carry. Later there may be a command view, or a free camera above the world for directing units and building, as in strategy games. So its actions never depend on how they're triggered: the screens send requests to the server, which runs the same faction operations as the commands.
- **The first version.** Preview a building from the catalog in the world, move and rotate it, and confirm to commission it (the same faction operation as the command, so every claim and overlap rule applies). Demolish your own buildings. Toggle a view of settlement borders.
- **Placing buildings, later.** Pick a building, style and level from the catalog, see it as a ghost in the world, move, rotate, mirror and adjust its height, walk around it before confirming. Confirming is the commission, with no separate "build building" step. Commissioning costs gold, not a crafted block. The builder lives on site until the job is finished and fetches the materials itself (see Builders and construction sites; couriers are on hold). Placement is only allowed inside your own claim and can't collide with existing buildings. Builders clear and level the site.
- **Our own, not Structurize (decided after S-023).** Structurize, the library behind MineColonies' build tool, was evaluated in a spike (docs/structurize.md, kept as the record). We won't use it, or BlockUI or Domum Ornamentum, because this mod stays fully distinct from MineColonies and its libraries (see Influences). The spike's other findings point the same way: two more required mods, one of them decorative content, and 1.21.1 builds that are all beta or snapshot and must be version-matched. Templates, placement, the preview and the interface stay our own.
- **Parity.** The interface is a front end over faction operations: commission a building here, demolish this, zone this lot, lay this road, terraform this, claim this. AI factions use the same operations under the same rules (own claim only, no collisions, same costs). How an AI decides where to build is its own business: a predetermined layout, zoning, or its own judgment. It doesn't imitate the player's preview flow. The details wait until AI factions are designed.
- **Styles and packs.** Buildings, styles and levels are data, so the community can ship building packs. Styles give AI factions a visual identity. Packs are also how custom Create contraptions and factories would arrive later.
- **Demolish.** All construction and demolition goes through builders. Ordering a demolition assigns builders, who take the building apart block by block, top down, and carry every material back to settlement storage, in as many trips as it takes. A block that still matches the template gives back what placing it cost. Containers that are part of the building are emptied first, with builders coming back for whatever they can't carry. Blocks that aren't the template's are left standing. When there's nowhere to put something, they hold onto it until there is. The settlement keeps the building until the last block is gone, then forgets it, so its ground is free again (a building otherwise keeps its footprint even when damaged, until repaired or demolished). It's for city planning, not strategy, so it never works on another faction's buildings. Units who lived or worked there become homeless or jobless. It may cost some gold (to be tuned).
- **Zoning.** Zone lots as residential, commercial, industrial, agricultural, municipal or defensive, like Cities: Skylines. Buildings could then emerge in zoned lots based on supply and demand, so the player doesn't have to place every building by hand. This may be how automatic commissioning works, with a setting to turn it off for players who want to hand-design. Units pay for private zoned buildings, and the treasury pays for municipal and defensive ones (see Money and commissions).
- **Infrastructure.** Drag walls, roads, bridges and retaining walls, and terraform, with automatic curves and smooth shaping. The player can also terraform directly, probably for some gold (to be tuned). Roads are cosmetic for now, and might later give faster movement or help unit pathing. Zoning doesn't require roads, unless requiring them turns out to make zoning much simpler to build.
- **Claim management.** Part of the interface, as the main way of managing a nation.

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
**Workplaces.** Every settlement job works from a workplace building of its kind: a lumberjack works from a lumberjack hut. You build the workplace first, then hire into it. A workplace can employ several workers, perhaps more at higher building levels. The workplace holds its workers' stock (what they produce, and their tools), and its panel holds the job's settings: work area, replanting, which wood to fell, and so on. Workplaces come from the nation management interface like every building; until then they're placed directly. Military units are exempt for now. Barracks or guard towers may come later. Huts hire automatically by default: unemployed settlers fill their openings, nearest hut first, and a player can switch a hut to manual hiring. Worker skills and matching units to jobs that suit them are deferred.

**Work areas.** A worker stays within an area around its workplace (and inside the claim), configurable from the workplace panel, so it doesn't walk across the city for one tree. A toggle on the panel or in the interface could show the area's edges.

**Supply (open).** Each workplace type has a base demand defined in data, its wanted stock (a lumberjack hut: one axe in use and a couple of spares). When delivery depends on a few couriers, everything waits on them: workers sit idle and the player steps in to help. Ideas to avoid that:
- Workers fill gaps themselves, trying the cheapest source first: their own workplace's stock, then the nearest settlement storage, then other workplaces' surplus offered for sale (each workplace a small shop), paying in gold once money exists.
- A market building where producers drop surplus and anyone buys, so buyers walk to one place instead of many.
- Couriers as an optional job that moves bulk surplus to markets or storage ahead of demand. They make things faster but never block work.
- Shortages shown as visible demand (on the workplace panel, perhaps a settlement overview), so the player can see what's missing and why.

## Builders and construction sites
Decided while planning S-011:
- **A builders' hut, not the town hall (S-043).** Builders were first hired at the town hall with no workplace. Owner, 2026-10-20: they're hired at a builders' hut now, a workplace where they keep their tools and stock and go back to between sites (see Builders as a business below). The town hall has nothing to do with builders any more; builders hired there before huts lose the job when they next load, and can be hired at a hut.
- **Assigned to sites.** Each builder is assigned to a construction site, from the site's panel (or later the settlement menu), and sets up shop there until it's done. Then it goes back to the pool. How many builders a site can take is open.
- **The construction site block.** It goes down inside a building when it's commissioned, on a spot the building leaves clear. While the building is built it's the site panel (progress, what's in the way, materials still needed and how many are on site) and the site's stock. Once the building is finished, its builders carry what's left in its stock (spare materials, what clearing broke, tools) to settlement storage, and then the block is taken away (S-040).
- **Core blocks only where a building does something.** Huts, the town hall, homes (with a residence core) and markets have their own block in code, which manages them once built: a hut's block shows its hut panel, a home's its residents. Those blocks are part of their templates. Buildings that are only blocks (a shed, decorations) have nothing once finished. A generic core for every building was considered and dropped: it would be a meaningless block in every shed, and blocks can't be registered from data, so per-building cores can only exist for buildings with code behind them anyway. Repair and demolition reach plain buildings through the nation management interface.
- **Materials pooled on site.** Builders build from the site's stock, so they never commute to a hut. Builders fetch what's missing themselves (couriers are on hold). For now that's from settlement storage. Later they'll buy from shops (other workplaces' surplus), which creates demand and rewards a central market.
- **Recommended lifecycle (S-025):**
  - Fetch as needed, in batches, not everything up front. A builder carries only 9 stacks, so gathering everything first still takes many trips, builds nothing in the meantime, and hoards stock other sites may need.
  - At most one builder per site fetches at a time while the others keep building. A lone builder alternates.
  - A fetch takes what's missing and not yet on site, in build order, as much as it can carry.
  - Whatever can't be found is skipped: builders build what they can and wait when nothing is buildable.
- **Demand.** A site's outstanding materials are visible demand: the player sees what's missing, and markets can read it later.
- **Builders as a business (direction, open; owner, 2026-10-20).** The pool at the town hall and fetching from settlement storage are where we are now, not where we're going. In a working economy, builders run a construction business:
  - **A place of business:** a builders' hut (decided, S-043; the name is open, perhaps a builders' guild) where builders are employed, keep their tools and hold some stock, instead of the town hall.
  - **Buying, not taking:** they buy what a commission needs from other workers' shops (their huts' surplus) or a market, paid from the commission (see Money and commissions), instead of taking it from settlement storage.
  - **Base demand:** like any workplace (see Workplaces and supply), a tool kit they can afford, bought as the business's capital, and perhaps a small stock of common materials. Buying to order for each commission fits the contract model best; keeping stock is a way to be faster, at the cost of tying up gold.
  - **Competition:** perhaps several builder businesses in a settlement, with different prices, speed or quality, and the player (or AI) choosing who gets a commission. Not only for builders.
  - **For the code now:** where a builder takes things from and puts things back (today the nearest settlement storage) stays in one place, so it can become the builder's hut, a shop or a market without rewriting the jobs. Fetching, clearing out, demolition (S-038) and tools (S-042) all go through it.

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

## Influences
Every design here is our own, shaped by this mod's needs, but plenty of ideas come from games and mods we admire. Where an idea clearly comes from somewhere, it's credited here (and in the README).

- **Polytopia:** the empire-building and strategy at the core; starting money that works like its stars (just enough to get going, no debt); the starter unit; relationships between factions (war, peace treaties, a lasting mark for breaking one) and AI moods built from visible opinions.
- **MineColonies:** settlement workers employed from buildings of their trade; building from templates with a preview in the world; a block that manages each building; starting from a placeable camp. Lessons we took from playing it: systems that are slow or tedious, especially supply that waits on a few couriers, push players to do the work by hand; limits players can route around (like climate-limited crops) don't constrain anything; and needing a building just to claim land scatters outposts.
- **Civilization:** borders that grow with a settlement (one of the claim-expansion ideas).
- **Cities: Skylines:** zoning, and growth that follows demand for residents and workers.
- **Crusader Kings, Anno, Manor Lords:** general inspiration for relationships, production and settlement life; specific credits get added here as designs draw on them.
- **Create:** the planned add-on for contraptions, factories and machines (see Create).

## Open questions
- The starter camp recipe and loot. The interface's name, and how it's reached later (a command view, a free camera); see Nation management interface.
- Money details: tax rate, permit fees, whether lots are leased, payment timing, subsidies, how the mint works, state wages, daily needs, prices inside a settlement, and sinks (see How money flows).
- How severe material shortages are.
- How many builders a construction site can take. There should be a cap, but the owner sees it coming from some level (perhaps a builders' guild's), not a fixed number; for now it's `max_builders` in building data, 2 by default. Also whether couriers ever come back to help builders (see Builders and construction sites).
- Workplaces: how many workers each can employ (building level?), and how worker supply works (markets, shops, couriers; see Workplaces and supply).
- Builders as a business: the builders' hut's name, what their base demand is (a tool kit, stocked materials), whether they buy to order or keep stock, and whether settlements have competing builder businesses (see Builders and construction sites).
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
- Unloaded settlements: units in unloaded chunks don't tick, so a settlement far from every player stops working. Should it keep producing while unloaded, as an abstract simulation (worked out when it loads, or by a cheap off-world tick)? This decides how "hundreds of workers across the world" behaves (see docs/perf.md).