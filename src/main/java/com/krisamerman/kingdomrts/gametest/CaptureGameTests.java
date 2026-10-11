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

import java.util.concurrent.atomic.AtomicInteger;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;
import com.krisamerman.kingdomrts.settlement.TownHallBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Capture scenarios in a real world: a settlement, hostile units at its town hall, real ticks.
// Units have AI off so they count for capture without wandering or fighting. Each test has its own
// batch, so tests run one at a time and their 3x3 chunk claims never overlap.
//
// Run all GameTests headless with ./gradlew runGameTestServer, or watch them in-game with /test runall.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class CaptureGameTests {
    // A full drain takes about 20 checks of 20 ticks; leave room for setup.
    private static final int CAPTURE_TIMEOUT = 600;
    private static final int CHECK = TownHallBlockEntity.CHECK_INTERVAL_TICKS;

    @GameTest(template = TEMPLATE, batch = "capture_flip", timeoutTicks = CAPTURE_TIMEOUT)
    public static void captureFlipsToStrongestAttacker(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Faction green = faction(helper, "Green");
        setWar(helper, blue, red, true);
        setWar(helper, blue, green, true);
        Settlement settlement = found(helper, HALL, blue);
        for (int i = 0; i < 3; i++) {
            unit(helper, new BlockPos(4 + i, FLOOR_Y + 1, 4), red, false);
        }
        unit(helper, new BlockPos(12, FLOOR_Y + 1, 12), green, false);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).owner().equals(red.id()),
                        "Expected Red to capture; control is " + control(helper)))
                .thenExecute(() -> {
                    helper.assertTrue(control(helper) == TownHallBlockEntity.MIN_CONTROL,
                            "Expected control " + TownHallBlockEntity.MIN_CONTROL + " after capture, got " + control(helper));
                    // The claim passes with the settlement.
                    Settlement claimant = SettlementData.get(helper.getLevel())
                            .claimAt(helper.getLevel().dimension(), new ChunkPos(helper.absolutePos(HALL)));
                    helper.assertTrue(claimant != null && claimant.owner().equals(red.id()), "Expected the claim to belong to Red");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "capture_hold", timeoutTicks = CAPTURE_TIMEOUT)
    public static void equalPresenceHolds(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        setWar(helper, blue, red, true);
        found(helper, HALL, blue);
        unit(helper, new BlockPos(4, FLOOR_Y + 1, 4), red, false);
        AtomicInteger heldAt = new AtomicInteger();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(control(helper) <= TownHallBlockEntity.MAX_CONTROL - 2 * TownHallBlockEntity.CONTROL_CHANGE_PER_CHECK,
                        "Waiting for the meter to drain"))
                .thenExecute(() -> {
                    unit(helper, new BlockPos(12, FLOOR_Y + 1, 12), blue, false); // now 1 vs 1
                    heldAt.set(control(helper));
                })
                .thenIdle(3 * CHECK + 1)
                .thenExecute(() -> {
                    helper.assertTrue(control(helper) == heldAt.get(),
                            "Expected control to hold at " + heldAt.get() + ", got " + control(helper));
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "capture_regen", timeoutTicks = CAPTURE_TIMEOUT)
    public static void defenderMajorityRegenerates(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        setWar(helper, blue, red, true);
        found(helper, HALL, blue);
        unit(helper, new BlockPos(4, FLOOR_Y + 1, 4), red, false);
        AtomicInteger lowest = new AtomicInteger();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(control(helper) <= TownHallBlockEntity.MAX_CONTROL - 2 * TownHallBlockEntity.CONTROL_CHANGE_PER_CHECK,
                        "Waiting for the meter to drain"))
                .thenExecute(() -> {
                    unit(helper, new BlockPos(12, FLOOR_Y + 1, 12), blue, false); // 2 defenders vs 1 attacker
                    unit(helper, new BlockPos(12, FLOOR_Y + 1, 4), blue, false);
                    lowest.set(control(helper));
                })
                .thenWaitUntil(() -> helper.assertTrue(control(helper) > lowest.get(),
                        "Expected control to rise above " + lowest.get() + ", got " + control(helper)))
                .thenExecute(() -> removeHall(helper, HALL))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "capture_tie", timeoutTicks = CAPTURE_TIMEOUT + 100)
    public static void tieForStrongestHoldsAtOne(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Faction green = faction(helper, "Green");
        setWar(helper, blue, red, true);
        setWar(helper, blue, green, true);
        Settlement settlement = found(helper, HALL, blue);
        unit(helper, new BlockPos(4, FLOOR_Y + 1, 4), red, false);
        unit(helper, new BlockPos(5, FLOOR_Y + 1, 4), red, false);
        unit(helper, new BlockPos(12, FLOOR_Y + 1, 12), green, false);
        unit(helper, new BlockPos(11, FLOOR_Y + 1, 12), green, false);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(control(helper) == TownHallBlockEntity.MIN_CONTROL,
                        "Waiting for control to reach " + TownHallBlockEntity.MIN_CONTROL + "; it's " + control(helper)))
                .thenIdle(3 * CHECK + 1)
                .thenExecute(() -> {
                    helper.assertTrue(current(helper, settlement).owner().equals(blue.id()), "Expected Blue to keep the settlement on a tie");
                    helper.assertTrue(control(helper) == TownHallBlockEntity.MIN_CONTROL, "Expected control to hold at the minimum");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }
}
