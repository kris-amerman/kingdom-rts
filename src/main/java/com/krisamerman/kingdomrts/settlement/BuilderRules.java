package com.krisamerman.kingdomrts.settlement;

import java.util.UUID;

import javax.annotation.Nullable;

// The rules for a settlement's builders, as pure functions (so they can be unit tested): who can be
// hired as a builder, and who can be assigned to a construction site. Each returns why not, or null.
public final class BuilderRules {
    private BuilderRules() {
    }

    // A unit can become one of a settlement's builders if it belongs to the settlement and has no
    // occupation (a worker or soldier keeps its own).
    @Nullable
    public static String hireProblem(String unitName, @Nullable UUID unitSettlement, UUID settlementId, String settlementName,
            boolean hasOccupation) {
        if (!settlementId.equals(unitSettlement)) {
            return unitName + " doesn't belong to " + settlementName;
        }
        if (hasOccupation) {
            return unitName + " already has a job";
        }
        return null;
    }

    // A builder can be assigned to a site if it's one of the settlement's builders, isn't working on
    // a site already, and the site is still being built and has room (maxBuilders).
    @Nullable
    public static String assignProblem(String unitName, boolean isBuilder, String buildingName, boolean built,
            @Nullable String busyAt, int assigned, int maxBuilders) {
        if (!isBuilder) {
            return unitName + " isn't one of the settlement's builders";
        }
        if (built) {
            return "The " + buildingName + " is already built";
        }
        if (busyAt != null) {
            return unitName + " is already working on the " + busyAt;
        }
        if (assigned >= maxBuilders) {
            return "The " + buildingName + " already has " + maxBuilders + " builders";
        }
        return null;
    }
}
