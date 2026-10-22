package com.krisamerman.kingdomrts.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

// Pure: turns what Perf recorded during a stress run into per-tick numbers, checks them against the
// budget, and formats the report (see docs/perf.md).
public final class PerfReport {
    // Our code's limit, in ms per tick, by number of units. Sizes not listed are only reported.
    public record Budget(Map<Integer, Double> msPerTickByUnits) {
        public static final Budget DEFAULT = new Budget(Map.of(100, 5.0, 200, 10.0));

        @Nullable
        public Double limitFor(int units) {
            return msPerTickByUnits.get(units);
        }
    }

    // One stress run: how many units, and what Perf recorded over it.
    public record Result(int units, Perf.Snapshot snapshot) {
        public double msPerTick(Perf.Section section) {
            return perTick(snapshot.nanos(section));
        }

        // Our share of the tick: units' whole ticks (everything else of ours runs inside them), town
        // hall capture checks, and answering players' nation interface requests.
        public double ourMsPerTick() {
            return perTick(snapshot.nanos(Perf.Section.UNIT) + snapshot.nanos(Perf.Section.CAPTURE) + snapshot.nanos(Perf.Section.NATION_REQUEST));
        }

        public double usPerUnit() {
            return units == 0 ? 0 : ourMsPerTick() * 1000.0 / units;
        }

        public double serverMsPerTick() {
            return perTick(snapshot.tickNanos());
        }

        public double worstTickMs() {
            return snapshot.worstTickNanos() / 1_000_000.0;
        }

        private double perTick(long nanos) {
            return snapshot.ticks() == 0 ? 0 : nanos / 1_000_000.0 / snapshot.ticks();
        }
    }

    private PerfReport() {
    }

    // Why the run is over budget, or null if it's within it (or the size has no limit).
    @Nullable
    public static String overBudget(Result result, Budget budget) {
        Double limit = budget.limitFor(result.units());
        if (limit == null || result.ourMsPerTick() <= limit) {
            return null;
        }
        return String.format("%d units: our code took %.2f ms/tick, over the %.1f ms budget", result.units(), result.ourMsPerTick(), limit);
    }

    // The results as a Markdown table, one row per size.
    public static String table(List<Result> results, Budget budget) {
        StringBuilder out = new StringBuilder();
        out.append("| Units | Server ms/tick (avg / worst) | Our ms/tick | µs per unit | unit | job | job seeking | capture | requests | Budget |\n");
        out.append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (Result r : results) {
            Double limit = budget.limitFor(r.units());
            String verdict = limit == null ? "-" : (overBudget(r, budget) == null ? "ok" : "OVER") + String.format(" (%.1f)", limit);
            out.append(String.format("| %d | %.2f / %.2f | %.2f | %.1f | %.2f | %.2f | %.2f | %.2f | %.2f | %s |\n", r.units(), r.serverMsPerTick(),
                    r.worstTickMs(), r.ourMsPerTick(), r.usPerUnit(), r.msPerTick(Perf.Section.UNIT), r.msPerTick(Perf.Section.JOB),
                    r.msPerTick(Perf.Section.JOB_SEEKING), r.msPerTick(Perf.Section.CAPTURE), r.msPerTick(Perf.Section.NATION_REQUEST), verdict));
        }
        return out.toString();
    }

    // How long one kind of nation interface request took to answer, on average over many calls in
    // the stress world, and its budget, both in microseconds per call.
    public record RequestTiming(String request, double usPerCall, double budgetUs) {
        public boolean overBudget() {
            return usPerCall > budgetUs;
        }
    }

    // Why a request timing is over budget, or null if every one is within it.
    @Nullable
    public static String overBudget(List<RequestTiming> timings) {
        for (RequestTiming timing : timings) {
            if (timing.overBudget()) {
                return String.format("Answering %s took %.0f µs per call, over its %.0f µs budget", timing.request(), timing.usPerCall(), timing.budgetUs());
            }
        }
        return null;
    }

    // The request timings as a Markdown table.
    public static String requestTable(List<RequestTiming> timings) {
        StringBuilder out = new StringBuilder();
        out.append("| Request | µs per call | Budget |\n");
        out.append("|---|---|---|\n");
        for (RequestTiming timing : timings) {
            out.append(String.format("| %s | %.1f | %s (%.0f) |\n", timing.request(), timing.usPerCall(), timing.overBudget() ? "OVER" : "ok",
                    timing.budgetUs()));
        }
        return out.toString();
    }

    // One line summing up a stretch of recording, for the log (/kingdomrts perf log).
    public static String logLine(Result result, int seconds) {
        return String.format("kingdomrts perf (last %d s): %d units, our %.2f ms/tick (unit %.2f, job %.2f, job seeking %.2f, capture %.2f, requests %.2f),"
                + " server %.2f ms/tick, worst %.1f ms", seconds, result.units(), result.ourMsPerTick(), result.msPerTick(Perf.Section.UNIT),
                result.msPerTick(Perf.Section.JOB), result.msPerTick(Perf.Section.JOB_SEEKING), result.msPerTick(Perf.Section.CAPTURE),
                result.msPerTick(Perf.Section.NATION_REQUEST), result.serverMsPerTick(), result.worstTickMs());
    }

    // What a player's client measured while a report was recording: every frame, and the frames
    // that drew a building preview (the preview's own drawing time, and how many blocks it drew).
    public record ClientStats(int frames, double averageFrameMs, double p95FrameMs, double worstFrameMs, int previewFrames,
            double averagePreviewMs, double p95PreviewMs, double worstPreviewMs, double averagePreviewBlocks) {
        public static final ClientStats NONE = new ClientStats(0, 0, 0, 0, 0, 0, 0, 0, 0);

        // From raw samples: frame times, and per preview frame its drawing time and blocks drawn.
        public static ClientStats of(long[] frameNanos, int frames, long[] previewNanos, long[] previewBlocks, int previewFrames) {
            return new ClientStats(frames, averageMs(frameNanos, frames), percentile(frameNanos, frames, 0.95) / 1e6, max(frameNanos, frames) / 1e6,
                    previewFrames, averageMs(previewNanos, previewFrames), percentile(previewNanos, previewFrames, 0.95) / 1e6,
                    max(previewNanos, previewFrames) / 1e6, previewFrames == 0 ? 0 : (double) sum(previewBlocks, previewFrames) / previewFrames);
        }

        public double averageFps() {
            return averageFrameMs == 0 ? 0 : 1000.0 / averageFrameMs;
        }
    }

    // The value at or below which the given share of the first count values fall (nearest rank),
    // or 0 for no values.
    public static long percentile(long[] values, int count, double share) {
        if (count == 0) {
            return 0;
        }
        long[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(share * count);
        return sorted[Math.max(0, Math.min(count - 1, rank - 1))];
    }

    private static double averageMs(long[] values, int count) {
        return count == 0 ? 0 : sum(values, count) / 1e6 / count;
    }

    private static long sum(long[] values, int count) {
        long total = 0;
        for (int i = 0; i < count; i++) {
            total += values[i];
        }
        return total;
    }

    private static long max(long[] values, int count) {
        long max = 0;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, values[i]);
        }
        return max;
    }

    // The client's numbers as a Markdown section.
    public static String clientSection(String player, ClientStats stats) {
        StringBuilder out = new StringBuilder("## Client (" + player + ")\n\n");
        out.append(String.format("%d frames, %.0f fps on average; frame time %.2f ms on average, %.2f ms 95th percentile, %.2f ms worst.\n\n",
                stats.frames(), stats.averageFps(), stats.averageFrameMs(), stats.p95FrameMs(), stats.worstFrameMs()));
        if (stats.previewFrames() == 0) {
            out.append("No building preview was up.\n");
        } else {
            out.append(String.format("Building preview, over %d frames: %.2f ms per frame on average, %.2f ms 95th percentile, %.2f ms worst,"
                    + " %.0f blocks drawn on average (budget 1 ms per frame).\n", stats.previewFrames(), stats.averagePreviewMs(), stats.p95PreviewMs(),
                    stats.worstPreviewMs(), stats.averagePreviewBlocks()));
        }
        return out.toString();
    }
}
