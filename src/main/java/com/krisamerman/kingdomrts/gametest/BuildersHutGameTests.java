package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.buildersHut;
import static com.krisamerman.kingdomrts.gametest.TestSupport.current;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.BuilderOps;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.ConstructionSiteBlockEntity;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.SettlementStorageBlockEntity;
import com.krisamerman.kingdomrts.settlement.WorkplaceBlockEntity;
import com.krisamerman.kingdomrts.settlement.WorkplaceOps;
import com.krisamerman.kingdomrts.unit.Occupation;
import com.krisamerman.kingdomrts.unit.Stock;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The builders' hut: builders are hired there (by hand or automatically), go back to it between
// sites, and take from and put things back in its stock before settlement storage. Losing the hut
// (or being fired from it) takes a builder off its site. Each test founds a settlement, so each has
// its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class BuildersHutGameTests {
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");
    private static final BlockPos SITE = new BlockPos(1, FLOOR_Y + 1, 1);
    private static final BlockPos HUT = new BlockPos(13, FLOOR_Y + 1, 13);
    private static final BlockPos STORAGE = new BlockPos(13, FLOOR_Y + 1, 3);
    private static final int TIMEOUT = 20 * 150;

    @GameTest(template = TEMPLATE, batch = "builders_hut_hire", timeoutTicks = 20 * 30)
    public static void anUnemployedSettlerTakesTheHutsOpeningAndWaitsThere(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        buildersHut(helper, HUT);
        Unit settler = settler(helper, settlement, blue, HALL.east(2));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(Occupation.BUILDER.equals(settler.getOccupationId())
                        && helper.absolutePos(HUT).equals(settler.getWorkplace()), "Waiting for the settler to be hired at the builders' hut"))
                .thenExecute(() -> helper.assertTrue(BuilderOps.isBuilder(helper.getLevel(), current(helper, settlement), settler.getUUID()),
                        "It should be one of the settlement's builders"))
                .thenWaitUntil(() -> helper.assertTrue(settler.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(HUT))) < 4,
                        "Waiting for the idle builder to wait at its hut"))
                .thenExecute(() -> removeHall(helper, HALL))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_hut_stock", timeoutTicks = TIMEOUT)
    public static void itBuildsFromTheHutsStockAndPutsLeftoversBackThere(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        buildersHut(helper, HUT);
        WorkplaceBlockEntity hutStock = (WorkplaceBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HUT));
        SettlementStorageBlockEntity storage = storage(helper);
        Building shed = commission(helper, blue);
        BuildingOps.remaining(helper.getLevel(), shed).remaining().materials()
                .forEach((item, count) -> Stock.insert(hutStock, new ItemStack(item, count))); // in the hut, not storage
        Stock.insert((ConstructionSiteBlockEntity) helper.getLevel().getBlockEntity(shed.site()), new ItemStack(Items.DIRT, 64)); // left over
        Unit builder = hutBuilder(helper, settlement, blue);
        assign(helper, settlement, shed, builder);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).isBuilt()
                        && current(helper, settlement).buildingAt(shed.origin()).site() == null, "Waiting for the shed to be built and its site closed"))
                .thenWaitUntil(() -> helper.assertTrue(builder.getInventory().isEmpty(), "Waiting for the builder to put everything away"))
                .thenExecute(() -> {
                    helper.assertTrue(hutStock.countItem(Items.DIRT) == 64, "The leftovers should go back to the hut");
                    helper.assertTrue(storage.isEmpty(), "Settlement storage isn't needed while the hut has room");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_hut_full", timeoutTicks = TIMEOUT)
    public static void withTheHutFullLeftoversGoToSettlementStorage(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        buildersHut(helper, HUT);
        WorkplaceBlockEntity hutStock = (WorkplaceBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HUT));
        for (int i = 0; i < hutStock.getContainerSize(); i++) {
            hutStock.setItem(i, new ItemStack(Items.COBBLESTONE, 64)); // full
        }
        SettlementStorageBlockEntity storage = storage(helper);
        Building shed = commission(helper, blue);
        Stock.insert((ConstructionSiteBlockEntity) helper.getLevel().getBlockEntity(shed.site()), new ItemStack(Items.DIRT, 64));
        Unit builder = hutBuilder(helper, settlement, blue);
        assign(helper, settlement, shed, builder);
        BuildingOps.finish(helper.getLevel(), current(helper, settlement), shed);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).site() == null, "Waiting for the site to close"))
                .thenWaitUntil(() -> helper.assertTrue(storage.countItem(Items.DIRT) == 64, "Waiting for the leftovers to reach settlement storage"))
                .thenExecute(() -> removeHall(helper, HALL))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_hut_removed")
    public static void losingTheHutTakesItsBuildersOffTheirSites(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        buildersHut(helper, HUT);
        Building shed = commission(helper, blue);
        Unit builder = hutBuilder(helper, settlement, blue);
        assign(helper, settlement, shed, builder);
        helper.setBlock(HUT, Blocks.AIR); // the hut goes
        helper.assertTrue(builder.getOccupationId() == null && builder.getWorkplace() == null, "Its builder should be out of a job");
        helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).builders().isEmpty(), "...and off its site");
        Unit second = hutBuilderAt(helper, settlement, blue, HALL.south(2));
        helper.assertTrue(second == null, "With no hut, nobody can be hired as a builder");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_hut_fired")
    public static void firingAtTheHutTakesTheBuilderOffItsSite(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        buildersHut(helper, HUT);
        Building shed = commission(helper, blue);
        Unit builder = hutBuilder(helper, settlement, blue);
        assign(helper, settlement, shed, builder);
        helper.assertTrue(WorkplaceOps.fire(helper.getLevel(), builder), "Expected it fired from the hut");
        helper.assertTrue(builder.getOccupationId() == null, "Fired: unemployed");
        helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).builders().isEmpty(), "Fired: off its site");
        removeHall(helper, HALL);
        helper.succeed();
    }

    // Builders used to be hired at the town hall, with no workplace. One of those, from an old save,
    // loses the job when it loads (catches up), and comes off its site, so it can be hired at a hut.
    @GameTest(template = TEMPLATE, batch = "builders_hut_old_save")
    public static void aBuilderHiredAtTheHallBeforeHutsLosesTheJobWhenItLoads(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        Unit old = settler(helper, settlement, blue, HALL.east(2));
        old.setOccupation(Occupation.BUILDER); // as hiring at the hall did: a builder, no workplace
        SettlementOps.refreshResident(helper.getLevel(), old);
        assign(helper, settlement, shed, old);
        SettlementOps.catchUp(helper.getLevel(), old);
        helper.assertTrue(old.getOccupationId() == null, "A builder with no builders' hut should lose the job");
        helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).builders().isEmpty(), "...and come off its site");
        removeHall(helper, HALL);
        helper.succeed();
    }

    private static Unit settler(GameTestHelper helper, Settlement settlement, Faction faction, BlockPos pos) {
        Unit unit = unit(helper, pos, faction, null, true);
        SettlementOps.setUnitSettlement(helper.getLevel(), unit, settlement);
        return unit;
    }

    private static Unit hutBuilder(GameTestHelper helper, Settlement settlement, Faction faction) {
        Unit unit = hutBuilderAt(helper, settlement, faction, HALL.east(2));
        if (unit == null) {
            throw new GameTestAssertException("Couldn't hire a builder at the hut");
        }
        return unit;
    }

    // A settler hired at the builders' hut, or null if it couldn't be.
    private static Unit hutBuilderAt(GameTestHelper helper, Settlement settlement, Faction faction, BlockPos pos) {
        Unit unit = settler(helper, settlement, faction, pos);
        return WorkplaceOps.hire(helper.getLevel(), unit, helper.absolutePos(HUT)) == null ? unit : null;
    }

    private static SettlementStorageBlockEntity storage(GameTestHelper helper) {
        helper.setBlock(STORAGE, KingdomRts.SETTLEMENT_STORAGE.get());
        Settlement settlement = SettlementOps.registerStorage(helper.getLevel(), helper.absolutePos(STORAGE));
        if (settlement == null || !(helper.getBlockEntity(STORAGE) instanceof SettlementStorageBlockEntity storage)) {
            throw new GameTestAssertException("Couldn't place settlement storage");
        }
        return storage;
    }

    private static Building commission(GameTestHelper helper, Faction faction) {
        BuildingOps.CommissionResult result = BuildingOps.commission(helper.getLevel(), faction, SHED, helper.absolutePos(SITE), Rotation.NONE);
        if (result.building() == null) {
            throw new GameTestAssertException("Couldn't commission the shed: " + result.problem());
        }
        return result.building();
    }

    private static void assign(GameTestHelper helper, Settlement settlement, Building shed, Unit builder) {
        String problem = BuilderOps.assign(helper.getLevel(), current(helper, settlement), shed, builder.getUUID());
        if (problem != null) {
            throw new GameTestAssertException("Couldn't assign the builder: " + problem);
        }
    }
}
