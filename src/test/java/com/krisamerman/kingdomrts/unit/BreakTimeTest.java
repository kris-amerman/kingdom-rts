package com.krisamerman.kingdomrts.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// Break times against numbers a player would see (oak logs have hardness 2; a bare hand has speed 1,
// a wooden axe 2, an iron axe 6). The GameTest breakTimeMatchesAPlayer checks against vanilla itself.
class BreakTimeTest {
    private static final float OAK_HARDNESS = 2.0f;

    @Test
    void oakByHandTakesThreeSeconds() {
        assertEquals(60, BreakTime.ticks(OAK_HARDNESS, 1.0f, true, 0, 1));
    }

    @Test
    void betterAxesAreFaster() {
        assertEquals(30, BreakTime.ticks(OAK_HARDNESS, 2.0f, true, 0, 1));
        assertEquals(10, BreakTime.ticks(OAK_HARDNESS, 6.0f, true, 0, 1));
    }

    @Test
    void theWrongToolForABlockThatNeedsOneIsMuchSlower() {
        // Stone (hardness 1.5) by hand: it needs a pickaxe.
        assertEquals(150, BreakTime.ticks(1.5f, 1.0f, false, 0, 1));
    }

    @Test
    void efficiencyOnlyHelpsRealTools() {
        assertEquals(60, BreakTime.ticks(OAK_HARDNESS, 1.0f, true, 10, 1), "Efficiency doesn't apply to a bare hand");
        // Iron axe with Efficiency I (+2): speed 8 -> 2 / 8 * 30 = 7.5 -> 8 ticks.
        assertEquals(8, BreakTime.ticks(OAK_HARDNESS, 6.0f, true, 2, 1));
    }

    @Test
    void breakSpeedMultiplies() {
        assertEquals(30, BreakTime.ticks(OAK_HARDNESS, 1.0f, true, 0, 2));
    }

    @Test
    void instantAndUnbreakableBlocks() {
        assertEquals(1, BreakTime.ticks(0, 1.0f, true, 0, 1));
        assertEquals(BreakTime.NEVER, BreakTime.ticks(-1, 1.0f, true, 0, 1));
        assertTrue(BreakTime.ticks(OAK_HARDNESS, 0, true, 0, 1) == BreakTime.NEVER);
    }
}
