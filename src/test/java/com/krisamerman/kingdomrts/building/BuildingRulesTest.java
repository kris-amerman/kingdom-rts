package com.krisamerman.kingdomrts.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

class BuildingRulesTest {
    private static final BlockState DOOR_NORTH = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);

    // A 3x1x3 blueprint: a door at its north-west corner, stone in the middle.
    private static Blueprint small() {
        return new Blueprint(new Vec3i(3, 1, 3), List.of(
                new Blueprint.Part(new BlockPos(0, 0, 0), DOOR_NORTH),
                new Blueprint.Part(new BlockPos(1, 0, 1), Blocks.STONE.defaultBlockState())));
    }

    @Test
    void placingTurnsTheBuildingInPlace() {
        BlockPos origin = new BlockPos(100, 64, 200);
        for (Rotation rotation : Rotation.values()) {
            BoundingBox box = BuildingRules.footprint(new Vec3i(3, 1, 3), origin, rotation);
            assertEquals(new BoundingBox(100, 64, 200, 102, 64, 202), box, "A square footprint stays put when turned " + rotation);
            for (Blueprint.Part part : BuildingRules.place(small(), origin, rotation)) {
                assertTrue(box.isInside(part.pos()), part + " should be inside the footprint");
            }
        }
        List<Blueprint.Part> turned = BuildingRules.place(small(), origin, Rotation.CLOCKWISE_90);
        assertEquals(new BlockPos(102, 64, 200), turned.get(0).pos(), "The north-west corner turns to the north-east");
        assertEquals(Direction.EAST, turned.get(0).state().getValue(DoorBlock.FACING), "And the door turns with it");
        assertEquals(new BlockPos(101, 64, 201), turned.get(1).pos(), "The middle stays in the middle");
    }

    @Test
    void anOblongFootprintSwapsItsSidesWhenTurned() {
        BoundingBox box = BuildingRules.footprint(new Vec3i(5, 2, 3), BlockPos.ZERO, Rotation.CLOCKWISE_90);
        assertEquals(3, box.getXSpan());
        assertEquals(5, box.getZSpan());
        assertEquals(2, box.getYSpan());
    }

    @Test
    void matchingCaresAboutFacingNotAboutUse() {
        assertTrue(BuildingRules.matches(DOOR_NORTH, DOOR_NORTH.setValue(DoorBlock.OPEN, true)), "An opened door is still the door");
        assertFalse(BuildingRules.matches(DOOR_NORTH, DOOR_NORTH.setValue(DoorBlock.FACING, Direction.SOUTH)), "A door the wrong way round isn't");
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        assertFalse(BuildingRules.matches(log, log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X)), "Nor is a log lying down");
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        assertTrue(BuildingRules.matches(slab, slab.setValue(BlockStateProperties.WATERLOGGED, true)), "Waterlogged doesn't matter");
        assertFalse(BuildingRules.matches(slab, slab.setValue(SlabBlock.TYPE, SlabType.TOP)), "Top or bottom does");
        assertFalse(BuildingRules.matches(Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()));
        assertTrue(BuildingRules.matches(Blocks.AIR.defaultBlockState(), Blocks.CAVE_AIR.defaultBlockState()), "Any air is air");
    }

    @Test
    void remainingSortsMissingFromInTheWay() {
        BlockPos stone = new BlockPos(0, 0, 0);
        BlockPos clear = new BlockPos(1, 0, 0);
        BlockPos plank = new BlockPos(2, 0, 0);
        BlockPos grass = new BlockPos(3, 0, 0);
        List<Blueprint.Part> placed = List.of(
                new Blueprint.Part(stone, Blocks.STONE.defaultBlockState()),
                new Blueprint.Part(clear, Blocks.AIR.defaultBlockState()),
                new Blueprint.Part(plank, Blocks.OAK_PLANKS.defaultBlockState()),
                new Blueprint.Part(grass, Blocks.OAK_PLANKS.defaultBlockState()));
        Map<BlockPos, BlockState> world = new HashMap<>();
        world.put(stone, Blocks.STONE.defaultBlockState());      // done
        world.put(clear, Blocks.DIRT.defaultBlockState());       // must be dug out
        world.put(plank, Blocks.COBBLESTONE.defaultBlockState()); // wrong block: in the way, and still missing
        world.put(grass, Blocks.SHORT_GRASS.defaultBlockState()); // replaceable: missing, but not in the way
        BuildingRules.Remaining remaining = BuildingRules.remaining(placed, pos -> world.getOrDefault(pos, Blocks.AIR.defaultBlockState()));
        assertEquals(List.of(plank, grass), remaining.missing().stream().map(Blueprint.Part::pos).toList());
        assertEquals(List.of(clear, plank), remaining.obstructions());
        assertEquals(Map.of(Items.OAK_PLANKS, 2), remaining.materials());
        assertFalse(remaining.isDone());
    }

    @Test
    void finishedWhenEverythingMatches() {
        List<Blueprint.Part> placed = BuildingRules.place(small(), BlockPos.ZERO, Rotation.NONE);
        Map<BlockPos, BlockState> world = new HashMap<>();
        placed.forEach(part -> world.put(part.pos(), part.state()));
        assertTrue(BuildingRules.remaining(placed, pos -> world.getOrDefault(pos, Blocks.AIR.defaultBlockState())).isDone());
    }

    @Test
    void whatBlocksCost() {
        assertEquals(Items.OAK_DOOR, BuildingRules.itemFor(DOOR_NORTH).getItem(), "A door's lower half costs the door");
        assertTrue(BuildingRules.itemFor(DOOR_NORTH.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)).isEmpty(), "Its upper half comes free");
        BlockState bed = Blocks.RED_BED.defaultBlockState();
        assertEquals(1, BuildingRules.itemFor(bed.setValue(BedBlock.PART, BedPart.FOOT)).getCount());
        assertTrue(BuildingRules.itemFor(bed.setValue(BedBlock.PART, BedPart.HEAD)).isEmpty());
        assertEquals(2, BuildingRules.itemFor(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)).getCount());
        assertEquals(Items.TORCH, BuildingRules.itemFor(Blocks.WALL_TORCH.defaultBlockState()).getItem(), "A wall torch is a torch");
        assertTrue(BuildingRules.itemFor(Blocks.AIR.defaultBlockState()).isEmpty());
        assertTrue(BuildingRules.itemFor(Blocks.WATER.defaultBlockState()).isEmpty());
        assertTrue(BuildingRules.itemFor(Blocks.FIRE.defaultBlockState()).isEmpty());
    }

    @Test
    void blocksWithNoItemAreUnplaceable() {
        List<Blueprint.Part> placed = List.of(
                new Blueprint.Part(BlockPos.ZERO, Blocks.FIRE.defaultBlockState()),
                new Blueprint.Part(BlockPos.ZERO.above(), DOOR_NORTH.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
        BuildingRules.Remaining remaining = BuildingRules.remaining(placed, pos -> Blocks.AIR.defaultBlockState());
        assertEquals(1, remaining.unplaceable().size(), "Fire can't be placed; a door's free upper half isn't a problem");
        assertTrue(remaining.materials().isEmpty());
    }

    @Test
    void theSiteGoesOnAClearFloorSpotNearTheMiddle() {
        // A 3x2x3 hut: a stone floor, a ring of planks on top, and air in the middle of the ring.
        List<Blueprint.Part> parts = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                parts.add(new Blueprint.Part(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState()));
                boolean middle = x == 1 && z == 1;
                parts.add(new Blueprint.Part(new BlockPos(x, 1, z), middle ? Blocks.AIR.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState()));
            }
        }
        parts.add(new Blueprint.Part(new BlockPos(0, 2, 0), Blocks.AIR.defaultBlockState())); // air over a corner, further out
        assertEquals(Optional.of(new BlockPos(1, 1, 1)), BuildingRules.siteSpot(parts));
    }

    @Test
    void aBuildingWithNoClearSpotHasNoSite() {
        assertTrue(BuildingRules.siteSpot(List.of(new Blueprint.Part(BlockPos.ZERO, Blocks.STONE.defaultBlockState()))).isEmpty());
        assertTrue(BuildingRules.siteSpot(List.of()).isEmpty());
    }

    @Test
    void floorSpotsBeatFloatingOnes() {
        List<Blueprint.Part> parts = List.of(
                new Blueprint.Part(new BlockPos(5, 3, 5), Blocks.AIR.defaultBlockState()),   // nothing under it
                new Blueprint.Part(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState()),
                new Blueprint.Part(new BlockPos(0, 1, 0), Blocks.AIR.defaultBlockState()));  // on the stone
        assertEquals(Optional.of(new BlockPos(0, 1, 0)), BuildingRules.siteSpot(parts));
    }
}
