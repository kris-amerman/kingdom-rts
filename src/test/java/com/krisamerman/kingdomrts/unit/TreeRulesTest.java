package com.krisamerman.kingdomrts.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.krisamerman.kingdomrts.unit.TreeRules.Kind;
import com.krisamerman.kingdomrts.unit.TreeRules.Tree;

import net.minecraft.core.BlockPos;

class TreeRulesTest {
    private final Map<BlockPos, Kind> world = new HashMap<>();

    private Optional<Tree> find(BlockPos start) {
        return TreeRules.find(start, pos -> world.getOrDefault(pos, Kind.OTHER));
    }

    // A small oak: a 4-log trunk with leaves around its top two logs.
    private void oak(BlockPos base) {
        for (int y = 0; y < 4; y++) {
            world.put(base.above(y), Kind.LOG);
        }
        for (int y = 2; y < 4; y++) {
            for (BlockPos side : new BlockPos[] {base.above(y).north(), base.above(y).south(), base.above(y).east(), base.above(y).west()}) {
                world.put(side, Kind.NATURAL_LEAVES);
            }
        }
        world.put(base.above(4), Kind.NATURAL_LEAVES);
    }

    @Test
    void findsATreeFromAnyOfItsLogs() {
        oak(BlockPos.ZERO);
        Tree tree = find(new BlockPos(0, 3, 0)).orElseThrow();
        assertEquals(BlockPos.ZERO, tree.base());
        assertEquals(4, tree.logs().size());
        assertEquals(tree, find(new BlockPos(0, 1, 0)).orElseThrow());
    }

    @Test
    void logsAreChoppedHighestFirst() {
        oak(BlockPos.ZERO);
        Tree tree = find(BlockPos.ZERO).orElseThrow();
        assertEquals(new BlockPos(0, 3, 0), tree.logs().get(0));
        assertEquals(BlockPos.ZERO, tree.logs().get(3));
    }

    @Test
    void diagonalBranchesBelongToTheTree() {
        oak(BlockPos.ZERO);
        world.put(new BlockPos(1, 4, 1), Kind.LOG); // a branch touching the trunk only at a corner
        Tree tree = find(BlockPos.ZERO).orElseThrow();
        assertTrue(tree.logs().contains(new BlockPos(1, 4, 1)));
    }

    @Test
    void logsWithoutNaturalLeavesAreNotATree() {
        for (int y = 0; y < 4; y++) {
            world.put(new BlockPos(0, y, 0), Kind.LOG); // a pillar; placed leaves would count as OTHER too
        }
        assertTrue(find(BlockPos.ZERO).isEmpty());
        world.put(new BlockPos(1, 3, 0), Kind.NATURAL_LEAVES);
        world.put(new BlockPos(-1, 3, 0), Kind.NATURAL_LEAVES);
        assertTrue(find(BlockPos.ZERO).isEmpty(), "Fewer than " + TreeRules.MIN_NATURAL_LEAVES + " natural leaves isn't enough");
    }

    @Test
    void tooManyConnectedLogsIsAStructure() {
        oak(BlockPos.ZERO);
        for (int x = 1; x <= TreeRules.MAX_TREE_LOGS; x++) {
            world.put(new BlockPos(x, 0, 0), Kind.LOG); // a long log wall joined to the tree
        }
        assertTrue(find(BlockPos.ZERO).isEmpty());
    }

    @Test
    void startMustBeALog() {
        oak(BlockPos.ZERO);
        assertTrue(find(new BlockPos(0, 4, 0)).isEmpty());
    }
}
