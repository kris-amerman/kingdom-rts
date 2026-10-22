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

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.BuilderOps;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.BuildingSiteMenu;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Clearing out a finished site: the building counts as built at once, but its construction site
// block stays until its builders have carried what's left in its stock to settlement storage, a load
// at a time; then the site closes. With no room in storage, they hold onto it and the site waits.
// Each test founds a settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class BuilderClearOutGameTests {
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");
    private static final BlockPos SITE = new BlockPos(1, FLOOR_Y + 1, 1);
    private static final BlockPos STORAGE = new BlockPos(13, FLOOR_Y + 1, 13);
    // More than a builder carries at once (9 slots), so it takes several trips.
    private static final int SPARE_DIRT_STACKS = 12;
    private static final int TIMEOUT = 20 * 150;

    @GameTest(template = TEMPLATE, batch = "builders_clear_out", timeoutTicks = TIMEOUT)
    public static void whatsLeftOnSiteGoesToStorageBeforeTheSiteCloses(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        SettlementStorageBlockEntity storage = storage(helper);
        ConstructionSiteBlockEntity stock = stockUp(helper, shed);
        Unit builder = builder(helper, settlement, blue, HALL.east(2));
        assign(helper, settlement, shed, builder);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).isBuilt(), "Waiting for the shed to be built"))
                .thenExecute(() -> {
                    Building built = current(helper, settlement).buildingAt(shed.origin());
                    helper.assertTrue(built.isBeingClearedOut(), "Its site should stay open while its stock is cleared out");
                    helper.assertTrue(helper.getLevel().getBlockState(shed.site()).is(KingdomRts.CONSTRUCTION_SITE.get()), "The site block should still be there");
                    BuildingSiteMenu.SiteInfo info = BuildingSiteMenu.snapshot(helper.getLevel(), stock, BuildingSiteMenu.View.OVERVIEW, 0);
                    helper.assertTrue(info != null && info.built(), "The site panel should show it's built and being cleared out");
                })
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).site() == null, "Waiting for the site to close"))
                .thenExecute(() -> {
                    helper.assertFalse(helper.getLevel().getBlockState(shed.site()).is(KingdomRts.CONSTRUCTION_SITE.get()), "The site block should be gone");
                    helper.assertTrue(TestSupport.stored(helper, storage, Items.DIRT) == 64 * SPARE_DIRT_STACKS, "All the spare dirt should be in storage");
                    helper.assertTrue(TestSupport.stored(helper, storage, Items.WOODEN_PICKAXE) == 1, "So should the pickaxe");
                    helper.assertTrue(builder.getInventory().isEmpty(), "The builder shouldn't be left carrying anything");
                    helper.assertTrue(BuilderOps.siteOf(current(helper, settlement), builder.getUUID()) == null, "The builder should be back in the pool");
                    assertNothingDropped(helper);
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_clear_out_full", timeoutTicks = TIMEOUT)
    public static void withNoRoomInStorageTheSiteWaits(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        SettlementStorageBlockEntity storage = storage(helper);
        for (int i = 0; i < storage.getContainerSize(); i++) {
            storage.setItem(i, new ItemStack(Items.COBBLESTONE, 64)); // full
        }
        stockUp(helper, shed);
        Unit builder = builder(helper, settlement, blue, HALL.east(2));
        TestSupport.fill(TestSupport.buildersHutStock(helper)); // no room in the builders' hut either
        assign(helper, settlement, shed, builder);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).isBuilt(), "Waiting for the shed to be built"))
                .thenIdle(200)
                .thenExecute(() -> {
                    helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).isBeingClearedOut(), "With no room in storage, the site stays open");
                    helper.assertFalse(builder.getInventory().isEmpty(), "The builder holds onto a load");
                    assertNothingDropped(helper);
                    for (int i = 0; i < storage.getContainerSize(); i++) {
                        storage.setItem(i, ItemStack.EMPTY); // room at last
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).site() == null, "Waiting for the site to close"))
                .thenExecute(() -> {
                    helper.assertTrue(TestSupport.stored(helper, storage, Items.DIRT) == 64 * SPARE_DIRT_STACKS && TestSupport.stored(helper, storage, Items.WOODEN_PICKAXE) == 1,
                            "Everything left on site should be in storage");
                    assertNothingDropped(helper);
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_clear_out_broken", timeoutTicks = TIMEOUT)
    public static void breakingTheSiteBlockOfABuiltBuildingKeepsTheBuilding(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        Stock.insert(stockUp(helper, shed), new ItemStack(Items.TORCH)); // something left over
        BuildingOps.finish(helper.getLevel(), current(helper, settlement), shed);
        helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).isBeingClearedOut(), "Finished, with its stock still to clear out");
        helper.getLevel().destroyBlock(shed.site(), false);
        helper.startSequence()
                .thenExecute(() -> {
                    Building built = current(helper, settlement).buildingAt(shed.origin());
                    helper.assertTrue(built != null && built.isBuilt() && built.site() == null, "The building should stay, built, with its site closed");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    private static SettlementStorageBlockEntity storage(GameTestHelper helper) {
        helper.setBlock(STORAGE, KingdomRts.SETTLEMENT_STORAGE.get());
        Settlement settlement = SettlementOps.registerStorage(helper.getLevel(), helper.absolutePos(STORAGE));
        if (settlement == null || !(helper.getBlockEntity(STORAGE) instanceof SettlementStorageBlockEntity storage)) {
            throw new GameTestAssertException("Couldn't place settlement storage");
        }
        return storage;
    }

    // Fills the site's stock with what the shed needs, a pickaxe, and more spare dirt than one load.
    private static ConstructionSiteBlockEntity stockUp(GameTestHelper helper, Building shed) {
        ConstructionSiteBlockEntity stock = (ConstructionSiteBlockEntity) helper.getLevel().getBlockEntity(shed.site());
        BuildingOps.remaining(helper.getLevel(), shed).remaining().materials()
                .forEach((item, count) -> Stock.insert(stock, new ItemStack(item, count)));
        Stock.insert(stock, new ItemStack(Items.WOODEN_PICKAXE));
        for (int i = 0; i < SPARE_DIRT_STACKS; i++) {
            Stock.insert(stock, new ItemStack(Items.DIRT, 64));
        }
        return stock;
    }

    private static void assertNothingDropped(GameTestHelper helper) {
        List<ItemEntity> left = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(HALL)).inflate(16));
        helper.assertTrue(left.isEmpty(), "Nothing should be dropped; found "
                + left.stream().map(item -> item.getItem().getCount() + " " + item.getItem().getHoverName().getString()).toList());
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
