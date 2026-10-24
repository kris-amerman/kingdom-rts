package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.current;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.lastPayload;
import static com.krisamerman.kingdomrts.gametest.TestSupport.recordingPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import java.util.List;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.gametest.TestSupport.RecordingPlayer;
import com.krisamerman.kingdomrts.nation.NationPayloads;
import com.krisamerman.kingdomrts.nation.NationRequests;
import com.krisamerman.kingdomrts.settlement.BuilderOps;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Demolish in the nation interface, as the server answers it: which buildings a player is shown,
// and demolishing one (or stopping its demolition): its own faction's only, with the settlement's
// idle builders put on it. Each test founds a settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class NationDemolishGameTests {
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");
    private static final BlockPos SITE = new BlockPos(1, FLOOR_Y + 1, 1);
    private static final BlockPos SECOND_SITE = SITE.south(6);

    @GameTest(template = TEMPLATE, batch = "nation_demolish_list")
    public static void aPlayerIsShownTheirFactionsBuildingsAround(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building finished = finishedShed(helper, settlement, blue, SITE);
        Building site = commission(helper, blue, SECOND_SITE);
        RecordingPlayer player = member(helper, blue);
        RecordingPlayer factionless = recordingPlayer(helper);
        try {
            standAt(helper, player, HALL);
            NationRequests.buildings(player);
            NationPayloads.ShowBuildings shown = lastPayload(player, NationPayloads.ShowBuildings.class);
            helper.assertTrue(shown != null && shown.buildings().size() == 2, "Expected both sheds: " + shown);
            NationPayloads.BuildingView done = view(shown, finished.origin());
            NationPayloads.BuildingView building = view(shown, site.origin());
            helper.assertTrue(done.built() && !done.demolishing() && done.name().equals("Storage shed")
                    && done.min().equals(new BlockPos(finished.footprint().minX(), finished.footprint().minY(), finished.footprint().minZ())),
                    "Expected the finished shed with its box: " + done);
            helper.assertTrue(!building.built(), "Expected the other still being built: " + building);

            player.sent.clear();
            NationRequests.buildings(player);
            helper.assertTrue(lastPayload(player, NationPayloads.ShowBuildings.class) == null, "Expected an ask straight after another to be dropped");
            NationRequests.buildings(factionless);
            NationPayloads.ShowBuildings none = lastPayload(factionless, NationPayloads.ShowBuildings.class);
            helper.assertTrue(none != null && none.buildings().isEmpty(), "Expected nothing for a player without a faction");
            standAt(helper, player, HALL.offset(NationRequests.SITES_RANGE + 40, 0, 0));
            helper.assertTrue(NationRequests.buildingsFor(player).buildings().isEmpty(), "Expected nothing from far away");
        } finally {
            leaveAndRemove(helper, player);
            removePlayer(helper, factionless);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    // Demolishing your own building orders it demolished and puts idle builders on it, up to as many
    // as it takes (two for a shed): of three idle builders, two go.
    @GameTest(template = TEMPLATE, batch = "nation_demolish_own")
    public static void demolishingYourOwnBuildingPutsIdleBuildersOnIt(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = finishedShed(helper, settlement, blue, SITE);
        List<Unit> builders = List.of(builder(helper, settlement, blue, HALL.east(2)), builder(helper, settlement, blue, HALL.east(3)),
                builder(helper, settlement, blue, HALL.east(4)));
        RecordingPlayer player = member(helper, blue);
        try {
            NationRequests.demolish(player, shed.origin());
            Settlement now = current(helper, settlement);
            Building ordered = now.buildingAt(shed.origin());
            helper.assertTrue(ordered != null && ordered.demolishing(), "Expected the shed ordered demolished: " + ordered);
            long assigned = builders.stream().filter(builder -> BuilderOps.siteOf(now, builder.getUUID()) != null).count();
            helper.assertTrue(assigned == 2, "Expected two of the three idle builders on it, got " + assigned);
            helper.assertTrue(player.actionBar.contains("Demolishing the storage shed: 2 builders are taking it apart"), "Expected to be told: " + player.actionBar);
            NationPayloads.DemolishAnswer answer = lastPayload(player, NationPayloads.DemolishAnswer.class);
            helper.assertTrue(answer != null && answer.done(), "Expected Demolish to be told it worked");
        } finally {
            leaveAndRemove(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "nation_demolish_refused")
    public static void onlyYourOwnBuildingsCanBeDemolished(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = finishedShed(helper, settlement, blue, SITE);
        Building coming = finishedShed(helper, settlement, blue, SECOND_SITE);
        BuildingOps.orderDemolition(helper.getLevel(), blue, current(helper, settlement), coming);
        RecordingPlayer member = member(helper, blue);
        RecordingPlayer outsider = member(helper, red);
        RecordingPlayer factionless = recordingPlayer(helper);
        try {
            refused(helper, outsider, shed.origin(), "isn't " + red.name() + "'s");
            refused(helper, factionless, shed.origin(), "faction");
            refused(helper, member, helper.absolutePos(HALL.east(3)), "no building there");
            refused(helper, member, coming.origin(), "already being demolished");
            helper.assertTrue(!current(helper, settlement).buildingAt(shed.origin()).demolishing(), "Expected the shed untouched");
        } finally {
            leaveAndRemove(helper, member);
            leaveAndRemove(helper, outsider);
            removePlayer(helper, factionless);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    // Demolish stops a demolition too, on the faction's own buildings only: the building is as it
    // was, and its builders free again (a built building takes none). Another faction can't stop it through the operation either.
    @GameTest(template = TEMPLATE, batch = "nation_demolish_stop")
    public static void yourOwnDemolitionCanBeStopped(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Settlement settlement = found(helper, HALL, blue);
        Building shed = finishedShed(helper, settlement, blue, SITE);
        Unit builder = builder(helper, settlement, blue, HALL.east(2));
        RecordingPlayer member = member(helper, blue);
        RecordingPlayer outsider = member(helper, red);
        RecordingPlayer factionless = recordingPlayer(helper);
        try {
            NationRequests.demolish(member, shed.origin());
            helper.assertTrue(BuilderOps.siteOf(current(helper, settlement), builder.getUUID()) != null, "Expected the builder put on the demolition");
            String problem = BuildingOps.stopDemolition(helper.getLevel(), red, current(helper, settlement), shed);
            helper.assertTrue(problem != null && problem.contains("belongs to another faction"), "Expected another faction refused: " + problem);
            refusedStop(helper, outsider, shed.origin(), "isn't " + red.name() + "'s");
            refusedStop(helper, factionless, shed.origin(), "faction");
            helper.assertTrue(current(helper, settlement).buildingAt(shed.origin()).demolishing(), "Expected it still coming down");

            member.actionBar.clear();
            member.sent.clear();
            NationRequests.stopDemolition(member, shed.origin());
            Settlement now = current(helper, settlement);
            Building stopped = now.buildingAt(shed.origin());
            helper.assertTrue(stopped != null && stopped.isBuilt() && !stopped.demolishing(), "Expected an ordinary built shed again: " + stopped);
            helper.assertTrue(BuilderOps.siteOf(now, builder.getUUID()) == null, "Expected its builder free again");
            helper.assertTrue(member.actionBar.equals(List.of("Stopped demolishing the storage shed")), "Expected to be told: " + member.actionBar);
            NationPayloads.DemolishAnswer answer = lastPayload(member, NationPayloads.DemolishAnswer.class);
            helper.assertTrue(answer != null && answer.done(), "Expected Demolish to be told it worked");
            refusedStop(helper, member, shed.origin(), "isn't being demolished");
        } finally {
            leaveAndRemove(helper, member);
            leaveAndRemove(helper, outsider);
            removePlayer(helper, factionless);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    private static void refusedStop(GameTestHelper helper, RecordingPlayer player, BlockPos origin, String reason) {
        player.actionBar.clear();
        player.sent.clear();
        NationRequests.stopDemolition(player, origin);
        helper.assertTrue(player.actionBar.size() == 1 && player.actionBar.get(0).startsWith("Can't stop that: ") && player.actionBar.get(0).contains(reason),
                "Expected to be told \"" + reason + "\": " + player.actionBar);
        NationPayloads.DemolishAnswer answer = lastPayload(player, NationPayloads.DemolishAnswer.class);
        helper.assertTrue(answer != null && !answer.done(), "Expected Demolish to be told it was refused");
    }

    private static void refused(GameTestHelper helper, RecordingPlayer player, BlockPos origin, String reason) {
        player.actionBar.clear();
        player.sent.clear();
        NationRequests.demolish(player, origin);
        helper.assertTrue(player.actionBar.size() == 1 && player.actionBar.get(0).startsWith("Can't demolish that: ") && player.actionBar.get(0).contains(reason),
                "Expected to be told \"" + reason + "\": " + player.actionBar);
        NationPayloads.DemolishAnswer answer = lastPayload(player, NationPayloads.DemolishAnswer.class);
        helper.assertTrue(answer != null && !answer.done(), "Expected Demolish to be told it was refused");
    }

    private static NationPayloads.BuildingView view(NationPayloads.ShowBuildings shown, BlockPos origin) {
        return shown.buildings().stream().filter(view -> view.origin().equals(origin)).findFirst()
                .orElseThrow(() -> new GameTestAssertException("Expected the building at " + origin.toShortString() + " in " + shown.buildings()));
    }

    private static Building commission(GameTestHelper helper, Faction faction, BlockPos relativePos) {
        BuildingOps.CommissionResult result = BuildingOps.commission(helper.getLevel(), faction, SHED, helper.absolutePos(relativePos), Rotation.NONE);
        if (result.building() == null) {
            throw new GameTestAssertException("Couldn't commission the shed: " + result.problem());
        }
        return result.building();
    }

    private static Building finishedShed(GameTestHelper helper, Settlement settlement, Faction faction, BlockPos relativePos) {
        Building shed = commission(helper, faction, relativePos);
        BuildingOps.finish(helper.getLevel(), current(helper, settlement), shed);
        return current(helper, settlement).buildingAt(shed.origin());
    }

    // A builder without AI: it stays put, since these check the order, not the work.
    private static Unit builder(GameTestHelper helper, Settlement settlement, Faction faction, BlockPos pos) {
        Unit unit = unit(helper, pos, faction, null, false);
        SettlementOps.setUnitSettlement(helper.getLevel(), unit, settlement);
        String problem = TestSupport.hireBuilder(helper, unit);
        if (problem != null) {
            throw new GameTestAssertException("Couldn't hire a builder: " + problem);
        }
        return unit;
    }

    private static void standAt(GameTestHelper helper, RecordingPlayer player, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private static RecordingPlayer member(GameTestHelper helper, Faction faction) {
        RecordingPlayer player = recordingPlayer(helper);
        FactionData.get(helper.getLevel()).join(player.getUUID(), faction);
        return player;
    }

    private static void leaveAndRemove(GameTestHelper helper, RecordingPlayer player) {
        FactionData.get(helper.getLevel()).leave(player.getUUID());
        removePlayer(helper, player);
    }
}
