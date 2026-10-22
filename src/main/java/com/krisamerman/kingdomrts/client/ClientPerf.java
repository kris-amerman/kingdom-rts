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
// and, on frames with a building preview up, how long drawing it took and how many blocks it drew.
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

    private ClientPerf() {
    }

    // The server started (true) or stopped (false) a report. Stopping sends what was measured.
    public static void setRecording(boolean on) {
        if (on) {
            frames = 0;
            previewFrames = 0;
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
                    PerfReport.ClientStats.of(frameNanos, frames, previewNanos, previewBlocks, previewFrames)));
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
