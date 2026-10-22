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
import java.util.function.Function;
import java.util.stream.Collectors;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.BuilderOps;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.ConstructionSiteBlockEntity;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.unit.Stock;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Builders clearing a site: what's in the way inside the footprint is broken (top down, taking as
// long as a player would, with the best tool in the site's stock), its drops go into the stock, and
// then the building goes up. What builders can't break (bedrock) is left. Each
// test founds a settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class BuilderClearingGameTests {
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");
    private static final BlockPos SITE = new BlockPos(1, FLOOR_Y + 1, 1);
    private static final int TIMEOUT = 20 * 90;

    @GameTest(template = TEMPLATE, batch = "builders_clear_drops", timeoutTicks = TIMEOUT)
    public static void whatsInTheWayGoesIntoTheStock(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        Spots spots = spots(helper, shed);
        set(helper, spots.inside(), Blocks.DIRT.defaultBlockState());
        set(helper, spots.inside().above(), Blocks.OAK_LOG.defaultBlockState()); // a stack, broken top down
        set(helper, spots.otherInside(), Blocks.TORCH.defaultBlockState()); // small, but still in the way
        set(helper, spots.floor(), Blocks.STONE.defaultBlockState()); // with no pickaxe, nothing
        assign(helper, settlement, shed, builder(helper, settlement, blue, HALL.east(2))); // nothing to build with
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(obstructions(helper, shed).isEmpty(), "Waiting for the site to be cleared"))
                .thenExecute(() -> {
                    ConstructionSiteBlockEntity stock = stock(helper, shed);
                    helper.assertTrue(stock.countItem(Items.DIRT) == 1 && stock.countItem(Items.OAK_LOG) == 1 && stock.countItem(Items.TORCH) == 1,
                            "Expected the dirt, log and torch in the site's stock");
                    helper.assertTrue(stock.countItem(Items.COBBLESTONE) == 0 && stock.countItem(Items.STONE) == 0,
                            "Stone broken without a pickaxe shouldn't drop anything");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_clear_tool", timeoutTicks = TIMEOUT)
    public static void theBestToolInTheStockIsUsedAndPutBack(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        Spots spots = spots(helper, shed);
        set(helper, spots.floor(), Blocks.STONE.defaultBlockState()); // stone only drops with a pickaxe
        Stock.insert(stock(helper, shed), new ItemStack(Items.WOODEN_SHOVEL));
        Stock.insert(stock(helper, shed), new ItemStack(Items.WOODEN_PICKAXE));
        assign(helper, settlement, shed, builder(helper, settlement, blue, HALL.east(2)));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(obstructions(helper, shed).isEmpty(), "Waiting for the stone to be cleared"))
                .thenIdle(5)
                .thenExecute(() -> {
                    ConstructionSiteBlockEntity stock = stock(helper, shed);
                    // The cobblestone may already be in its place, if that's what the floor wants there.
                    int cobblestone = stock.countItem(Items.COBBLESTONE) + (helper.getLevel().getBlockState(spots.floor()).is(Blocks.COBBLESTONE) ? 1 : 0);
                    helper.assertTrue(cobblestone == 1, "With a pickaxe, the stone should drop cobblestone into the stock");
                    helper.assertTrue(damage(stock, Items.WOODEN_PICKAXE) == 1, "The pickaxe should be back in the stock, a little worn");
                    helper.assertTrue(damage(stock, Items.WOODEN_SHOVEL) == 0, "The shovel isn't the tool for stone");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_clear_build", timeoutTicks = TIMEOUT + 400)
    public static void aClearedSiteGetsBuilt(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        stockUp(helper, shed); // before the obstacles, so their spots' blocks are counted as needed
        Spots spots = spots(helper, shed);
        set(helper, spots.inside(), Blocks.DIRT.defaultBlockState());
        set(helper, spots.inside().above(), Blocks.OAK_LOG.defaultBlockState());
        set(helper, spots.floor(), Blocks.DIRT.defaultBlockState());
        assign(helper, settlement, shed, builder(helper, settlement, blue, HALL.east(2)));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).isBuilt(), "Waiting for the shed to be built"))
                .thenExecute(() -> removeHall(helper, HALL))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_clear_unbreakable", timeoutTicks = TIMEOUT + 400)
    public static void whatBuildersCantBreakIsLeft(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        stockUp(helper, shed);
        Spots spots = spots(helper, shed);
        set(helper, spots.inside(), Blocks.BEDROCK.defaultBlockState());
        assign(helper, settlement, shed, builder(helper, settlement, blue, HALL.east(2)));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).isBuilt(),
                        "Waiting for the shed to be built around what can't be cleared"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getLevel().getBlockState(spots.inside()).is(Blocks.BEDROCK), "The bedrock should still be there");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    // Spots in the shed to put things in the way (absolute): two inside, on its floor, where it wants
    // air, with air above the first; and one of its floor's full blocks. Picked from its blueprint, so
    // the tests don't depend on its exact layout.
    private record Spots(BlockPos inside, BlockPos otherInside, BlockPos floor) {
    }

    private static Spots spots(GameTestHelper helper, Building shed) {
        List<Blueprint.Part> parts = BuildingOps.placed(helper.getLevel(), shed);
        Map<BlockPos, BlockState> wanted = parts.stream().collect(Collectors.toMap(Blueprint.Part::pos, Blueprint.Part::state));
        int bottom = parts.stream().mapToInt(part -> part.pos().getY()).min().orElseThrow();
        Function<BlockPos, Boolean> fullFloor = pos -> pos.getY() == bottom && wanted.containsKey(pos) && !pos.equals(shed.site())
                && wanted.get(pos).isCollisionShapeFullBlock(helper.getLevel(), pos);
        List<BlockPos> inside = parts.stream().map(Blueprint.Part::pos)
                .filter(pos -> wanted.get(pos).isAir() && !pos.equals(shed.site()) && fullFloor.apply(pos.below()))
                .filter(pos -> wanted.getOrDefault(pos.above(), Blocks.STONE.defaultBlockState()).isAir())
                .toList();
        BlockPos floor = parts.stream().map(Blueprint.Part::pos).filter(fullFloor::apply).findFirst()
                .orElseThrow(() -> new GameTestAssertException("The shed has no floor"));
        if (inside.size() < 2) {
            throw new GameTestAssertException("The shed has no room inside");
        }
        return new Spots(inside.get(0), inside.get(inside.size() - 1), floor);
    }

    private static void set(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.getLevel().setBlockAndUpdate(pos, state);
    }

    private static List<BlockPos> obstructions(GameTestHelper helper, Building shed) {
        return BuildingOps.remaining(helper.getLevel(), shed).remaining().obstructions();
    }

    private static int damage(ConstructionSiteBlockEntity stock, Item tool) {
        for (int i = 0; i < stock.getContainerSize(); i++) {
            if (stock.getItem(i).is(tool)) {
                return stock.getItem(i).getDamageValue();
            }
        }
        throw new GameTestAssertException("No " + tool + " in the site's stock");
    }

    private static ConstructionSiteBlockEntity stock(GameTestHelper helper, Building shed) {
        return (ConstructionSiteBlockEntity) helper.getLevel().getBlockEntity(shed.site());
    }

    // Fills the site's stock with exactly what the shed still needs.
    private static void stockUp(GameTestHelper helper, Building shed) {
        BuildingOps.remaining(helper.getLevel(), shed).remaining().materials()
                .forEach((item, count) -> Stock.insert(stock(helper, shed), new ItemStack(item, count)));
    }

    private static Building commission(GameTestHelper helper, Faction faction) {
        BuildingOps.CommissionResult result = BuildingOps.commission(helper.getLevel(), faction, SHED, helper.absolutePos(SITE), Rotation.NONE);
        if (result.building() == null) {
            throw new GameTestAssertException("Couldn't commission the shed: " + result.problem());
        }
        return result.building();
    }

    private static Unit builder(GameTestHelper helper, Settlement settlement, Faction faction, BlockPos pos) {
        Unit unit = unit(helper, pos, faction, null, true);
        SettlementOps.setUnitSettlement(helper.getLevel(), unit, settlement);
        String problem = TestSupport.hireBuilder(helper, unit);
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
