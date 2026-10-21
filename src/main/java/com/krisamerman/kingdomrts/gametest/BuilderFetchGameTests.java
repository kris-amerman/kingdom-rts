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
import java.util.UUID;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.BuilderOps;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.SettlementStorageBlockEntity;
import com.krisamerman.kingdomrts.settlement.SiteReservations;
import com.krisamerman.kingdomrts.unit.Stock;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Builders fetching materials: with nothing to place, one builder per site goes to the nearest
// settlement storage that has what the site is missing, takes what it can carry and puts it in the
// site's stock. Builders never drop what they carry: back in the pool, they take it to storage, or
// hold onto it while there's no room. Each test founds a settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class BuilderFetchGameTests {
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");
    private static final BlockPos SITE = new BlockPos(1, FLOOR_Y + 1, 1);
    private static final BlockPos STORAGE = new BlockPos(13, FLOOR_Y + 1, 13);
    private static final int TIMEOUT = 20 * 120;

    @GameTest(template = TEMPLATE, batch = "builders_fetch", timeoutTicks = TIMEOUT)
    public static void aBuilderFetchesFromStorageAndBuilds(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        SettlementStorageBlockEntity storage = storage(helper);
        Map<Item, Integer> needed = needed(helper, shed);
        needed.forEach((item, count) -> Stock.insert(storage, new ItemStack(item, count)));
        Unit builder = builder(helper, settlement, blue, HALL.east(2));
        assign(helper, settlement, shed, builder);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).isBuilt(), "Waiting for the shed to be built"))
                .thenExecute(() -> {
                    helper.assertTrue(storage.isEmpty(), "Everything the shed needed should have been fetched from storage");
                    helper.assertTrue(builder.getInventory().isEmpty(), "The builder shouldn't be left carrying anything");
                    assertNothingDropped(helper, shed);
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    // The shed's materials fit in one load, so two builders fetching at once wouldn't both end up
    // carrying anything; instead, another builder holds the site's fetch, and this one must leave
    // the fetching to it until it's let go.
    @GameTest(template = TEMPLATE, batch = "builders_fetch_one", timeoutTicks = TIMEOUT + 200)
    public static void whileAnotherBuilderFetchesItDoesnt(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        SettlementStorageBlockEntity storage = storage(helper);
        needed(helper, shed).forEach((item, count) -> Stock.insert(storage, new ItemStack(item, count)));
        UUID other = UUID.randomUUID();
        SiteReservations.reserveFetch(shed.origin(), other, helper.getLevel().getGameTime());
        Unit builder = builder(helper, settlement, blue, HALL.east(2));
        assign(helper, settlement, shed, builder);
        helper.startSequence()
                .thenIdle(200)
                .thenExecute(() -> {
                    helper.assertTrue(builder.getInventory().isEmpty(), "Another builder is fetching for the site");
                    helper.assertFalse(storage.isEmpty(), "Nothing should have been taken from storage yet");
                    SiteReservations.releaseFetch(shed.origin(), other);
                })
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).isBuilt(), "Waiting for the shed to be built"))
                .thenExecute(() -> removeHall(helper, HALL))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_fetch_wait", timeoutTicks = TIMEOUT + 200)
    public static void withNothingInStorageItWaitsThenCarriesOn(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = commission(helper, blue);
        SettlementStorageBlockEntity storage = storage(helper);
        Map<Item, Integer> needed = needed(helper, shed);
        Unit builder = builder(helper, settlement, blue, HALL.east(2));
        assign(helper, settlement, shed, builder);
        helper.startSequence()
                .thenIdle(200)
                .thenExecute(() -> {
                    helper.assertFalse(current(helper, settlement).buildingAt(shed.origin()).isBuilt(), "Nothing to build with yet");
                    helper.assertTrue(builder.getInventory().isEmpty(), "Nothing to carry yet");
                    needed.forEach((item, count) -> Stock.insert(storage, new ItemStack(item, count)));
                })
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).isBuilt(), "Waiting for the shed to be built"))
                .thenExecute(() -> removeHall(helper, HALL))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_fetch_hold", timeoutTicks = TIMEOUT)
    public static void aBuilderInThePoolHoldsWhatItCarriesUntilThereIsRoom(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        SettlementStorageBlockEntity storage = storage(helper);
        for (int i = 0; i < storage.getContainerSize(); i++) {
            storage.setItem(i, new ItemStack(Items.DIRT, 64)); // full
        }
        Unit builder = builder(helper, settlement, blue, HALL.east(2));
        builder.getInventory().addItem(new ItemStack(Items.OAK_PLANKS, 20)); // left over from a trip
        helper.startSequence()
                .thenIdle(300)
                .thenExecute(() -> {
                    helper.assertTrue(builder.getInventory().countItem(Items.OAK_PLANKS) == 20, "With no room in storage, it holds onto the planks");
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(HALL)).inflate(16)).isEmpty(),
                            "Nothing should be dropped");
                    storage.setItem(0, ItemStack.EMPTY); // room at last
                })
                .thenWaitUntil(() -> helper.assertTrue(storage.countItem(Items.OAK_PLANKS) == 20, "Waiting for the planks to be put in storage"))
                .thenExecute(() -> {
                    helper.assertTrue(builder.getInventory().isEmpty(), "It should have put everything down");
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

    private static Map<Item, Integer> needed(GameTestHelper helper, Building shed) {
        return BuildingOps.remaining(helper.getLevel(), shed).remaining().materials();
    }

    // Leftovers drop when a finished site's block is taken away; with storage holding exactly what
    // was needed, there should be none.
    private static void assertNothingDropped(GameTestHelper helper, Building shed) {
        List<ItemEntity> left = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(shed.site()).inflate(16));
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
