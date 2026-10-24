package com.krisamerman.kingdomrts.building;

import java.util.List;
import java.util.Optional;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Which building a player is looking at, from their buildings' boxes: the first box the line of
// sight from their eye meets within range, or the one they're standing in. Pure, so it's tested
// without a world; the nation interface's Demolish uses it to pick a building.
public final class BuildingPicker {
    private BuildingPicker() {
    }

    // The index of the box looked at, or -1 for none. look is the direction they face (any length).
    public static int pick(Vec3 eye, Vec3 look, double range, List<AABB> boxes) {
        Vec3 end = eye.add(look.normalize().scale(range));
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < boxes.size(); i++) {
            AABB box = boxes.get(i);
            double distance;
            if (box.contains(eye)) {
                distance = 0;
            } else {
                Optional<Vec3> hit = box.clip(eye, end);
                if (hit.isEmpty()) {
                    continue;
                }
                distance = hit.get().distanceToSqr(eye);
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }
}
