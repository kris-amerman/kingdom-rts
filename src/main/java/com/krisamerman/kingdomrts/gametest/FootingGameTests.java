package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.unit.Unit;
import com.krisamerman.kingdomrts.unit.job.Footing;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Where a stuck unit hops to (Footing#escapeSpot, the last resort of a stuck walk): out of an
// enclosure, yes, but never across a gap to somewhere it couldn't walk back from. A builder once
// hopped off the test platform onto the next test's, and held its load forever (S-047).
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class FootingGameTests {
    // The unit's spot and its target, to the north.
    private static final BlockPos UNIT = new BlockPos(4, FLOOR_Y + 1, 4);
    private static final BlockPos TARGET = new BlockPos(4, FLOOR_Y + 1, 0);

    @GameTest(template = TEMPLATE)
    public static void aStuckUnitNeverHopsAcrossAGap(GameTestHelper helper) {
        // The unit stands on a 3x3 island; the floor is gone from there to row 8, and starts again at
        // row 9: standable ground across a gap, and the only spots further from the target.
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 8; z++) {
                boolean island = Math.abs(x - UNIT.getX()) <= 1 && Math.abs(z - UNIT.getZ()) <= 1;
                if (!island) {
                    helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.AIR);
                    helper.setBlock(new BlockPos(x, FLOOR_Y - 1, z), Blocks.AIR);
                }
            }
        }
        Unit unit = unit(helper, UNIT, null, false);
        BlockPos spot = Footing.escapeSpot(unit, helper.absolutePos(TARGET));
        helper.assertTrue(spot == null, "Expected no hop across the gap, got " + (spot != null ? spot.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() : null));
        unit.discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aUnitBoxedInStillHopsOut(GameTestHelper helper) {
        // Walls three high all round the unit's spot: no way to walk out, ground all the way.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    for (int dy = 0; dy < 3; dy++) {
                        helper.setBlock(UNIT.offset(dx, dy, dz), Blocks.STONE);
                    }
                }
            }
        }
        Unit unit = unit(helper, UNIT, null, false);
        BlockPos spot = Footing.escapeSpot(unit, helper.absolutePos(TARGET));
        helper.assertTrue(spot != null && Footing.isStandable(helper.getLevel(), spot), "Expected a hop out of the box, got " + spot);
        unit.discard();
        helper.succeed();
    }
}
