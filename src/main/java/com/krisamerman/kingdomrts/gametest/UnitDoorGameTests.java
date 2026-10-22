package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.current;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.BuilderOps;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.unit.Occupation;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Units and doors: paths go through wooden doors, which units open on the way and close behind
// them. Iron doors (redstone only) stay shut to them, as to players.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class UnitDoorGameTests {
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");
    // A closed room, 3x3 inside, with its door in its south wall; and a post well outside it (beyond
    // the soldier's hold radius from inside).
    private static final BlockPos ROOM_MIN = new BlockPos(1, FLOOR_Y + 1, 1);
    private static final BlockPos INSIDE = new BlockPos(3, FLOOR_Y + 1, 3);
    private static final BlockPos DOOR = new BlockPos(3, FLOOR_Y + 1, 5);
    private static final BlockPos POST = new BlockPos(3, FLOOR_Y + 1, 14);

    @GameTest(template = TEMPLATE, timeoutTicks = 20 * 30)
    public static void aUnitWalksThroughAWoodenDoorAndClosesIt(GameTestHelper helper) {
        room(helper, Blocks.OAK_DOOR);
        Unit soldier = unit(helper, POST, null, Occupation.SOLDIER, true);
        moveTo(helper, soldier, INSIDE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(soldier.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(POST))) < Unit.HOLD_RADIUS,
                        "Waiting for the soldier to walk out to its post"))
                .thenWaitUntil(() -> helper.assertFalse(helper.getBlockState(DOOR).getValue(DoorBlock.OPEN), "Waiting for the door to be closed behind it"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20 * 30)
    public static void anIronDoorStillKeepsAUnitIn(GameTestHelper helper) {
        room(helper, Blocks.IRON_DOOR);
        Unit soldier = unit(helper, POST, null, Occupation.SOLDIER, true);
        moveTo(helper, soldier, INSIDE);
        helper.startSequence()
                .thenIdle(20 * 10)
                .thenExecute(() -> {
                    BlockPos at = soldier.blockPosition();
                    BlockPos min = helper.absolutePos(ROOM_MIN);
                    helper.assertTrue(at.getX() > min.getX() && at.getX() < min.getX() + 4 && at.getZ() > min.getZ() && at.getZ() < min.getZ() + 4,
                            "The soldier should still be inside, at " + at.toShortString());
                    helper.assertFalse(helper.getBlockState(DOOR).getValue(DoorBlock.OPEN), "The iron door should stay shut");
                })
                .thenSucceed();
    }

    // The shed's own door: a builder inside the finished shed walks out to its builders' hut.
    @GameTest(template = TEMPLATE, batch = "unit_doors_shed", timeoutTicks = 20 * 40)
    public static void aBuilderWalksOutOfAShedThroughItsDoor(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        BuildingOps.CommissionResult result = BuildingOps.commission(helper.getLevel(), blue, SHED, helper.absolutePos(ROOM_MIN), Rotation.NONE);
        if (result.building() == null) {
            throw new GameTestAssertException("Couldn't commission the shed: " + result.problem());
        }
        Building shed = result.building();
        BuildingOps.finish(helper.getLevel(), current(helper, settlement), shed);
        List<Blueprint.Part> parts = BuildingOps.placed(helper.getLevel(), shed);
        Map<BlockPos, BlockState> wanted = parts.stream().collect(Collectors.toMap(Blueprint.Part::pos, Blueprint.Part::state));
        BlockPos door = parts.stream().filter(part -> part.state().getBlock() instanceof DoorBlock && part.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                .map(Blueprint.Part::pos).findFirst().orElseThrow(() -> new GameTestAssertException("The shed has no door"));
        BlockPos inside = parts.stream().map(Blueprint.Part::pos)
                .filter(pos -> wanted.get(pos).isAir() && wanted.getOrDefault(pos.above(), Blocks.STONE.defaultBlockState()).isAir()
                        && !wanted.getOrDefault(pos.below(), Blocks.AIR.defaultBlockState()).isAir() && !pos.equals(door))
                .findFirst().orElseThrow(() -> new GameTestAssertException("The shed has no room inside"));
        Unit builder = unit(helper, HALL.east(2), blue, null, true);
        SettlementOps.setUnitSettlement(helper.getLevel(), builder, settlement);
        String problem = TestSupport.hireBuilder(helper, builder);
        if (problem != null) {
            throw new GameTestAssertException("Couldn't hire a builder: " + problem);
        }
        builder.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, 0.0f, 0.0f);
        // A stuck walk ends with a hop to a spot nearby (see WalkToStep), which could also get it out:
        // the door opening shows it went through the door.
        boolean[] opened = {false};
        helper.onEachTick(() -> opened[0] |= helper.getLevel().getBlockState(door).getValue(DoorBlock.OPEN));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(builder.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(TestSupport.BUILDERS_HUT))) < 4,
                        "Waiting for the builder to walk out of the shed to its hut"))
                .thenWaitUntil(() -> helper.assertFalse(helper.getLevel().getBlockState(door).getValue(DoorBlock.OPEN), "Waiting for the door to be closed behind it"))
                .thenExecute(() -> {
                    helper.assertTrue(opened[0], "The builder should have gone out through the door");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    // Stone walls three high around a 3x3 floor, a roof, and a closed door in the south wall.
    private static void room(GameTestHelper helper, Block door) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                for (int y = 0; y <= 3; y++) {
                    boolean wall = x == 0 || x == 4 || z == 0 || z == 4 || y == 3;
                    helper.setBlock(ROOM_MIN.offset(x, y, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        helper.setBlock(DOOR, lower);
        helper.setBlock(DOOR.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void moveTo(GameTestHelper helper, Unit unit, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        unit.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
    }
}
