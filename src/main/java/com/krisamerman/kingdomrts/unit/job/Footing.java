package com.krisamerman.kingdomrts.unit.job;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

// Where a unit can stand: helpers for picking walk targets and getting unstuck. All checks look at a
// handful of blocks, never a big area.
public final class Footing {
    private Footing() {
    }

    // A spot a unit can stand in: solid ground below that isn't part of a tree, and room for its
    // body (two blocks with nothing to bump into, and no water or lava).
    public static boolean isStandable(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState floor = level.getBlockState(below);
        if (isTreePart(floor) || !floor.isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        return isClear(level, pos) && isClear(level, pos.above());
    }

    private static boolean isClear(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    public static boolean isTreePart(BlockState state) {
        return state.is(BlockTags.LOGS) || state.getBlock() instanceof LeavesBlock;
    }

    // Whether the unit is standing on a tree (leaves or logs), e.g. after jumping onto one: it should
    // get down before carrying on.
    public static boolean isOnTree(Unit unit) {
        return unit.onGround() && isTreePart(unit.level().getBlockState(unit.getBlockPosBelowThatAffectsMyMovement()));
    }

    // Standable spots next to a block (within radius blocks sideways, and one block up or down),
    // nearest to the block first.
    public static List<BlockPos> standSpotsNear(Level level, BlockPos block, int radius) {
        List<BlockPos> spots = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos spot = block.offset(dx, dy, dz);
                    if ((dx != 0 || dz != 0) && isStandable(level, spot)) {
                        spots.add(spot);
                    }
                }
            }
        }
        spots.sort(Comparator.comparingDouble(spot -> spot.distSqr(block)));
        return spots;
    }

    // Where to move a stuck unit to: a standable spot within 8 blocks sideways (4 up or down), at
    // least 2 blocks from where it is now, not closer to its target than it already was (so it can't
    // hop over a wall to reach a target it couldn't walk to), and never somewhere it couldn't walk
    // back from: not across a gap (the far side of a ravine, a neighbouring roof), and not more than
    // a block below both where it is and its target (off a ledge it couldn't climb back up; down from
    // a tree to a target on the ground is fine). Spots near the target's height first (out of a pit,
    // down from a tree), then nearest.
    @Nullable
    public static BlockPos escapeSpot(Unit unit, BlockPos target) {
        Level level = unit.level();
        BlockPos from = unit.blockPosition();
        double currentDistance = from.distSqr(target);
        int lowestAllowed = Math.min(from.getY(), target.getY()) - 1;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos spot : BlockPos.betweenClosed(from.offset(-8, -4, -8), from.offset(8, 4, 8))) {
            int sideways = Math.max(Math.abs(spot.getX() - from.getX()), Math.abs(spot.getZ() - from.getZ()));
            if (sideways < 2 || spot.getY() < lowestAllowed || spot.distSqr(target) < currentDistance - 1 || !isStandable(level, spot)) {
                continue;
            }
            double score = Math.abs(spot.getY() - target.getY()) * 100 + spot.distSqr(from);
            if (score < bestScore && !crossesGap(level, from, spot)) {
                bestScore = score;
                best = spot.immutable();
            }
        }
        return best;
    }

    // How far below the lower end of a hop the ground may be, under every column it passes over: a
    // unit can climb back up one block, so a deeper drop is a gap it couldn't walk back over.
    private static final int MAX_DROP_UNDER_HOP = 1;

    // Whether a hop in a straight line from one spot to another passes over a gap: a column with no
    // solid ground within MAX_DROP_UNDER_HOP blocks below the lower of the two spots. Looks at one
    // column per block of the hop (at most a few dozen blocks in all).
    static boolean crossesGap(Level level, BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        int lowest = Math.min(from.getY(), to.getY());
        for (int i = 1; i < steps; i++) {
            int x = from.getX() + Math.round((float) dx * i / steps);
            int z = from.getZ() + Math.round((float) dz * i / steps);
            if (!hasGroundBelow(level, x, z, lowest)) {
                return true;
            }
        }
        return false;
    }

    // Whether there's a solid top within MAX_DROP_UNDER_HOP blocks below y (or at its level, for a
    // hop that steps up), in this column.
    private static boolean hasGroundBelow(Level level, int x, int z, int y) {
        for (int below = 0; below <= MAX_DROP_UNDER_HOP; below++) {
            BlockPos pos = new BlockPos(x, y - 1 - below, z);
            if (level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) {
                return true;
            }
        }
        return false;
    }
}
