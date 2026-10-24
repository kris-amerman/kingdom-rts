package com.krisamerman.kingdomrts.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

class BuildingPickerTest {
    private static final Vec3 EYE = new Vec3(0.5, 65.6, 0.5);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final AABB NEAR = new AABB(5, 64, -2, 10, 68, 3);
    private static final AABB FAR = new AABB(20, 64, -2, 25, 68, 3);
    private static final AABB NORTH = new AABB(-2, 64, -20, 3, 68, -15);

    @Test
    void theNearestBoxInTheLineOfSightIsPicked() {
        assertEquals(0, BuildingPicker.pick(EYE, EAST, 64, List.of(NEAR, FAR, NORTH)));
        assertEquals(1, BuildingPicker.pick(EYE, EAST, 64, List.of(FAR, NEAR, NORTH)), "Whatever order they come in");
    }

    @Test
    void nothingIsPickedOutOfRangeOrOutOfSight() {
        assertEquals(-1, BuildingPicker.pick(EYE, EAST, 4, List.of(NEAR)), "Too far");
        assertEquals(-1, BuildingPicker.pick(EYE, new Vec3(-1, 0, 0), 64, List.of(NEAR, FAR)), "Behind the player");
        assertEquals(-1, BuildingPicker.pick(EYE, EAST, 64, List.of()), "No buildings");
    }

    @Test
    void standingInsideABuildingPicksIt() {
        AABB around = new AABB(-3, 64, -3, 4, 68, 4);
        assertEquals(1, BuildingPicker.pick(EYE, EAST, 64, List.of(NEAR, around)));
    }
}
