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
    // least 2 blocks from where it is now, and not closer to its target than it already was (so it
    // can't hop over a wall to reach a target it couldn't walk to). Spots near the target's height
    // first (out of a pit, down from a tree), then nearest to the unit.
    @Nullable
    public static BlockPos escapeSpot(Unit unit, BlockPos target) {
        Level level = unit.level();
        BlockPos from = unit.blockPosition();
        double currentDistance = from.distSqr(target);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos spot : BlockPos.betweenClosed(from.offset(-8, -4, -8), from.offset(8, 4, 8))) {
            int sideways = Math.max(Math.abs(spot.getX() - from.getX()), Math.abs(spot.getZ() - from.getZ()));
            if (sideways < 2 || spot.distSqr(target) < currentDistance - 1 || !isStandable(level, spot)) {
                continue;
            }
            double score = Math.abs(spot.getY() - target.getY()) * 100 + spot.distSqr(from);
            if (score < bestScore) {
                bestScore = score;
                best = spot.immutable();
            }
        }
        return best;
    }
}
