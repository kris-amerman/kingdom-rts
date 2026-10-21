package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.current;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.runCommand;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import java.util.LinkedHashMap;
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
import com.krisamerman.kingdomrts.settlement.SettlementStorageBlockEntity;
import com.krisamerman.kingdomrts.unit.Stock;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Builders demolishing a building: they take its own blocks down (emptying its chest first), keep
// what each block cost to place, and carry it all to settlement storage, a load at a time, holding
// onto it while there's no room. Blocks that aren't the building's are left. Once nothing of it is
// left, the settlement forgets it. Each test founds a settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class BuilderDemolishGameTests {
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");
    private static final BlockPos SITE = new BlockPos(1, FLOOR_Y + 1, 1);
    private static final BlockPos STORAGE = new BlockPos(13, FLOOR_Y + 1, 13);
    // What's put in the shed's chest: more than a builder carries at once (9 slots).
    private static final int DIRT_STACKS = 12;
    // Builders put their tools away with their first load (S-042 decides which tools they keep), so the
    // rest comes down by hand, as slowly as for a player.
    private static final int TIMEOUT = 20 * 600;

    @GameTest(template = TEMPLATE, batch = "builders_demolish", timeoutTicks = TIMEOUT)
    public static void everyMaterialGoesBackToStorage(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = builtShed(helper, settlement, blue);
        Map<Item, Integer> cost = costOf(helper, shed);
        Container chest = fillChest(helper, shed);
        BlockPos extra = helper.absolutePos(SITE.offset(1, 1, 1)); // not the shed's: something put inside it
        helper.getLevel().setBlockAndUpdate(extra, Blocks.STONE.defaultBlockState());
        SettlementStorageBlockEntity storage = storage(helper);
        order(helper, settlement, blue, shed);
        Unit builder = builder(helper, settlement, blue, HALL.east(2));
        assign(helper, settlement, shed, builder);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()) == null, "Waiting for the shed to be gone"))
                .thenWaitUntil(() -> helper.assertTrue(builder.getInventory().isEmpty(), "Waiting for the builder to put its last load away"))
                .thenExecute(() -> {
                    cost.forEach((item, count) -> helper.assertTrue(storage.countItem(item) == count,
                            "Expected " + count + " " + item + " in storage, found " + storage.countItem(item)));
                    helper.assertTrue(storage.countItem(Items.DIRT) == 64 * DIRT_STACKS, "What was in the chest should be in storage");
                    helper.assertTrue(helper.getLevel().getBlockState(extra).is(Blocks.STONE), "Blocks that aren't the shed's are left");
                    helper.assertTrue(chest.isEmpty(), "The chest was emptied before it went");
                    assertNothingDropped(helper);
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_demolish_two", timeoutTicks = TIMEOUT)
    public static void twoBuildersTakeItDownWithNothingCountedTwice(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = builtShed(helper, settlement, blue);
        Map<Item, Integer> cost = costOf(helper, shed);
        SettlementStorageBlockEntity storage = storage(helper);
        order(helper, settlement, blue, shed);
        List<Unit> builders = List.of(builder(helper, settlement, blue, HALL.east(2)), builder(helper, settlement, blue, HALL.south(2)));
        builders.forEach(builder -> assign(helper, settlement, shed, builder));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()) == null, "Waiting for the shed to be gone"))
                .thenWaitUntil(() -> helper.assertTrue(builders.stream().allMatch(builder -> builder.getInventory().isEmpty()),
                        "Waiting for the builders to put their loads away"))
                .thenExecute(() -> {
                    cost.forEach((item, count) -> helper.assertTrue(storage.countItem(item) == count,
                            "Expected " + count + " " + item + " in storage, found " + storage.countItem(item)));
                    assertNothingDropped(helper);
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_demolish_full", timeoutTicks = TIMEOUT)
    public static void withNoRoomInStorageItHoldsOntoItsLoad(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = builtShed(helper, settlement, blue);
        fillChest(helper, shed);
        SettlementStorageBlockEntity storage = storage(helper);
        for (int i = 0; i < storage.getContainerSize(); i++) {
            storage.setItem(i, new ItemStack(Items.COBBLESTONE, 64)); // full
        }
        order(helper, settlement, blue, shed);
        Unit builder = builder(helper, settlement, blue, HALL.east(2));
        assign(helper, settlement, shed, builder);
        helper.startSequence()
                .thenIdle(20 * 30)
                .thenExecute(() -> {
                    helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()) != null, "It can't finish while it can't put its load down");
                    helper.assertFalse(builder.getInventory().isEmpty(), "The builder holds onto its load");
                    assertNothingDropped(helper);
                    for (int i = 0; i < storage.getContainerSize(); i++) {
                        storage.setItem(i, ItemStack.EMPTY); // room at last
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()) == null, "Waiting for the shed to be gone"))
                .thenWaitUntil(() -> helper.assertTrue(builder.getInventory().isEmpty(), "Waiting for the builder to put its last load away"))
                .thenExecute(() -> {
                    helper.assertTrue(storage.countItem(Items.DIRT) == 64 * DIRT_STACKS, "What was in the chest should be in storage");
                    assertNothingDropped(helper);
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_demolish_site", timeoutTicks = TIMEOUT)
    public static void aSiteUnderConstructionIsTakenDownWithItsStock(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        ConstructionSiteBlockEntity stock = (ConstructionSiteBlockEntity) helper.getLevel().getBlockEntity(shed.site());
        Stock.insert(stock, new ItemStack(Items.OAK_PLANKS, 10));
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(SITE.offset(x, 0, z), Blocks.COBBLESTONE); // the floor, by hand
            }
        }
        int floor = (int) BuildingOps.placed(helper.getLevel(), shed).stream()
                .filter(part -> BuildingRules.isToTakeDown(part.state(), helper.getLevel().getBlockState(part.pos())) && !part.pos().equals(shed.site()))
                .count();
        SettlementStorageBlockEntity storage = storage(helper);
        order(helper, settlement, blue, shed);
        Unit builder = builder(helper, settlement, blue, HALL.east(2));
        assign(helper, settlement, shed, builder);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()) == null, "Waiting for the site to be gone"))
                .thenWaitUntil(() -> helper.assertTrue(builder.getInventory().isEmpty(), "Waiting for the builder to put its last load away"))
                .thenExecute(() -> {
                    helper.assertFalse(helper.getLevel().getBlockState(shed.site()).is(KingdomRts.CONSTRUCTION_SITE.get()), "The site block should be gone");
                    helper.assertTrue(storage.countItem(Items.OAK_PLANKS) == 10, "The site's stock should be in storage");
                    helper.assertTrue(storage.countItem(Items.COBBLESTONE) == floor, "The floor laid so far should be in storage");
                    assertNothingDropped(helper);
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_demolish_other")
    public static void anotherFactionCantDemolishIt(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = builtShed(helper, settlement, blue);
        String problem = BuildingOps.orderDemolition(helper.getLevel(), red, current(helper, settlement), shed);
        helper.assertTrue(problem != null && problem.contains("another faction"), "Expected it refused, got " + problem);
        helper.assertFalse(current(helper, settlement).buildingAt(shed.origin()).demolishing(), "It shouldn't be marked for demolition");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_demolish_commands")
    public static void theCommandsOrderAndStopADemolition(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = builtShed(helper, settlement, blue);
        String at = coords(helper, SITE.offset(2, 2, 2));
        List<String> noFaction = runCommand(helper, HALL, "kingdomrts building demolish " + at);
        helper.assertTrue(noFaction.stream().anyMatch(line -> line.contains("not in a faction")), "Expected a faction to be needed, got " + noFaction);
        List<String> ordered = runCommand(helper, HALL, "kingdomrts building demolish " + at + " faction " + blue.name());
        helper.assertTrue(ordered.stream().anyMatch(line -> line.startsWith("Ordered the Storage shed")), "Expected it ordered, got " + ordered);
        helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).demolishing(), "It should be marked for demolition");
        List<String> listed = runCommand(helper, HALL, "kingdomrts building list");
        helper.assertTrue(listed.stream().anyMatch(line -> line.contains("Storage shed") && line.contains("being demolished")), "Expected it listed, got " + listed);
        List<String> again = runCommand(helper, HALL, "kingdomrts building demolish " + at + " faction " + blue.name());
        helper.assertTrue(again.stream().anyMatch(line -> line.contains("already being demolished")), "Expected it refused, got " + again);
        List<String> stopped = runCommand(helper, HALL, "kingdomrts building cancel " + at);
        helper.assertTrue(stopped.stream().anyMatch(line -> line.startsWith("Stopped demolishing the Storage shed")), "Expected it stopped, got " + stopped);
        Building after = current(helper, settlement).buildingAt(shed.origin());
        helper.assertTrue(after != null && after.isBuilt() && !after.demolishing(), "It should be an ordinary built building again");
        removeHall(helper, HALL);
        helper.succeed();
    }

    // A storage shed, finished at once (its site closes, its stock being empty).
    private static Building builtShed(GameTestHelper helper, Settlement settlement, Faction faction) {
        Building shed = commission(helper, faction);
        BuildingOps.finish(helper.getLevel(), current(helper, settlement), shed);
        Building built = current(helper, settlement).buildingAt(shed.origin());
        if (built == null || !built.isBuilt() || built.site() != null) {
            throw new GameTestAssertException("Couldn't finish the shed: " + built);
        }
        return built;
    }

    // What placing the shed's blocks cost, all together.
    private static Map<Item, Integer> costOf(GameTestHelper helper, Building shed) {
        Map<Item, Integer> cost = new LinkedHashMap<>();
        for (Blueprint.Part part : BuildingOps.placed(helper.getLevel(), shed)) {
            BuildingRules.costOf(part.state()).forEach(stack -> cost.merge(stack.getItem(), stack.getCount(), Integer::sum));
        }
        return cost;
    }

    // Fills the shed's chest with more dirt than one load.
    private static Container fillChest(GameTestHelper helper, Building shed) {
        BlockPos chestPos = BuildingOps.placed(helper.getLevel(), shed).stream().filter(part -> part.state().getBlock() instanceof ChestBlock)
                .map(Blueprint.Part::pos).findFirst().orElseThrow(() -> new GameTestAssertException("The shed has no chest"));
        if (!(helper.getLevel().getBlockEntity(chestPos) instanceof Container chest)) {
            throw new GameTestAssertException("The shed's chest has no inventory");
        }
        for (int i = 0; i < DIRT_STACKS; i++) {
            chest.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        return chest;
    }

    private static void order(GameTestHelper helper, Settlement settlement, Faction faction, Building shed) {
        String problem = BuildingOps.orderDemolition(helper.getLevel(), faction, current(helper, settlement), shed);
        if (problem != null) {
            throw new GameTestAssertException("Couldn't order the shed demolished: " + problem);
        }
    }

    private static SettlementStorageBlockEntity storage(GameTestHelper helper) {
        helper.setBlock(STORAGE, KingdomRts.SETTLEMENT_STORAGE.get());
        Settlement settlement = SettlementOps.registerStorage(helper.getLevel(), helper.absolutePos(STORAGE));
        if (settlement == null || !(helper.getBlockEntity(STORAGE) instanceof SettlementStorageBlockEntity storage)) {
            throw new GameTestAssertException("Couldn't place settlement storage");
        }
        return storage;
    }

    private static void assertNothingDropped(GameTestHelper helper) {
        List<ItemEntity> left = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(HALL)).inflate(16));
        helper.assertTrue(left.isEmpty(), "Nothing should be dropped; found "
                + left.stream().map(item -> item.getItem().getCount() + " " + item.getItem().getHoverName().getString()).toList());
    }

    private static String coords(GameTestHelper helper, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static Building commission(GameTestHelper helper, Faction faction) {
        BuildingOps.CommissionResult result = BuildingOps.commission(helper.getLevel(), faction, SHED, helper.absolutePos(SITE), Rotation.NONE);
        if (result.building() == null) {
            throw new GameTestAssertException("Couldn't commission the shed: " + result.problem());
        }
        return result.building();
    }

    // A builder carrying an iron pickaxe and axe to start with (builders don't fetch tools yet: S-042).
    private static Unit builder(GameTestHelper helper, Settlement settlement, Faction faction, BlockPos pos) {
        Unit unit = unit(helper, pos, faction, null, true);
        unit.getInventory().addItem(new ItemStack(Items.IRON_PICKAXE));
        unit.getInventory().addItem(new ItemStack(Items.IRON_AXE));
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
