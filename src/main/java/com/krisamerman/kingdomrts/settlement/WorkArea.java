package com.krisamerman.kingdomrts.settlement;

import net.minecraft.core.BlockPos;

// A workplace's work area: the blocks within a radius of the workplace, measured across the ground
// (height doesn't count), and only inside its settlement's claim (the caller checks the claim).
// Pure, so it can be unit tested.
public final class WorkArea {
    public static final int MIN_RADIUS = 4;
    // How much the hut panel's -/+ buttons change the radius.
    public static final int RADIUS_STEP = 4;

    private WorkArea() {
    }

    // Whether column (x, z) is within radius of center.
    public static boolean contains(BlockPos center, int radius, int x, int z) {
        long dx = x - center.getX();
        long dz = z - center.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    // Whether the chunk whose corner columns are (minX, minZ)-(maxX, maxZ) could have any column
    // within radius of center (a cheap box test, to skip chunks entirely).
    public static boolean touchesChunk(BlockPos center, int radius, int minX, int minZ, int maxX, int maxZ) {
        long nearestX = Math.max(minX, Math.min(center.getX(), maxX));
        long nearestZ = Math.max(minZ, Math.min(center.getZ(), maxZ));
        return contains(center, radius, (int) nearestX, (int) nearestZ);
    }

    public static int clampRadius(int radius, int max) {
        return Math.max(MIN_RADIUS, Math.min(radius, max));
    }
}
