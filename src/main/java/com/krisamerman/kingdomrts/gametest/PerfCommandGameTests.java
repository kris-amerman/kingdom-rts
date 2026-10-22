package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.lastPayload;
import static com.krisamerman.kingdomrts.gametest.TestSupport.recordingPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.runCommand;
import static com.krisamerman.kingdomrts.gametest.TestSupport.runOpCommandAs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.gametest.TestSupport.RecordingPlayer;
import com.krisamerman.kingdomrts.util.PerfCommands;
import com.krisamerman.kingdomrts.util.PerfPayloads;
import com.krisamerman.kingdomrts.util.PerfReport;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// /kingdomrts perf, run for real: start and stop write a report, the log logs, misuse is refused,
// and a client's numbers are added to the report of the player who started it. Recording is global,
// so each test has its own batch (batches run one at a time). Reports a test writes are deleted.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class PerfCommandGameTests {
    private static final Pattern REPORT = Pattern.compile("Report: " + PerfCommands.REPORT_DIR + "/(perf-[0-9-]+)\\.md");
    private static final int RECORD_TICKS = 20;

    @GameTest(template = TEMPLATE, batch = "perf_report")
    public static void startAndStopWriteAReport(GameTestHelper helper) {
        List<String> started = runCommand(helper, HALL, "kingdomrts perf start");
        helper.assertTrue(started.stream().anyMatch(line -> line.startsWith("Recording")), "Expected recording to start: " + started);
        helper.runAfterDelay(RECORD_TICKS, () -> {
            List<String> stopped = runCommand(helper, HALL, "kingdomrts perf stop");
            String name = reportName(stopped);
            try {
                String markdown = read(name + ".md");
                helper.assertTrue(markdown.startsWith("# Kingdom RTS performance") && markdown.contains("| Units | Server ms/tick")
                        && markdown.contains("| requests |"), "Expected the report's table: " + markdown);
                JsonObject result = JsonParser.parseString(read(name + ".json")).getAsJsonObject().getAsJsonArray("results").get(0).getAsJsonObject();
                helper.assertTrue(result.get("ticks").getAsLong() >= RECORD_TICKS - 1 && result.has("unit_ms_per_tick") && result.has("nation_request_ms_per_tick"),
                        "Expected the recorded ticks and our sections: " + result);
                helper.assertTrue(stopped.stream().anyMatch(line -> line.startsWith("Our code took")), "Expected a summary in chat: " + stopped);
            } finally {
                delete(name);
            }
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "perf_refused")
    public static void stoppingWithoutStartingAndStartingTwiceAreRefused(GameTestHelper helper) {
        List<String> stopped = runCommand(helper, HALL, "kingdomrts perf stop");
        helper.assertTrue(stopped.stream().anyMatch(line -> line.contains("Not recording")), "Expected stop to be refused: " + stopped);
        runCommand(helper, HALL, "kingdomrts perf start");
        List<String> again = runCommand(helper, HALL, "kingdomrts perf start");
        helper.assertTrue(again.stream().anyMatch(line -> line.contains("Already recording")), "Expected a second start to be refused: " + again);
        delete(reportName(runCommand(helper, HALL, "kingdomrts perf stop")));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "perf_log")
    public static void theLogWritesASummaryEverySoOften(GameTestHelper helper) {
        List<String> status = runCommand(helper, HALL, "kingdomrts perf log");
        helper.assertTrue(status.stream().anyMatch(line -> line.contains("log is off") && line.contains("/kingdomrts perf log on")),
                "Expected bare log to say it's off and how to turn it on: " + status);
        List<String> on = runCommand(helper, HALL, "kingdomrts perf log on 1");
        helper.assertTrue(on.stream().anyMatch(line -> line.contains("every 1 s")), "Expected logging to start: " + on);
        String before = PerfCommands.lastLogLine();
        helper.runAfterDelay(25, () -> {
            String line = PerfCommands.lastLogLine();
            helper.assertTrue(line != null && !line.equals(before) && line.startsWith("kingdomrts perf (last 1 s): "), "Expected a log line: " + line);
            List<String> onStatus = runCommand(helper, HALL, "kingdomrts perf log");
            helper.assertTrue(onStatus.stream().anyMatch(text -> text.contains("log is on, every 1 s")), "Expected bare log to say it's on: " + onStatus);
            List<String> off = runCommand(helper, HALL, "kingdomrts perf log off");
            helper.assertTrue(off.stream().anyMatch(text -> text.startsWith("Stopped logging")), "Expected logging to stop: " + off);
            List<String> offAgain = runCommand(helper, HALL, "kingdomrts perf log off");
            helper.assertTrue(offAgain.stream().anyMatch(text -> text.contains("isn't on")), "Expected a second off to be refused: " + offAgain);
            helper.succeed();
        });
    }

    // The player who started a report has their client measure frames, and its numbers are added
    // to the report when they arrive. Another player's numbers are ignored.
    @GameTest(template = TEMPLATE, batch = "perf_client")
    public static void theStartingPlayersClientNumbersAreAddedToTheReport(GameTestHelper helper) {
        RecordingPlayer player = recordingPlayer(helper);
        RecordingPlayer other = recordingPlayer(helper);
        runOpCommandAs(player, "kingdomrts perf start");
        PerfPayloads.ClientRecording start = lastPayload(player, PerfPayloads.ClientRecording.class);
        helper.assertTrue(start != null && start.recording(), "Expected the player's client to be told to measure");
        helper.runAfterDelay(RECORD_TICKS, () -> {
            String name = reportName(runOpCommandAs(player, "kingdomrts perf stop"));
            try {
                PerfPayloads.ClientRecording stop = lastPayload(player, PerfPayloads.ClientRecording.class);
                helper.assertTrue(stop != null && !stop.recording() && PerfCommands.isWaitingForClient(), "Expected the client to be asked for its numbers");
                PerfReport.ClientStats stats = new PerfReport.ClientStats(1200, 16.7, 20.1, 33.0, 600, 0.31, 0.5, 0.9, 496);
                PerfCommands.clientReport(other, stats);
                helper.assertTrue(PerfCommands.isWaitingForClient() && !read(name + ".md").contains("## Client"), "Expected another player's numbers to be ignored");
                PerfCommands.clientReport(player, stats);
                String markdown = read(name + ".md");
                helper.assertTrue(markdown.contains("## Client (" + player.getGameProfile().getName() + ")") && markdown.contains("0.31 ms per frame"),
                        "Expected the client's numbers in the report: " + markdown);
                JsonObject client = JsonParser.parseString(read(name + ".json")).getAsJsonObject().getAsJsonObject("client");
                helper.assertTrue(client != null && client.get("average_preview_blocks").getAsDouble() == 496, "Expected them in the JSON: " + client);
                helper.assertTrue(other.sent.stream().noneMatch(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof PerfPayloads.ClientRecording), "Expected only the starting player's client to measure");
            } finally {
                delete(name);
                removePlayer(helper, player);
                removePlayer(helper, other);
            }
            helper.succeed();
        });
    }

    // The report's name, from stop's message.
    private static String reportName(List<String> output) {
        for (String line : output) {
            Matcher matcher = REPORT.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new GameTestAssertException("Expected stop to name its report: " + output);
    }

    private static String read(String file) {
        try {
            return Files.readString(PerfCommands.reportDir().resolve(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GameTestAssertException("Couldn't read " + file + ": " + e.getMessage());
        }
    }

    private static void delete(String name) {
        for (String extension : List.of(".md", ".json")) {
            Path file = PerfCommands.reportDir().resolve(name + extension);
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                // left behind; harmless
            }
        }
    }
}
