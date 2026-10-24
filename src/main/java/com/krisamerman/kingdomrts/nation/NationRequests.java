package com.krisamerman.kingdomrts.nation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.building.BuildingTemplates;
import com.krisamerman.kingdomrts.building.BuildingType;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.settlement.BuilderOps;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

// Server side of the nation management interface: what happens when a player's screen sends a
// request, however it was triggered (the N key, /kingdomrts nation, later a command view).
//
// The interface is one way for a player to run their faction's operations, so every request is
// checked here first: the player must belong to a faction, and anything a request acts on must be
// their faction's. Then the request runs the same faction operation as the matching command, so the
// interface never gets rules of its own (and AI factions, which call the operations directly, play
// by the same ones).
public final class NationRequests {
    private static final String NO_FACTION = "Join or found a faction first";
    // The closest together a player's placement checks are answered. The client asks less often
    // than this (BuildPreviewClient), so this only stops a client that asks too fast.
    public static final int CHECK_INTERVAL_TICKS = 2;
    // When each player's last placement check was answered (game time). Weak keys: a player's entry
    // goes when they leave. Server thread only.
    private static final Map<ServerPlayer, Long> LAST_CHECK = new WeakHashMap<>();
    // Unfinished buildings shown while previewing: how far from the player (in blocks, along the
    // ground), how many at most, and how often a player is answered. The client asks less often.
    public static final int SITES_RANGE = 96;
    public static final int MAX_SITES = 32;
    public static final int SITES_INTERVAL_TICKS = 10;
    private static final Map<ServerPlayer, Long> LAST_SITES = new WeakHashMap<>();
    // The most of a faction's buildings Demolish outlines at once (within SITES_RANGE).
    public static final int MAX_BUILDINGS = 64;
    private static final Map<ServerPlayer, Long> LAST_BUILDINGS = new WeakHashMap<>();

    private NationRequests() {
    }

    // Opens the interface's first screen for the player: their faction's overview, or the "join or
    // found a faction first" screen if they have none.
    public static void open(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new NationPayloads.ShowNation(overview(player)));
    }

    // What the first screen shows the player.
    public static NationOverview overview(ServerPlayer player) {
        Faction faction = actingFaction(player);
        if (faction == null) {
            return NationOverview.NO_FACTION;
        }
        List<NationOverview.SettlementLine> settlements = SettlementData.get(player.serverLevel()).all().stream()
                .filter(settlement -> faction.id().equals(settlement.owner()))
                .sorted(Comparator.comparing((Settlement settlement) -> settlement.name().toLowerCase(Locale.ROOT)))
                .map(settlement -> new NationOverview.SettlementLine(settlement.name(), settlement.dimension().location().toString(),
                        settlement.townHall(), settlement.chunks().size(), settlement.units().size()))
                .toList();
        return new NationOverview(faction.name(), player.getUUID().equals(faction.leader()), settlements);
    }

    // Sends the player the Build catalog: every building whose template can be built, by name.
    // Refused (with a message) for players without a faction.
    public static void catalog(ServerPlayer player) {
        if (actingFaction(player) == null) {
            player.sendSystemMessage(Component.literal(NO_FACTION));
            return;
        }
        PacketDistributor.sendToPlayer(player, new NationPayloads.ShowCatalog(catalogEntries(player.serverLevel())));
    }

    // Every building in the catalog. A building whose template is missing or unusable is left out
    // (BuildingTemplates logs why).
    public static List<CatalogEntry> catalogEntries(ServerLevel level) {
        List<CatalogEntry> entries = new ArrayList<>();
        level.registryAccess().registry(BuildingType.REGISTRY_KEY).ifPresent(registry -> registry.entrySet().forEach(entry ->
                BuildingTemplates.blueprint(level, entry.getValue()).ifPresent(blueprint ->
                        entries.add(new CatalogEntry(entry.getKey().location(), entry.getValue().name(), blueprint.size())))));
        entries.sort(Comparator.comparing((CatalogEntry entry) -> entry.name().toLowerCase(Locale.ROOT)).thenComparing(entry -> entry.id().toString()));
        return entries;
    }

    // Sends the player a building's blueprint, to preview it. Refused (with a message) for players
    // without a faction and for buildings that can't be built.
    public static void blueprint(ServerPlayer player, ResourceLocation building) {
        if (actingFaction(player) == null) {
            player.sendSystemMessage(Component.literal(NO_FACTION));
            return;
        }
        ServerLevel level = player.serverLevel();
        Optional<BuildingType> type = BuildingType.get(level, building);
        Optional<Blueprint> blueprint = type.flatMap(found -> BuildingTemplates.blueprint(level, found));
        if (blueprint.isEmpty()) {
            player.sendSystemMessage(Component.literal("Can't preview " + building + ": there's no such building to build"));
            return;
        }
        PacketDistributor.sendToPlayer(player, new NationPayloads.ShowBlueprint(building, type.get().name(), blueprint.get()));
    }

    // Commissions a building from a confirmed preview: the same operation as /kingdomrts building
    // commission, for the player's faction, so every claim and overlap rule applies. The player is
    // told how it went on the action bar, and their preview whether to end.
    public static void commission(ServerPlayer player, NationPayloads.CommissionBuilding request) {
        Faction faction = actingFaction(player);
        if (faction == null) {
            answerCommission(player, false, NO_FACTION);
            return;
        }
        ServerLevel level = player.serverLevel();
        BuildingOps.CommissionResult result = BuildingOps.commission(level, faction, request.building(), request.origin(), request.rotation());
        if (result.building() == null) {
            answerCommission(player, false, "Can't build there: " + result.problem());
            return;
        }
        Settlement settlement = SettlementData.get(level).claimAt(level.dimension(), new ChunkPos(request.origin()));
        String name = BuildingType.get(level, request.building()).map(BuildingType::name).orElse(request.building().toString());
        answerCommission(player, true, name + " commissioned" + (settlement != null ? " in " + settlement.name() : ""));
    }

    private static void answerCommission(ServerPlayer player, boolean commissioned, String message) {
        player.displayClientMessage(Component.literal(message), true);
        PacketDistributor.sendToPlayer(player, new NationPayloads.CommissionAnswer(commissioned));
    }

    // Tells the player whether a building could go where their preview is, without commissioning
    // it, so the ghost can show it. Asked as the preview moves, so it's rate limited: a player's
    // checks closer together than CHECK_INTERVAL_TICKS are dropped (the client asks again).
    public static void checkPlacement(ServerPlayer player, NationPayloads.CheckPlacement request) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_CHECK.get(player);
        if (last != null && now - last < CHECK_INTERVAL_TICKS && now >= last) {
            return;
        }
        LAST_CHECK.put(player, now);
        Faction faction = actingFaction(player);
        String problem = faction == null ? NO_FACTION
                : BuildingOps.commissionProblem(player.serverLevel(), faction, request.building(), request.origin(), request.rotation());
        PacketDistributor.sendToPlayer(player, new NationPayloads.PlacementAnswer(request.building(), request.origin(), request.rotation(),
                problem != null ? problem : ""));
    }

    // Sends the player their faction's unfinished buildings around them (not ones being torn down),
    // nearest first, with each kind's blueprint, so a preview can show them finished. Players
    // without a faction get none. Rate limited like checks (SITES_INTERVAL_TICKS).
    public static void sites(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_SITES.get(player);
        if (last != null && now - last < SITES_INTERVAL_TICKS && now >= last) {
            return;
        }
        LAST_SITES.put(player, now);
        PacketDistributor.sendToPlayer(player, sitesFor(player));
    }

    public static NationPayloads.ShowSites sitesFor(ServerPlayer player) {
        Faction faction = actingFaction(player);
        if (faction == null) {
            return new NationPayloads.ShowSites(List.of(), Map.of());
        }
        ServerLevel level = player.serverLevel();
        List<Building> nearby = new ArrayList<>();
        for (Settlement settlement : SettlementData.get(level).all()) {
            if (!faction.id().equals(settlement.owner()) || !settlement.dimension().equals(level.dimension())) {
                continue;
            }
            for (Building building : settlement.buildings()) {
                if (!building.isBuilt() && !building.demolishing() && distanceSqr(player, building) <= SITES_RANGE * SITES_RANGE) {
                    nearby.add(building);
                }
            }
        }
        nearby.sort(Comparator.comparingDouble(building -> distanceSqr(player, building)));
        List<NationPayloads.SiteView> sites = new ArrayList<>();
        Map<ResourceLocation, Blueprint> blueprints = new HashMap<>();
        for (Building building : nearby.subList(0, Math.min(MAX_SITES, nearby.size()))) {
            Optional<Blueprint> blueprint = BuildingType.get(level, building.type()).flatMap(type -> BuildingTemplates.blueprint(level, type));
            if (blueprint.isPresent()) {
                sites.add(new NationPayloads.SiteView(building.type(), building.origin(), building.rotation()));
                blueprints.put(building.type(), blueprint.get());
            }
        }
        return new NationPayloads.ShowSites(sites, blueprints);
    }

    // Sends the player their faction's buildings around them (finished, being built or coming down),
    // nearest first, so Demolish can outline them. Players without a faction get none. Rate limited
    // like the sites (SITES_INTERVAL_TICKS).
    public static void buildings(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_BUILDINGS.get(player);
        if (last != null && now - last < SITES_INTERVAL_TICKS && now >= last) {
            return;
        }
        LAST_BUILDINGS.put(player, now);
        PacketDistributor.sendToPlayer(player, buildingsFor(player));
    }

    public static NationPayloads.ShowBuildings buildingsFor(ServerPlayer player) {
        Faction faction = actingFaction(player);
        if (faction == null) {
            return new NationPayloads.ShowBuildings(List.of());
        }
        ServerLevel level = player.serverLevel();
        List<Building> nearby = new ArrayList<>();
        for (Settlement settlement : SettlementData.get(level).all()) {
            if (faction.id().equals(settlement.owner()) && settlement.dimension().equals(level.dimension())) {
                settlement.buildings().stream().filter(building -> distanceSqr(player, building) <= SITES_RANGE * SITES_RANGE).forEach(nearby::add);
            }
        }
        nearby.sort(Comparator.comparingDouble(building -> distanceSqr(player, building)));
        return new NationPayloads.ShowBuildings(nearby.subList(0, Math.min(MAX_BUILDINGS, nearby.size())).stream()
                .map(building -> new NationPayloads.BuildingView(BuildingType.get(level, building.type()).map(BuildingType::name).orElse(building.type().toString()),
                        building.origin(), new BlockPos(building.footprint().minX(), building.footprint().minY(), building.footprint().minZ()),
                        new BlockPos(building.footprint().maxX(), building.footprint().maxY(), building.footprint().maxZ()), building.isBuilt(),
                        building.demolishing()))
                .toList());
    }

    // Orders the building with its template corner at origin demolished, from Demolish: the same
    // operation as /kingdomrts building demolish, for the player's faction, on one of its own
    // settlements only. The settlement's idle builders are put on it straight away (owner's call,
    // S-035), up to as many as it takes. The player is told how it went on the action bar.
    public static void demolish(ServerPlayer player, BlockPos origin) {
        ServerLevel level = player.serverLevel();
        Settlement settlement = SettlementData.get(level).claimAt(level.dimension(), new ChunkPos(origin));
        Building building = settlement != null ? settlement.buildingAt(origin) : null;
        String problem = settlementProblem(player, settlement);
        if (problem == null && building == null) {
            problem = "There's no building there to demolish";
        }
        if (problem == null) {
            problem = BuildingOps.orderDemolition(level, actingFaction(player), settlement, building);
        }
        if (problem != null) {
            answerDemolish(player, false, "Can't demolish that: " + problem);
            return;
        }
        Settlement current = SettlementData.get(level).byId(settlement.id());
        int builders = current != null ? BuilderOps.assignIdle(level, current, current.buildingAt(origin)) : 0;
        String name = BuilderOps.typeName(level, building);
        String who = builders == 0 ? "no builders are free: assign some when they are"
                : builders + (builders == 1 ? " builder is" : " builders are") + " taking it apart";
        answerDemolish(player, true, "Demolishing the " + name + ": " + who);
    }

    // Stops demolishing the building with its template corner at origin, from Demolish: the same
    // operation as /kingdomrts building cancel on a building coming down, for the player's faction,
    // on one of its own settlements only. A site keeps its builders; a built building's are free again.
    public static void stopDemolition(ServerPlayer player, BlockPos origin) {
        ServerLevel level = player.serverLevel();
        Settlement settlement = SettlementData.get(level).claimAt(level.dimension(), new ChunkPos(origin));
        Building building = settlement != null ? settlement.buildingAt(origin) : null;
        String problem = settlementProblem(player, settlement);
        if (problem == null && building == null) {
            problem = "There's no building there";
        }
        if (problem == null) {
            problem = BuildingOps.stopDemolition(level, actingFaction(player), settlement, building);
        }
        if (problem != null) {
            answerDemolish(player, false, "Can't stop that: " + problem);
            return;
        }
        answerDemolish(player, true, "Stopped demolishing the " + BuilderOps.typeName(level, building));
    }

    private static void answerDemolish(ServerPlayer player, boolean done, String message) {
        player.displayClientMessage(Component.literal(message), true);
        PacketDistributor.sendToPlayer(player, new NationPayloads.DemolishAnswer(done));
    }

    // How far a building's middle is from the player, squared, along the ground.
    private static double distanceSqr(ServerPlayer player, Building building) {
        double dx = building.footprint().getCenter().getX() + 0.5 - player.getX();
        double dz = building.footprint().getCenter().getZ() + 0.5 - player.getZ();
        return dx * dx + dz * dz;
    }

    // The faction the player acts for: the one they belong to, or null if they have none. For now
    // every member can use the interface; ranks within a faction are open.
    @Nullable
    public static Faction actingFaction(ServerPlayer player) {
        return FactionData.get(player.serverLevel()).factionOf(player.getUUID());
    }

    // Why the player can't act on this settlement through the interface, or null if they can: it
    // must exist and belong to their faction. Requests that act on a settlement or one of its
    // buildings (commissioning, demolishing, stopping a demolition) check this before running their operation.
    @Nullable
    public static String settlementProblem(ServerPlayer player, @Nullable Settlement settlement) {
        Faction faction = actingFaction(player);
        if (faction == null) {
            return NO_FACTION;
        }
        if (settlement == null) {
            return "That settlement doesn't exist";
        }
        if (!faction.id().equals(settlement.owner())) {
            return settlement.name() + " isn't " + faction.name() + "'s";
        }
        return null;
    }
}
