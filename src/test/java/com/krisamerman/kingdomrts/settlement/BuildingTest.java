package com.krisamerman.kingdomrts.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

class BuildingTest {
    @Test
    void aSiteSurvivesSaving() {
        Building building = new Building(ResourceLocation.parse("kingdomrts:storage_shed"), new BlockPos(10, 64, -20), Rotation.CLOCKWISE_180,
                new BoundingBox(10, 64, -20, 14, 67, -16), 1234L);
        assertEquals(building, Building.load(building.save()));
    }
}
