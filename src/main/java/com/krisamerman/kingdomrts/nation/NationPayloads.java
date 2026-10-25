package com.krisamerman.kingdomrts.nation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.client.BordersClient;
import com.krisamerman.kingdomrts.client.BuildPreviewClient;
import com.krisamerman.kingdomrts.client.DemolishClient;
import com.krisamerman.kingdomrts.client.NationClient;
import com.krisamerman.kingdomrts.util.Perf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
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

    // Client to server: "commission this building here", from a confirmed preview: the building, its
    // template corner (BuildingPreview.origin) and how it's turned.
    public record CommissionBuilding(ResourceLocation building, BlockPos origin, Rotation rotation) implements CustomPacketPayload {
        public static final Type<CommissionBuilding> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "commission_building"));
        public static final StreamCodec<FriendlyByteBuf, CommissionBuilding> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, CommissionBuilding::building,
                BlockPos.STREAM_CODEC, CommissionBuilding::origin,
                NeoForgeStreamCodecs.enumCodec(Rotation.class), CommissionBuilding::rotation,
                CommissionBuilding::new);

        @Override
        public Type<CommissionBuilding> type() {
            return TYPE;
        }
    }

    // Server to client: how a commission request went. The player is also told in words (on the
    // action bar); this tells the preview whether to end.
    public record CommissionAnswer(boolean commissioned) implements CustomPacketPayload {
        public static final Type<CommissionAnswer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "commission_answer"));
        public static final StreamCodec<FriendlyByteBuf, CommissionAnswer> STREAM_CODEC = ByteBufCodecs.BOOL
                .map(CommissionAnswer::new, CommissionAnswer::commissioned).cast();

        @Override
        public Type<CommissionAnswer> type() {
            return TYPE;
        }
    }

    // Client to server: "could this building go here?", as the player moves a preview. Changes
    // nothing; the server answers with PlacementAnswer.
    public record CheckPlacement(ResourceLocation building, BlockPos origin, Rotation rotation) implements CustomPacketPayload {
        public static final Type<CheckPlacement> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "check_placement"));
        public static final StreamCodec<FriendlyByteBuf, CheckPlacement> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, CheckPlacement::building,
                BlockPos.STREAM_CODEC, CheckPlacement::origin,
                NeoForgeStreamCodecs.enumCodec(Rotation.class), CheckPlacement::rotation,
                CheckPlacement::new);

        @Override
        public Type<CheckPlacement> type() {
            return TYPE;
        }
    }

    // Server to client: whether the building could go where it was checked. problem is why not, or
    // "" if it could.
    public record PlacementAnswer(ResourceLocation building, BlockPos origin, Rotation rotation, String problem) implements CustomPacketPayload {
        public static final Type<PlacementAnswer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "placement_answer"));
        public static final StreamCodec<FriendlyByteBuf, PlacementAnswer> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, PlacementAnswer::building,
                BlockPos.STREAM_CODEC, PlacementAnswer::origin,
                NeoForgeStreamCodecs.enumCodec(Rotation.class), PlacementAnswer::rotation,
                ByteBufCodecs.STRING_UTF8, PlacementAnswer::problem,
                PlacementAnswer::new);

        public boolean fits() {
            return problem.isEmpty();
        }

        @Override
        public Type<PlacementAnswer> type() {
            return TYPE;
        }
    }

    // Client to server: "send me my faction's unfinished buildings around me", to show them finished
    // while previewing.
    public record RequestSites() implements CustomPacketPayload {
        public static final Type<RequestSites> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "request_sites"));
        public static final StreamCodec<FriendlyByteBuf, RequestSites> STREAM_CODEC = StreamCodec.unit(new RequestSites());

        @Override
        public Type<RequestSites> type() {
            return TYPE;
        }
    }

    // An unfinished building as the preview shows it: which building, its template corner and how
    // it's turned.
    public record SiteView(ResourceLocation building, BlockPos origin, Rotation rotation) {
        public static final StreamCodec<FriendlyByteBuf, SiteView> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, SiteView::building,
                BlockPos.STREAM_CODEC, SiteView::origin,
                NeoForgeStreamCodecs.enumCodec(Rotation.class), SiteView::rotation,
                SiteView::new);
    }

    // Server to client: the unfinished buildings around the player, and the blueprint of each kind
    // among them (once per kind, without air; see BlueprintCodec).
    public record ShowSites(List<SiteView> sites, Map<ResourceLocation, Blueprint> blueprints) implements CustomPacketPayload {
        public static final Type<ShowSites> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "show_sites"));
        public static final StreamCodec<FriendlyByteBuf, ShowSites> STREAM_CODEC = StreamCodec.composite(
                SiteView.STREAM_CODEC.apply(ByteBufCodecs.list()), ShowSites::sites,
                ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC.cast(), BlueprintCodec.STREAM_CODEC), ShowSites::blueprints,
                ShowSites::new);

        @Override
        public Type<ShowSites> type() {
            return TYPE;
        }
    }

    // Client to server: "send me my faction's buildings around me", to pick one to demolish.
    public record RequestBuildings() implements CustomPacketPayload {
        public static final Type<RequestBuildings> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "request_buildings"));
        public static final StreamCodec<FriendlyByteBuf, RequestBuildings> STREAM_CODEC = StreamCodec.unit(new RequestBuildings());

        @Override
        public Type<RequestBuildings> type() {
            return TYPE;
        }
    }

    // One of the faction's buildings as Demolish shows it: its name, template corner (which names it
    // in a demolish request), the box it takes up, and whether it's finished or already coming down.
    public record BuildingView(String name, BlockPos origin, BlockPos min, BlockPos max, boolean built, boolean demolishing) {
        public static final StreamCodec<FriendlyByteBuf, BuildingView> STREAM_CODEC = StreamCodec.of((buf, view) -> {
            buf.writeUtf(view.name());
            buf.writeBlockPos(view.origin());
            buf.writeBlockPos(view.min());
            buf.writeBlockPos(view.max());
            buf.writeBoolean(view.built());
            buf.writeBoolean(view.demolishing());
        }, buf -> new BuildingView(buf.readUtf(), buf.readBlockPos(), buf.readBlockPos(), buf.readBlockPos(), buf.readBoolean(), buf.readBoolean()));
    }

    // Server to client: the faction's buildings around the player.
    public record ShowBuildings(List<BuildingView> buildings) implements CustomPacketPayload {
        public static final Type<ShowBuildings> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "show_buildings"));
        public static final StreamCodec<FriendlyByteBuf, ShowBuildings> STREAM_CODEC = BuildingView.STREAM_CODEC.apply(ByteBufCodecs.list())
                .map(ShowBuildings::new, ShowBuildings::buildings);

        @Override
        public Type<ShowBuildings> type() {
            return TYPE;
        }
    }

    // Client to server: "show me the borders around me" (on), or "stop" (off). While it's on, the
    // server sends them again when they change or the player has moved on (BorderViewers).
    public record ToggleBorders(boolean on) implements CustomPacketPayload {
        public static final Type<ToggleBorders> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "toggle_borders"));
        public static final StreamCodec<FriendlyByteBuf, ToggleBorders> STREAM_CODEC = ByteBufCodecs.BOOL
                .map(ToggleBorders::new, ToggleBorders::on).cast();

        @Override
        public Type<ToggleBorders> type() {
            return TYPE;
        }
    }

    // A settlement's claim as the borders view shows it: its name, its owner's name ("" if none),
    // the owner's colour (0xRRGGBB, FactionColors), whether it's the viewer's own faction's, and its
    // claimed chunks (packed, ChunkPos#toLong).
    public record BorderView(String settlement, String faction, int color, boolean own, long[] chunks) {
        public static final StreamCodec<FriendlyByteBuf, BorderView> STREAM_CODEC = StreamCodec.of((buf, view) -> {
            buf.writeUtf(view.settlement());
            buf.writeUtf(view.faction());
            buf.writeInt(view.color());
            buf.writeBoolean(view.own());
            buf.writeLongArray(view.chunks());
        }, buf -> new BorderView(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readBoolean(), buf.readLongArray()));
    }

    // Server to client: the claims around the player, in the dimension they're in.
    public record ShowBorders(ResourceLocation dimension, List<BorderView> borders) implements CustomPacketPayload {
        public static final Type<ShowBorders> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "show_borders"));
        public static final StreamCodec<FriendlyByteBuf, ShowBorders> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, ShowBorders::dimension,
                BorderView.STREAM_CODEC.apply(ByteBufCodecs.list()), ShowBorders::borders,
                ShowBorders::new);

        @Override
        public Type<ShowBorders> type() {
            return TYPE;
        }
    }

    // Client to server: "demolish the building with its template corner here".
    public record DemolishBuilding(BlockPos origin) implements CustomPacketPayload {
        public static final Type<DemolishBuilding> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "demolish_building"));
        public static final StreamCodec<FriendlyByteBuf, DemolishBuilding> STREAM_CODEC = BlockPos.STREAM_CODEC
                .map(DemolishBuilding::new, DemolishBuilding::origin).cast();

        @Override
        public Type<DemolishBuilding> type() {
            return TYPE;
        }
    }

    // Client to server: "stop demolishing the building with its template corner here".
    public record StopDemolition(BlockPos origin) implements CustomPacketPayload {
        public static final Type<StopDemolition> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "stop_demolition"));
        public static final StreamCodec<FriendlyByteBuf, StopDemolition> STREAM_CODEC = BlockPos.STREAM_CODEC
                .map(StopDemolition::new, StopDemolition::origin).cast();

        @Override
        public Type<StopDemolition> type() {
            return TYPE;
        }
    }

    // Server to client: whether a demolish or stop request went through (the player is told in words
    // on the action bar).
    public record DemolishAnswer(boolean done) implements CustomPacketPayload {
        public static final Type<DemolishAnswer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "demolish_answer"));
        public static final StreamCodec<FriendlyByteBuf, DemolishAnswer> STREAM_CODEC = ByteBufCodecs.BOOL
                .map(DemolishAnswer::new, DemolishAnswer::done).cast();

        @Override
        public Type<DemolishAnswer> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        // Every request is timed as one Perf section (kingdomrts:nation_request).
        registrar.playToServer(OpenNation.TYPE, OpenNation.STREAM_CODEC,
                (payload, context) -> timed(context, NationRequests::open));
        registrar.playToServer(RequestCatalog.TYPE, RequestCatalog.STREAM_CODEC,
                (payload, context) -> timed(context, NationRequests::catalog));
        registrar.playToServer(RequestBlueprint.TYPE, RequestBlueprint.STREAM_CODEC,
                (payload, context) -> timed(context, player -> NationRequests.blueprint(player, payload.building())));
        registrar.playToServer(CommissionBuilding.TYPE, CommissionBuilding.STREAM_CODEC,
                (payload, context) -> timed(context, player -> NationRequests.commission(player, payload)));
        registrar.playToServer(RequestSites.TYPE, RequestSites.STREAM_CODEC,
                (payload, context) -> timed(context, NationRequests::sites));
        registrar.playToServer(RequestBuildings.TYPE, RequestBuildings.STREAM_CODEC,
                (payload, context) -> timed(context, NationRequests::buildings));
        registrar.playToServer(DemolishBuilding.TYPE, DemolishBuilding.STREAM_CODEC,
                (payload, context) -> timed(context, player -> NationRequests.demolish(player, payload.origin())));
        registrar.playToServer(StopDemolition.TYPE, StopDemolition.STREAM_CODEC,
                (payload, context) -> timed(context, player -> NationRequests.stopDemolition(player, payload.origin())));
        registrar.playToServer(ToggleBorders.TYPE, ToggleBorders.STREAM_CODEC,
                (payload, context) -> timed(context, player -> BorderViewers.toggle(player, payload.on())));
        registrar.playToServer(CheckPlacement.TYPE, CheckPlacement.STREAM_CODEC,
                (payload, context) -> timed(context, player -> NationRequests.checkPlacement(player, payload)));
        // These lambdas only touch the client-only classes (NationClient, BuildPreviewClient,
        // DemolishClient, BordersClient) when a payload arrives, which only happens on a client, so a dedicated
        // server never loads them.
        registrar.playToClient(ShowNation.TYPE, ShowNation.STREAM_CODEC, (payload, context) -> NationClient.show(payload.overview()));
        registrar.playToClient(ShowCatalog.TYPE, ShowCatalog.STREAM_CODEC, (payload, context) -> NationClient.showCatalog(payload.entries()));
        registrar.playToClient(ShowBlueprint.TYPE, ShowBlueprint.STREAM_CODEC, (payload, context) -> BuildPreviewClient.start(payload));
        registrar.playToClient(CommissionAnswer.TYPE, CommissionAnswer.STREAM_CODEC, (payload, context) -> BuildPreviewClient.commissionAnswered(payload));
        registrar.playToClient(ShowSites.TYPE, ShowSites.STREAM_CODEC, (payload, context) -> BuildPreviewClient.showSites(payload));
        registrar.playToClient(ShowBuildings.TYPE, ShowBuildings.STREAM_CODEC, (payload, context) -> DemolishClient.showBuildings(payload.buildings()));
        registrar.playToClient(DemolishAnswer.TYPE, DemolishAnswer.STREAM_CODEC, (payload, context) -> DemolishClient.demolishAnswered(payload.done()));
        registrar.playToClient(ShowBorders.TYPE, ShowBorders.STREAM_CODEC, (payload, context) -> BordersClient.show(payload));
        registrar.playToClient(PlacementAnswer.TYPE, PlacementAnswer.STREAM_CODEC, (payload, context) -> BuildPreviewClient.placementAnswered(payload));
    }

    // Runs a request's handler for the player who sent it, inside the request Perf section.
    private static void timed(IPayloadContext context, Consumer<ServerPlayer> handler) {
        ServerPlayer player = (ServerPlayer) context.player();
        long start = Perf.start(player.serverLevel(), Perf.Section.NATION_REQUEST);
        try {
            handler.accept(player);
        } finally {
            Perf.end(player.serverLevel(), Perf.Section.NATION_REQUEST, start);
        }
    }
}
