package com.krisamerman.kingdomrts.nation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionColors;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;
import com.krisamerman.kingdomrts.util.Perf;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Server side of the borders view: which players have it on, and sending each of them the claims
// around them. They're sent when the view is switched on, and again only when claims have changed
// (SettlementData#claimsVersion) or the player has moved well away from where they were last sent,
// checked once a second for the few players viewing. Borders are public: anyone can see whose
// land is whose, with or without a faction.
@EventBusSubscriber(modid = KingdomRts.MODID)
public final class BorderViewers {
    // How far around the player claims are sent, in chunks (a square), and the most sent at once.
    public static final int RANGE_CHUNKS = 12;
    public static final int MAX_SETTLEMENTS = 32;
    public static final int MAX_CHUNKS = 1024;
    // How far a player moves (in chunks) before they're sent the claims around them again: well
    // inside RANGE_CHUNKS, so the edge of what they were sent stays out of sight.
    public static final int MOVE_CHUNKS = 4;
    // How often viewers are checked, and the closest together one player is sent borders.
    public static final int CHECK_TICKS = 20;
    public static final int SEND_INTERVAL_TICKS = NationRequests.SITES_INTERVAL_TICKS;

    // What a viewer was last sent: for which claims version, around which chunk, in which dimension.
    private static final class Viewer {
        int version = -1;
        @Nullable
        ChunkPos center;
        @Nullable
        ResourceKey<Level> dimension;
    }

    // Viewers by player id (a player who dies and respawns is a new ServerPlayer). Server thread only.
    private static final Map<UUID, Viewer> VIEWERS = new HashMap<>();
    // When each player was last sent borders (server ticks), kept while the view is off too, so
    // switching it off and on can't get round the interval.
    private static final Map<UUID, Long> LAST_SENT = new HashMap<>();

    private BorderViewers() {
    }

    // The player switched the view on (they're sent the borders now, unless they were just sent
    // some, in which case the next check sends them) or off.
    public static void toggle(ServerPlayer player, boolean on) {
        if (!on) {
            VIEWERS.remove(player.getUUID());
            return;
        }
        Viewer viewer = VIEWERS.computeIfAbsent(player.getUUID(), id -> new Viewer());
        viewer.version = -1; // whatever it was sent, send it again
        long now = player.getServer().getTickCount();
        if (mayBeSent(player, now)) {
            send(player, viewer, now);
        }
    }

    public static boolean isViewing(ServerPlayer player) {
        return VIEWERS.containsKey(player.getUUID());
    }

    // Sends the borders again to each viewer whose claims changed or who moved on. Viewers who have
    // left are dropped. Called once every CHECK_TICKS; public for tests.
    public static void check(MinecraftServer server) {
        if (VIEWERS.isEmpty()) {
            return;
        }
        int version = SettlementData.get(server).claimsVersion();
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, Viewer>> entries = VIEWERS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Viewer> entry = entries.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                entries.remove();
                continue;
            }
            Viewer viewer = entry.getValue();
            ChunkPos at = player.chunkPosition();
            boolean moved = viewer.center == null || viewer.dimension != player.level().dimension()
                    || Math.max(Math.abs(at.x - viewer.center.x), Math.abs(at.z - viewer.center.z)) >= MOVE_CHUNKS;
            if ((moved || viewer.version != version) && mayBeSent(player, now)) {
                send(player, viewer, now);
            }
        }
    }

    private static boolean mayBeSent(ServerPlayer player, long now) {
        Long last = LAST_SENT.get(player.getUUID());
        return last == null || now - last >= SEND_INTERVAL_TICKS;
    }

    private static void send(ServerPlayer player, Viewer viewer, long now) {
        LAST_SENT.put(player.getUUID(), now);
        viewer.version = SettlementData.get(player.getServer()).claimsVersion();
        viewer.center = player.chunkPosition();
        viewer.dimension = player.level().dimension();
        PacketDistributor.sendToPlayer(player, bordersFor(player));
    }

    // The claims around the player, in their dimension: every settlement with a claimed chunk within
    // RANGE_CHUNKS of theirs, nearest first, whole (so no false border shows where the range cuts a
    // claim), up to MAX_SETTLEMENTS and MAX_CHUNKS.
    public static NationPayloads.ShowBorders bordersFor(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        ChunkPos at = player.chunkPosition();
        FactionData factions = FactionData.get(player.getServer());
        Faction mine = factions.factionOf(player.getUUID());
        record Near(Settlement settlement, int distance) {
        }
        List<Near> near = new ArrayList<>();
        for (Settlement settlement : SettlementData.get(player.getServer()).all()) {
            if (settlement.dimension() != dimension) {
                continue;
            }
            int distance = Integer.MAX_VALUE;
            for (ChunkPos chunk : settlement.chunks()) {
                distance = Math.min(distance, Math.max(Math.abs(chunk.x - at.x), Math.abs(chunk.z - at.z)));
            }
            if (distance <= RANGE_CHUNKS) {
                near.add(new Near(settlement, distance));
            }
        }
        near.sort(Comparator.comparingInt(Near::distance));
        List<NationPayloads.BorderView> borders = new ArrayList<>();
        int chunks = 0;
        for (Near entry : near) {
            Settlement settlement = entry.settlement();
            if (borders.size() >= MAX_SETTLEMENTS || chunks + settlement.chunks().size() > MAX_CHUNKS) {
                break;
            }
            chunks += settlement.chunks().size();
            Faction owner = factions.byId(settlement.owner());
            borders.add(new NationPayloads.BorderView(settlement.name(), owner != null ? owner.name() : "", FactionColors.of(settlement.owner()),
                    mine != null && mine.id().equals(settlement.owner()), settlement.chunks().stream().mapToLong(ChunkPos::toLong).toArray()));
        }
        return new NationPayloads.ShowBorders(dimension.location(), borders);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (VIEWERS.isEmpty() || server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        long start = Perf.start(server.overworld(), Perf.Section.NATION_REQUEST);
        try {
            check(server);
        } finally {
            Perf.end(server.overworld(), Perf.Section.NATION_REQUEST, start);
        }
    }

    @SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        VIEWERS.remove(event.getEntity().getUUID());
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    // A single-player game's server stops when the world is closed, and the next world's starts in
    // the same Java process, where this map lives on.
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        VIEWERS.clear();
        LAST_SENT.clear();
    }
}
