package com.krisamerman.kingdomrts.nation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.building.BuildingTemplates;
import com.krisamerman.kingdomrts.building.BuildingType;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    // The faction the player acts for: the one they belong to, or null if they have none. For now
    // every member can use the interface; ranks within a faction are open.
    @Nullable
    public static Faction actingFaction(ServerPlayer player) {
        return FactionData.get(player.serverLevel()).factionOf(player.getUUID());
    }

    // Why the player can't act on this settlement through the interface, or null if they can: it
    // must exist and belong to their faction. Requests that act on a settlement or one of its
    // buildings (commissioning, demolishing) check this before running their operation.
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
