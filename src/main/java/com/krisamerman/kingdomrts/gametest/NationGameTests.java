package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.lastNationScreen;
import static com.krisamerman.kingdomrts.gametest.TestSupport.recordingPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.runCommand;
import static com.krisamerman.kingdomrts.gametest.TestSupport.runCommandAs;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import java.util.List;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.gametest.TestSupport.RecordingPlayer;
import com.krisamerman.kingdomrts.nation.NationOverview;
import com.krisamerman.kingdomrts.nation.NationRequests;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The nation management interface's request handling: what the server sends a player who opens it
// (by the key's request or the command), and which settlements a player's requests may act on.
// Tests that found a settlement each have their own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class NationGameTests {
    @GameTest(template = TEMPLATE, batch = "nation_overview")
    public static void aMemberSeesTheirFactionAndOnlyItsSettlements(GameTestHelper helper) {
        RecordingPlayer leader = recordingPlayer(helper);
        RecordingPlayer outsider = recordingPlayer(helper);
        Faction blue = FactionData.get(helper.getLevel()).foundFactionFor(leader);
        Faction red = faction(helper, "Red");
        FactionData.get(helper.getLevel()).join(outsider.getUUID(), red);
        Settlement settlement = found(helper, HALL, blue);
        for (int i = 2; i <= 3; i++) {
            SettlementOps.setUnitSettlement(helper.getLevel(), unit(helper, HALL.east(i), blue, false), settlement);
        }
        try {
            NationRequests.open(leader);
            NationOverview overview = lastNationScreen(leader);
            helper.assertTrue(overview != null, "Expected the interface to open");
            helper.assertTrue(overview.factionName().equals(blue.name()) && overview.leader(), "Expected " + blue.name() + ", as its leader: " + overview);
            helper.assertTrue(overview.settlements().equals(List.of(new NationOverview.SettlementLine(settlement.name(), Level.OVERWORLD.location().toString(),
                    helper.absolutePos(HALL), settlement.chunks().size(), 2))), "Expected just " + settlement.name() + ", with 2 people: " + overview.settlements());

            NationRequests.open(outsider);
            NationOverview theirs = lastNationScreen(outsider);
            helper.assertTrue(theirs != null && theirs.factionName().equals(red.name()) && !theirs.leader() && theirs.settlements().isEmpty(),
                    "Expected " + red.name() + " with none of " + blue.name() + "'s settlements, as a member: " + theirs);
        } finally {
            leaveAndRemove(helper, leader);
            leaveAndRemove(helper, outsider);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPlayerWithoutAFactionIsToldToGetOneFirst(GameTestHelper helper) {
        RecordingPlayer player = recordingPlayer(helper);
        try {
            NationRequests.open(player);
            NationOverview overview = lastNationScreen(player);
            helper.assertTrue(overview != null && !overview.hasFaction() && overview.settlements().isEmpty(),
                    "Expected the join-or-found screen: " + overview);
        } finally {
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anyPlayerCanOpenItByCommandButNotTheTestingCommands(GameTestHelper helper) {
        RecordingPlayer player = recordingPlayer(helper);
        Faction blue = faction(helper, "Blue");
        FactionData.get(helper.getLevel()).join(player.getUUID(), blue);
        try {
            helper.assertFalse(player.hasPermissions(2), "Expected the test player not to be an operator");
            List<String> output = runCommandAs(player, "kingdomrts nation");
            NationOverview overview = lastNationScreen(player);
            helper.assertTrue(overview != null && overview.factionName().equals(blue.name()), "Expected /kingdomrts nation to open it: " + overview + " " + output);

            List<String> refused = runCommandAs(player, "kingdomrts faction list");
            helper.assertTrue(refused.stream().noneMatch(line -> line.contains(blue.name())) && !refused.isEmpty(),
                    "Expected the testing commands to stay operator-only: " + firstLines(refused));
            List<String> asOperator = runCommand(helper, HALL, "kingdomrts faction list");
            helper.assertTrue(asOperator.stream().anyMatch(line -> line.contains(blue.name())), "Expected operators to keep them: " + firstLines(asOperator));
        } finally {
            leaveAndRemove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "nation_requests")
    public static void requestsOnlyActOnTheirOwnFactionsSettlements(GameTestHelper helper) {
        RecordingPlayer member = recordingPlayer(helper);
        RecordingPlayer outsider = recordingPlayer(helper);
        RecordingPlayer factionless = recordingPlayer(helper);
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        FactionData.get(helper.getLevel()).join(member.getUUID(), blue);
        FactionData.get(helper.getLevel()).join(outsider.getUUID(), red);
        Settlement settlement = found(helper, HALL, blue);
        try {
            helper.assertTrue(NationRequests.settlementProblem(member, settlement) == null, "Expected a member to act on their own settlement");
            String outsiders = NationRequests.settlementProblem(outsider, settlement);
            helper.assertTrue(outsiders != null && outsiders.contains(settlement.name()), "Expected another faction's member refused: " + outsiders);
            helper.assertTrue(NationRequests.settlementProblem(factionless, settlement) != null, "Expected a factionless player refused");
            helper.assertTrue(NationRequests.settlementProblem(member, null) != null, "Expected a missing settlement refused");
        } finally {
            leaveAndRemove(helper, member);
            leaveAndRemove(helper, outsider);
            removePlayer(helper, factionless);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    // The test world keeps factions between runs, so a full faction list can be very long.
    private static List<String> firstLines(List<String> output) {
        return output.subList(0, Math.min(3, output.size()));
    }

    private static void leaveAndRemove(GameTestHelper helper, RecordingPlayer player) {
        FactionData.get(helper.getLevel()).leave(player.getUUID());
        removePlayer(helper, player);
    }
}
