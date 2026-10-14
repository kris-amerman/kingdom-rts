package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.util.Perf;
import com.krisamerman.kingdomrts.util.PerfReport;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The stress test (see docs/perf.md): builds StressScenario for 50, 100, 200 and 400 units, lets it
// warm up, then records with Perf for a minute and checks our code against PerfReport.Budget. Each
// size adds a row to <game dir>/kingdomrts-stress/report.md (and report.json).
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
                    scenario.remove();
                    RESULTS.add(result);
                    RESULTS.sort(Comparator.comparingInt(PerfReport.Result::units));
                    writeReport();
                    double trees = (double) logs / StressScenario.TREE_HEIGHT / scenario.lumberjackCount();
                    KingdomRts.LOGGER.info("Stress test: {} units done, {} trees per lumberjack\n{}", units, String.format("%.1f", trees),
                            PerfReport.table(RESULTS, PerfReport.Budget.DEFAULT));
                    helper.assertTrue(trees >= MIN_TREES_PER_LUMBERJACK,
                            String.format("The lumberjacks barely worked (%.1f trees each), so the numbers mean little", trees));
                    String over = PerfReport.overBudget(result, PerfReport.Budget.DEFAULT);
                    helper.assertTrue(over == null, over);
                })
                .thenSucceed();
    }

    // Rewrites the report with every size run so far.
    private static void writeReport() {
        Path dir = FMLPaths.GAMEDIR.get().resolve("kingdomrts-stress");
        String status = git("status", "--porcelain");
        String commit = git("rev-parse", "--short", "HEAD") + (status.isEmpty() || status.equals("unknown") ? "" : " (uncommitted changes)");
        String machine = Runtime.getRuntime().availableProcessors() + " cores, Java " + System.getProperty("java.version")
                + ", " + System.getProperty("os.name") + " " + System.getProperty("os.arch");
        String markdown = "# Stress test\n\n" + LocalDate.now() + ", commit " + commit + ", " + machine + "\n\n"
                + "Recorded over " + RECORD_TICKS + " ticks after " + WARM_UP_TICKS + " to warm up. Times are ms per tick.\n\n"
                + PerfReport.table(RESULTS, PerfReport.Budget.DEFAULT);
        JsonObject json = new JsonObject();
        json.addProperty("date", LocalDate.now().toString());
        json.addProperty("commit", commit);
        json.addProperty("machine", machine);
        JsonArray rows = new JsonArray();
        for (PerfReport.Result result : RESULTS) {
            JsonObject row = new JsonObject();
            row.addProperty("units", result.units());
            row.addProperty("server_ms_per_tick", result.serverMsPerTick());
            row.addProperty("worst_tick_ms", result.worstTickMs());
            row.addProperty("our_ms_per_tick", result.ourMsPerTick());
            row.addProperty("us_per_unit", result.usPerUnit());
            for (Perf.Section section : Perf.Section.values()) {
                row.addProperty(section.profilerName().substring("kingdomrts:".length()) + "_ms_per_tick", result.msPerTick(section));
            }
            rows.add(row);
        }
        json.add("results", rows);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("report.md"), markdown, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            KingdomRts.LOGGER.warn("Couldn't write the stress report to {}", dir, e);
        }
    }

    // Runs a git command in the project (the dev run's game directory is inside it), or "unknown".
    private static String git(String... args) {
        try {
            List<String> command = new ArrayList<>(List.of("git"));
            command.addAll(List.of(args));
            Process process = new ProcessBuilder(command).directory(FMLPaths.GAMEDIR.get().toFile()).redirectErrorStream(true).start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0 ? out : "unknown";
        } catch (IOException | InterruptedException e) {
            return "unknown";
        }
    }
}
