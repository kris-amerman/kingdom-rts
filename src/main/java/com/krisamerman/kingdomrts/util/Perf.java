package com.krisamerman.kingdomrts.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.krisamerman.kingdomrts.KingdomRts;

import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// Times the mod's hot paths. Every section is also a profiler section ("kingdomrts:..."), so
// Minecraft's profiler (/debug start, /debug stop) and tools like Spark show our share of the tick.
// While recording it also adds up how long each section took, and how long whole server ticks
// took. Two things record: the stress test (startRecording/stopRecording, which start from zero),
// and windows (openWindow), each reading what happened since it opened, which /kingdomrts perf
// uses in a live world. Not recording, it costs only the profiler push and pop.
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
        CAPTURE("kingdomrts:capture"),
        // Answering a request from a player's nation management interface (outside any unit).
        NATION_REQUEST("kingdomrts:nation_request");

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

    // Client-only sections: profiler names only, never timed or counted (frames aren't ticks, and
    // the stress test has no client). They show in the F3 profiler pie chart (Shift+F3, under
    // gameRenderer), in /debug profiles and in Spark's client profiler. Use them as
    //   Perf.push(profiler, Perf.ClientSection.PREVIEW_RENDER);
    //   ... the work ...
    //   profiler.pop();
    public enum ClientSection {
        // Each client tick while previewing a building: keys, where the player looks, checks.
        PREVIEW_TICK("kingdomrts:preview_tick"),
        // Drawing the previewed building's ghost and outline.
        PREVIEW_RENDER("kingdomrts:preview_render"),
        // Drawing the faction's unfinished buildings finished, while previewing.
        SITES_RENDER("kingdomrts:sites_render");

        private final String profilerName;

        ClientSection(String profilerName) {
            this.profilerName = profilerName;
        }

        public String profilerName() {
            return profilerName;
        }
    }

    public static void push(ProfilerFiller profiler, ClientSection section) {
        profiler.push(section.profilerName());
    }

    // A recording that reads what happened since it opened (or was last restarted), alongside any
    // others: /kingdomrts perf's report and its log each have one. Server thread only.
    public static final class Window {
        private long[] startNanos;
        private long startTicks;
        private long startTickNanos;
        private long worstTickNanos;

        private Window() {
            restart();
        }

        // What was recorded since the window opened or was last restarted.
        public Snapshot read() {
            long[] nanos = sectionNanos.clone();
            for (int i = 0; i < nanos.length; i++) {
                nanos[i] -= startNanos[i];
            }
            return new Snapshot(nanos, ticks - startTicks, tickNanos - startTickNanos, worstTickNanos);
        }

        // Starts reading from now.
        public void restart() {
            startNanos = sectionNanos.clone();
            startTicks = ticks;
            startTickNanos = tickNanos;
            worstTickNanos = 0;
        }
    }

    private static final List<Window> windows = new ArrayList<>();
    // The stress test is recording (startRecording to stopRecording).
    private static boolean stressRecording;
    // Either the stress test or a window is recording.
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

    // Starts recording from zero (the stress test). Not used while a window is open.
    public static void startRecording() {
        Arrays.fill(sectionNanos, 0);
        ticks = 0;
        tickNanos = 0;
        worstTickNanos = 0;
        tickStart = 0;
        stressRecording = true;
        updateRecording();
    }

    public static Snapshot stopRecording() {
        stressRecording = false;
        updateRecording();
        return snapshot();
    }

    // Opens a window: recording runs while any window is open.
    public static Window openWindow() {
        Window window = new Window();
        windows.add(window);
        updateRecording();
        return window;
    }

    // Closes a window, returning what it recorded.
    public static Snapshot closeWindow(Window window) {
        Snapshot snapshot = window.read();
        windows.remove(window);
        updateRecording();
        return snapshot;
    }

    // Whether the stress test is recording (so a window's numbers would be reset under it).
    public static boolean isStressRecording() {
        return stressRecording;
    }

    private static void updateRecording() {
        recording = stressRecording || !windows.isEmpty();
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
            tickEnded(System.nanoTime() - tickStart);
        }
    }

    // A whole server tick took this long (package-private so tests can feed ticks in).
    static void tickEnded(long took) {
        ticks++;
        tickNanos += took;
        worstTickNanos = Math.max(worstTickNanos, took);
        for (Window window : windows) {
            window.worstTickNanos = Math.max(window.worstTickNanos, took);
        }
    }
}
