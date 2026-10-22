package com.krisamerman.kingdomrts.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.krisamerman.kingdomrts.KingdomRts;

import net.neoforged.fml.loading.FMLPaths;

// Writes performance reports (the stress test's, and /kingdomrts perf's) as a pair of files: a
// Markdown report to read or paste, and the same numbers as JSON. Both say when, which commit
// (when the game runs inside the project's git checkout, as dev runs do) and on what machine.
public final class PerfReportFiles {
    private PerfReportFiles() {
    }

    // The first line under a report's title: the date, commit and machine.
    public static String header() {
        return LocalDate.now() + ", commit " + commit() + ", " + machine();
    }

    // The report's numbers as JSON: the per-tick results, request timings, and client stats if any.
    public static JsonObject json(List<PerfReport.Result> results, List<PerfReport.RequestTiming> requests, @Nullable PerfReport.ClientStats client) {
        JsonObject json = new JsonObject();
        json.addProperty("date", LocalDate.now().toString());
        json.addProperty("commit", commit());
        json.addProperty("machine", machine());
        JsonArray rows = new JsonArray();
        for (PerfReport.Result result : results) {
            JsonObject row = new JsonObject();
            row.addProperty("units", result.units());
            row.addProperty("ticks", result.snapshot().ticks());
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
        JsonArray timings = new JsonArray();
        for (PerfReport.RequestTiming timing : requests) {
            JsonObject row = new JsonObject();
            row.addProperty("request", timing.request());
            row.addProperty("us_per_call", timing.usPerCall());
            row.addProperty("budget_us", timing.budgetUs());
            timings.add(row);
        }
        json.add("requests", timings);
        if (client != null) {
            JsonObject stats = new JsonObject();
            stats.addProperty("frames", client.frames());
            stats.addProperty("average_fps", client.averageFps());
            stats.addProperty("average_frame_ms", client.averageFrameMs());
            stats.addProperty("p95_frame_ms", client.p95FrameMs());
            stats.addProperty("worst_frame_ms", client.worstFrameMs());
            stats.addProperty("preview_frames", client.previewFrames());
            stats.addProperty("average_preview_ms", client.averagePreviewMs());
            stats.addProperty("p95_preview_ms", client.p95PreviewMs());
            stats.addProperty("worst_preview_ms", client.worstPreviewMs());
            stats.addProperty("average_preview_blocks", client.averagePreviewBlocks());
            json.add("client", stats);
        }
        return json;
    }

    // Writes <dir>/<name>.md and <dir>/<name>.json. Returns false (and logs why) if it can't.
    public static boolean write(Path dir, String name, String markdown, JsonObject json) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name + ".md"), markdown, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve(name + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            KingdomRts.LOGGER.warn("Couldn't write the performance report {} to {}", name, dir, e);
            return false;
        }
    }

    // The checked-out commit, marked if there are uncommitted changes, or "unknown" outside git.
    private static String commit() {
        String status = git("status", "--porcelain");
        return git("rev-parse", "--short", "HEAD") + (status.isEmpty() || status.equals("unknown") ? "" : " (uncommitted changes)");
    }

    private static String machine() {
        return Runtime.getRuntime().availableProcessors() + " cores, Java " + System.getProperty("java.version")
                + ", " + System.getProperty("os.name") + " " + System.getProperty("os.arch");
    }

    // Runs a git command in the game directory (a dev run's is inside the project), or "unknown".
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
