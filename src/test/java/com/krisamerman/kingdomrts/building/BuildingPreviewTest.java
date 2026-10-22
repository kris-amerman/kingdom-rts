package com.krisamerman.kingdomrts.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

class BuildingPreviewTest {
    private static final BlockPos ANCHOR = new BlockPos(100, 64, -40);

    // A 5x3x4 blueprint (even depth, so turning it moves its box around the pivot), with a block in
    // each bottom corner and one in the middle of the bottom layer.
    private static BuildingPreview preview() {
        List<Blueprint.Part> parts = new ArrayList<>();
        for (BlockPos corner : List.of(new BlockPos(0, 0, 0), new BlockPos(4, 0, 0), new BlockPos(0, 0, 3), new BlockPos(4, 0, 3))) {
            parts.add(new Blueprint.Part(corner, Blocks.OAK_PLANKS.defaultBlockState()));
        }
        parts.add(new Blueprint.Part(new BlockPos(2, 0, 1), Blocks.STONE.defaultBlockState()));
        parts.add(new Blueprint.Part(new BlockPos(2, 2, 1), Blocks.OAK_SLAB.defaultBlockState()));
        return new BuildingPreview(ResourceLocation.parse("kingdomrts:test"), "Test", new Blueprint(new Vec3i(5, 3, 4), parts));
    }

    @Test
    void theBuildingStandsOnWhereThePlayerLooksHoweverItsTurned() {
        BuildingPreview preview = preview();
        for (int turns = 0; turns < 4; turns++) {
            List<Blueprint.Part> placed = BuildingRules.place(preview.blueprint(), preview.origin(ANCHOR), preview.rotation());
            assertTrue(placed.contains(new Blueprint.Part(ANCHOR, Blocks.STONE.defaultBlockState())),
                    "The middle of the bottom layer is on the anchor, turned " + preview.rotation() + ": " + placed);
            BoundingBox box = preview.footprint(ANCHOR);
            assertEquals(ANCHOR.getY(), box.minY(), "Its bottom layer is level with the anchor");
            assertTrue(box.isInside(ANCHOR), "Its box covers the anchor");
            preview.rotate();
        }
        assertEquals(Rotation.NONE, preview.rotation(), "Four quarter turns come back round");
    }

    @Test
    void theGhostsBlocksAreTheBuildingsBlocks() {
        BuildingPreview preview = preview();
        for (int turns = 0; turns < 4; turns++) {
            BlockPos origin = preview.origin(ANCHOR);
            List<Blueprint.Part> moved = preview.turnedParts().stream().map(part -> new Blueprint.Part(part.pos().offset(origin), part.state())).toList();
            assertEquals(BuildingRules.place(preview.blueprint(), origin, preview.rotation()), moved,
                    "What's drawn is what would be built, turned " + preview.rotation());
            for (Blueprint.Part part : moved) {
                assertTrue(preview.footprint(ANCHOR).isInside(part.pos()), part + " is inside the box");
            }
            preview.rotate();
        }
    }

    @Test
    void nudgesMoveRelativeToWhereThePlayerFaces() {
        assertEquals(Direction.EAST, BuildingPreview.direction(BuildingPreview.Nudge.FORWARD, Direction.EAST));
        assertEquals(Direction.WEST, BuildingPreview.direction(BuildingPreview.Nudge.BACK, Direction.EAST));
        assertEquals(Direction.NORTH, BuildingPreview.direction(BuildingPreview.Nudge.LEFT, Direction.EAST));
        assertEquals(Direction.SOUTH, BuildingPreview.direction(BuildingPreview.Nudge.RIGHT, Direction.EAST));
        assertEquals(Direction.WEST, BuildingPreview.direction(BuildingPreview.Nudge.LEFT, Direction.NORTH));
        assertEquals(Direction.UP, BuildingPreview.direction(BuildingPreview.Nudge.UP, Direction.SOUTH));
        assertEquals(Direction.DOWN, BuildingPreview.direction(BuildingPreview.Nudge.DOWN, Direction.SOUTH));
    }

    @Test
    void nudgingMovesTheWholeBuildingAndStaysWhileTurning() {
        BuildingPreview preview = preview();
        BoundingBox before = preview.footprint(ANCHOR);
        preview.nudge(BuildingPreview.Nudge.FORWARD, Direction.NORTH);
        preview.nudge(BuildingPreview.Nudge.FORWARD, Direction.NORTH);
        preview.nudge(BuildingPreview.Nudge.UP, Direction.NORTH);
        assertEquals(new BlockPos(0, 1, -2), preview.offset());
        assertEquals(before.moved(0, 1, -2), preview.footprint(ANCHOR), "Two blocks north and one up");
        preview.rotate();
        assertEquals(new BlockPos(0, 1, -2), preview.offset(), "Turning keeps the nudge");
        assertTrue(BuildingRules.place(preview.blueprint(), preview.origin(ANCHOR), preview.rotation())
                .contains(new Blueprint.Part(ANCHOR.offset(0, 1, -2), Blocks.STONE.defaultBlockState())), "and still turns on the nudged spot");
    }
}
