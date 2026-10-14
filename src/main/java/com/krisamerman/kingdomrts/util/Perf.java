package com.krisamerman.kingdomrts.util;

import java.util.Arrays;

import com.krisamerman.kingdomrts.KingdomRts;

import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// Times the mod's hot paths. Every section is also a profiler section ("kingdomrts:..."), so
// Minecraft's profiler (/debug start, /debug stop) and tools like Spark show our share of the tick.
// While recording (the stress test, see docs/perf.md) it also adds up how long each section took,
// and how long whole server ticks took. Not recording, it costs only the profiler push and pop.
//
// Use it as
//   long start = Perf.start(level, Perf.Section.JOB);
//   ... the work ...
//   Perf.end(level, Perf.Section.JOB, start);
// which allocates nothing. Server thread only: client-side calls are profiled but never counted.
@EventBusSubscriber(modid = KingdomRts.MODID)
public final class Perf {
    public enum Section {
        // A unit's whole server tick: vanilla movement, AI and pathfinding included. The sections
        // below run inside it.
        UNIT("kingdomrts:unit"),
        JOB("kingdomrts:job"),
        JOB_SEEKING("kingdomrts:job_seeking"),
        // A town hall's capture check (a block entity tick, outside any unit).
        CAPTURE("kingdomrts:capture");

        private final String profilerName;

        Section(String profilerName) {
            this.profilerName = profilerName;
        }

        public String profilerName() {
            return profilerName;
        }
    }

    // What was recorded: nanoseconds per section, plus whole server ticks (how many, their total and
    // the slowest).
    public record Snapshot(long[] sectionNanos, long ticks, long tickNanos, long worstTickNanos) {
        public long nanos(Section section) {
            return sectionNanos[section.ordinal()];
        }
    }

    private static boolean recording;
    private static final long[] sectionNanos = new long[Section.values().length];
    private static long ticks;
    private static long tickNanos;
    private static long worstTickNanos;
    private static long tickStart;

    private Perf() {
    }

    public static long start(Level level, Section section) {
        level.getProfiler().push(section.profilerName());
        return recording && !level.isClientSide() ? System.nanoTime() : 0;
    }

    public static void end(Level level, Section section, long start) {
        end(level.getProfiler(), section, start);
    }

    // The same, given the profiler directly (so it can be tested without a world).
    public static long start(ProfilerFiller profiler, Section section) {
        profiler.push(section.profilerName());
        return recording ? System.nanoTime() : 0;
    }

    public static void end(ProfilerFiller profiler, Section section, long start) {
        if (recording && start != 0) {
            sectionNanos[section.ordinal()] += System.nanoTime() - start;
        }
        profiler.pop();
    }

    // Starts recording from zero.
    public static void startRecording() {
        Arrays.fill(sectionNanos, 0);
        ticks = 0;
        tickNanos = 0;
        worstTickNanos = 0;
        tickStart = 0;
        recording = true;
    }

    public static Snapshot stopRecording() {
        recording = false;
        return snapshot();
    }

    public static Snapshot snapshot() {
        return new Snapshot(sectionNanos.clone(), ticks, tickNanos, worstTickNanos);
    }

    public static boolean isRecording() {
        return recording;
    }

    @SubscribeEvent
    static void onServerTickStart(ServerTickEvent.Pre event) {
        tickStart = recording ? System.nanoTime() : 0;
    }

    @SubscribeEvent
    static void onServerTickEnd(ServerTickEvent.Post event) {
        if (recording && tickStart != 0) {
            long took = System.nanoTime() - tickStart;
            ticks++;
            tickNanos += took;
            worstTickNanos = Math.max(worstTickNanos, took);
        }
    }
}
