package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.current;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.runCommand;

import java.util.List;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.building.BuildingRules;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.Settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Commissioning buildings from templates and checking on their sites, through the faction
// operations and the /kingdomrts building commands. The sample building is the storage shed
// (data/kingdomrts/structure/buildings/storage_shed.nbt): 5x4x5, a cobblestone floor, log corners,
// plank walls with an oak door on its north side, a chest and a wall torch inside, and a slab roof.
// Each test founds a settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class BuildingGameTests {
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");
    // The shed's template corner, inside the hall's claim and clear of the hall.
    private static final BlockPos SITE = new BlockPos(1, FLOOR_Y + 1, 1);
    // Blocks of the shed: 25 floor, 25 roof, 8 log corners, 22 planks, the door's two halves, the chest
    // and the torch.
    private static final int SHED_BLOCKS = 84;

    @GameTest(template = TEMPLATE, batch = "building_commission")
    public static void commissionRecordsTheSite(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        expectOutput(helper, "kingdomrts building commission " + SHED + " " + coords(helper, SITE) + " faction " + blue.name(), "Commissioned Storage shed");
        Building building = current(helper, settlement).buildingAt(helper.absolutePos(SITE));
        helper.assertTrue(building != null, "Expected the site to be recorded");
        helper.assertTrue(building.footprint().getXSpan() == 5 && building.footprint().getYSpan() == 4 && building.footprint().getZSpan() == 5,
                "Expected the shed's 5x4x5 footprint, got " + building.footprint());
        expectOutput(helper, "kingdomrts building list", "- Storage shed at " + helper.absolutePos(SITE).toShortString() + ": " + SHED_BLOCKS + " blocks to place");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "building_refused")
    public static void commissionIsRefusedOutsideYourOwnClaim(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Settlement settlement = found(helper, HALL, blue);
        // Outside any claim.
        BlockPos far = helper.absolutePos(SITE).offset(160, 0, 0);
        expectProblem(helper, blue, far, "isn't in a settlement's claim");
        // In someone else's claim.
        expectProblem(helper, red, helper.absolutePos(SITE), "not " + red.name() + "'s");
        // Partly outside: the corner is just inside the claim's east edge.
        int eastEdge = settlement.chunks().stream().mapToInt(ChunkPos::getMaxBlockX).max().orElseThrow();
        BlockPos edge = new BlockPos(eastEdge - 1, helper.absolutePos(SITE).getY(), helper.absolutePos(SITE).getZ());
        expectProblem(helper, blue, edge, "doesn't fit inside");
        // Over the town hall.
        expectProblem(helper, blue, helper.absolutePos(HALL).offset(-2, 0, -2), "cover the town hall");
        helper.assertTrue(current(helper, settlement).buildings().isEmpty(), "Nothing should have been commissioned");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "building_overlap")
    public static void buildingsCantOverlap(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        commission(helper, blue, SITE, Rotation.NONE);
        expectProblem(helper, blue, helper.absolutePos(SITE).offset(3, 0, 0), "overlaps the storage shed");
        helper.assertTrue(BuildingOps.commission(helper.getLevel(), blue, SHED, helper.absolutePos(SITE).south(5), Rotation.NONE).building() != null,
                "A shed right next to it, not overlapping, is fine");
        helper.assertTrue(current(helper, settlement).buildings().size() == 2, "Expected two sheds");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "building_remaining")
    public static void remainingWorkOnAFreshSite(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        found(helper, HALL, blue);
        Building building = commission(helper, blue, SITE, Rotation.NONE);
        BuildingRules.Remaining remaining = remaining(helper, building);
        helper.assertTrue(remaining.missing().size() == SHED_BLOCKS, "Expected every block missing, got " + remaining.missing().size());
        helper.assertTrue(remaining.obstructions().isEmpty(), "Nothing should be in the way: " + remaining.obstructions());
        var materials = remaining.materials();
        helper.assertTrue(materials.get(Items.COBBLESTONE) == 25 && materials.get(Items.OAK_SLAB) == 25 && materials.get(Items.OAK_LOG) == 8
                && materials.get(Items.OAK_PLANKS) == 22 && materials.get(Items.OAK_DOOR) == 1 && materials.get(Items.CHEST) == 1
                && materials.get(Items.TORCH) == 1 && materials.size() == 7, "Unexpected materials: " + materials);
        expectOutput(helper, "kingdomrts building remaining " + coords(helper, SITE.offset(2, 1, 2)), SHED_BLOCKS + " blocks to place, 0 in the way");
        expectOutput(helper, "kingdomrts building remaining " + coords(helper, SITE.offset(2, 1, 2)), "Materials: 25 Cobblestone, 25 Oak Slab");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "building_partial")
    public static void remainingWorkAfterSomeBlocksArePlaced(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        found(helper, HALL, blue);
        Building building = commission(helper, blue, SITE, Rotation.NONE);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(SITE.offset(x, 0, z), Blocks.COBBLESTONE); // the floor, by hand
            }
        }
        BlockPos corner = SITE.above(); // a corner log, but lying on its side
        helper.setBlock(corner, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        BlockPos inside = SITE.offset(1, 1, 1); // the template wants this clear
        helper.setBlock(inside, Blocks.STONE);
        BuildingRules.Remaining remaining = remaining(helper, building);
        helper.assertTrue(remaining.missing().size() == SHED_BLOCKS - 25, "Expected the floor done and the rest missing, got "
                + remaining.missing().size());
        helper.assertTrue(remaining.missing().stream().anyMatch(part -> part.pos().equals(helper.absolutePos(corner))),
                "A log lying the wrong way doesn't count");
        helper.assertTrue(remaining.obstructions().contains(helper.absolutePos(corner)) && remaining.obstructions().contains(helper.absolutePos(inside))
                && remaining.obstructions().size() == 2, "Expected the wrong log and the stone in the way: " + remaining.obstructions());
        helper.assertFalse(remaining.materials().containsKey(Items.COBBLESTONE), "The floor needs no more cobblestone");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "building_finish")
    public static void finishBuildsItToTheTemplate(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        found(helper, HALL, blue);
        Building building = commission(helper, blue, SITE, Rotation.NONE);
        helper.setBlock(SITE.offset(1, 1, 1), Blocks.STONE); // in the way
        expectOutput(helper, "kingdomrts building finish " + coords(helper, SITE), "Finished the Storage shed");
        helper.assertTrue(remaining(helper, building).isDone(), "Nothing should be left after finishing: " + remaining(helper, building));
        assertDoor(helper, helper.absolutePos(SITE.offset(2, 1, 0)), Direction.NORTH);
        helper.assertBlockPresent(Blocks.AIR, SITE.offset(1, 1, 1));
        expectOutput(helper, "kingdomrts building remaining " + coords(helper, SITE), "is built");
        expectOutput(helper, "kingdomrts building list", "built on day");
        expectOutput(helper, "kingdomrts building cancel " + coords(helper, SITE), "already built");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "building_rotated")
    public static void aRotatedBuildingFacesTheNewWay(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building building = commission(helper, blue, SITE.offset(0, 0, 8), Rotation.CLOCKWISE_90);
        BoundingBox box = building.footprint();
        helper.assertTrue(box.isInside(helper.absolutePos(SITE.offset(2, 1, 10))), "A square building turns in place: " + box);
        BuildingOps.finish(helper.getLevel(), current(helper, settlement), building);
        helper.assertTrue(remaining(helper, building).isDone(), "Nothing should be left after finishing");
        // The door was on the north side facing north; turned clockwise it's on the east side, facing east.
        BlockPos door = null;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (state.is(Blocks.OAK_DOOR) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                door = pos.immutable();
            }
        }
        helper.assertTrue(door != null && door.getX() == box.maxX(), "Expected the door on the east side, got " + door);
        assertDoor(helper, door, Direction.EAST);
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "building_cancel")
    public static void cancelForgetsTheSite(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        commission(helper, blue, SITE, Rotation.NONE);
        expectOutput(helper, "kingdomrts building cancel " + coords(helper, SITE.offset(4, 3, 4)), "Cancelled the Storage shed");
        helper.assertTrue(current(helper, settlement).buildings().isEmpty(), "The site should be gone");
        helper.assertBlockNotPresent(KingdomRts.CONSTRUCTION_SITE.get(), SITE.offset(2, 1, 2)); // its site block too
        expectOutput(helper, "kingdomrts building remaining " + coords(helper, SITE), "No building is commissioned");
        removeHall(helper, HALL);
        helper.succeed();
    }

    private static Building commission(GameTestHelper helper, Faction faction, BlockPos relativePos, Rotation rotation) {
        BuildingOps.CommissionResult result = BuildingOps.commission(helper.getLevel(), faction, SHED, helper.absolutePos(relativePos), rotation);
        if (result.building() == null) {
            throw new GameTestAssertException("Couldn't commission the shed: " + result.problem());
        }
        return result.building();
    }

    private static void expectProblem(GameTestHelper helper, Faction faction, BlockPos absolutePos, String problem) {
        BuildingOps.CommissionResult result = BuildingOps.commission(helper.getLevel(), faction, SHED, absolutePos, Rotation.NONE);
        helper.assertTrue(result.building() == null && result.problem() != null && result.problem().contains(problem),
                "Expected \"" + problem + "\" at " + absolutePos.toShortString() + ", got " + result);
    }

    private static BuildingRules.Remaining remaining(GameTestHelper helper, Building building) {
        BuildingOps.RemainingResult result = BuildingOps.remaining(helper.getLevel(), building);
        if (result.remaining() == null) {
            throw new GameTestAssertException("No remaining work: " + result.problem());
        }
        return result.remaining();
    }

    // Both halves of the door, its lower half at an absolute position, facing the given way.
    private static void assertDoor(GameTestHelper helper, BlockPos lower, Direction facing) {
        BlockState bottom = helper.getLevel().getBlockState(lower);
        BlockState top = helper.getLevel().getBlockState(lower.above());
        helper.assertTrue(bottom.is(Blocks.OAK_DOOR) && top.is(Blocks.OAK_DOOR) && bottom.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && top.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, "Expected both halves of the door at " + lower.toShortString());
        helper.assertTrue(bottom.getValue(DoorBlock.FACING) == facing, "Expected the door facing " + facing + ", got " + bottom.getValue(DoorBlock.FACING));
    }

    // The absolute coordinates of a test position, for a command.
    private static String coords(GameTestHelper helper, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    // Runs a command from the hall and checks a line of its output contains the text.
    private static void expectOutput(GameTestHelper helper, String command, String expected) {
        List<String> output = runCommand(helper, HALL, command);
        helper.assertTrue(output.stream().anyMatch(line -> line.contains(expected)),
                "Expected \"" + expected + "\" from /" + command + ", got " + output);
    }
}
