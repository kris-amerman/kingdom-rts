package com.krisamerman.kingdomrts.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.TownHallBlockEntity;
import com.krisamerman.kingdomrts.unit.Occupation;
import com.krisamerman.kingdomrts.unit.Unit;
import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
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

    // Spawns a soldier posted where it stands. ai = false gives a unit that still counts for
    // capture but never moves or fights, which keeps counting tests deterministic.
    static Unit unit(GameTestHelper helper, BlockPos relativePos, @Nullable Faction faction, boolean ai) {
        return unit(helper, relativePos, faction, Occupation.SOLDIER, ai);
    }

    // Spawns a unit with the given occupation (null for none), posted where it stands.
    static Unit unit(GameTestHelper helper, BlockPos relativePos, @Nullable Faction faction,
            @Nullable ResourceLocation occupation, boolean ai) {
        Unit unit = KingdomRts.UNIT.get().create(helper.getLevel());
        if (unit == null) {
            throw new GameTestAssertException("Couldn't create a unit");
        }
        BlockPos pos = helper.absolutePos(relativePos);
        Vec3 feet = Vec3.atBottomCenterOf(pos);
        unit.moveTo(feet.x, feet.y, feet.z, 0.0f, 0.0f);
        unit.setup(faction != null ? faction.id() : null, pos, occupation);
        unit.setNoAi(!ai);
        helper.getLevel().addFreshEntity(unit);
        return unit;
    }

    // Runs a command as an operator standing at relativePos (so selectors like @e[distance=..3]
    // and ~ ~ ~ work from there), and returns everything it printed, failures included.
    static List<String> runCommand(GameTestHelper helper, BlockPos relativePos, String command) {
        List<String> output = new ArrayList<>();
        CommandSource recorder = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                output.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = level.getServer().createCommandSourceStack()
                .withSource(recorder)
                .withLevel(level)
                .withPosition(Vec3.atBottomCenterOf(helper.absolutePos(relativePos)))
                .withPermission(4);
        level.getServer().getCommands().performPrefixedCommand(source, command);
        return output;
    }

    // A fake server player in the test world, without operator rights (it counts as creative mode).
    // Remove it with removePlayer when done. Vanilla marks this helper deprecated for removal in a
    // later Minecraft version but offers no replacement in 1.21.1; this is the one place to change.
    @SuppressWarnings("removal")
    static ServerPlayer mockPlayer(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
    }

    // A fake server player that records its action-bar messages, so tests can check what a player
    // would see. Built the same way as vanilla's mock player. Remove it with removePlayer when done.
    static RecordingPlayer recordingPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-recorder"), false);
        RecordingPlayer player = new RecordingPlayer(helper.getLevel(), cookie);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection); // a do-nothing network channel
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    static final class RecordingPlayer extends ServerPlayer {
        final List<String> actionBar = new ArrayList<>();

        RecordingPlayer(ServerLevel level, CommonListenerCookie cookie) {
            super(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            if (actionBar) {
                this.actionBar.add(message.getString());
            }
            super.displayClientMessage(message, actionBar);
        }
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
