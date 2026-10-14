package com.krisamerman.kingdomrts.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;

class PerfTest {
    private static final ProfilerFiller PROFILER = InactiveProfiler.INSTANCE;

    @AfterEach
    void stop() {
        Perf.stopRecording();
    }

    @Test
    void notRecordingCountsNothing() {
        Perf.startRecording();
        Perf.stopRecording();
        long start = Perf.start(PROFILER, Perf.Section.JOB);
        busy();
        Perf.end(PROFILER, Perf.Section.JOB, start);
        assertEquals(0, Perf.snapshot().nanos(Perf.Section.JOB));
    }

    @Test
    void nestedSectionsCountUnderTheirOwnNames() {
        Perf.startRecording();
        long unit = Perf.start(PROFILER, Perf.Section.UNIT);
        busy();
        long job = Perf.start(PROFILER, Perf.Section.JOB);
        busy();
        Perf.end(PROFILER, Perf.Section.JOB, job);
        Perf.end(PROFILER, Perf.Section.UNIT, unit);
        Perf.Snapshot snapshot = Perf.stopRecording();
        assertTrue(snapshot.nanos(Perf.Section.JOB) > 0);
        assertTrue(snapshot.nanos(Perf.Section.UNIT) > snapshot.nanos(Perf.Section.JOB), "The unit's time includes its job's");
        assertEquals(0, snapshot.nanos(Perf.Section.CAPTURE));
    }

    @Test
    void startingAgainStartsFromZero() {
        Perf.startRecording();
        long start = Perf.start(PROFILER, Perf.Section.CAPTURE);
        busy();
        Perf.end(PROFILER, Perf.Section.CAPTURE, start);
        Perf.startRecording();
        assertEquals(0, Perf.snapshot().nanos(Perf.Section.CAPTURE));
    }

    private static void busy() {
        for (long until = System.nanoTime() + 100_000; System.nanoTime() < until; ) {
            // spin
        }
    }
}
