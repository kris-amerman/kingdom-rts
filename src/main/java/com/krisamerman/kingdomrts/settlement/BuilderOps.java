package com.krisamerman.kingdomrts.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.building.BuildingType;
import com.krisamerman.kingdomrts.unit.Occupation;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.server.level.ServerLevel;

// A settlement's builders, as actions of its faction: firing them, and assigning them to
// construction sites. Builders are hired at a builders' hut, a workplace like any other (see
// WorkplaceOps). Their assignments are kept on the buildings (see Building#builders), so they're
// known while unloaded.
public final class BuilderOps {
    private BuilderOps() {
    }

    // A builder, as the panels list them.
    public record Listed(UUID id, String name) {
    }

    // Fires a builder: it's taken off its site and leaves its builders' hut, with no occupation any
    // more. An unloaded builder loses the job when it next loads (see WorkplaceOps#catchUp).
    public static void fire(ServerLevel level, Settlement settlement, UUID builder) {
        unassignEverywhere(level, settlement, builder);
        for (Workplace workplace : settlement.workplaces()) {
            if (workplace.workers().containsKey(builder)) {
                WorkplaceOps.fire(level, workplace, builder);
                return;
            }
        }
        if (level.getEntity(builder) instanceof Unit unit && Occupation.BUILDER.equals(unit.getOccupationId())) {
            unit.setOccupation(null); // a builder with no hut (see SettlementOps#catchUp)
            SettlementOps.refreshResident(level, unit);
        }
    }

    // Whether the unit is one of the settlement's builders (as it is now if loaded, else as last seen).
    public static boolean isBuilder(ServerLevel level, Settlement settlement, UUID id) {
        if (!settlement.units().contains(id)) {
            return false;
        }
        if (level.getEntity(id) instanceof Unit unit) {
            return Occupation.BUILDER.equals(unit.getOccupationId());
        }
        Resident resident = settlement.residents().get(id);
        return resident != null && Occupation.BUILDER.equals(resident.occupation());
    }

    // The settlement's builders, by name.
    public static List<Listed> builders(ServerLevel level, Settlement settlement) {
        List<Listed> builders = new ArrayList<>();
        settlement.residents().forEach((id, resident) -> {
            if (isBuilder(level, settlement, id)) {
                builders.add(new Listed(id, nameOf(level, id, resident)));
            }
        });
        builders.sort(Comparator.comparing((Listed builder) -> builder.name().toLowerCase(Locale.ROOT)).thenComparing(Listed::id));
        return builders;
    }

    // The settlement's builders not working on any site.
    public static List<Listed> idleBuilders(ServerLevel level, Settlement settlement) {
        return builders(level, settlement).stream().filter(builder -> siteOf(settlement, builder.id()) == null).toList();
    }

    // The site a builder is working on, if any.
    @Nullable
    public static Building siteOf(Settlement settlement, UUID builder) {
        for (Building building : settlement.buildings()) {
            if (building.takesBuilders() && building.builders().containsKey(builder)) {
                return building;
            }
        }
        return null;
    }

    // Assigns one of the settlement's builders to a site. Returns why it can't be, or null.
    @Nullable
    public static String assign(ServerLevel level, Settlement settlement, Building building, UUID builder) {
        Building current = settlement.buildingAt(building.origin());
        if (current == null) {
            return "That building is no longer commissioned";
        }
        String name = nameOf(level, builder, settlement.residents().get(builder));
        Building busy = siteOf(settlement, builder);
        int max = BuildingType.get(level, current.type()).map(BuildingType::maxBuilders).orElse(BuildingType.DEFAULT_MAX_BUILDERS);
        // A built building whose stock is still being cleared out, or that's being demolished, can still take builders.
        boolean closed = !current.takesBuilders();
        String problem = BuilderRules.assignProblem(name, isBuilder(level, settlement, builder), typeName(level, current), closed,
                busy != null ? typeName(level, busy) : null, current.builders().size(), max);
        if (problem != null) {
            return problem;
        }
        SettlementData.get(level).assignBuilder(settlement, current, builder, name);
        return null;
    }

    // Takes a builder off a site. Returns false if it wasn't assigned there.
    public static boolean unassign(ServerLevel level, Settlement settlement, Building building, UUID builder) {
        Building current = settlement.buildingAt(building.origin());
        if (current == null || !current.builders().containsKey(builder)) {
            return false;
        }
        SettlementData.get(level).unassignBuilder(settlement, current, builder);
        return true;
    }

    // Takes a unit off whatever site it's assigned to (it died, left, or was fired).
    static void unassignEverywhere(ServerLevel level, Settlement settlement, UUID unit) {
        for (Building building : List.copyOf(settlement.buildings())) {
            if (building.builders().containsKey(unit)) {
                SettlementData.get(level).unassignBuilder(settlement, building, unit);
            }
        }
    }

    // How a building is named in messages: its type's name, lower-cased ("storage shed").
    public static String typeName(ServerLevel level, Building building) {
        return BuildingType.get(level, building.type()).map(BuildingType::name).orElse(building.type().getPath().replace('_', ' '))
                .toLowerCase(Locale.ROOT);
    }

    private static String nameOf(ServerLevel level, UUID id, @Nullable Resident resident) {
        if (level.getEntity(id) instanceof Unit unit) {
            return unit.getName().getString();
        }
        return resident != null && !resident.name().isEmpty() ? resident.name() : "Unknown settler";
    }
}
