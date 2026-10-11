package com.krisamerman.kingdomrts.settlement;

import static com.krisamerman.kingdomrts.settlement.TownHallBlockEntity.CONTROL_CHANGE_PER_CHECK;
import static com.krisamerman.kingdomrts.settlement.TownHallBlockEntity.MAX_CONTROL;
import static com.krisamerman.kingdomrts.settlement.TownHallBlockEntity.MIN_CONTROL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.krisamerman.kingdomrts.settlement.CaptureRules.Outcome;
import com.krisamerman.kingdomrts.settlement.CaptureRules.Trend;

class CaptureRulesTest {
    private static final UUID RED = UUID.randomUUID();
    private static final UUID GREEN = UUID.randomUUID();

    @Test
    void strongerAttackerDrains() {
        Outcome outcome = CaptureRules.evaluate(MAX_CONTROL, 1, Map.of(RED, 2));
        assertEquals(MAX_CONTROL - CONTROL_CHANGE_PER_CHECK, outcome.newControl());
        assertEquals(Trend.DRAINING, outcome.trend());
        assertNull(outcome.capturedBy());
    }

    @Test
    void defenderMajorityRegenerates() {
        Outcome outcome = CaptureRules.evaluate(50, 3, Map.of(RED, 2));
        assertEquals(50 + CONTROL_CHANGE_PER_CHECK, outcome.newControl());
        assertEquals(Trend.RISING, outcome.trend());
    }

    @Test
    void noAttackersRegenerates() {
        assertEquals(50 + CONTROL_CHANGE_PER_CHECK, CaptureRules.evaluate(50, 0, Map.of()).newControl());
    }

    @Test
    void regenerationStopsAtMax() {
        assertEquals(MAX_CONTROL, CaptureRules.evaluate(MAX_CONTROL - 1, 0, Map.of()).newControl());
        assertEquals(MAX_CONTROL, CaptureRules.evaluate(MAX_CONTROL, 0, Map.of()).newControl());
    }

    @Test
    void equalPresenceHolds() {
        Outcome outcome = CaptureRules.evaluate(50, 2, Map.of(RED, 2));
        assertEquals(50, outcome.newControl());
        assertEquals(Trend.HOLDING, outcome.trend());
    }

    @Test
    void hostileFactionsAreNotAddedTogether() {
        // 2 + 2 hostile units outnumber 3 defenders in total, but neither faction does on its own.
        Outcome outcome = CaptureRules.evaluate(50, 3, Map.of(RED, 2, GREEN, 2));
        assertEquals(Trend.RISING, outcome.trend());
        assertEquals(2, outcome.strongestAttackers());
    }

    @Test
    void drainBelowMinFlipsToStrongestAttacker() {
        // Flips as soon as a drain would go below 1, without stopping at 1 first.
        Outcome outcome = CaptureRules.evaluate(MIN_CONTROL + CONTROL_CHANGE_PER_CHECK - 1, 0, Map.of(RED, 3, GREEN, 1));
        assertEquals(RED, outcome.capturedBy());
        assertEquals(MIN_CONTROL, outcome.newControl());
        assertFalse(outcome.tiedForCapture());
    }

    @Test
    void drainThatStaysAboveMinDoesNotFlip() {
        Outcome outcome = CaptureRules.evaluate(MIN_CONTROL + CONTROL_CHANGE_PER_CHECK, 0, Map.of(RED, 3));
        assertNull(outcome.capturedBy());
        assertEquals(MIN_CONTROL, outcome.newControl());
    }

    @Test
    void tieForStrongestHoldsAtMin() {
        Outcome outcome = CaptureRules.evaluate(MIN_CONTROL, 0, Map.of(RED, 2, GREEN, 2));
        assertNull(outcome.capturedBy());
        assertTrue(outcome.tiedForCapture());
        assertEquals(MIN_CONTROL, outcome.newControl());
        assertTrue(outcome.draining());
    }
}
