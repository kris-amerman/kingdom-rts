package com.krisamerman.kingdomrts.settlement;

import java.util.UUID;

import javax.annotation.Nullable;

// The rule for hiring a unit at a workplace, as a pure function so it can be unit tested.
// A unit can be hired if it belongs to the workplace's settlement, has no occupation yet (so it
// isn't already working somewhere, and isn't a soldier), and the workplace has room.
public final class WorkplaceRules {
    private WorkplaceRules() {
    }

    // Why the unit can't be hired, or null if it can.
    @Nullable
    public static String hireProblem(String unitName, @Nullable UUID unitSettlement, UUID workplaceSettlement, String settlementName,
            boolean hasOccupation, int workers, int capacity, String workplaceName) {
        if (!workplaceSettlement.equals(unitSettlement)) {
            return unitName + " doesn't belong to " + settlementName;
        }
        if (hasOccupation) {
            return unitName + " already has an occupation";
        }
        if (workers >= capacity) {
            return "The " + workplaceName + " is full (" + workers + "/" + capacity + " workers)";
        }
        return null;
    }
}
