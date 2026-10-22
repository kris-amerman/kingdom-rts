package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.nation.NationRequests;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.util.Perf;
import com.krisamerman.kingdomrts.util.PerfReport;
import com.krisamerman.kingdomrts.util.PerfReportFiles;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The stress test (see docs/perf.md): builds StressScenario for 50, 100, 200 and 400 units, lets it
// warm up, then records with Perf for a minute and checks our code against PerfReport.Budget. Each
// size adds a row to <game dir>/kingdomrts-stress/report.md (and report.json). It also times the
// nation interface's requests against their budgets (interfaceRequests).
//
// It has its own namespace, kingdomrts_stress, so the normal GameTest run (namespace kingdomrts)
// never picks it up: NeoForge only registers tests whose template namespace is enabled. Run it with
// ./gradlew runStressTest. Sizes run in separate batches, one at a time.
@GameTestHolder(StressGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public class StressGameTests {
    static final String NAMESPACE = "kingdomrts_stress";
    private static final int WARM_UP_TICKS = 200;
    private static final int RECORD_TICKS = 1200;
    private static final int TIMEOUT_TICKS = WARM_UP_TICKS + RECORD_TICKS + 200;
    // Every lumberjack should fell at least this many trees over the run, or the scenario isn't
    // really working (and an idle town would be cheap).
    private static final double MIN_TREES_PER_LUMBERJACK = 1.0;

    private static final List<PerfReport.Result> RESULTS = new ArrayList<>();
    private static final List<PerfReport.RequestTiming> REQUESTS = new ArrayList<>();

    // The nation interface request benchmark: a town with this many unfinished sheds (the most a
    // preview is sent), each request answered WARM_UP_CALLS times untimed, then CALLS times timed.
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");
    private static final int WARM_UP_CALLS = 200;
    private static final int CALLS = 500;
    // Budgets in µs per call (docs/perf.md, Interface budget): a placement check is asked up to ten
    // times a second while a ghost moves; sites and the catalog much less often.
    private static final double CHECK_BUDGET_US = 200;
    private static final double SITES_BUDGET_US = 1000;
    private static final double CATALOG_BUDGET_US = 1000;

    @GameTest(template = TEMPLATE, batch = "stress_050", timeoutTicks = TIMEOUT_TICKS)
    public static void units50(GameTestHelper helper) {
        run(helper, 50, 0);
    }

    @GameTest(template = TEMPLATE, batch = "stress_100", timeoutTicks = TIMEOUT_TICKS)
    public static void units100(GameTestHelper helper) {
        run(helper, 100, 1);
    }

    @GameTest(template = TEMPLATE, batch = "stress_200", timeoutTicks = TIMEOUT_TICKS)
    public static void units200(GameTestHelper helper) {
        run(helper, 200, 2);
    }

    @GameTest(template = TEMPLATE, batch = "stress_400", timeoutTicks = TIMEOUT_TICKS)
    public static void units400(GameTestHelper helper) {
        run(helper, 400, 3);
    }

    // How long the server takes to answer the nation interface's requests, with a town as busy as
    // a preview can see: MAX_SITES unfinished sheds around a player standing at its hall.
    @GameTest(template = TEMPLATE, batch = "stress_requests", timeoutTicks = 200)
    public static void interfaceRequests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        ChunkPos area = new ChunkPos(origin.x + 64 + 128 * 4, origin.z); // beyond the unit sizes' areas
        int y = helper.absolutePos(new BlockPos(0, FLOOR_Y + 1, 0)).getY();
        BlockPos hall = area.getMiddleBlockPosition(y);
        Faction faction = faction(helper, "Requests");
        level.setBlockAndUpdate(hall, KingdomRts.TOWN_HALL.get().defaultBlockState());
        Settlement settlement = SettlementOps.found(level, hall, faction).settlement();
        helper.assertTrue(settlement != null, "Couldn't found the benchmark town");
        // Sheds on a grid across the claim, skipping spots that don't work (the hall).
        int sheds = 0;
        BlockPos free = null;
        int west = (area.x - SettlementOps.STARTING_CLAIM_RADIUS) * 16 + 1;
        int north = (area.z - SettlementOps.STARTING_CLAIM_RADIUS) * 16 + 1;
        for (int z = north; z < north + 42; z += 6) {
            for (int x = west; x < west + 42; x += 6) {
                BlockPos spot = new BlockPos(x, y, z);
                if (sheds < NationRequests.MAX_SITES) {
                    if (BuildingOps.commission(level, faction, SHED, spot, Rotation.NONE).building() != null) {
                        sheds++;
                    }
                } else if (free == null && BuildingOps.commissionProblem(level, faction, SHED, spot, Rotation.NONE) == null) {
                    free = spot; // a spot that works: a check there runs every rule
                }
            }
        }
        helper.assertTrue(sheds == NationRequests.MAX_SITES && free != null, "Expected " + NationRequests.MAX_SITES + " sheds and a free spot, got "
                + sheds + " and " + free);
        TestSupport.RecordingPlayer player = TestSupport.recordingPlayer(helper);
        FactionData.get(level).join(player.getUUID(), faction);
        player.teleportTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5);
        helper.assertTrue(NationRequests.sitesFor(player).sites().size() == NationRequests.MAX_SITES, "Expected every shed to be in range");

        BlockPos checked = free;
        List<PerfReport.RequestTiming> timings = List.of(
                time("placement check", CHECK_BUDGET_US, () -> BuildingOps.commissionProblem(level, faction, SHED, checked, Rotation.NONE)),
                time("unfinished sites", SITES_BUDGET_US, () -> NationRequests.sitesFor(player)),
                time("catalog", CATALOG_BUDGET_US, () -> NationRequests.catalogEntries(level)));
        REQUESTS.clear();
        REQUESTS.addAll(timings);
        writeReport();
        KingdomRts.LOGGER.info("Stress test: nation interface requests\n{}", PerfReport.requestTable(timings));
        FactionData.get(level).leave(player.getUUID());
        TestSupport.removePlayer(helper, player);
        level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState());
        String over = PerfReport.overBudget(timings);
        helper.assertTrue(over == null, over);
        helper.succeed();
    }

    // Times a request: WARM_UP_CALLS untimed (so the JIT has compiled it), then CALLS timed.
    private static PerfReport.RequestTiming time(String request, double budgetUs, Supplier<?> call) {
        for (int i = 0; i < WARM_UP_CALLS; i++) {
            call.get();
        }
        long start = System.nanoTime();
        for (int i = 0; i < CALLS; i++) {
            call.get();
        }
        return new PerfReport.RequestTiming(request, (System.nanoTime() - start) / 1000.0 / CALLS, budgetUs);
    }

    private static void run(GameTestHelper helper, int units, int index) {
        // Each size in its own place, far from the test structure and from the other sizes.
        ChunkPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        ChunkPos area = new ChunkPos(origin.x + 64 + 128 * index, origin.z);
        int floorY = helper.absolutePos(new BlockPos(0, FLOOR_Y, 0)).getY();
        StressScenario scenario = StressScenario.build(helper.getLevel(), faction(helper, "Stress"), area, floorY, units);
        KingdomRts.LOGGER.info("Stress test: {} units in {} settlements, warming up", scenario.unitCount(),
                scenario.unitCount() / StressScenario.UNITS_PER_SETTLEMENT);
        helper.startSequence()
                .thenIdle(WARM_UP_TICKS)
                .thenExecute(Perf::startRecording)
                .thenIdle(RECORD_TICKS)
                .thenExecute(() -> {
                    PerfReport.Result result = new PerfReport.Result(scenario.unitCount(), Perf.stopRecording());
                    int logs = scenario.logsCollected();
                    int shedsBuilt = scenario.shedsBuilt();
                    scenario.remove();
                    RESULTS.add(result);
                    RESULTS.sort(Comparator.comparingInt(PerfReport.Result::units));
                    writeReport();
                    double trees = (double) logs / StressScenario.TREE_HEIGHT / scenario.lumberjackCount();
                    KingdomRts.LOGGER.info("Stress test: {} units done, {} trees per lumberjack, {} of {} sheds built\n{}", units, String.format("%.1f", trees),
                            shedsBuilt, scenario.unitCount() / StressScenario.UNITS_PER_SETTLEMENT,
                            PerfReport.table(RESULTS, PerfReport.Budget.DEFAULT));
                    helper.assertTrue(trees >= MIN_TREES_PER_LUMBERJACK,
                            String.format("The lumberjacks barely worked (%.1f trees each), so the numbers mean little", trees));
                    int towns = scenario.unitCount() / StressScenario.UNITS_PER_SETTLEMENT;
                    helper.assertTrue(shedsBuilt * 2 >= towns,
                            "The builders barely worked (" + shedsBuilt + " of " + towns + " sheds built), so the numbers mean little");
                    String over = PerfReport.overBudget(result, PerfReport.Budget.DEFAULT);
                    helper.assertTrue(over == null, over);
                })
                .thenSucceed();
    }

    // Rewrites the report with every size run so far.
    private static void writeReport() {
        String markdown = "# Stress test\n\n" + PerfReportFiles.header() + "\n\n"
                + "Recorded over " + RECORD_TICKS + " ticks after " + WARM_UP_TICKS + " to warm up. Times are ms per tick.\n\n"
                + PerfReport.table(RESULTS, PerfReport.Budget.DEFAULT)
                + (REQUESTS.isEmpty() ? "" : "\n## Nation interface requests\n\nAverage over " + CALLS + " calls, with "
                        + NationRequests.MAX_SITES + " unfinished sheds around the player.\n\n" + PerfReport.requestTable(REQUESTS));
        PerfReportFiles.write(FMLPaths.GAMEDIR.get().resolve("kingdomrts-stress"), "report", markdown, PerfReportFiles.json(RESULTS, REQUESTS, null));
    }
}
