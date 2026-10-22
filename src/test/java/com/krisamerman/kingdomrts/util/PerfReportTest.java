package com.krisamerman.kingdomrts.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PerfReportTest {
    private static final long MS = 1_000_000;

    // A run of the given units over 100 ticks, with our code taking unitMs + captureMs per tick.
    private static PerfReport.Result run(int units, double unitMs, double captureMs) {
        long[] nanos = new long[Perf.Section.values().length];
        nanos[Perf.Section.UNIT.ordinal()] = (long) (unitMs * MS * 100);
        nanos[Perf.Section.JOB.ordinal()] = (long) (unitMs * MS * 50); // inside the unit's time: not added again
        nanos[Perf.Section.CAPTURE.ordinal()] = (long) (captureMs * MS * 100);
        return new PerfReport.Result(units, new Perf.Snapshot(nanos, 100, 20 * MS * 100, 30 * MS));
    }

    @Test
    void perTickNumbers() {
        PerfReport.Result result = run(100, 4.0, 0.5);
        assertEquals(4.5, result.ourMsPerTick(), 1e-9, "Units plus capture; jobs run inside units' ticks");
        assertEquals(45.0, result.usPerUnit(), 1e-9);
        assertEquals(2.0, result.msPerTick(Perf.Section.JOB), 1e-9);
        assertEquals(20.0, result.serverMsPerTick(), 1e-9);
        assertEquals(30.0, result.worstTickMs(), 1e-9);
    }

    @Test
    void budgetAtOneAndTwoHundredUnits() {
        PerfReport.Budget budget = PerfReport.Budget.DEFAULT;
        assertNull(PerfReport.overBudget(run(100, 4.5, 0.4), budget));
        assertNotNull(PerfReport.overBudget(run(100, 5.0, 0.1), budget), "5.1 ms is over 5 at 100 units");
        assertNull(PerfReport.overBudget(run(200, 9.0, 0.5), budget));
        assertNotNull(PerfReport.overBudget(run(200, 10.0, 0.5), budget));
    }

    @Test
    void sizesWithoutALimitAreOnlyReported() {
        assertNull(PerfReport.overBudget(run(400, 100.0, 0), PerfReport.Budget.DEFAULT));
        assertTrue(PerfReport.table(List.of(run(400, 100.0, 0)), PerfReport.Budget.DEFAULT).contains("| - |"));
    }

    @Test
    void noTicksRecordedIsZeroNotAnError() {
        PerfReport.Result empty = new PerfReport.Result(0, new Perf.Snapshot(new long[Perf.Section.values().length], 0, 0, 0));
        assertEquals(0.0, empty.ourMsPerTick());
        assertEquals(0.0, empty.usPerUnit());
    }

    @Test
    void requestTimingsAgainstTheirBudgets() {
        List<PerfReport.RequestTiming> fine = List.of(new PerfReport.RequestTiming("check", 150, 200), new PerfReport.RequestTiming("sites", 900, 1000));
        assertNull(PerfReport.overBudget(fine));
        assertTrue(PerfReport.requestTable(fine).contains("| check | 150.0 | ok (200) |"));
        List<PerfReport.RequestTiming> slow = List.of(new PerfReport.RequestTiming("check", 150, 200), new PerfReport.RequestTiming("sites", 1200, 1000));
        assertTrue(PerfReport.overBudget(slow).contains("sites"), "The slow one is named");
        assertTrue(PerfReport.requestTable(slow).contains("OVER"));
    }

    @Test
    void interfaceRequestsCountAsOurs() {
        long[] nanos = new long[Perf.Section.values().length];
        nanos[Perf.Section.UNIT.ordinal()] = 3 * MS * 100;
        nanos[Perf.Section.NATION_REQUEST.ordinal()] = MS * 100;
        assertEquals(4.0, new PerfReport.Result(100, new Perf.Snapshot(nanos, 100, 0, 0)).ourMsPerTick(), 1e-9);
    }

    @Test
    void percentilesByNearestRank() {
        long[] values = {5, 1, 4, 2, 3, 0, 0};
        assertEquals(5, PerfReport.percentile(values, 5, 1.0));
        assertEquals(3, PerfReport.percentile(values, 5, 0.5));
        assertEquals(5, PerfReport.percentile(values, 5, 0.95), "Nearest rank: the 95th percentile of five is the top one");
        assertEquals(0, PerfReport.percentile(values, 0, 0.95), "Nothing to rank");
    }

    @Test
    void clientStatsFromSamples() {
        long[] frames = {10 * MS, 20 * MS, 30 * MS, 40 * MS};
        long[] preview = {MS, 3 * MS};
        long[] blocks = {100, 300};
        PerfReport.ClientStats stats = PerfReport.ClientStats.of(frames, 4, preview, blocks, 2);
        assertEquals(25.0, stats.averageFrameMs(), 1e-9);
        assertEquals(40.0, stats.worstFrameMs(), 1e-9);
        assertEquals(40.0, stats.averageFps(), 1e-9);
        assertEquals(2.0, stats.averagePreviewMs(), 1e-9);
        assertEquals(200.0, stats.averagePreviewBlocks(), 1e-9);
        assertTrue(PerfReport.clientSection("Steve", stats).contains("Building preview, over 2 frames: 2.00 ms per frame"));
        assertTrue(PerfReport.clientSection("Steve", PerfReport.ClientStats.NONE).contains("No building preview was up"));
    }

    @Test
    void theLogLineSumsUpAStretch() {
        String line = PerfReport.logLine(run(37, 0.4, 0.02), 10);
        assertTrue(line.startsWith("kingdomrts perf (last 10 s): 37 units, our 0.42 ms/tick (unit 0.40,"), line);
        assertTrue(line.contains("server 20.00 ms/tick, worst 30.0 ms"), line);
    }

    @Test
    void theTableHasARequestsColumn() {
        assertTrue(PerfReport.table(List.of(run(100, 1, 0)), PerfReport.Budget.DEFAULT).contains("| capture | requests | Budget |"));
    }
}
