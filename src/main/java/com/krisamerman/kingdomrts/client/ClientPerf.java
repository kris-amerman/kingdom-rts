package com.krisamerman.kingdomrts.client;

import java.util.Arrays;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.util.PerfPayloads;
import com.krisamerman.kingdomrts.util.PerfReport;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Client only. Measures the client's side of a /kingdomrts perf report: how long every frame takes,
// on frames with a building preview up, how long drawing it took and how many blocks it drew, and,
// while the borders view is on, how long drawing it took each frame and checking its ground each tick.
// The server starts and stops it (for the player who started the report); stopping sends the
// numbers back. Not recording, it does nothing.
@EventBusSubscriber(modid = KingdomRts.MODID, value = Dist.CLIENT)
public final class ClientPerf {
    // At most this many frames are kept (about an hour at 60 fps); later ones aren't added.
    private static final int MAX_SAMPLES = 216_000;

    private static boolean recording;
    private static long lastFrameStart;
    private static long[] frameNanos = new long[1024];
    private static int frames;
    private static long[] previewNanos = new long[1024];
    private static long[] previewBlocks = new long[1024];
    private static int previewFrames;
    private static long[] borderNanos = new long[1024];
    private static long[] borderPieces = new long[1024];
    private static int borderFrames;
    private static long[] borderTickNanos = new long[1024];
    private static int borderTicks;

    private ClientPerf() {
    }

    // The server started (true) or stopped (false) a report. Stopping sends what was measured.
    public static void setRecording(boolean on) {
        if (on) {
            frames = 0;
            previewFrames = 0;
            borderFrames = 0;
            borderTicks = 0;
            lastFrameStart = 0;
            recording = true;
            return;
        }
        if (!recording) {
            return;
        }
        recording = false;
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new PerfPayloads.ClientReport(
                    PerfReport.ClientStats.of(frameNanos, frames, previewNanos, previewBlocks, previewFrames,
                            PerfReport.Borders.of(borderNanos, borderPieces, borderFrames, borderTickNanos, borderTicks))));
        }
    }

    public static boolean isRecording() {
        return recording;
    }

    // A frame drew a building preview, taking this long and drawing this many blocks.
    public static void previewFrame(long nanos, int blocks) {
        if (!recording || previewFrames >= MAX_SAMPLES) {
            return;
        }
        if (previewFrames == previewNanos.length) {
            previewNanos = Arrays.copyOf(previewNanos, previewNanos.length * 2);
            previewBlocks = Arrays.copyOf(previewBlocks, previewBlocks.length * 2);
        }
        previewNanos[previewFrames] = nanos;
        previewBlocks[previewFrames] = blocks;
        previewFrames++;
    }

    // A frame drew the borders view, taking this long and drawing this many pieces of border.
    public static void bordersFrame(long nanos, int pieces) {
        if (!recording || borderFrames >= MAX_SAMPLES) {
            return;
        }
        if (borderFrames == borderNanos.length) {
            borderNanos = Arrays.copyOf(borderNanos, borderNanos.length * 2);
            borderPieces = Arrays.copyOf(borderPieces, borderPieces.length * 2);
        }
        borderNanos[borderFrames] = nanos;
        borderPieces[borderFrames] = pieces;
        borderFrames++;
    }

    // A client tick checked the ground under the borders view (and rebaked what changed), taking this long.
    public static void bordersTick(long nanos) {
        if (!recording || borderTicks >= MAX_SAMPLES) {
            return;
        }
        if (borderTicks == borderTickNanos.length) {
            borderTickNanos = Arrays.copyOf(borderTickNanos, borderTickNanos.length * 2);
        }
        borderTickNanos[borderTicks++] = nanos;
    }

    // Each frame's time is from its start to the next frame's start.
    @SubscribeEvent
    static void onFrameStart(RenderFrameEvent.Pre event) {
        if (!recording) {
            return;
        }
        long now = System.nanoTime();
        if (lastFrameStart != 0 && frames < MAX_SAMPLES) {
            if (frames == frameNanos.length) {
                frameNanos = Arrays.copyOf(frameNanos, frameNanos.length * 2);
            }
            frameNanos[frames++] = now - lastFrameStart;
        }
        lastFrameStart = now;
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        recording = false;
    }
}
