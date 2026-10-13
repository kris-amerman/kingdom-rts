package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.lumberjackHut;
import static com.krisamerman.kingdomrts.gametest.TestSupport.recordingPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.gametest.TestSupport.RecordingPlayer;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.WorkplaceOps;
import com.krisamerman.kingdomrts.unit.BreakTime;
import com.krisamerman.kingdomrts.unit.Occupation;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The lumberjack in a real world: it fells natural trees in its settlement's claim, leaves
// player-built logs alone, recovers when its tree disappears, and idles (without crashing) when it
// has no settlement or no room. It breaks logs as fast as a player would with the same tool, uses
// and wears out its axe, and gets itself out of awkward spots (a tree top, a pit) or gives up on a
// tree it can't reach. Each test founds a settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class LumberjackGameTests {
    private static final int TREE_HEIGHT = 4;
    private static final BlockPos TREE = new BlockPos(3, FLOOR_Y + 1, 3);
    private static final BlockPos OTHER_TREE = new BlockPos(13, FLOOR_Y + 1, 3);
    private static final BlockPos LUMBERJACK = new BlockPos(13, FLOOR_Y + 1, 13);
    private static final BlockPos HUT = new BlockPos(16, FLOOR_Y + 1, 8);

    @GameTest(template = TEMPLATE, batch = "lumberjack_fell", timeoutTicks = 600)
    public static void fellsATreeInItsClaim(GameTestHelper helper) {
        Unit lumberjack = setUp(helper);
        tree(helper, TREE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, TREE) == 0, "Waiting for the tree to be felled"))
                .thenExecute(() -> {
                    helper.assertTrue(logsCollected(helper, lumberjack) == TREE_HEIGHT,
                            "Expected the " + TREE_HEIGHT + " logs, got " + logsCollected(helper, lumberjack));
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_built", timeoutTicks = 400)
    public static void leavesPlayerBuiltLogsAlone(GameTestHelper helper) {
        Unit lumberjack = setUp(helper);
        // A bare log pillar, and one topped with leaves a player placed (persistent).
        for (int y = 0; y < TREE_HEIGHT; y++) {
            helper.setBlock(TREE.above(y), Blocks.OAK_LOG);
            helper.setBlock(OTHER_TREE.above(y), Blocks.OAK_LOG);
        }
        BlockState placedLeaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockPos top = OTHER_TREE.above(TREE_HEIGHT - 1);
        for (BlockPos leaf : new BlockPos[] {top.north(), top.south(), top.east(), top.west(), top.above()}) {
            helper.setBlock(leaf, placedLeaves);
        }
        helper.startSequence()
                .thenIdle(200)
                .thenExecute(() -> {
                    helper.assertTrue(logsLeft(helper, TREE) == TREE_HEIGHT && logsLeft(helper, OTHER_TREE) == TREE_HEIGHT,
                            "The lumberjack chopped player-built logs");
                    helper.assertTrue(lumberjack.getInventory().isEmpty(), "Expected nothing chopped");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_recover", timeoutTicks = 800)
    public static void recoversWhenItsTreeDisappears(GameTestHelper helper) {
        Unit lumberjack = setUp(helper);
        tree(helper, TREE);
        helper.startSequence()
                // Once it's walking to the tree, someone else takes the tree away and a new one grows.
                .thenWaitUntil(() -> helper.assertFalse(lumberjack.getNavigation().isDone(), "Waiting for it to set off"))
                .thenExecute(() -> {
                    for (int y = 0; y < TREE_HEIGHT; y++) {
                        helper.setBlock(TREE.above(y), Blocks.AIR);
                    }
                    tree(helper, OTHER_TREE);
                })
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, OTHER_TREE) == 0, "Waiting for it to fell the other tree"))
                .thenExecute(() -> {
                    helper.assertTrue(logsCollected(helper, lumberjack) == TREE_HEIGHT,
                            "Expected only the other tree's logs, got " + logsCollected(helper, lumberjack));
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_outside", timeoutTicks = 400)
    public static void ignoresTreesOutsideItsClaim(GameTestHelper helper) {
        Unit lumberjack = setUp(helper);
        // Two chunks past the hall's chunk: outside the 3x3 claim.
        ChunkPos hallChunk = new ChunkPos(helper.absolutePos(HALL));
        BlockPos outside = helper.relativePos(new BlockPos((hallChunk.x + 2) * 16 + 8, helper.absolutePos(TREE).getY(), hallChunk.getMiddleBlockZ()));
        tree(helper, outside);
        helper.startSequence()
                .thenIdle(200)
                .thenExecute(() -> {
                    helper.assertTrue(logsLeft(helper, outside) == TREE_HEIGHT, "The lumberjack chopped a tree outside its claim");
                    clearTree(helper, outside); // outside the test area, so the framework won't clear it
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_full", timeoutTicks = 400)
    public static void waitsWhenItAndItsHutAreFull(GameTestHelper helper) {
        Unit lumberjack = setUp(helper);
        Container stock = stock(helper);
        for (int i = 0; i < stock.getContainerSize(); i++) {
            stock.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        for (int i = 0; i < Unit.INVENTORY_SIZE; i++) {
            lumberjack.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        tree(helper, TREE);
        helper.startSequence()
                .thenIdle(300)
                .thenExecute(() -> {
                    helper.assertTrue(logsLeft(helper, TREE) == TREE_HEIGHT, "With nowhere to put logs, it shouldn't chop");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    // ---- Working from the hut ----

    @GameTest(template = TEMPLATE, batch = "lumberjack_deposit", timeoutTicks = 900)
    public static void bringsItsLogsToTheHut(GameTestHelper helper) {
        Unit lumberjack = setUp(helper);
        tree(helper, TREE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(stock(helper).countItem(Items.OAK_LOG) == TREE_HEIGHT,
                        "Waiting for the logs to reach the hut's stock; it has " + stock(helper).countItem(Items.OAK_LOG)))
                .thenExecute(() -> {
                    helper.assertTrue(lumberjack.getInventory().countItem(Items.OAK_LOG) == 0, "It should have handed over every log");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_work_area", timeoutTicks = 1100)
    public static void staysInsideItsWorkArea(GameTestHelper helper) {
        Unit lumberjack = setUp(helper);
        Settlement settlement = SettlementData.get(helper.getLevel()).byId(lumberjack.getSettlementId());
        WorkplaceOps.setWorkRadius(helper.getLevel(), settlement.workplaceAt(helper.absolutePos(HUT)), 6);
        BlockPos near = HUT.west(4); // inside 6 blocks of the hut
        tree(helper, TREE);          // about 14 blocks away: outside
        tree(helper, near);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, near) == 0, "Waiting for it to fell the tree in its work area"))
                // Long enough to go back to the hut, search again, walk to the other tree and chop a log
                // (60 ticks by hand), if it were allowed to.
                .thenIdle(400)
                .thenExecute(() -> {
                    helper.assertTrue(logsLeft(helper, TREE) == TREE_HEIGHT, "It went outside its work area");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_hut_axe", timeoutTicks = 800)
    public static void takesAnAxeFromTheHut(GameTestHelper helper) {
        Unit lumberjack = setUp(helper);
        stock(helper).setItem(5, new ItemStack(Items.IRON_AXE));
        tree(helper, TREE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(lumberjack.getMainHandItem().is(Items.IRON_AXE), "Waiting for it to chop with the hut's axe"))
                .thenExecute(() -> helper.assertTrue(stock(helper).countItem(Items.IRON_AXE) == 0, "The axe should have left the stock"))
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, TREE) == 0, "Waiting for the tree to be felled"))
                .thenExecute(() -> removeHall(helper, HALL))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_drops", timeoutTicks = 1000)
    public static void collectsTreeDropsForTheHut(GameTestHelper helper) {
        setUp(helper);
        tree(helper, TREE);
        // What leaves drop as they decay, lying around the tree, plus something it doesn't want.
        helper.spawnItem(Items.OAK_SAPLING, Vec3.atBottomCenterOf(TREE.offset(2, 0, 1)));
        helper.spawnItem(Items.STICK, Vec3.atBottomCenterOf(TREE.offset(1, 0, 2)));
        helper.spawnItem(Items.APPLE, Vec3.atBottomCenterOf(TREE.offset(2, 0, 2)));
        helper.spawnItem(Items.DIRT, Vec3.atBottomCenterOf(TREE.offset(-2, 0, 0)));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    Container stock = stock(helper);
                    // At least: the canopy may add some more of its own.
                    helper.assertTrue(stock.countItem(Items.OAK_SAPLING) >= 1 && stock.countItem(Items.STICK) >= 1
                            && stock.countItem(Items.APPLE) >= 1 && stock.countItem(Items.OAK_LOG) == TREE_HEIGHT,
                            "Waiting for the logs, sapling, stick and apple to reach the hut");
                })
                .thenExecute(() -> {
                    helper.assertItemEntityPresent(Items.DIRT, TREE.offset(-2, 0, 0), 2.0); // left alone
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void idlesWithoutASettlement(GameTestHelper helper) {
        unit(helper, LUMBERJACK, null, Occupation.LUMBERJACK, true);
        tree(helper, TREE);
        helper.startSequence()
                .thenIdle(150)
                .thenExecute(() -> helper.assertTrue(logsLeft(helper, TREE) == TREE_HEIGHT,
                        "A lumberjack without a settlement has no claim to work in"))
                .thenSucceed();
    }

    // ---- Breaking time and axes ----

    @GameTest(template = TEMPLATE)
    public static void breakTimeMatchesAPlayer(GameTestHelper helper) {
        helper.setBlock(TREE, Blocks.OAK_LOG);
        BlockPos pos = helper.absolutePos(TREE);
        BlockState log = helper.getLevel().getBlockState(pos);
        Unit unit = unit(helper, LUMBERJACK, null, Occupation.LUMBERJACK, false);
        RecordingPlayer player = recordingPlayer(helper);
        try {
            player.setOnGround(true); // vanilla slows players down 5x in the air; units are always "on the ground"
            for (ItemStack tool : new ItemStack[] {ItemStack.EMPTY, new ItemStack(Items.WOODEN_AXE), new ItemStack(Items.IRON_AXE)}) {
                player.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
                int vanilla = (int) Math.ceil(1.0f / log.getDestroyProgress(player, helper.getLevel(), pos));
                int ours = BreakTime.ticks(unit, tool, log, helper.getLevel(), pos);
                helper.assertTrue(vanilla == ours, "With " + tool + ": vanilla takes " + vanilla + " ticks, BreakTime says " + ours);
            }
        } finally {
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_hand", timeoutTicks = 600)
    public static void fellingByHandTakesAsLongAsAPlayer(GameTestHelper helper) {
        Unit lumberjack = setUpNextToTree(helper);
        helper.startSequence()
                // By hand each oak log takes 60 ticks: after 100, at most one can be down.
                .thenIdle(100)
                .thenExecute(() -> helper.assertTrue(logsLeft(helper, TREE) >= TREE_HEIGHT - 1,
                        "Too fast for bare hands: " + logsLeft(helper, TREE) + " logs left after 100 ticks"))
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, TREE) == 0, "Waiting for the tree to be felled"))
                .thenExecute(() -> {
                    helper.assertTrue(logsCollected(helper, lumberjack) == TREE_HEIGHT, "Expected the logs");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_axe", timeoutTicks = 300)
    public static void usesItsAxe(GameTestHelper helper) {
        Unit lumberjack = setUpNextToTree(helper);
        lumberjack.getInventory().setItem(3, new ItemStack(Items.IRON_AXE));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(lumberjack.getMainHandItem().is(Items.IRON_AXE), "Waiting for it to take its axe"))
                // An iron axe takes 10 ticks per oak log, instead of 60 by hand.
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, TREE) == 0, "Waiting for the tree to be felled"))
                .thenExecute(() -> helper.assertTrue(logsCollected(helper, lumberjack) == TREE_HEIGHT, "Expected the logs"))
                .thenWaitUntil(() -> helper.assertTrue(lumberjack.getMainHandItem().isEmpty(), "Waiting for it to put the axe away"))
                .thenExecute(() -> {
                    ItemStack axe = lumberjack.getInventory().getItem(3);
                    helper.assertTrue(axe.is(Items.IRON_AXE) && axe.getDamageValue() == TREE_HEIGHT,
                            "Expected the axe back in its slot with " + TREE_HEIGHT + " damage, got " + axe + " damage " + axe.getDamageValue());
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_axe_breaks", timeoutTicks = 600)
    public static void carriesOnByHandWhenItsAxeBreaks(GameTestHelper helper) {
        Unit lumberjack = setUpNextToTree(helper);
        ItemStack worn = new ItemStack(Items.IRON_AXE);
        worn.setDamageValue(worn.getMaxDamage() - 1); // one use left
        lumberjack.getInventory().setItem(0, worn);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, TREE) == 0, "Waiting for the tree to be felled"))
                .thenExecute(() -> {
                    helper.assertTrue(lumberjack.getInventory().countItem(Items.IRON_AXE) == 0 && lumberjack.getMainHandItem().isEmpty(),
                            "Expected the axe to have broken");
                    helper.assertTrue(logsCollected(helper, lumberjack) == TREE_HEIGHT, "Expected all the logs");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    // ---- Getting there ----

    @GameTest(template = TEMPLATE, batch = "lumberjack_walled", timeoutTicks = 800)
    public static void reachesATreeOpenOnOneSide(GameTestHelper helper) {
        Unit lumberjack = setUp(helper);
        tree(helper, TREE);
        // Walls on three sides of the tree, two blocks out and three high: only the east side is open.
        for (int d = -2; d <= 2; d++) {
            for (int y = 0; y < 3; y++) {
                helper.setBlock(TREE.offset(-2, y, d), Blocks.STONE);
                helper.setBlock(TREE.offset(d, y, -2), Blocks.STONE);
                helper.setBlock(TREE.offset(d, y, 2), Blocks.STONE);
            }
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, TREE) == 0, "Waiting for the tree to be felled"))
                .thenExecute(() -> {
                    helper.assertTrue(logsCollected(helper, lumberjack) == TREE_HEIGHT, "Expected the logs");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_tree_top", timeoutTicks = 800)
    public static void getsDownFromATreeTop(GameTestHelper helper) {
        Unit lumberjack = setUp(helper);
        tree(helper, TREE);
        BlockPos onTop = helper.absolutePos(TREE.above(TREE_HEIGHT + 1)); // standing on the top leaves
        lumberjack.teleportTo(onTop.getX() + 0.5, onTop.getY(), onTop.getZ() + 0.5);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, TREE) == 0, "Waiting for the tree to be felled"))
                .thenExecute(() -> {
                    helper.assertTrue(logsCollected(helper, lumberjack) == TREE_HEIGHT, "Expected the logs");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_pit", timeoutTicks = 1000)
    public static void climbsOutOfAPit(GameTestHelper helper) {
        Unit lumberjack = setUp(helper);
        tree(helper, TREE);
        // Walled in on all four sides, three blocks high: it can't walk or jump out.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    for (int y = 0; y < 3; y++) {
                        helper.setBlock(LUMBERJACK.offset(dx, y, dz), Blocks.STONE);
                    }
                }
            }
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, TREE) == 0, "Waiting for the tree to be felled"))
                .thenExecute(() -> {
                    helper.assertTrue(logsCollected(helper, lumberjack) == TREE_HEIGHT, "Expected the logs");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_unreachable", timeoutTicks = 1600)
    public static void skipsATreeItCantReach(GameTestHelper helper) {
        Unit lumberjack = setUp(helper);
        BlockPos walledTree = new BlockPos(4, FLOOR_Y + 1, 4);
        BlockPos openTree = new BlockPos(13, FLOOR_Y + 1, 13);
        lumberjack.teleportTo(helper.absolutePos(new BlockPos(11, FLOOR_Y + 1, 4)).getX() + 0.5, lumberjack.getY(),
                helper.absolutePos(new BlockPos(11, FLOOR_Y + 1, 4)).getZ() + 0.5); // nearer the walled tree
        tree(helper, walledTree);
        tree(helper, openTree);
        // A ring of wall three blocks out and four high (taller than it can jump) around the first tree.
        for (int d = -3; d <= 3; d++) {
            for (int y = 0; y < 4; y++) {
                helper.setBlock(walledTree.offset(-3, y, d), Blocks.STONE);
                helper.setBlock(walledTree.offset(3, y, d), Blocks.STONE);
                helper.setBlock(walledTree.offset(d, y, -3), Blocks.STONE);
                helper.setBlock(walledTree.offset(d, y, 3), Blocks.STONE);
            }
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, openTree) == 0, "Waiting for it to fell the reachable tree"))
                .thenExecute(() -> {
                    helper.assertTrue(logsLeft(helper, walledTree) == TREE_HEIGHT, "It got to the walled-off tree");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_canopy", timeoutTicks = 800)
    public static void clearsTheCanopyOfAFelledTree(GameTestHelper helper) {
        Unit lumberjack = setUpNextToTree(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, TREE) == 0 && leavesAround(helper, TREE) == 0,
                        "Waiting for the tree and its canopy to go; " + leavesAround(helper, TREE) + " leaves left"))
                .thenExecute(() -> {
                    // Whatever the leaves dropped went to the lumberjack, not the ground.
                    for (var item : new Item[] {Items.OAK_SAPLING, Items.STICK, Items.APPLE}) {
                        helper.assertItemEntityNotPresent(item, TREE, 8.0);
                    }
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    // Two canopies touching, as in a dense forest: felling one tree must leave the leaves that are
    // still within reach of the other's logs. The other "tree" here is a bare log pillar (too few
    // natural leaves to count as a tree, so it isn't felled), joined to the first by a bridge leaf.
    @GameTest(template = TEMPLATE, batch = "lumberjack_shared_canopy", timeoutTicks = 800)
    public static void leavesNeighbouringCanopiesStanding(GameTestHelper helper) {
        setUpNextToTree(helper);
        BlockPos top = TREE.above(TREE_HEIGHT - 1);
        BlockPos pillar = TREE.east(3);
        for (int y = 0; y < TREE_HEIGHT; y++) {
            helper.setBlock(pillar.above(y), Blocks.OAK_LOG);
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        BlockPos bridge = top.east(2); // touches the tree's east leaf and the pillar
        helper.setBlock(bridge, leaves);
        BlockPos nearPillar = top.east(); // the tree's own east leaf, 2 from the pillar through the bridge
        BlockPos farSide = top.west();    // the tree's west leaf, with no way to the pillar
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, TREE) == 0 && helper.getBlockState(farSide).isAir(),
                        "Waiting for the tree to be felled and its own canopy cleared"))
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.OAK_LEAVES, nearPillar);
                    helper.assertBlockPresent(Blocks.OAK_LEAVES, bridge);
                    helper.assertTrue(logsLeft(helper, pillar) == TREE_HEIGHT, "The pillar isn't a tree and should stand");
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "lumberjack_full_trips", timeoutTicks = 1600)
    public static void fellsSeveralTreesBeforeGoingBack(GameTestHelper helper) {
        Unit lumberjack = setUp(helper);
        tree(helper, TREE);
        tree(helper, OTHER_TREE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, TREE) == 0 || logsLeft(helper, OTHER_TREE) == 0,
                        "Waiting for the first tree"))
                .thenWaitUntil(() -> helper.assertTrue(logsLeft(helper, TREE) == 0 && logsLeft(helper, OTHER_TREE) == 0,
                        "Waiting for the second tree"))
                .thenExecute(() -> helper.assertTrue(stock(helper).countItem(Items.OAK_LOG) == 0,
                        "It went back to the hut between trees, with room to spare"))
                .thenWaitUntil(() -> helper.assertTrue(stock(helper).countItem(Items.OAK_LOG) == 2 * TREE_HEIGHT,
                        "Waiting for both trees' logs to reach the hut at once"))
                .thenExecute(() -> removeHall(helper, HALL))
                .thenSucceed();
    }

    // A settlement and lumberjack, with a tree right next to it (so tests about chopping aren't
    // slowed down by walking).
    private static Unit setUpNextToTree(GameTestHelper helper) {
        Unit lumberjack = setUp(helper);
        tree(helper, TREE);
        BlockPos next = helper.absolutePos(TREE.east(2));
        lumberjack.teleportTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5);
        return lumberjack;
    }

    // A settlement for a new faction with a lumberjack hut, and a settler hired there as its
    // lumberjack (the only way a unit gets the job).
    private static Unit setUp(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        Unit lumberjack = unit(helper, LUMBERJACK, blue, null, true);
        SettlementOps.setUnitSettlement(helper.getLevel(), lumberjack, settlement);
        String problem = WorkplaceOps.hire(helper.getLevel(), lumberjack, helper.absolutePos(HUT));
        if (problem != null) {
            throw new GameTestAssertException("Couldn't hire the lumberjack: " + problem);
        }
        return lumberjack;
    }

    // A small natural oak at base: dirt under a 4-log trunk, with natural (non-persistent) leaves
    // around its top two logs and on top.
    private static void tree(GameTestHelper helper, BlockPos base) {
        helper.setBlock(base.below(), Blocks.DIRT);
        for (int y = 0; y < TREE_HEIGHT; y++) {
            helper.setBlock(base.above(y), Blocks.OAK_LOG);
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int y = TREE_HEIGHT - 2; y < TREE_HEIGHT; y++) {
            BlockPos log = base.above(y);
            for (BlockPos leaf : new BlockPos[] {log.north(), log.south(), log.east(), log.west()}) {
                helper.setBlock(leaf, leaves);
            }
        }
        helper.setBlock(base.above(TREE_HEIGHT), leaves);
    }

    private static void clearTree(GameTestHelper helper, BlockPos base) {
        for (int y = -1; y <= TREE_HEIGHT; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.setBlock(base.offset(dx, y, dz), Blocks.AIR);
                }
            }
        }
    }

    // The hut's stock.
    private static Container stock(GameTestHelper helper) {
        return (Container) helper.getBlockEntity(HUT);
    }

    // Logs the lumberjack has collected so far: carried, or already in the hut's stock.
    private static int logsCollected(GameTestHelper helper, Unit lumberjack) {
        return lumberjack.getInventory().countItem(Items.OAK_LOG) + stock(helper).countItem(Items.OAK_LOG);
    }

    // Oak leaves left in the small tree's canopy area.
    private static int leavesAround(GameTestHelper helper, BlockPos base) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, TREE_HEIGHT, 1))) {
            if (helper.getBlockState(pos).is(Blocks.OAK_LEAVES)) {
                count++;
            }
        }
        return count;
    }

    private static int logsLeft(GameTestHelper helper, BlockPos base) {
        int count = 0;
        for (int y = 0; y < TREE_HEIGHT; y++) {
            if (helper.getBlockState(base.above(y)).is(Blocks.OAK_LOG)) {
                count++;
            }
        }
        return count;
    }
}
