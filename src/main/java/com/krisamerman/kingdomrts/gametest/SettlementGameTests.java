package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;
import com.krisamerman.kingdomrts.settlement.SettlementOps;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Founding settlements and their claims. Each test has its own batch so claims never overlap.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class SettlementGameTests {
    @GameTest(template = TEMPLATE, batch = "settlement_overlap")
    public static void overlappingClaimRefused(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        found(helper, HALL, blue);
        BlockPos second = new BlockPos(2, FLOOR_Y + 1, 2); // well inside the first settlement's claim
        helper.setBlock(second, KingdomRts.TOWN_HALL.get());
        String problem = SettlementOps.foundingProblem(helper.getLevel(), helper.absolutePos(second));
        helper.assertTrue(problem != null && problem.startsWith("Too close"), "Expected an overlap refusal, got: " + problem);
        removeHall(helper, second);
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "settlement_dissolve")
    public static void hallRemovalDissolves(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        removeHall(helper, HALL);
        SettlementData settlements = SettlementData.get(helper.getLevel());
        helper.assertTrue(settlements.byId(settlement.id()) == null, "Expected the settlement to be dissolved");
        helper.assertTrue(settlements.claimAt(helper.getLevel().dimension(), new ChunkPos(helper.absolutePos(HALL))) == null,
                "Expected the claim to be released");
        helper.succeed();
    }
}
