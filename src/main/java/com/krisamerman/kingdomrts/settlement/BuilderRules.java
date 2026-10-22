package com.krisamerman.kingdomrts.settlement;

import java.util.UUID;

import javax.annotation.Nullable;

// The rules for a settlement's builders, as pure functions (so they can be unit tested): who can be
// assigned to a construction site. Returns why not, or null. (Hiring is at a builders' hut: WorkplaceRules.)
public final class BuilderRules {
    private BuilderRules() {
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
