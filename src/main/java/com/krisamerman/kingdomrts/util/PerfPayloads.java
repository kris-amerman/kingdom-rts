package com.krisamerman.kingdomrts.util;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.client.ClientPerf;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// The network messages /kingdomrts perf uses to add the client's numbers to a report: the server
// tells the client of the player who started recording to start and stop measuring its frames, and
// the client sends back what it measured.
@EventBusSubscriber(modid = KingdomRts.MODID)
public final class PerfPayloads {
    private static final String VERSION = "1";

    private PerfPayloads() {
    }

    // Server to client: start (true) or stop (false) measuring frames for a report. Stopping sends
    // the numbers back.
    public record ClientRecording(boolean recording) implements CustomPacketPayload {
        public static final Type<ClientRecording> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "perf_client_recording"));
        public static final StreamCodec<FriendlyByteBuf, ClientRecording> STREAM_CODEC = ByteBufCodecs.BOOL
                .map(ClientRecording::new, ClientRecording::recording).cast();

        @Override
        public Type<ClientRecording> type() {
            return TYPE;
        }
    }

    // Client to server: what the client measured while the report was recording.
    public record ClientReport(PerfReport.ClientStats stats) implements CustomPacketPayload {
        public static final Type<ClientReport> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "perf_client_report"));
        public static final StreamCodec<FriendlyByteBuf, ClientReport> STREAM_CODEC = StreamCodec.of(
                (buf, report) -> write(buf, report.stats()), buf -> new ClientReport(read(buf)));

        @Override
        public Type<ClientReport> type() {
            return TYPE;
        }
    }

    private static void write(FriendlyByteBuf buf, PerfReport.ClientStats stats) {
        buf.writeVarInt(stats.frames());
        buf.writeDouble(stats.averageFrameMs());
        buf.writeDouble(stats.p95FrameMs());
        buf.writeDouble(stats.worstFrameMs());
        buf.writeVarInt(stats.previewFrames());
        buf.writeDouble(stats.averagePreviewMs());
        buf.writeDouble(stats.p95PreviewMs());
        buf.writeDouble(stats.worstPreviewMs());
        buf.writeDouble(stats.averagePreviewBlocks());
        PerfReport.Borders borders = stats.borders();
        buf.writeVarInt(borders.frames());
        buf.writeDouble(borders.averageMs());
        buf.writeDouble(borders.p95Ms());
        buf.writeDouble(borders.worstMs());
        buf.writeDouble(borders.averagePieces());
        buf.writeVarInt(borders.ticks());
        buf.writeDouble(borders.averageTickMs());
        buf.writeDouble(borders.worstTickMs());
    }

    private static PerfReport.ClientStats read(FriendlyByteBuf buf) {
        return new PerfReport.ClientStats(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), new PerfReport.Borders(buf.readVarInt(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readDouble(), buf.readDouble()));
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(ClientReport.TYPE, ClientReport.STREAM_CODEC,
                (payload, context) -> PerfCommands.clientReport((ServerPlayer) context.player(), payload.stats()));
        // Only touches the client-only ClientPerf when a payload arrives, which only happens on a client.
        registrar.playToClient(ClientRecording.TYPE, ClientRecording.STREAM_CODEC, (payload, context) -> ClientPerf.setRecording(payload.recording()));
    }
}
