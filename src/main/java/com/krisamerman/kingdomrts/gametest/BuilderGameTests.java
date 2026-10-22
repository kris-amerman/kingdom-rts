package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.buildersHut;
import static com.krisamerman.kingdomrts.gametest.TestSupport.current;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.lastOpenedScreen;
import static com.krisamerman.kingdomrts.gametest.TestSupport.recordingPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.runCommand;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import java.util.List;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.gametest.TestSupport.RecordingPlayer;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuilderOps;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.BuildingSiteMenu;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.WorkplaceOps;
import com.krisamerman.kingdomrts.unit.Occupation;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// A settlement's builders (hired at a builders' hut): assigned to and taken off construction sites
// at the site panel (up to the building's max_builders), walking to their site and back to their
// hut, and kept consistent when a builder dies or leaves, or its site is finished or cancelled.
// Each test founds a settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class BuilderGameTests {
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");
    private static final BlockPos SITE = new BlockPos(1, FLOOR_Y + 1, 1);
    private static final BlockPos OTHER_SITE = new BlockPos(1, FLOOR_Y + 1, 9);
    private static final BlockPos HUT = new BlockPos(13, FLOOR_Y + 1, 13);

    @GameTest(template = TEMPLATE, batch = "builders_site")
    public static void assigningAtTheSiteUpToItsLimit(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        for (int i = 0; i < 3; i++) {
            hire(helper, settlement, settler(helper, settlement, blue, HALL.offset(i, 0, 3), false));
        }
        Building shed = commission(helper, blue, SITE);
        Building other = commission(helper, blue, OTHER_SITE);
        RecordingPlayer player = playerAt(helper, blue, shed.site().east(2));
        try {
            useBlock(helper, player, shed.site());
            sitePress(player, BuildingSiteMenu.SHOW_BUILDERS_BUTTON);
            BuildingSiteMenu.SiteInfo info = siteMenu(helper, player).getInfo();
            helper.assertTrue(info.assigned().isEmpty() && info.available().size() == 3 && info.maxBuilders() == 2,
                    "Expected three idle builders and room for two: " + info);
            sitePress(player, BuildingSiteMenu.ASSIGN_BUTTON);
            sitePress(player, BuildingSiteMenu.ASSIGN_BUTTON);
            assertNoScreenClosed(helper, player);
            info = siteMenu(helper, player).getInfo();
            helper.assertTrue(info.assigned().size() == 2 && info.isFull() && info.available().size() == 1, "Expected the site full: " + info);
            helper.assertFalse(((BuildingSiteMenu) player.containerMenu).clickMenuButton(player, BuildingSiteMenu.ASSIGN_BUTTON),
                    "A third builder can't be assigned");
            helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).builders().size() == 2, "Still two on the site");

            String busy = BuilderOps.assign(helper.getLevel(), current(helper, settlement), other, info.assigned().get(0).id());
            helper.assertTrue(busy != null && busy.contains("already working on the storage shed"), "A busy builder can't go elsewhere: " + busy);
            helper.assertTrue(BuilderOps.idleBuilders(helper.getLevel(), current(helper, settlement)).size() == 1,
                    "Only the unassigned builder is offered to other sites");

            sitePress(player, BuildingSiteMenu.UNASSIGN_BUTTON);
            info = siteMenu(helper, player).getInfo();
            helper.assertTrue(info.assigned().size() == 1 && info.available().size() == 2, "Unassigning frees a builder: " + info);
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    // Assigned, a builder walks to its site and stays by the site block; when the site is cancelled
    // it's back in the pool and walks back to its builders' hut.
    @GameTest(template = TEMPLATE, batch = "builders_walk", timeoutTicks = 800)
    public static void aBuilderGoesToItsSiteAndBack(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Unit builder = settler(helper, settlement, blue, HALL.east(2), true);
        hire(helper, settlement, builder);
        Building shed = commission(helper, blue, SITE);
        assign(helper, settlement, shed, builder);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(near(builder, shed.site(), 4.0), "Waiting for the builder to reach its site"))
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(near(builder, shed.site(), 4.5), "It should stay by the site block");
                    BuildingOps.cancel(helper.getLevel(), current(helper, settlement), current(helper, settlement).buildingAt(shed.origin()));
                })
                .thenWaitUntil(() -> helper.assertTrue(near(builder, helper.absolutePos(TestSupport.BUILDERS_HUT), 4.5), "Waiting for it to walk back to its hut"))
                .thenExecute(() -> removeHall(helper, HALL))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_finish", timeoutTicks = 800)
    public static void finishingASiteFreesItsBuilders(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Unit builder = settler(helper, settlement, blue, SITE.offset(2, 0, -1), true);
        hire(helper, settlement, builder);
        Building shed = commission(helper, blue, SITE);
        assign(helper, settlement, shed, builder);
        BuildingOps.finish(helper.getLevel(), current(helper, settlement), shed);
        helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).builders().isEmpty(), "A finished building has no builders");
        helper.assertTrue(BuilderOps.siteOf(current(helper, settlement), builder.getUUID()) == null, "The builder is back in the pool");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(near(builder, helper.absolutePos(TestSupport.BUILDERS_HUT), 4.5), "Waiting for it to walk back to its hut"))
                .thenExecute(() -> removeHall(helper, HALL))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_consistency", timeoutTicks = 200)
    public static void aBuilderThatDiesOrLeavesIsTakenOffItsSite(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Unit dies = settler(helper, settlement, blue, HALL.east(2), false);
        Unit leaves = settler(helper, settlement, blue, HALL.east(3), false);
        hire(helper, settlement, dies);
        hire(helper, settlement, leaves);
        Building shed = commission(helper, blue, SITE);
        assign(helper, settlement, shed, dies);
        assign(helper, settlement, shed, leaves);
        SettlementOps.setUnitSettlement(helper.getLevel(), leaves, null);
        helper.assertTrue(!current(helper, settlement).buildingAt(shed.origin()).builders().containsKey(leaves.getUUID()),
                "A builder that leaves its settlement is taken off its site");
        helper.assertTrue(leaves.getOccupationId() == null, "...and isn't a builder any more");
        dies.kill(); // removed once its death animation is over
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).builders().isEmpty(),
                        "Waiting for the dead builder to be taken off its site"))
                .thenExecute(() -> removeHall(helper, HALL))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "builders_commands")
    public static void builderCommands(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        BlockPos spot = HALL.east(3);
        Unit settler = settler(helper, settlement, blue, spot, false);
        Building shed = commission(helper, blue, SITE);
        Building other = commission(helper, blue, OTHER_SITE);
        String here = "@e[type=kingdomrts:unit,distance=..1]";
        buildersHut(helper, HUT);
        expect(helper, spot, "kingdomrts unit hire " + here + " " + coords(helper, HUT), "Hired 1 unit(s) at");
        helper.assertTrue(Occupation.BUILDER.equals(settler.getOccupationId()), "Expected a builder");
        expect(helper, spot, "kingdomrts building assign " + coords(helper, SITE) + " " + here, "Assigned 1 builder(s) to the Storage shed");
        expect(helper, spot, "kingdomrts building assign " + coords(helper, OTHER_SITE) + " " + here, "already working on the storage shed");
        expect(helper, spot, "kingdomrts building unassign " + coords(helper, SITE) + " " + here, "Unassigned 1 builder(s) from the Storage shed");
        helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).builders().isEmpty()
                && current(helper, settlement).buildingAt(other.origin()).builders().isEmpty(), "Nobody should be assigned now");
        expect(helper, spot, "kingdomrts unit fire " + here, "Fired 1 unit(s)");
        helper.assertTrue(settler.getOccupationId() == null, "Fired: unemployed again");
        removeHall(helper, HALL);
        helper.succeed();
    }

    private static boolean near(Unit unit, BlockPos pos, double distance) {
        return unit.position().distanceTo(Vec3.atBottomCenterOf(pos)) <= distance;
    }

    // An unemployed unit of the faction that belongs to the settlement.
    private static Unit settler(GameTestHelper helper, Settlement settlement, Faction faction, BlockPos pos, boolean ai) {
        Unit unit = unit(helper, pos, faction, null, ai);
        SettlementOps.setUnitSettlement(helper.getLevel(), unit, settlement);
        return unit;
    }

    private static void hire(GameTestHelper helper, Settlement settlement, Unit unit) {
        String problem = TestSupport.hireBuilder(helper, unit);
        if (problem != null) {
            throw new GameTestAssertException("Couldn't hire a builder: " + problem);
        }
    }

    private static void assign(GameTestHelper helper, Settlement settlement, Building building, Unit unit) {
        String problem = BuilderOps.assign(helper.getLevel(), current(helper, settlement), building, unit.getUUID());
        if (problem != null) {
            throw new GameTestAssertException("Couldn't assign the builder: " + problem);
        }
    }

    private static Building commission(GameTestHelper helper, Faction faction, BlockPos relativePos) {
        BuildingOps.CommissionResult result = BuildingOps.commission(helper.getLevel(), faction, SHED, helper.absolutePos(relativePos), Rotation.NONE);
        if (result.building() == null) {
            throw new GameTestAssertException("Couldn't commission the shed: " + result.problem());
        }
        return result.building();
    }

    private static RecordingPlayer playerAt(GameTestHelper helper, Faction faction, BlockPos absolutePos) {
        RecordingPlayer player = recordingPlayer(helper);
        FactionData.get(helper.getLevel()).join(player.getUUID(), faction);
        Vec3 at = Vec3.atBottomCenterOf(absolutePos);
        player.teleportTo(at.x, at.y, at.z);
        return player;
    }

    private static void leave(GameTestHelper helper, RecordingPlayer player) {
        FactionData.get(helper.getLevel()).leave(player.getUUID());
        removePlayer(helper, player);
    }

    private static void useBlock(GameTestHelper helper, RecordingPlayer player, BlockPos pos) {
        player.sent.clear();
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static BuildingSiteMenu siteMenu(GameTestHelper helper, RecordingPlayer player) {
        if (!(player.containerMenu instanceof BuildingSiteMenu)) {
            throw new GameTestAssertException("The site panel isn't open; got " + player.containerMenu);
        }
        TestSupport.OpenedScreen opened = lastOpenedScreen(helper, player);
        return new BuildingSiteMenu(opened.windowId(), player.getInventory(), opened.data());
    }

    private static void sitePress(RecordingPlayer player, int button) {
        if (!(player.containerMenu instanceof BuildingSiteMenu menu) || !menu.stillValid(player)) {
            throw new GameTestAssertException("The site panel isn't open and valid");
        }
        player.sent.clear();
        menu.clickMenuButton(player, button);
    }

    private static void assertNoScreenClosed(GameTestHelper helper, RecordingPlayer player) {
        helper.assertFalse(player.sent.stream().anyMatch(packet -> packet instanceof ClientboundContainerClosePacket),
                "The client was told to close its screen, which re-centres the mouse");
    }

    private static String coords(GameTestHelper helper, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    // Runs a command from a spot and checks a line of its output contains the text.
    private static void expect(GameTestHelper helper, BlockPos from, String command, String expected) {
        List<String> output = runCommand(helper, from, command);
        helper.assertTrue(output.stream().anyMatch(line -> line.contains(expected)), "Expected \"" + expected + "\" from /" + command + ", got " + output);
    }
}
