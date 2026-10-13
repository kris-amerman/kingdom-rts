package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.control;
import static com.krisamerman.kingdomrts.gametest.TestSupport.current;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.runCommand;
import static com.krisamerman.kingdomrts.gametest.TestSupport.setWar;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import java.util.List;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Units belonging to settlements: the settlement's unit list follows deaths and dissolving, and on
// capture its non-military units pass to the new owner, including units that were unloaded at the
// time. Each test founds a settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class SettlementUnitGameTests {
    private static final BlockPos CIVILIAN = new BlockPos(12, FLOOR_Y + 1, 12);
    private static final BlockPos SOLDIER = new BlockPos(12, FLOOR_Y + 1, 4);

    @GameTest(template = TEMPLATE, batch = "settlement_units_capture", timeoutTicks = 600)
    public static void captureHandsCiviliansToNewOwner(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        setWar(helper, blue, red, true);
        Settlement settlement = found(helper, HALL, blue);
        Unit civilian = unit(helper, CIVILIAN, blue, null, false);
        Unit soldier = unit(helper, SOLDIER, blue, false); // one defender, outnumbered below
        SettlementOps.setUnitSettlement(helper.getLevel(), civilian, settlement);
        SettlementOps.setUnitSettlement(helper.getLevel(), soldier, settlement);
        for (int i = 0; i < 3; i++) {
            unit(helper, new BlockPos(4 + i, FLOOR_Y + 1, 4), red, false);
        }

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).owner().equals(red.id()),
                        "Waiting for Red to capture; control is " + control(helper)))
                .thenExecute(() -> {
                    helper.assertTrue(red.id().equals(civilian.getFactionId()), "Expected the civilian to pass to Red");
                    helper.assertTrue(blue.id().equals(soldier.getFactionId()), "Expected the soldier to stay Blue");
                    Settlement now = current(helper, settlement);
                    helper.assertTrue(now.units().contains(civilian.getUUID()) && now.units().contains(soldier.getUUID()),
                            "Both units should still belong to the settlement");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "settlement_units_unloaded")
    public static void unloadedUnitsCatchUpAfterCapture(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Settlement settlement = found(helper, HALL, blue);
        Unit civilian = unit(helper, CIVILIAN, blue, null, false);
        Unit soldier = unit(helper, SOLDIER, blue, false);
        SettlementOps.setUnitSettlement(helper.getLevel(), civilian, settlement);
        SettlementOps.setUnitSettlement(helper.getLevel(), soldier, settlement);

        // Unload both the way a chunk unload does: saved, then removed without being destroyed.
        CompoundTag savedCivilian = civilian.saveWithoutId(new CompoundTag());
        CompoundTag savedSoldier = soldier.saveWithoutId(new CompoundTag());
        civilian.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        soldier.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(current(helper, settlement).units().size() == 2, "Unloading shouldn't remove units from the settlement");

        SettlementOps.transfer(helper.getLevel(), settlement, red.id()); // captured while they were away

        Unit civilianBack = reload(helper, savedCivilian);
        Unit soldierBack = reload(helper, savedSoldier);
        helper.assertTrue(red.id().equals(civilianBack.getFactionId()), "Expected the reloaded civilian to pass to Red");
        helper.assertTrue(blue.id().equals(soldierBack.getFactionId()), "Expected the reloaded soldier to stay Blue");
        helper.assertTrue(civilianBack.getSeenOwnerChanges() == current(helper, settlement).ownerChanges(),
                "Expected the civilian to have caught up");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "settlement_units_death", timeoutTicks = 100)
    public static void deadUnitLeavesSettlement(GameTestHelper helper) {
        Settlement settlement = found(helper, HALL, faction(helper, "Blue"));
        Unit civilian = unit(helper, CIVILIAN, null, null, false);
        SettlementOps.setUnitSettlement(helper.getLevel(), civilian, settlement);
        helper.assertTrue(current(helper, settlement).units().contains(civilian.getUUID()), "Expected the unit to join");
        civilian.kill();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(current(helper, settlement).units().contains(civilian.getUUID()),
                        "Waiting for the dead unit to leave the settlement"))
                .thenExecute(() -> removeHall(helper, HALL))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "settlement_units_dissolve")
    public static void dissolvingReleasesUnits(GameTestHelper helper) {
        Settlement settlement = found(helper, HALL, faction(helper, "Blue"));
        Unit civilian = unit(helper, CIVILIAN, null, null, false);
        SettlementOps.setUnitSettlement(helper.getLevel(), civilian, settlement);
        removeHall(helper, HALL); // dissolves the settlement
        helper.assertTrue(civilian.getSettlementId() == null, "Expected the unit to no longer belong to a settlement");
        helper.succeed();
    }

    // The staging and inspection commands, run for real: spawn a unit, join it to the settlement
    // with a selector, see it counted and described, then make it leave.
    @GameTest(template = TEMPLATE, batch = "settlement_units_commands")
    public static void commandsStageAndInspectUnits(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        String units = "@e[type=kingdomrts:unit,distance=..1]";

        expectOutput(helper, CIVILIAN, "kingdomrts spawnunit occupation none faction " + blue.name(), "Spawned ");
        expectOutput(helper, CIVILIAN, "kingdomrts unit join " + units + " " + settlement.name(), "1 unit(s) joined " + settlement.name());
        helper.assertTrue(current(helper, settlement).units().size() == 1, "Expected the settlement to have 1 unit");
        expectOutput(helper, HALL, "kingdomrts settlement here", settlement.name() + " (" + blue.name() + "), 1 units");
        expectOutput(helper, CIVILIAN, "kingdomrts unit info " + units,
                "faction: " + blue.name() + " | occupation: none | settlement: " + settlement.name());

        expectOutput(helper, CIVILIAN, "kingdomrts unit leave " + units, "1 unit(s) left their settlement");
        helper.assertTrue(current(helper, settlement).units().isEmpty(), "Expected the settlement to have no units");
        expectOutput(helper, CIVILIAN, "kingdomrts unit info " + units, "settlement: none");

        // Nothing selected: vanilla's selector refuses before our command runs. Something selected
        // but no units among it: our own message.
        expectOutput(helper, HALL, "kingdomrts unit join " + units + " " + settlement.name(), "No entity was found");
        helper.spawn(EntityType.PIG, HALL.east(4));
        expectOutput(helper, HALL.east(4), "kingdomrts unit join @e[type=minecraft:pig,distance=..1] " + settlement.name(), "No units selected");
        expectOutput(helper, CIVILIAN, "kingdomrts unit join " + units + " Nowhere", "Unknown settlement: Nowhere");
        removeHall(helper, HALL);
        helper.succeed();
    }

    // Runs a command from relativePos and checks some line of its output contains expected.
    private static void expectOutput(GameTestHelper helper, BlockPos relativePos, String command, String expected) {
        List<String> output = runCommand(helper, relativePos, command);
        helper.assertTrue(output.stream().anyMatch(line -> line.contains(expected)),
                "/" + command + ": expected output containing \"" + expected + "\", got " + output);
    }

    // Loads a saved unit back into the world, as loading its chunk would.
    private static Unit reload(GameTestHelper helper, CompoundTag saved) {
        Unit unit = KingdomRts.UNIT.get().create(helper.getLevel());
        unit.load(saved);
        helper.getLevel().addFreshEntity(unit);
        return unit;
    }
}
