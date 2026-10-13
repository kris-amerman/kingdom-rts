package com.krisamerman.kingdomrts.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

class WorkAreaTest {
    private static final BlockPos HUT = new BlockPos(100, 64, -40);

    @Test
    void aCircleAroundTheHutIgnoringHeight() {
        assertTrue(WorkArea.contains(HUT, 10, 110, -40));
        assertTrue(WorkArea.contains(HUT, 10, 106, -32)); // 6, 8: exactly 10 away
        assertFalse(WorkArea.contains(HUT, 10, 108, -32)); // 8, 8: about 11.3 away
    }

    @Test
    void chunksOutOfReachAreSkipped() {
        // The chunk to the east, starting 12 blocks away.
        assertTrue(WorkArea.touchesChunk(HUT, 16, 112, -48, 127, -33));
        assertFalse(WorkArea.touchesChunk(HUT, 10, 112, -48, 127, -33));
        // The hut's own chunk always counts.
        assertTrue(WorkArea.touchesChunk(HUT, WorkArea.MIN_RADIUS, 96, -48, 111, -33));
    }

    @Test
    void theRadiusStaysWithinItsLimits() {
        assertEquals(WorkArea.MIN_RADIUS, WorkArea.clampRadius(0, 48));
        assertEquals(48, WorkArea.clampRadius(60, 48));
        assertEquals(20, WorkArea.clampRadius(20, 48));
    }
}
