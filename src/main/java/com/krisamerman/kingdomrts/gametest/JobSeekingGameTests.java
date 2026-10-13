package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.current;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.lumberjackHut;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.Workplace;
import com.krisamerman.kingdomrts.settlement.WorkplaceOps;
import com.krisamerman.kingdomrts.unit.Occupation;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Job seeking: unemployed settlers take openings at huts that hire automatically, nearest first, by
// themselves (checking every Unit.JOB_SEEK_INTERVAL_TICKS). Manual huts, soldiers and units from
// outside the settlement are left alone. Each test founds a settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class JobSeekingGameTests {
    // Long enough for every unit to have had its check at least once.
    private static final int SEEK_WAIT = Unit.JOB_SEEK_INTERVAL_TICKS + 20;
    private static final BlockPos HUT = new BlockPos(4, FLOOR_Y + 1, 4);
    private static final BlockPos OTHER_HUT = new BlockPos(14, FLOOR_Y + 1, 14);

    @GameTest(template = TEMPLATE, batch = "job_seeking_fill", timeoutTicks = SEEK_WAIT + 40)
    public static void unemployedSettlersFillAnOpening(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        Unit settler = settler(helper, settlement, blue, new BlockPos(12, FLOOR_Y + 1, 4));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(Occupation.LUMBERJACK.equals(settler.getOccupationId()), "Waiting for it to find the job"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.absolutePos(HUT).equals(settler.getWorkplace()), "Expected it to work at the hut");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "job_seeking_manual", timeoutTicks = SEEK_WAIT + 40)
    public static void manualHutsAreLeftAlone(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        Workplace hut = current(helper, settlement).workplaceAt(helper.absolutePos(HUT));
        WorkplaceOps.setAutoHire(helper.getLevel(), hut, false);
        Unit settler = settler(helper, settlement, blue, new BlockPos(12, FLOOR_Y + 1, 4));
        helper.startSequence()
                .thenIdle(SEEK_WAIT)
                .thenExecute(() -> {
                    helper.assertTrue(settler.getOccupationId() == null, "A hut with automatic hiring off shouldn't take settlers");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "job_seeking_nearest", timeoutTicks = SEEK_WAIT + 40)
    public static void theNearestHutWins(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        lumberjackHut(helper, OTHER_HUT);
        Unit settler = settler(helper, settlement, blue, OTHER_HUT.west(2));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(settler.getWorkplace() != null, "Waiting for it to find a job"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.absolutePos(OTHER_HUT).equals(settler.getWorkplace()), "Expected the nearer hut");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "job_seeking_others", timeoutTicks = SEEK_WAIT + 40)
    public static void soldiersAndOutsidersDontSeekWork(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        Unit soldier = unit(helper, HUT.east(2), blue, false);
        SettlementOps.setUnitSettlement(helper.getLevel(), soldier, settlement);
        Unit outsider = unit(helper, HUT.east(3), blue, null, false); // belongs to no settlement
        helper.startSequence()
                .thenIdle(SEEK_WAIT)
                .thenExecute(() -> {
                    helper.assertTrue(current(helper, settlement).workplaceAt(helper.absolutePos(HUT)).workers().isEmpty(),
                            "Neither the soldier nor the outsider should have taken the job");
                    helper.assertTrue(outsider.getOccupationId() == null, "The outsider should still have no job");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "job_seeking_fired", timeoutTicks = SEEK_WAIT + 40)
    public static void aWorkerFiredFromThePanelIsntReHired(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        Unit worker = settler(helper, settlement, blue, HUT.east(2));
        WorkplaceOps.hire(helper.getLevel(), worker, helper.absolutePos(HUT));
        // What the panel's Fire button does: switch to manual hiring, then fire.
        Workplace hut = current(helper, settlement).workplaceAt(helper.absolutePos(HUT));
        WorkplaceOps.setAutoHire(helper.getLevel(), hut, false);
        WorkplaceOps.fire(helper.getLevel(), hut, worker.getUUID());
        helper.startSequence()
                .thenIdle(SEEK_WAIT)
                .thenExecute(() -> {
                    helper.assertTrue(worker.getOccupationId() == null, "A worker fired by hand shouldn't walk straight back into the job");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    private static Unit settler(GameTestHelper helper, Settlement settlement, Faction faction, BlockPos pos) {
        Unit unit = unit(helper, pos, faction, null, false);
        SettlementOps.setUnitSettlement(helper.getLevel(), unit, settlement);
        return unit;
    }
}
