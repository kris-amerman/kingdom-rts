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

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.building.BuildingRules;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.BuilderOps;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.ConstructionSiteBlockEntity;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.SitePlacement;
import com.krisamerman.kingdomrts.unit.Stock;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Builders building: an assigned builder places its site's missing blocks from the site stock until
// the building matches its template, several builders share a site without doubling up, and a
// builder short of materials waits and carries on when they arrive. The awkward blocks (doors,
// beds, attached and falling blocks, water, potted plants) are placed through SitePlacement with a
// part list built here. Each test founds a settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class BuilderPlacementGameTests {
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");
    private static final BlockPos SITE = new BlockPos(1, FLOOR_Y + 1, 1);
    private static final int BUILD_TIMEOUT = 20 * 90;

    @GameTest(template = TEMPLATE, batch = "builders_place_one", timeoutTicks = BUILD_TIMEOUT)
    public static void aBuilderBuildsAStockedShed(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        stockUp(helper, shed);
        Unit builder = builder(helper, settlement, blue, HALL.east(2));
        assign(helper, settlement, shed, builder);
        long started = helper.getLevel().getGameTime();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).isBuilt(), "Waiting for the shed to be built"))
                .thenExecute(() -> {
                    KingdomRts.LOGGER.info("Builder test: one builder built the shed in {} ticks", helper.getLevel().getGameTime() - started);
                    assertNothingLeftOver(helper, shed);
                    helper.assertTrue(BuilderOps.siteOf(current(helper, settlement), builder.getUUID()) == null, "The builder should be back in the pool");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_place_two", timeoutTicks = BUILD_TIMEOUT)
    public static void twoBuildersShareASiteWithoutDoublingUp(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        stockUp(helper, shed);
        assign(helper, settlement, shed, builder(helper, settlement, blue, HALL.east(2)));
        assign(helper, settlement, shed, builder(helper, settlement, blue, HALL.east(3)));
        int wastedBefore = SitePlacement.wastedAttempts();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).isBuilt(), "Waiting for the shed to be built"))
                .thenExecute(() -> {
                    assertNothingLeftOver(helper, shed);
                    helper.assertTrue(SitePlacement.wastedAttempts() == wastedBefore,
                            "Two builders went for the same block " + (SitePlacement.wastedAttempts() - wastedBefore) + " time(s)");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_place_wait", timeoutTicks = BUILD_TIMEOUT + 300)
    public static void aBuilderWaitsForMaterialsThenCarriesOn(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        Unit builder = builder(helper, settlement, blue, HALL.east(2));
        assign(helper, settlement, shed, builder);
        int missing = remaining(helper, shed);
        helper.startSequence()
                .thenIdle(200)
                .thenExecute(() -> {
                    helper.assertTrue(remaining(helper, shed) == missing, "Nothing can be built from an empty stock");
                    helper.assertTrue(builder.isAlive() && BuilderOps.siteOf(current(helper, settlement), builder.getUUID()) != null, "It should still be on the job");
                    stockUp(helper, shed);
                })
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).isBuilt(), "Waiting for the shed to be built"))
                .thenExecute(() -> removeHall(helper, HALL))
                .thenSucceed();
    }

    // The awkward blocks, placed as a builder would, through SitePlacement.
    @GameTest(template = TEMPLATE, batch = "builders_place_awkward")
    public static void awkwardBlocksGoDownRight(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(3, FLOOR_Y + 1, 3));
        Container stock = new SimpleContainer(27);
        for (Item item : List.of(Items.STONE, Items.STONE, Items.TORCH, Items.OAK_DOOR, Items.RED_BED, Items.SAND, Items.WATER_BUCKET, Items.FLOWER_POT, Items.POPPY)) {
            Stock.insert(stock, new ItemStack(item));
        }

        // A torch on a wall: not before the wall is there.
        Blueprint.Part wall = part(base, Blocks.STONE.defaultBlockState());
        Blueprint.Part torch = part(base.east(), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
        expect(helper, SitePlacement.place(level, torch, null, stock, null), SitePlacement.Result.CANT_STAY_YET, "a torch with no wall");
        expect(helper, SitePlacement.place(level, wall, null, stock, null), SitePlacement.Result.PLACED, "the wall");
        expect(helper, SitePlacement.place(level, torch, null, stock, null), SitePlacement.Result.PLACED, "the torch on its wall");

        // A door: both halves at once, for one door.
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        Blueprint.Part lower = part(base.south(3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        Blueprint.Part upper = part(base.south(3).above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        expect(helper, SitePlacement.place(level, lower, partnerIn(lower, upper), stock, null), SitePlacement.Result.PLACED, "the door");
        helper.assertTrue(level.getBlockState(lower.pos()).is(Blocks.OAK_DOOR) && level.getBlockState(upper.pos()).is(Blocks.OAK_DOOR), "Both halves of the door");

        // A bed: foot and head together.
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        Blueprint.Part foot = part(base.east(4), bed.setValue(BedBlock.PART, BedPart.FOOT));
        Blueprint.Part head = part(base.east(4).south(), bed.setValue(BedBlock.PART, BedPart.HEAD));
        expect(helper, SitePlacement.place(level, foot, partnerIn(foot, head), stock, null), SitePlacement.Result.PLACED, "the bed");
        helper.assertTrue(level.getBlockState(head.pos()).is(Blocks.RED_BED) && level.getBlockState(head.pos()).getValue(BedBlock.PART) == BedPart.HEAD,
                "The bed's head too");

        // Sand: not over air.
        Blueprint.Part sand = part(base.south(6).above(), Blocks.SAND.defaultBlockState());
        expect(helper, SitePlacement.place(level, sand, null, stock, null), SitePlacement.Result.CANT_STAY_YET, "sand over air");
        expect(helper, SitePlacement.place(level, part(base.south(6), Blocks.STONE.defaultBlockState()), null, stock, null),
                SitePlacement.Result.PLACED, "the stone under the sand");
        expect(helper, SitePlacement.place(level, sand, null, stock, null), SitePlacement.Result.PLACED, "sand on stone");

        // Water: a bucket's worth, and the bucket comes back.
        Blueprint.Part water = part(base.east(6).south(6), Blocks.WATER.defaultBlockState());
        expect(helper, SitePlacement.place(level, water, null, stock, null), SitePlacement.Result.PLACED, "the water");
        helper.assertTrue(level.getBlockState(water.pos()).getFluidState().isSource(), "A water source");

        // A potted plant: a pot and the plant.
        Blueprint.Part pot = part(base.east(6), Blocks.POTTED_POPPY.defaultBlockState());
        expect(helper, SitePlacement.place(level, pot, null, stock, null), SitePlacement.Result.PLACED, "the potted poppy");

        // Not enough left for another door.
        expect(helper, SitePlacement.place(level, part(base.south(8), door), part(base.south(8).above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)),
                stock, null), SitePlacement.Result.NO_MATERIALS, "a second door with none left");

        helper.assertTrue(stock.countItem(Items.BUCKET) == 1, "The empty bucket should be back in the stock");
        for (Item used : List.of(Items.STONE, Items.TORCH, Items.OAK_DOOR, Items.RED_BED, Items.SAND, Items.WATER_BUCKET, Items.FLOWER_POT, Items.POPPY)) {
            helper.assertTrue(stock.countItem(used) == 0, "All the " + used + " should be used, " + stock.countItem(used) + " left");
        }
        helper.succeed();
    }

    private static Blueprint.Part part(BlockPos pos, BlockState state) {
        return new Blueprint.Part(pos, state);
    }

    private static Blueprint.Part partnerIn(Blueprint.Part part, Blueprint.Part other) {
        return BuildingRules.partnerOf(part, Map.of(other.pos(), other)).orElseThrow(
                () -> new GameTestAssertException("Expected " + other + " to be the other half of " + part));
    }

    private static void expect(GameTestHelper helper, SitePlacement.Result actual, SitePlacement.Result expected, String what) {
        helper.assertTrue(actual == expected, "Placing " + what + ": expected " + expected + ", got " + actual);
    }

    private static Building commission(GameTestHelper helper, Faction faction) {
        BuildingOps.CommissionResult result = BuildingOps.commission(helper.getLevel(), faction, SHED, helper.absolutePos(SITE), Rotation.NONE);
        if (result.building() == null) {
            throw new GameTestAssertException("Couldn't commission the shed: " + result.problem());
        }
        return result.building();
    }

    // Fills the site's stock with exactly what the shed still needs.
    private static void stockUp(GameTestHelper helper, Building shed) {
        ConstructionSiteBlockEntity stock = (ConstructionSiteBlockEntity) helper.getLevel().getBlockEntity(shed.site());
        BuildingOps.remaining(helper.getLevel(), shed).remaining().materials()
                .forEach((item, count) -> Stock.insert(stock, new ItemStack(item, count)));
    }

    private static int remaining(GameTestHelper helper, Building shed) {
        return BuildingOps.remaining(helper.getLevel(), shed).remaining().missing().size();
    }

    // Leftovers drop when a finished site's block is taken away; with the stock filled exactly,
    // there should be none.
    private static void assertNothingLeftOver(GameTestHelper helper, Building shed) {
        List<ItemEntity> left = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(shed.site()).inflate(4));
        helper.assertTrue(left.isEmpty(), "Every material should have been used exactly once; left over: "
                + left.stream().map(item -> item.getItem().getCount() + " " + item.getItem().getHoverName().getString()).toList());
    }

    private static Unit builder(GameTestHelper helper, Settlement settlement, Faction faction, BlockPos pos) {
        Unit unit = unit(helper, pos, faction, null, true);
        SettlementOps.setUnitSettlement(helper.getLevel(), unit, settlement);
        String problem = BuilderOps.hire(helper.getLevel(), current(helper, settlement), unit);
        if (problem != null) {
            throw new GameTestAssertException("Couldn't hire a builder: " + problem);
        }
        return unit;
    }

    private static void assign(GameTestHelper helper, Settlement settlement, Building shed, Unit builder) {
        String problem = BuilderOps.assign(helper.getLevel(), current(helper, settlement), shed, builder.getUUID());
        if (problem != null) {
            throw new GameTestAssertException("Couldn't assign the builder: " + problem);
        }
    }
}
