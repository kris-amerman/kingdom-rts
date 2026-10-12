package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.recordingPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;

import java.util.List;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Territory messages: crossing a claim's edge shows one "Entering" or "Leaving" message.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class TerritoryGameTests {
    @GameTest(template = TEMPLATE, batch = "territory_messages")
    public static void crossingClaimEdgeShowsMessages(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        BlockPos hall = helper.absolutePos(HALL);
        // A spot one chunk beyond the edge of the starting claim, at the hall's height.
        ChunkPos hallChunk = new ChunkPos(hall);
        int outsideX = (hallChunk.x + SettlementOps.STARTING_CLAIM_RADIUS + 1) * 16 + 8;
        int insideX = hall.getX();
        double y = hall.getY();
        double z = hall.getZ() + 0.5;

        TestSupport.RecordingPlayer player = recordingPlayer(helper);
        try {
            player.teleportTo(outsideX + 0.5, y, z);
            player.actionBar.clear(); // ignore whatever moving here from spawn produced

            player.teleportTo(insideX + 0.5, y, z);
            expect(helper, player.actionBar, "Entering " + settlement.name() + " (" + blue.name() + ")");

            player.teleportTo(insideX + 4.5, y, z); // still inside the claim
            expect(helper, player.actionBar);

            player.teleportTo(outsideX + 0.5, y, z);
            expect(helper, player.actionBar, "Leaving " + settlement.name());
        } finally {
            removePlayer(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    // Checks the messages since the last check, then clears them.
    private static void expect(GameTestHelper helper, List<String> actionBar, String... expected) {
        helper.assertTrue(actionBar.equals(List.of(expected)), "Expected action bar " + List.of(expected) + ", got " + actionBar);
        actionBar.clear();
    }
}
