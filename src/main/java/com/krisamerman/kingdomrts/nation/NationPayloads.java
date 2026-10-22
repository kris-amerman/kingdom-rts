package com.krisamerman.kingdomrts.nation;

import java.util.List;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.client.BuildPreviewClient;
import com.krisamerman.kingdomrts.client.NationClient;

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

// The nation management interface's network messages. A "payload" is a message of our own sent
// inside a vanilla custom-payload packet; each has an id, a codec that writes and reads it, and a
// handler that runs when it arrives (on the main thread, NeoForge's default).
//
// Screens only ever send requests: the server checks the player and runs the faction operation
// (NationRequests), so it never matters whether a request came from a key, a command or, later, a
// command view.
@EventBusSubscriber(modid = KingdomRts.MODID)
public final class NationPayloads {
    // Bumped when a payload's format changes, so a client and server that disagree refuse to connect.
    private static final String VERSION = "1";

    private NationPayloads() {
    }

    // Client to server: "open the nation management interface for me".
    public record OpenNation() implements CustomPacketPayload {
        public static final Type<OpenNation> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "open_nation"));
        public static final StreamCodec<FriendlyByteBuf, OpenNation> STREAM_CODEC = StreamCodec.unit(new OpenNation());

        @Override
        public Type<OpenNation> type() {
            return TYPE;
        }
    }

    // Server to client: show the interface's first screen with this overview.
    public record ShowNation(NationOverview overview) implements CustomPacketPayload {
        public static final Type<ShowNation> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "show_nation"));
        public static final StreamCodec<FriendlyByteBuf, ShowNation> STREAM_CODEC = NationOverview.STREAM_CODEC.map(ShowNation::new,
                ShowNation::overview);

        @Override
        public Type<ShowNation> type() {
            return TYPE;
        }
    }

    // Client to server: "send me the Build catalog".
    public record RequestCatalog() implements CustomPacketPayload {
        public static final Type<RequestCatalog> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "request_catalog"));
        public static final StreamCodec<FriendlyByteBuf, RequestCatalog> STREAM_CODEC = StreamCodec.unit(new RequestCatalog());

        @Override
        public Type<RequestCatalog> type() {
            return TYPE;
        }
    }

    // Server to client: show the Build catalog with these buildings.
    public record ShowCatalog(List<CatalogEntry> entries) implements CustomPacketPayload {
        public static final Type<ShowCatalog> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "show_catalog"));
        public static final StreamCodec<FriendlyByteBuf, ShowCatalog> STREAM_CODEC = CatalogEntry.LIST_CODEC.map(ShowCatalog::new, ShowCatalog::entries);

        @Override
        public Type<ShowCatalog> type() {
            return TYPE;
        }
    }

    // Client to server: "send me this building's blueprint, to preview it".
    public record RequestBlueprint(ResourceLocation building) implements CustomPacketPayload {
        public static final Type<RequestBlueprint> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "request_blueprint"));
        public static final StreamCodec<FriendlyByteBuf, RequestBlueprint> STREAM_CODEC = ResourceLocation.STREAM_CODEC
                .map(RequestBlueprint::new, RequestBlueprint::building).cast();

        @Override
        public Type<RequestBlueprint> type() {
            return TYPE;
        }
    }

    // Server to client: preview this building (its blocks, without air; see BlueprintCodec).
    public record ShowBlueprint(ResourceLocation building, String name, Blueprint blueprint) implements CustomPacketPayload {
        public static final Type<ShowBlueprint> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "show_blueprint"));
        public static final StreamCodec<FriendlyByteBuf, ShowBlueprint> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, ShowBlueprint::building,
                ByteBufCodecs.STRING_UTF8, ShowBlueprint::name,
                BlueprintCodec.STREAM_CODEC, ShowBlueprint::blueprint,
                ShowBlueprint::new);

        @Override
        public Type<ShowBlueprint> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(OpenNation.TYPE, OpenNation.STREAM_CODEC,
                (payload, context) -> NationRequests.open((ServerPlayer) context.player()));
        registrar.playToServer(RequestCatalog.TYPE, RequestCatalog.STREAM_CODEC,
                (payload, context) -> NationRequests.catalog((ServerPlayer) context.player()));
        registrar.playToServer(RequestBlueprint.TYPE, RequestBlueprint.STREAM_CODEC,
                (payload, context) -> NationRequests.blueprint((ServerPlayer) context.player(), payload.building()));
        // These lambdas only touch the client-only classes (NationClient, BuildPreviewClient) when a
        // payload arrives, which only happens on a client, so a dedicated server never loads them.
        registrar.playToClient(ShowNation.TYPE, ShowNation.STREAM_CODEC, (payload, context) -> NationClient.show(payload.overview()));
        registrar.playToClient(ShowCatalog.TYPE, ShowCatalog.STREAM_CODEC, (payload, context) -> NationClient.showCatalog(payload.entries()));
        registrar.playToClient(ShowBlueprint.TYPE, ShowBlueprint.STREAM_CODEC, (payload, context) -> BuildPreviewClient.start(payload));
    }
}
