package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;

import java.util.ArrayList;
import java.util.List;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.WorkplaceOps;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// A lumberjack reaching trees across its work area, on open ground (S-029: lumberjacks idled by
// their hut for minutes while a tree stood ~18 blocks away). Each test lays a strip of grass across
// the settlement's claim, puts the hut near one end with the largest work area, plants one real
// vanilla tree (grown by the game's own tree generator) at a set distance, and expects felling to
// start within 30 seconds. Each test founds a settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class LumberjackReachGameTests {
    // Felling must start within this (plus a little setup).
    private static final int START_WITHIN_TICKS = 20 * 30;
    private static final int TIMEOUT_TICKS = START_WITHIN_TICKS + 40;
    // The strip of open ground: how far it reaches either side of its row, and how high it's cleared.
    private static final int STRIP_HALF_WIDTH = 5;
    private static final int CLEAR_HEIGHT = 14;

    @GameTest(template = TEMPLATE, batch = "reach_10", timeoutTicks = TIMEOUT_TICKS)
    public static void reachesATree10BlocksAway(GameTestHelper helper) {
        run(helper, 10, TreeFeatures.OAK, false);
    }

    @GameTest(template = TEMPLATE, batch = "reach_18", timeoutTicks = TIMEOUT_TICKS)
    public static void reachesATree18BlocksAway(GameTestHelper helper) {
        run(helper, 18, TreeFeatures.OAK, false);
    }

    @GameTest(template = TEMPLATE, batch = "reach_26", timeoutTicks = TIMEOUT_TICKS)
    public static void reachesATree26BlocksAway(GameTestHelper helper) {
        run(helper, 26, TreeFeatures.OAK, false);
    }

    @GameTest(template = TEMPLATE, batch = "reach_34", timeoutTicks = TIMEOUT_TICKS)
    public static void reachesATree34BlocksAway(GameTestHelper helper) {
        run(helper, 34, TreeFeatures.OAK, false);
    }

    @GameTest(template = TEMPLATE, batch = "reach_fancy", timeoutTicks = TIMEOUT_TICKS)
    public static void reachesAFancyOak26BlocksAway(GameTestHelper helper) {
        run(helper, 26, TreeFeatures.FANCY_OAK, false);
    }

    // As in the owner's world: the town hall stands on the straight line from the hut to the tree.
    @GameTest(template = TEMPLATE, batch = "reach_hall_between", timeoutTicks = TIMEOUT_TICKS)
    public static void reachesATreeWithTheHallInTheWay(GameTestHelper helper) {
        run(helper, 20, TreeFeatures.OAK, true);
    }

    private static void run(GameTestHelper helper, int distance, ResourceKey<ConfiguredFeature<?, ?>> tree, boolean hallInTheWay) {
        ServerLevel level = helper.getLevel();
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        BlockPos hall = helper.absolutePos(HALL);
        int minX = settlement.chunks().stream().mapToInt(ChunkPos::getMinBlockX).min().orElseThrow();
        int maxX = settlement.chunks().stream().mapToInt(ChunkPos::getMaxBlockX).max().orElseThrow();
        int floorY = hall.getY() - 1;
        List<ChunkPos> forced = new ArrayList<>(settlement.chunks());
        forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, true));

        // The row the hut and tree stand on: the hall's own row, or a few blocks to the side.
        int row = hallInTheWay ? hall.getZ() : hall.getZ() + 4;
        BlockPos hut;
        BlockPos base;
        if (hallInTheWay) {
            hut = new BlockPos(hall.getX() + 6, hall.getY(), row);
            base = new BlockPos(hut.getX() - distance, hall.getY(), row);
        } else {
            hut = new BlockPos(minX + 3, hall.getY(), row);
            base = new BlockPos(hut.getX() + distance, hall.getY(), row);
        }
        if (base.getX() < minX + 3 || base.getX() > maxX - 3) {
            throw new GameTestAssertException("The tree at " + base.toShortString() + " wouldn't be inside the claim");
        }
        lay(level, minX, maxX, row, floorY, hall);

        level.setBlockAndUpdate(hut, KingdomRts.LUMBERJACK_HUT.get().defaultBlockState());
        var workplace = WorkplaceOps.register(level, hut);
        if (workplace == null) {
            throw new GameTestAssertException("Couldn't register the hut");
        }
        WorkplaceOps.setWorkRadius(level, workplace, 48);
        Unit lumberjack = KingdomRts.UNIT.get().create(level);
        BlockPos start = hut.south();
        lumberjack.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0f, 0.0f);
        lumberjack.setup(blue.id(), start, null);
        level.addFreshEntity(lumberjack);
        SettlementOps.setUnitSettlement(level, lumberjack, settlement);

        boolean grown = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(tree).value()
                .place(level, level.getChunkSource().getGenerator(), RandomSource.create(42), base);
        int logs = logsAround(level, base);
        if (!grown || logs == 0) {
            throw new GameTestAssertException("The tree didn't grow at " + base.toShortString());
        }
        String problem = WorkplaceOps.hire(level, lumberjack, hut);
        if (problem != null) {
            throw new GameTestAssertException("Couldn't hire the lumberjack: " + problem);
        }
        long started = level.getGameTime();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(logsAround(level, base) < logs,
                        "Waiting for the lumberjack to start felling the tree " + distance + " blocks from its hut"))
                .thenExecute(() -> {
                    long took = level.getGameTime() - started;
                    KingdomRts.LOGGER.info("Reach test: tree {} blocks away, felling started after {} ticks", distance, took);
                    helper.assertTrue(took <= START_WITHIN_TICKS, "Felling only started after " + took + " ticks");
                    lumberjack.discard();
                    lay(level, minX, maxX, row, floorY, hall); // clear the tree away again
                    removeHall(helper, HALL);
                    forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
                })
                .thenSucceed();
    }

    // A strip of grass across the claim, with nothing above it (except the town hall).
    private static void lay(ServerLevel level, int minX, int maxX, int row, int floorY, BlockPos hall) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = row - STRIP_HALF_WIDTH; z <= row + STRIP_HALF_WIDTH; z++) {
                level.setBlock(pos.set(x, floorY, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 1; y <= CLEAR_HEIGHT; y++) {
                    pos.set(x, floorY + y, z);
                    if (!pos.equals(hall)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static int logsAround(ServerLevel level, BlockPos base) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-4, 0, -4), base.offset(4, CLEAR_HEIGHT, 4))) {
            if (level.getBlockState(pos).is(BlockTags.LOGS)) {
                count++;
            }
        }
        return count;
    }
}
