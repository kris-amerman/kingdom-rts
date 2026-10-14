package com.krisamerman.kingdomrts.util;

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

        // Our share of the tick: units' whole ticks (everything else of ours runs inside them) plus
        // town hall capture checks.
        public double ourMsPerTick() {
            return perTick(snapshot.nanos(Perf.Section.UNIT) + snapshot.nanos(Perf.Section.CAPTURE));
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
        out.append("| Units | Server ms/tick (avg / worst) | Our ms/tick | µs per unit | unit | job | job seeking | capture | Budget |\n");
        out.append("|---|---|---|---|---|---|---|---|---|\n");
        for (Result r : results) {
            Double limit = budget.limitFor(r.units());
            String verdict = limit == null ? "-" : (overBudget(r, budget) == null ? "ok" : "OVER") + String.format(" (%.1f)", limit);
            out.append(String.format("| %d | %.2f / %.2f | %.2f | %.1f | %.2f | %.2f | %.2f | %.2f | %s |\n", r.units(), r.serverMsPerTick(),
                    r.worstTickMs(), r.ourMsPerTick(), r.usPerUnit(), r.msPerTick(Perf.Section.UNIT), r.msPerTick(Perf.Section.JOB),
                    r.msPerTick(Perf.Section.JOB_SEEKING), r.msPerTick(Perf.Section.CAPTURE), verdict));
        }
        return out.toString();
    }
}
