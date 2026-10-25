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
import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.BuilderOps;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.ConstructionSiteBlockEntity;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.SettlementStorageBlockEntity;
import com.krisamerman.kingdomrts.settlement.Workplace;
import com.krisamerman.kingdomrts.settlement.WorkplaceOps;
import com.krisamerman.kingdomrts.unit.Stock;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The lumberjack hut as a building (S-026): its construction site block stands on its hut block's
// spot while it's built, nobody is hired there until it's finished, and then the site block becomes
// the hut block, keeping the site's leftovers as its stock, and the hut hires. Demolishing it fires
// its worker, and the hut block gives nothing back. Each test founds a settlement, so each has its
// own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class LumberjackHutBuildingGameTests {
    private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "lumberjack_hut");
    private static final BlockPos SITE = new BlockPos(1, FLOOR_Y + 1, 1);
    private static final BlockPos STORAGE = new BlockPos(13, FLOOR_Y + 1, 13);
    private static final int TIMEOUT = 20 * 600;

    // The site block goes on the hut block's spot, turned with the building, and the hut isn't a
    // workplace yet: a settler looking for work doesn't find it.
    @GameTest(template = TEMPLATE, batch = "hut_building_site")
    public static void aHutsSiteStandsWhereItsHutBlockGoes(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Unit settler = settler(helper, settlement, blue);
        for (Rotation rotation : List.of(Rotation.NONE, Rotation.CLOCKWISE_90)) {
            BlockPos origin = rotation == Rotation.NONE ? SITE : SITE.south(8);
            Building hut = commission(helper, blue, origin, rotation);
            BlockPos core = coreSpot(helper, hut);
            helper.assertTrue(core.equals(hut.site()), "Expected the site on the hut block's spot " + core.toShortString() + " (" + rotation + "), got "
                    + hut.site());
            helper.assertTrue(helper.getLevel().getBlockState(core).is(KingdomRts.CONSTRUCTION_SITE.get()), "Expected a site block there (" + rotation + ")");
            helper.assertTrue(WorkplaceOps.workplaceAt(helper.getLevel(), core) == null, "It shouldn't be a workplace yet (" + rotation + ")");
        }
        helper.assertFalse(WorkplaceOps.seekJob(helper.getLevel(), settler), "Nobody should be hired at a hut that isn't built");
        removeHall(helper, HALL);
        helper.succeed();
    }

    // Finished (here at once), the site block becomes the hut block with the site's stock in it, the
    // site closes, and the hut hires a settler looking for work. Nothing drops.
    @GameTest(template = TEMPLATE, batch = "hut_building_finished")
    public static void aFinishedHutKeepsItsLeftoversAndHires(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Unit settler = settler(helper, settlement, blue);
        Building hut = commission(helper, blue, SITE, Rotation.NONE);
        ConstructionSiteBlockEntity site = (ConstructionSiteBlockEntity) helper.getLevel().getBlockEntity(hut.site());
        Stock.insert(site, new ItemStack(Items.IRON_AXE));
        Stock.insert(site, new ItemStack(Items.OAK_PLANKS, 10));
        BuildingOps.finish(helper.getLevel(), current(helper, settlement), hut);
        Building built = current(helper, settlement).buildingAt(hut.origin());
        helper.assertTrue(built != null && built.isBuilt() && built.site() == null, "Expected it built with its site closed: " + built);
        BlockPos core = coreSpot(helper, hut);
        helper.assertTrue(helper.getLevel().getBlockState(core).is(KingdomRts.LUMBERJACK_HUT.get()), "Expected the hut block where the site was");
        Workplace workplace = WorkplaceOps.workplaceAt(helper.getLevel(), core);
        helper.assertTrue(workplace != null, "Expected the hut to be a workplace now");
        Container stock = (Container) helper.getLevel().getBlockEntity(core);
        helper.assertTrue(stock.countItem(Items.IRON_AXE) == 1 && stock.countItem(Items.OAK_PLANKS) == 10, "Expected the site's leftovers in the hut's stock");
        assertNothingDropped(helper);
        helper.assertTrue(WorkplaceOps.seekJob(helper.getLevel(), settler) && core.equals(settler.getWorkplace()), "Expected the settler hired at the hut");
        removeHall(helper, HALL);
        helper.succeed();
    }

    // Breaking a hut's site block while it's built cancels it, as for any site.
    @GameTest(template = TEMPLATE, batch = "hut_building_cancel")
    public static void breakingItsSiteCancelsAHut(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building hut = commission(helper, blue, SITE, Rotation.NONE);
        helper.getLevel().destroyBlock(hut.site(), false);
        helper.assertTrue(current(helper, settlement).buildingAt(hut.origin()) == null, "Expected the hut no longer commissioned");
        helper.assertTrue(WorkplaceOps.workplaceAt(helper.getLevel(), hut.site()) == null, "Expected no workplace");
        removeHall(helper, HALL);
        helper.succeed();
    }

    // A builder builds the hut from settlement storage, around its site block, and the hut block
    // takes the site block's place once the rest is up.
    @GameTest(template = TEMPLATE, batch = "hut_building_built", timeoutTicks = TIMEOUT)
    public static void aBuilderBuildsTheHut(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building hut = commission(helper, blue, SITE, Rotation.NONE);
        SettlementStorageBlockEntity storage = storage(helper);
        BuildingOps.remaining(helper.getLevel(), hut).remaining().materials().forEach((item, count) -> Stock.insert(storage, new ItemStack(item, count)));
        Unit builder = builder(helper, settlement, blue);
        assign(helper, settlement, hut, builder);
        BlockPos core = coreSpot(helper, hut);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Building now = current(helper, settlement).buildingAt(hut.origin());
                    helper.assertTrue(now != null && now.isBuilt(), "Waiting for the hut to be built: "
                            + BuildingOps.remaining(helper.getLevel(), now != null ? now : hut).remaining().missing().stream()
                                    .map(part -> part.state() + " at " + part.pos().subtract(helper.absolutePos(SITE)).toShortString()).toList()
                            + " missing, builder " + builder.jobActivity() + " carrying " + builder.getInventory().getItems()
                            + ", storage " + storage.getItem(0) + " " + storage.getItem(1) + " " + storage.getItem(2) + " " + storage.getItem(3));
                })
                .thenExecute(() -> {
                    helper.assertTrue(helper.getLevel().getBlockState(core).is(KingdomRts.LUMBERJACK_HUT.get()), "Expected the hut block where the site was");
                    helper.assertTrue(WorkplaceOps.workplaceAt(helper.getLevel(), core) != null, "Expected the hut to be a workplace");
                    helper.assertTrue(storage.countItem(KingdomRts.LUMBERJACK_HUT_ITEM.get()) == 0, "The hut block isn't a material");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    // Builders take a built hut down: they empty its stock into storage, and taking its hut block
    // down fires its lumberjack and gives no hut item back.
    @GameTest(template = TEMPLATE, batch = "hut_building_demolish", timeoutTicks = TIMEOUT)
    public static void demolishingAHutFiresItsWorker(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building hut = commission(helper, blue, SITE, Rotation.NONE);
        BuildingOps.finish(helper.getLevel(), current(helper, settlement), hut);
        BlockPos core = coreSpot(helper, hut);
        Stock.insert((Container) helper.getLevel().getBlockEntity(core), new ItemStack(Items.DIRT, 20));
        Unit lumberjack = settler(helper, settlement, blue);
        String hired = WorkplaceOps.hire(helper.getLevel(), lumberjack, core);
        helper.assertTrue(hired == null, "Couldn't hire a lumberjack: " + hired);
        SettlementStorageBlockEntity storage = storage(helper);
        Building built = current(helper, settlement).buildingAt(hut.origin());
        String problem = BuildingOps.orderDemolition(helper.getLevel(), blue, current(helper, settlement), built);
        helper.assertTrue(problem == null, "Couldn't order the hut demolished: " + problem);
        Unit builder = builder(helper, settlement, blue);
        assign(helper, settlement, built, builder);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(hut.origin()) == null, "Waiting for the hut to be gone, builder "
                        + builder.jobActivity()))
                .thenWaitUntil(() -> helper.assertTrue(builder.getInventory().isEmpty(), "Waiting for the builder to put its last load away"))
                .thenExecute(() -> {
                    helper.assertTrue(lumberjack.getWorkplace() == null, "Expected the lumberjack out of a job");
                    helper.assertTrue(WorkplaceOps.workplaceAt(helper.getLevel(), core) == null, "Expected the workplace gone");
                    helper.assertTrue(TestSupport.stored(helper, storage, Items.DIRT) == 20, "Expected the hut's stock in storage");
                    helper.assertTrue(TestSupport.stored(helper, storage, KingdomRts.LUMBERJACK_HUT_ITEM.get()) == 0, "The hut block shouldn't come back as an item");
                    assertNothingDropped(helper);
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    private static Building commission(GameTestHelper helper, Faction faction, BlockPos relativeOrigin, Rotation rotation) {
        BuildingOps.CommissionResult result = BuildingOps.commission(helper.getLevel(), faction, HUT, helper.absolutePos(relativeOrigin), rotation);
        if (result.building() == null) {
            throw new GameTestAssertException("Couldn't commission the hut: " + result.problem());
        }
        return result.building();
    }

    // Where the hut's template puts its hut block, for this building.
    private static BlockPos coreSpot(GameTestHelper helper, Building hut) {
        return BuildingOps.placed(helper.getLevel(), hut).stream().filter(part -> part.state().is(KingdomRts.LUMBERJACK_HUT.get()))
                .map(Blueprint.Part::pos).findFirst().orElseThrow(() -> new GameTestAssertException("The hut's template has no hut block"));
    }

    // A settler of the settlement with no job, standing still.
    private static Unit settler(GameTestHelper helper, Settlement settlement, Faction faction) {
        Unit unit = unit(helper, HALL.west(2), faction, null, false);
        SettlementOps.setUnitSettlement(helper.getLevel(), unit, settlement);
        return unit;
    }

    // A builder carrying an iron pickaxe and axe to start with (builders don't fetch tools yet: S-042).
    private static Unit builder(GameTestHelper helper, Settlement settlement, Faction faction) {
        Unit unit = unit(helper, HALL.east(2), faction, null, true);
        unit.getInventory().addItem(new ItemStack(Items.IRON_PICKAXE));
        unit.getInventory().addItem(new ItemStack(Items.IRON_AXE));
        SettlementOps.setUnitSettlement(helper.getLevel(), unit, settlement);
        String problem = TestSupport.hireBuilder(helper, unit);
        if (problem != null) {
            throw new GameTestAssertException("Couldn't hire a builder: " + problem);
        }
        return unit;
    }

    private static void assign(GameTestHelper helper, Settlement settlement, Building hut, Unit builder) {
        String problem = BuilderOps.assign(helper.getLevel(), current(helper, settlement), hut, builder.getUUID());
        if (problem != null) {
            throw new GameTestAssertException("Couldn't assign the builder: " + problem);
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
}
