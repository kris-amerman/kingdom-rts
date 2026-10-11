package com.krisamerman.kingdomrts.gametest;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.TownHallBlockEntity;
import com.krisamerman.kingdomrts.unit.MilitaryStats;
import com.krisamerman.kingdomrts.unit.MilitaryUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// Shared setup for GameTests. Positions are relative to the test: the framework puts a structure
// block at y = 0 and the "platform" template above it, so the template's stone floor is at y = 1
// and things stand at y = 2 (FLOOR_Y + 1).
//
// Factions and settlements are world-wide saved data, not per test area, so tests use unique
// faction names and remove their town halls when they finish (which dissolves the settlement).
//
// Running tests in-game: /test runall (everything) or /test run <method name, lowercase>.
// /test runall <class> does NOT work here: vanilla finds a class's tests by a "<class>." name
// prefix, and our tests are unprefixed (@PrefixGameTestTemplate(false)) so they can share one
// template. Prefixing would need a template copy per class; not worth it at this size.
final class TestSupport {
    static final String TEMPLATE = "platform";
    static final int FLOOR_Y = 1;
    static final BlockPos HALL = new BlockPos(8, FLOOR_Y + 1, 8);

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TestSupport() {
    }

    // A new faction with a unique name, e.g. "gt_Red_7".
    static Faction faction(GameTestHelper helper, String name) {
        return FactionData.get(helper.getLevel()).create("gt_" + name + "_" + COUNTER.incrementAndGet(), null);
    }

    static void setWar(GameTestHelper helper, Faction a, Faction b, boolean atWar) {
        FactionData.get(helper.getLevel()).setHostile(a, b, atWar);
    }

    // Places a town hall and founds a settlement there for the faction, through the same operation
    // the Claim button uses.
    static Settlement found(GameTestHelper helper, BlockPos relativePos, Faction faction) {
        clearLeftoverSettlements(helper, relativePos);
        helper.setBlock(relativePos, KingdomRts.TOWN_HALL.get());
        SettlementOps.FoundResult result = SettlementOps.found(helper.getLevel(), helper.absolutePos(relativePos), faction);
        if (result.settlement() == null) {
            throw new GameTestAssertException("Couldn't found a settlement: " + result.problem());
        }
        return result.settlement();
    }

    // A test that fails partway never removes its town hall, so its settlement would stay and make
    // every later test in the same spot fail with "Too close to ...", hiding the real failure.
    // Settlement batches run one at a time, so anything claiming this spot is left over: dissolve it.
    private static void clearLeftoverSettlements(GameTestHelper helper, BlockPos relativePos) {
        SettlementData settlements = SettlementData.get(helper.getLevel());
        for (ChunkPos chunk : SettlementOps.startingClaim(helper.absolutePos(relativePos))) {
            Settlement leftover = settlements.claimAt(helper.getLevel().dimension(), chunk);
            if (leftover != null) {
                SettlementOps.dissolve(helper.getLevel(), leftover);
            }
        }
    }

    // Removes a town hall, which dissolves its settlement, so the claim doesn't affect later tests.
    static void removeHall(GameTestHelper helper, BlockPos relativePos) {
        helper.setBlock(relativePos, Blocks.AIR);
    }

    // Spawns a military unit posted where it stands. ai = false gives a unit that still counts for
    // capture but never moves or fights, which keeps counting tests deterministic.
    static MilitaryUnit unit(GameTestHelper helper, BlockPos relativePos, @Nullable Faction faction, boolean ai) {
        MilitaryUnit unit = KingdomRts.MILITARY_UNIT.get().create(helper.getLevel());
        if (unit == null) {
            throw new GameTestAssertException("Couldn't create a military unit");
        }
        BlockPos pos = helper.absolutePos(relativePos);
        Vec3 feet = Vec3.atBottomCenterOf(pos);
        unit.moveTo(feet.x, feet.y, feet.z, 0.0f, 0.0f);
        unit.setup(faction != null ? faction.id() : null, pos, MilitaryStats.DEFAULT_ID);
        unit.setNoAi(!ai);
        helper.getLevel().addFreshEntity(unit);
        return unit;
    }

    // A fake server player in the test world, without operator rights (it counts as creative mode).
    // Remove it with removePlayer when done. Vanilla marks this helper deprecated for removal in a
    // later Minecraft version but offers no replacement in 1.21.1; this is the one place to change.
    @SuppressWarnings("removal")
    static ServerPlayer mockPlayer(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
    }

    static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    static TownHallBlockEntity hall(GameTestHelper helper, BlockPos relativePos) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(relativePos)) instanceof TownHallBlockEntity townHall) {
            return townHall;
        }
        throw new GameTestAssertException("No town hall at " + relativePos.toShortString());
    }

    static int control(GameTestHelper helper) {
        return hall(helper, HALL).getControl();
    }

    // The settlement's current state, looked up again (ownership changes on capture).
    static Settlement current(GameTestHelper helper, Settlement settlement) {
        Settlement current = SettlementData.get(helper.getLevel()).byId(settlement.id());
        if (current == null) {
            throw new GameTestAssertException("Settlement " + settlement.name() + " no longer exists");
        }
        return current;
    }
}
