package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.current;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.lastPayload;
import static com.krisamerman.kingdomrts.gametest.TestSupport.recordingPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionColors;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.gametest.TestSupport.RecordingPlayer;
import com.krisamerman.kingdomrts.nation.BorderViewers;
import com.krisamerman.kingdomrts.nation.NationPayloads;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The borders view, as the server answers it: which claims a player is sent, and that viewers are
// sent them again when claims change or they move on, and not otherwise. Each test founds a
// settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class NationBordersGameTests {
    // Long enough for the next send to be allowed.
    private static final int AFTER_INTERVAL = BorderViewers.SEND_INTERVAL_TICKS + 1;

    // Borders are public: a member, an outsider and a player with no faction are all sent the
    // settlement's whole claim, in its owner's colour; only the member's is marked as their own.
    @GameTest(template = TEMPLATE, batch = "nation_borders_sent")
    public static void aViewerIsSentTheClaimsAroundThem(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Settlement settlement = found(helper, HALL, blue);
        RecordingPlayer member = member(helper, blue);
        RecordingPlayer outsider = member(helper, red);
        RecordingPlayer factionless = recordingPlayer(helper);
        try {
            NationPayloads.BorderView own = viewOf(helper, BorderViewers.bordersFor(standAtHall(helper, member)), settlement);
            helper.assertTrue(own.own() && own.faction().equals(blue.name()) && own.color() == FactionColors.of(blue.id()),
                    "Expected Blue's own claim in Blue's colour: " + describe(own));
            Set<ChunkPos> sent = Arrays.stream(own.chunks()).mapToObj(ChunkPos::new).collect(Collectors.toSet());
            helper.assertTrue(sent.equals(current(helper, settlement).chunks()), "Expected the whole claim, got " + sent);
            NationPayloads.BorderView theirs = viewOf(helper, BorderViewers.bordersFor(standAtHall(helper, outsider)), settlement);
            helper.assertTrue(!theirs.own() && theirs.faction().equals(blue.name()), "Expected an outsider shown Blue's claim as Blue's: " + describe(theirs));
            NationPayloads.BorderView anyone = viewOf(helper, BorderViewers.bordersFor(standAtHall(helper, factionless)), settlement);
            helper.assertTrue(!anyone.own(), "Expected a player without a faction shown it too");

            BlockPos hall = helper.absolutePos(HALL);
            member.teleportTo(hall.getX() + (BorderViewers.RANGE_CHUNKS + 3) * 16, hall.getY(), hall.getZ());
            helper.assertTrue(BorderViewers.bordersFor(member).borders().stream().noneMatch(view -> view.settlement().equals(settlement.name())),
                    "Expected nothing from a claim out of range");
        } finally {
            leaveAndRemove(helper, member);
            leaveAndRemove(helper, outsider);
            removePlayer(helper, factionless);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    // A viewer is sent the borders when they switch the view on, not again while nothing changes,
    // again once the settlement changes hands, and never once they've switched it off.
    @GameTest(template = TEMPLATE, batch = "nation_borders_changes", timeoutTicks = 200)
    public static void viewersAreSentBordersAgainWhenClaimsChange(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Settlement settlement = found(helper, HALL, blue);
        RecordingPlayer player = member(helper, blue);
        standAtHall(helper, player);
        BorderViewers.toggle(player, true);
        helper.assertTrue(lastPayload(player, NationPayloads.ShowBorders.class) != null, "Expected the borders on switching the view on");
        player.sent.clear();
        helper.runAfterDelay(AFTER_INTERVAL, () -> {
            BorderViewers.check(helper.getLevel().getServer());
            if (lastPayload(player, NationPayloads.ShowBorders.class) != null) {
                fail(helper, player, "Expected nothing new while nothing changed");
            }
            SettlementOps.transfer(helper.getLevel(), current(helper, settlement), red.id());
            helper.runAfterDelay(AFTER_INTERVAL, () -> {
                BorderViewers.check(helper.getLevel().getServer());
                NationPayloads.ShowBorders again = lastPayload(player, NationPayloads.ShowBorders.class);
                NationPayloads.BorderView view = again != null ? viewOf(helper, again, settlement) : null;
                if (view == null || view.own() || !view.faction().equals(red.name())) {
                    fail(helper, player, "Expected the claim sent again as Red's, got " + (view != null ? describe(view) : null));
                }
                BorderViewers.toggle(player, false);
                player.sent.clear();
                SettlementOps.transfer(helper.getLevel(), current(helper, settlement), blue.id());
                helper.runAfterDelay(AFTER_INTERVAL, () -> {
                    BorderViewers.check(helper.getLevel().getServer());
                    if (lastPayload(player, NationPayloads.ShowBorders.class) != null || BorderViewers.isViewing(player)) {
                        fail(helper, player, "Expected nothing once the view is off");
                    }
                    leaveAndRemove(helper, player);
                    removeHall(helper, HALL);
                    helper.succeed();
                });
            });
        });
    }

    // Switching the view off and on again at once isn't answered at once (one send per interval),
    // but by the next check; and a viewer who walks a few chunks on is sent the claims around them.
    @GameTest(template = TEMPLATE, batch = "nation_borders_moving", timeoutTicks = 200)
    public static void viewersAreSentBordersAsTheyMoveButNotTooOften(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        found(helper, HALL, blue);
        RecordingPlayer player = member(helper, blue);
        standAtHall(helper, player);
        BorderViewers.toggle(player, true);
        player.sent.clear();
        BorderViewers.toggle(player, false);
        BorderViewers.toggle(player, true);
        if (lastPayload(player, NationPayloads.ShowBorders.class) != null) {
            fail(helper, player, "Expected switching it on again at once not to be answered at once");
        }
        helper.runAfterDelay(AFTER_INTERVAL, () -> {
            BorderViewers.check(helper.getLevel().getServer());
            if (lastPayload(player, NationPayloads.ShowBorders.class) == null) {
                fail(helper, player, "Expected the borders by the next check");
            }
            player.sent.clear();
            BlockPos hall = helper.absolutePos(HALL);
            player.teleportTo(hall.getX() + BorderViewers.MOVE_CHUNKS * 16 + 8, hall.getY(), hall.getZ());
            helper.runAfterDelay(AFTER_INTERVAL, () -> {
                BorderViewers.check(helper.getLevel().getServer());
                if (lastPayload(player, NationPayloads.ShowBorders.class) == null) {
                    fail(helper, player, "Expected the borders again after moving " + BorderViewers.MOVE_CHUNKS + " chunks on");
                }
                BorderViewers.toggle(player, false);
                leaveAndRemove(helper, player);
                removeHall(helper, HALL);
                helper.succeed();
            });
        });
    }

    private static void fail(GameTestHelper helper, RecordingPlayer player, String message) {
        BorderViewers.toggle(player, false);
        leaveAndRemove(helper, player);
        removeHall(helper, HALL);
        throw new GameTestAssertException(message);
    }

    private static NationPayloads.BorderView viewOf(GameTestHelper helper, NationPayloads.ShowBorders shown, Settlement settlement) {
        return shown.borders().stream().filter(view -> view.settlement().equals(settlement.name())).findFirst()
                .orElseThrow(() -> new GameTestAssertException("Expected " + settlement.name() + " in " + shown.borders().stream().map(NationPayloads.BorderView::settlement).toList()));
    }

    private static String describe(NationPayloads.BorderView view) {
        return view.settlement() + " (" + view.faction() + ", #" + Integer.toHexString(view.color()) + (view.own() ? ", own" : "") + ", "
                + view.chunks().length + " chunks)";
    }

    private static RecordingPlayer standAtHall(GameTestHelper helper, RecordingPlayer player) {
        BlockPos pos = helper.absolutePos(HALL);
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5);
        return player;
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
