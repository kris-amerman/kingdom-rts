package com.krisamerman.kingdomrts.unit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

// What counts as a tree a lumberjack may fell: logs connected to each other (diagonals included, so
// branches count), with natural leaves touching them. Leaves a player places are marked persistent
// and never count, and neither does a log with no leaves at all, so player-built log walls, houses
// and pillars are left alone.
//
// Pure: it only sees blocks through kindAt, so it can be tested without a world.
public final class TreeRules {
    // More logs than this connected together is a structure, not a tree.
    public static final int MAX_TREE_LOGS = 256;
    // Natural leaf blocks that must touch the logs for them to count as a tree.
    public static final int MIN_NATURAL_LEAVES = 3;

    public enum Kind { LOG, NATURAL_LEAVES, OTHER }

    // A tree: its lowest log (where a lumberjack stands to fell it) and all its logs, highest first
    // (the order they're chopped, so the trunk goes last).
    public record Tree(BlockPos base, List<BlockPos> logs) {
    }

    private TreeRules() {
    }

    // The tree that the log at start belongs to, if it's a tree.
    public static Optional<Tree> find(BlockPos start, Function<BlockPos, Kind> kindAt) {
        if (kindAt.apply(start) != Kind.LOG) {
            return Optional.empty();
        }
        Set<BlockPos> logs = new HashSet<>();
        Deque<BlockPos> toVisit = new ArrayDeque<>();
        logs.add(start.immutable());
        toVisit.add(start.immutable());
        while (!toVisit.isEmpty()) {
            BlockPos log = toVisit.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = log.offset(dx, dy, dz);
                        if (!logs.contains(next) && kindAt.apply(next) == Kind.LOG) {
                            if (logs.size() >= MAX_TREE_LOGS) {
                                return Optional.empty(); // too big to be a tree
                            }
                            logs.add(next);
                            toVisit.add(next);
                        }
                    }
                }
            }
        }
        if (naturalLeavesTouching(logs, kindAt) < MIN_NATURAL_LEAVES) {
            return Optional.empty();
        }
        List<BlockPos> ordered = new ArrayList<>(logs);
        // Highest first; ties broken by position so the order is always the same.
        ordered.sort(Comparator.<BlockPos>comparingInt(pos -> -pos.getY())
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return Optional.of(new Tree(ordered.get(ordered.size() - 1), List.copyOf(ordered)));
    }

    private static int naturalLeavesTouching(Set<BlockPos> logs, Function<BlockPos, Kind> kindAt) {
        Set<BlockPos> leaves = new HashSet<>();
        for (BlockPos log : logs) {
            for (Direction direction : Direction.values()) {
                BlockPos next = log.relative(direction);
                if (kindAt.apply(next) == Kind.NATURAL_LEAVES) {
                    leaves.add(next);
                }
            }
        }
        return leaves.size();
    }

    // The box around a tree's logs, grown by margin on every side: where its leaves can be (vanilla
    // leaves reach at most 6 blocks from a log).
    public static BoundingBox canopyBox(List<BlockPos> logs, int margin) {
        BoundingBox box = BoundingBox.encapsulatingPositions(logs).orElseThrow();
        return box.inflatedBy(margin);
    }
}
