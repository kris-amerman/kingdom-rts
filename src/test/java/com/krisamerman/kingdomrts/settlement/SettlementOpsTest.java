package com.krisamerman.kingdomrts.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

class SettlementOpsTest {
    private static final int SIDE = 2 * SettlementOps.STARTING_CLAIM_RADIUS + 1;

    @Test
    void startingClaimIsSquareAroundHallChunk() {
        Set<ChunkPos> claim = SettlementOps.startingClaim(new BlockPos(8, 64, 8)); // chunk 0, 0
        assertEquals(SIDE * SIDE, claim.size());
        for (int dx = -SettlementOps.STARTING_CLAIM_RADIUS; dx <= SettlementOps.STARTING_CLAIM_RADIUS; dx++) {
            for (int dz = -SettlementOps.STARTING_CLAIM_RADIUS; dz <= SettlementOps.STARTING_CLAIM_RADIUS; dz++) {
                assertTrue(claim.contains(new ChunkPos(dx, dz)));
            }
        }
    }

    @Test
    void negativeCoordinatesUseTheRightChunk() {
        // Block -1 is in chunk -1, not chunk 0.
        Set<ChunkPos> claim = SettlementOps.startingClaim(new BlockPos(-1, 64, -1));
        assertTrue(claim.contains(new ChunkPos(-1, -1)));
        assertTrue(claim.contains(new ChunkPos(-1 - SettlementOps.STARTING_CLAIM_RADIUS, -1 - SettlementOps.STARTING_CLAIM_RADIUS)));
        assertEquals(SIDE * SIDE, claim.size());
    }

    @Test
    void chunkEdgesDoNotShiftTheClaim() {
        // Both ends of chunk 1 (blocks 16 and 31) give the same claim.
        assertEquals(SettlementOps.startingClaim(new BlockPos(16, 64, 16)), SettlementOps.startingClaim(new BlockPos(31, 64, 31)));
    }
}
