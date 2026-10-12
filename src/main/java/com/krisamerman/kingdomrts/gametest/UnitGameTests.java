package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.control;
import static com.krisamerman.kingdomrts.gametest.TestSupport.current;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.setWar;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import java.util.UUID;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.TownHallBlockEntity;
import com.krisamerman.kingdomrts.unit.MilitaryOccupation;
import com.krisamerman.kingdomrts.unit.Occupation;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Units and their occupations: only a military occupation counts at capture, occupation stats
// reach the entity, and everything a unit has survives saving and loading.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class UnitGameTests {
    private static final BlockPos SPOT = new BlockPos(4, FLOOR_Y + 1, 4);

    @GameTest(template = TEMPLATE, batch = "unit_capture_presence", timeoutTicks = 300)
    public static void unitsWithoutMilitaryOccupationDontCount(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        setWar(helper, blue, red, true);
        Settlement settlement = found(helper, HALL, blue);
        // Hostile units right at the hall, but none has a military occupation: one has none, and
        // one names an occupation that doesn't exist.
        unit(helper, SPOT, red, null, false);
        unit(helper, SPOT.east(), red, null, false);
        unit(helper, SPOT.east(2), red, ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "missing"), false);

        helper.startSequence()
                .thenIdle(5 * TownHallBlockEntity.CHECK_INTERVAL_TICKS)
                .thenExecute(() -> {
                    helper.assertTrue(control(helper) == TownHallBlockEntity.MAX_CONTROL,
                            "Expected units without a military occupation to be ignored; control is " + control(helper));
                    helper.assertTrue(current(helper, settlement).owner().equals(blue.id()), "Expected Blue to keep the settlement");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void soldierGetsStatsFromItsOccupation(GameTestHelper helper) {
        Unit unit = unit(helper, SPOT, null, false);
        MilitaryOccupation soldier = (MilitaryOccupation) Occupation.get(helper.getLevel(), Occupation.SOLDIER).orElseThrow();
        helper.assertTrue(unit.getOccupation() == soldier, "Expected the soldier occupation");
        helper.assertTrue(unit.getMaxHealth() == (float) soldier.maxHealth(), "Max health " + unit.getMaxHealth() + " != " + soldier.maxHealth());
        helper.assertTrue(unit.getAttackRange() == soldier.attackRange(), "Attack range " + unit.getAttackRange() + " != " + soldier.attackRange());
        helper.assertTrue(unit.isMilitary(), "A soldier should count at capture");

        Unit idle = unit(helper, SPOT.east(), null, null, false);
        helper.assertTrue(idle.getOccupation() == null && idle.getAttackRange() == 0 && !idle.isMilitary(),
                "A unit with no occupation has no attack range and doesn't count at capture");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unitSurvivesSaveAndLoad(GameTestHelper helper) {
        Faction red = faction(helper, "Red");
        Unit original = unit(helper, SPOT, red, false);
        UUID settlementId = UUID.randomUUID();
        BlockPos home = helper.absolutePos(SPOT.east(3));
        original.setSettlement(settlementId, 3);
        original.setHome(home);
        original.getInventory().setItem(2, new ItemStack(Items.OAK_LOG, 12));

        // The same save data the game writes to disk, loaded into a fresh entity.
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        Unit loaded = KingdomRts.UNIT.get().create(helper.getLevel());
        loaded.load(saved);

        helper.assertTrue(red.id().equals(loaded.getFactionId()), "Faction not saved");
        helper.assertTrue(settlementId.equals(loaded.getSettlementId()) && loaded.getSeenOwnerChanges() == 3, "Settlement not saved");
        helper.assertTrue(home.equals(loaded.getHome()), "Home not saved");
        helper.assertTrue(original.getPost().equals(loaded.getPost()), "Post not saved");
        helper.assertTrue(Occupation.SOLDIER.equals(loaded.getOccupationId()), "Occupation not saved");
        helper.assertTrue(loaded.getOccupation() != null && loaded.getAttackRange() == original.getAttackRange(),
                "Occupation not re-applied on load");
        ItemStack logs = loaded.getInventory().getItem(2);
        helper.assertTrue(logs.is(Items.OAK_LOG) && logs.getCount() == 12, "Inventory not saved: " + logs);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void inventoryDropsOnDeath(GameTestHelper helper) {
        Unit unit = unit(helper, SPOT, null, false);
        unit.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        unit.kill();
        helper.succeedWhen(() -> helper.assertItemEntityPresent(Items.DIAMOND, SPOT, 2.0));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void unitPicksUpItemsNearby(GameTestHelper helper) {
        Unit unit = unit(helper, SPOT, null, false);
        helper.spawnItem(Items.OAK_LOG, Vec3.atBottomCenterOf(SPOT.east())); // within reach
        helper.succeedWhen(() -> {
            helper.assertTrue(unit.getInventory().countItem(Items.OAK_LOG) == 1, "Waiting for the unit to pick up the log");
            helper.assertItemEntityNotPresent(Items.OAK_LOG, SPOT, 3.0);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void fullUnitLeavesItemsOnTheGround(GameTestHelper helper) {
        Unit unit = unit(helper, SPOT, null, false);
        for (int i = 0; i < Unit.INVENTORY_SIZE; i++) {
            unit.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        helper.spawnItem(Items.OAK_LOG, Vec3.atBottomCenterOf(SPOT.east())); // within reach
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertItemEntityPresent(Items.OAK_LOG, SPOT, 3.0);
                    helper.assertTrue(unit.getInventory().countItem(Items.OAK_LOG) == 0, "A full unit shouldn't take more items");
                })
                .thenSucceed();
    }
}
