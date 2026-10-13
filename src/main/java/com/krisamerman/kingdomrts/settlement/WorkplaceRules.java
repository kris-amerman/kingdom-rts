package com.krisamerman.kingdomrts.settlement;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;

// The rules for hiring a unit at a workplace, as pure functions so they can be unit tested.
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

    // A workplace as job seeking sees it.
    public record Opening(BlockPos pos, int workers, int capacity, boolean autoHire) {
    }

    // Where an unemployed settler at from should work: the nearest workplace with room that hires
    // automatically, or null if there's none.
    @Nullable
    public static BlockPos chooseWorkplace(List<Opening> workplaces, BlockPos from) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Opening workplace : workplaces) {
            double distance = workplace.pos().distSqr(from);
            if (workplace.autoHire() && workplace.workers() < workplace.capacity() && distance < bestDistance) {
                best = workplace.pos();
                bestDistance = distance;
            }
        }
        return best;
    }
}
