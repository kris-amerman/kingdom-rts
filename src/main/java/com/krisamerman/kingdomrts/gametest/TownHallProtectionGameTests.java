package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.mockPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// A claimed town hall can't be destroyed in survival; an unclaimed one breaks normally.
// Tests that found a settlement have their own batch so claims never overlap.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class TownHallProtectionGameTests {
    // Something an explosion between it and the hall certainly destroys, proving the blast happened.
    private static final BlockPos DIRT = new BlockPos(11, FLOOR_Y + 1, 8);
    private static final float TNT_POWER = 4.0f;

    @GameTest(template = TEMPLATE, batch = "protection_explosion")
    public static void claimedHallSurvivesExplosion(GameTestHelper helper) {
        Settlement settlement = found(helper, HALL, faction(helper, "Blue"));
        helper.setBlock(DIRT, Blocks.DIRT);
        explodeNextToHall(helper);
        helper.assertBlockPresent(Blocks.AIR, DIRT);
        helper.assertBlockPresent(KingdomRts.TOWN_HALL.get(), HALL);
        helper.assertTrue(SettlementData.get(helper.getLevel()).byId(settlement.id()) != null, "Expected the settlement to survive");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unclaimedHallIsDestroyedByExplosion(GameTestHelper helper) {
        helper.setBlock(HALL, KingdomRts.TOWN_HALL.get());
        explodeNextToHall(helper);
        helper.assertBlockNotPresent(KingdomRts.TOWN_HALL.get(), HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "protection_player")
    public static void playerCantBreakClaimedHall(GameTestHelper helper) {
        Settlement settlement = found(helper, HALL, faction(helper, "Blue"));
        BlockPos hall = helper.absolutePos(HALL);
        // A mock player without operator rights (the mock counts as creative, so this also checks
        // that creative mode alone isn't enough).
        ServerPlayer player = mockPlayer(helper);
        try {
            BlockState state = helper.getLevel().getBlockState(hall);
            helper.assertTrue(state.getDestroyProgress(player, helper.getLevel(), hall) == 0.0f,
                    "Expected mining a claimed hall to make no progress");
            player.gameMode.destroyBlock(hall);
            helper.assertBlockPresent(KingdomRts.TOWN_HALL.get(), HALL);
            helper.assertTrue(SettlementData.get(helper.getLevel()).byId(settlement.id()) != null, "Expected the settlement to survive");
        } finally {
            removePlayer(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void playerCanBreakUnclaimedHall(GameTestHelper helper) {
        helper.setBlock(HALL, KingdomRts.TOWN_HALL.get());
        ServerPlayer player = mockPlayer(helper);
        try {
            player.gameMode.destroyBlock(helper.absolutePos(HALL));
            helper.assertBlockNotPresent(KingdomRts.TOWN_HALL.get(), HALL);
        } finally {
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    // A TNT-strength explosion between the hall and the dirt block.
    private static void explodeNextToHall(GameTestHelper helper) {
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(HALL.east()));
        helper.getLevel().explode(null, center.x, center.y, center.z, TNT_POWER, Level.ExplosionInteraction.TNT);
    }
}
