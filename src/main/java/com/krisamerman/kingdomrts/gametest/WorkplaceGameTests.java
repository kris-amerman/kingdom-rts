package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.current;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.lumberjackHut;
import static com.krisamerman.kingdomrts.gametest.TestSupport.mockPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.runCommand;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import java.util.List;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.Workplace;
import com.krisamerman.kingdomrts.settlement.WorkplaceOps;
import com.krisamerman.kingdomrts.unit.Occupation;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Workplaces: a hut is recorded on its settlement, units get a job only by being hired there (up
// to its capacity), and lose it when fired, when they leave the settlement, or when the hut is
// removed, including units that were unloaded at the time. Huts and their workers pass with the
// settlement on capture, and other factions can't break them. Each test founds a settlement, so
// each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class WorkplaceGameTests {
    private static final BlockPos HUT = new BlockPos(4, FLOOR_Y + 1, 4);
    private static final BlockPos A = new BlockPos(12, FLOOR_Y + 1, 12);
    private static final BlockPos B = new BlockPos(12, FLOOR_Y + 1, 4);

    @GameTest(template = TEMPLATE, batch = "workplace_record")
    public static void hutIsRecordedOnItsSettlement(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Settlement settlement = found(helper, HALL, blue);
        BlockPos pos = helper.absolutePos(HUT);
        helper.assertTrue(SettlementOps.ownClaimProblem(helper.getLevel(), pos, blue.id(), "workplace") == null, "Blue may build in its claim");
        helper.assertTrue(SettlementOps.ownClaimProblem(helper.getLevel(), pos, red.id(), "workplace") != null, "Red may not build in Blue's claim");
        lumberjackHut(helper, HUT);
        Workplace workplace = current(helper, settlement).workplaceAt(pos);
        helper.assertTrue(workplace != null && workplace.type().getPath().equals("lumberjack_hut"), "Expected the hut on the settlement");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "workplace_hire")
    public static void hiresUpToCapacity(GameTestHelper helper) {
        Settlement settlement = setUp(helper);
        Unit first = settler(helper, settlement, A);
        Unit second = settler(helper, settlement, B);
        BlockPos pos = helper.absolutePos(HUT);

        helper.assertTrue(WorkplaceOps.hire(helper.getLevel(), first, pos) == null, "Expected the first settler to be hired");
        helper.assertTrue(Occupation.LUMBERJACK.equals(first.getOccupationId()) && pos.equals(first.getWorkplace()),
                "Expected a lumberjack working at the hut");
        helper.assertTrue(current(helper, settlement).workplaceAt(pos).workers().containsKey(first.getUUID()), "Expected the hut to list its worker");

        String problem = WorkplaceOps.hire(helper.getLevel(), second, pos);
        helper.assertTrue(problem != null && problem.contains("full"), "The hut has room for one; got " + problem);
        helper.assertTrue(second.getOccupationId() == null && second.getWorkplace() == null, "The second settler should stay unemployed");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "workplace_refuse")
    public static void onlyUnemployedSettlersCanBeHired(GameTestHelper helper) {
        Settlement settlement = setUp(helper);
        BlockPos pos = helper.absolutePos(HUT);
        Unit stranger = unit(helper, A, null, null, false); // no settlement
        Unit soldier = unit(helper, B, null, false);
        SettlementOps.setUnitSettlement(helper.getLevel(), soldier, settlement);
        helper.assertTrue(String.valueOf(WorkplaceOps.hire(helper.getLevel(), stranger, pos)).contains("doesn't belong"),
                "A unit from outside the settlement can't be hired");
        helper.assertTrue(String.valueOf(WorkplaceOps.hire(helper.getLevel(), soldier, pos)).contains("already has an occupation"),
                "A soldier can't be hired");
        helper.assertTrue(current(helper, settlement).workplaceAt(pos).workers().isEmpty(), "Nobody should have been hired");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "workplace_fire")
    public static void firedAndLeavingWorkersLoseTheirJob(GameTestHelper helper) {
        Settlement settlement = setUp(helper);
        BlockPos pos = helper.absolutePos(HUT);
        Unit worker = settler(helper, settlement, A);
        WorkplaceOps.hire(helper.getLevel(), worker, pos);
        helper.assertTrue(WorkplaceOps.fire(helper.getLevel(), worker), "Expected the worker to be fired");
        helper.assertTrue(worker.getOccupationId() == null && worker.getWorkplace() == null, "A fired worker has no job");
        helper.assertTrue(current(helper, settlement).workplaceAt(pos).workers().isEmpty(), "The hut should forget a fired worker");

        WorkplaceOps.hire(helper.getLevel(), worker, pos);
        SettlementOps.setUnitSettlement(helper.getLevel(), worker, null); // leaves the settlement
        helper.assertTrue(worker.getOccupationId() == null && current(helper, settlement).workplaceAt(pos).workers().isEmpty(),
                "A worker who leaves the settlement leaves its workplace too");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "workplace_removed")
    public static void removingTheHutUnemploysItsWorkers(GameTestHelper helper) {
        Settlement settlement = setUp(helper);
        BlockPos pos = helper.absolutePos(HUT);
        lumberjackHut(helper, B.west(2)); // a second hut, so both workers can be hired
        Unit loaded = settler(helper, settlement, A);
        Unit away = settler(helper, settlement, B);
        WorkplaceOps.hire(helper.getLevel(), loaded, pos);
        WorkplaceOps.hire(helper.getLevel(), away, helper.absolutePos(B.west(2)));
        // The second worker is unloaded (as if its chunk unloaded) while the huts are removed.
        CompoundTag saved = away.saveWithoutId(new CompoundTag());
        away.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);

        helper.setBlock(HUT, Blocks.AIR);
        helper.setBlock(B.west(2), Blocks.AIR);
        helper.assertTrue(loaded.getOccupationId() == null && loaded.getWorkplace() == null, "The loaded worker should lose its job at once");
        helper.assertTrue(current(helper, settlement).workplaces().isEmpty(), "The settlement should forget removed huts");

        Unit back = KingdomRts.UNIT.get().create(helper.getLevel());
        back.load(saved);
        helper.getLevel().addFreshEntity(back);
        helper.assertTrue(back.getOccupationId() == null && back.getWorkplace() == null, "The unloaded worker should lose its job when it loads");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "workplace_death", timeoutTicks = 100)
    public static void aDeadWorkerLeavesItsWorkplace(GameTestHelper helper) {
        Settlement settlement = setUp(helper);
        BlockPos pos = helper.absolutePos(HUT);
        Unit worker = settler(helper, settlement, A);
        WorkplaceOps.hire(helper.getLevel(), worker, pos);
        worker.kill();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).workplaceAt(pos).workers().isEmpty(),
                        "Waiting for the dead worker to leave the hut"))
                .thenExecute(() -> removeHall(helper, HALL))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "workplace_capture")
    public static void hutAndWorkersPassWithTheSettlement(GameTestHelper helper) {
        Faction red = faction(helper, "Red");
        Settlement settlement = setUp(helper);
        BlockPos pos = helper.absolutePos(HUT);
        Unit worker = settler(helper, settlement, A);
        WorkplaceOps.hire(helper.getLevel(), worker, pos);
        SettlementOps.transfer(helper.getLevel(), settlement, red.id()); // as on capture
        helper.assertTrue(current(helper, settlement).workplaceAt(pos).workers().containsKey(worker.getUUID()), "The hut keeps its worker");
        helper.assertTrue(red.id().equals(worker.getFactionId()) && Occupation.LUMBERJACK.equals(worker.getOccupationId()),
                "The worker passes to Red and keeps its job");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "workplace_protected")
    public static void otherFactionsCantBreakTheHut(GameTestHelper helper) {
        Settlement settlement = setUp(helper);
        Faction red = faction(helper, "Red");
        ServerPlayer player = mockPlayer(helper);
        FactionData factions = FactionData.get(helper.getLevel());
        try {
            factions.join(player.getUUID(), red);
            player.gameMode.destroyBlock(helper.absolutePos(HUT));
            helper.assertBlockPresent(KingdomRts.LUMBERJACK_HUT.get(), HUT);
            helper.assertTrue(current(helper, settlement).workplaceAt(helper.absolutePos(HUT)) != null, "The hut should still be recorded");
        } finally {
            factions.leave(player.getUUID());
            removePlayer(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "workplace_commands")
    public static void hireAndFireCommands(GameTestHelper helper) {
        Settlement settlement = setUp(helper);
        BlockPos pos = helper.absolutePos(HUT);
        String hut = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        expectOutput(helper, A, "kingdomrts spawnunit occupation kingdomrts:lumberjack", "is a job");
        Unit worker = settler(helper, settlement, A);
        String here = "@e[type=kingdomrts:unit,distance=..1]";
        expectOutput(helper, A, "kingdomrts unit hire " + here + " " + hut, "Hired 1 unit(s)");
        helper.assertTrue(Occupation.LUMBERJACK.equals(worker.getOccupationId()), "Expected the hire command to give the job");
        expectOutput(helper, A, "kingdomrts unit info " + here, "workplace: " + pos.toShortString());
        expectOutput(helper, A, "kingdomrts unit fire " + here, "Fired 1 unit(s)");
        helper.assertTrue(worker.getOccupationId() == null, "Expected the fire command to take the job away");
        expectOutput(helper, A, "kingdomrts unit fire " + here, "None of those units has a workplace");
        removeHall(helper, HALL);
        helper.succeed();
    }

    // A settlement for a new faction, with a lumberjack hut.
    private static Settlement setUp(GameTestHelper helper) {
        Settlement settlement = found(helper, HALL, faction(helper, "Blue"));
        lumberjackHut(helper, HUT);
        return settlement;
    }

    // An unemployed unit of the settlement's owner that belongs to the settlement.
    private static Unit settler(GameTestHelper helper, Settlement settlement, BlockPos pos) {
        Faction owner = FactionData.get(helper.getLevel()).byId(settlement.owner());
        Unit unit = unit(helper, pos, owner, null, false);
        SettlementOps.setUnitSettlement(helper.getLevel(), unit, settlement);
        return unit;
    }

    private static void expectOutput(GameTestHelper helper, BlockPos relativePos, String command, String expected) {
        List<String> output = runCommand(helper, relativePos, command);
        helper.assertTrue(output.stream().anyMatch(line -> line.contains(expected)),
                "/" + command + ": expected output containing \"" + expected + "\", got " + output);
    }
}
