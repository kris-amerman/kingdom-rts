package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.lastOpenedScreen;
import static com.krisamerman.kingdomrts.gametest.TestSupport.lumberjackHut;
import static com.krisamerman.kingdomrts.gametest.TestSupport.recordingPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import java.util.List;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.gametest.TestSupport.RecordingPlayer;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.TownHallMenu;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// The town hall panel's population page, used the way a player uses it: right-click the hall, read
// what the client was sent, press its buttons. Each test founds a settlement, so each has its own
// batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class TownHallPanelGameTests {
    private static final BlockPos HUT = new BlockPos(3, FLOOR_Y + 1, 3);

    @GameTest(template = TEMPLATE, batch = "hall_population")
    public static void populationListsEveryoneWithTheirJobs(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        WorkplaceOps.setAutoHire(helper.getLevel(), WorkplaceOps.workplaceAt(helper.getLevel(), helper.absolutePos(HUT)), false);
        Unit worker = settler(helper, settlement, blue, HALL.east(2), null);
        WorkplaceOps.hire(helper.getLevel(), worker, helper.absolutePos(HUT));
        Unit soldier = settler(helper, settlement, blue, HALL.east(3), Occupation.SOLDIER);
        Unit idle = settler(helper, settlement, blue, HALL.east(4), null);
        unit(helper, HALL.south(2), blue, null, false); // in the faction, but not the settlement
        RecordingPlayer player = playerAtHall(helper, blue);
        try {
            TownHallMenu.HallInfo overview = rightClick(helper, player).getInfo();
            helper.assertTrue(overview.member() && overview.population() == 3 && overview.people().isEmpty(),
                    "Expected the overview to count 3 settlers (and not list them yet): " + overview);
            press(player, TownHallMenu.SHOW_POPULATION_BUTTON);
            assertNoScreenClosed(helper, player);
            List<TownHallMenu.Person> people = clientMenu(helper, player).getInfo().people();
            helper.assertTrue(people.size() == 3, "Expected 3 people, got " + people);
            helper.assertTrue(people.stream().map(TownHallMenu.Person::name).toList()
                    .equals(people.stream().map(TownHallMenu.Person::name).sorted(String.CASE_INSENSITIVE_ORDER).toList()), "Expected them by name: " + people);
            TownHallMenu.Person lumberjack = person(people, worker);
            helper.assertTrue(lumberjack.occupation().equals(Occupation.LUMBERJACK.toString()) && lumberjack.name().equals(worker.getName().getString()),
                    "Unexpected lumberjack row: " + lumberjack);
            helper.assertTrue(person(people, soldier).occupation().equals(Occupation.SOLDIER.toString()), "Unexpected soldier row: " + person(people, soldier));
            helper.assertTrue(person(people, idle).occupation().isEmpty(), "Expected the idle settler unemployed: " + person(people, idle));
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "hall_population_away")
    public static void unitsAwayAreListedAsLastSeen(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        Unit worker = settler(helper, settlement, blue, HALL.east(2), null);
        WorkplaceOps.hire(helper.getLevel(), worker, helper.absolutePos(HUT));
        String name = worker.getName().getString();
        worker.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); // as if its chunk unloaded: it still belongs
        RecordingPlayer player = playerAtHall(helper, blue);
        try {
            rightClick(helper, player);
            press(player, TownHallMenu.SHOW_POPULATION_BUTTON);
            List<TownHallMenu.Person> people = clientMenu(helper, player).getInfo().people();
            helper.assertTrue(people.size() == 1, "Expected the unloaded worker still listed: " + people);
            TownHallMenu.Person away = people.get(0);
            helper.assertTrue(away.name().equals(name) && away.occupation().equals(Occupation.LUMBERJACK.toString()),
                    "Expected it listed as last seen: " + away);
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "hall_population_pages")
    public static void populationComesAPageAtATime(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        for (int i = 0; i < TownHallMenu.PEOPLE_PER_PAGE + 2; i++) {
            settler(helper, settlement, blue, HALL.offset(i - 3, 0, 3), null);
        }
        RecordingPlayer player = playerAtHall(helper, blue);
        try {
            rightClick(helper, player);
            press(player, TownHallMenu.SHOW_POPULATION_BUTTON);
            TownHallMenu.HallInfo info = clientMenu(helper, player).getInfo();
            helper.assertTrue(info.pages() == 2 && info.pagePeople().size() == TownHallMenu.PEOPLE_PER_PAGE, "Expected a full first page of two: " + info);
            press(player, TownHallMenu.NEXT_PAGE_BUTTON);
            info = clientMenu(helper, player).getInfo();
            helper.assertTrue(info.page() == 1 && info.pagePeople().size() == 2, "Expected the last two on page 2: " + info);
            press(player, TownHallMenu.SHOW_OVERVIEW_BUTTON);
            helper.assertTrue(clientMenu(helper, player).getInfo().view() == TownHallMenu.View.OVERVIEW, "Back should show the overview");
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "hall_population_live")
    public static void openPopulationUpdatesWhenSomeoneGetsAJob(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        WorkplaceOps.setAutoHire(helper.getLevel(), WorkplaceOps.workplaceAt(helper.getLevel(), helper.absolutePos(HUT)), false);
        Unit settler = settler(helper, settlement, blue, HALL.east(2), null);
        RecordingPlayer player = playerAtHall(helper, blue);
        rightClick(helper, player);
        press(player, TownHallMenu.SHOW_POPULATION_BUTTON);
        WorkplaceOps.hire(helper.getLevel(), settler, helper.absolutePos(HUT));
        Unit newcomer = settler(helper, settlement, blue, HALL.east(4), null);
        player.sent.clear();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    List<TownHallMenu.Person> people = clientMenu(helper, player).getInfo().people();
                    helper.assertTrue(people.size() == 2 && person(people, settler).occupation().equals(Occupation.LUMBERJACK.toString())
                            && person(people, newcomer) != null, "Waiting for the open page to show the new job and the newcomer");
                })
                .thenExecute(() -> {
                    helper.assertTrue(clientMenu(helper, player).getInfo().view() == TownHallMenu.View.POPULATION, "It should stay on the population page");
                    assertNoScreenClosed(helper, player);
                    leave(helper, player);
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "hall_population_outsider")
    public static void outsidersDontSeeThePopulation(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Settlement settlement = found(helper, HALL, blue);
        settler(helper, settlement, blue, HALL.east(2), null);
        RecordingPlayer outsider = playerAtHall(helper, red);
        try {
            TownHallMenu.HallInfo info = rightClick(helper, outsider).getInfo();
            helper.assertTrue(!info.member() && info.population() == 0 && info.people().isEmpty() && info.settlementName().equals(settlement.name()),
                    "An outsider sees the overview without the population: " + info);
            helper.assertFalse(((TownHallMenu) outsider.containerMenu).clickMenuButton(outsider, TownHallMenu.SHOW_POPULATION_BUTTON),
                    "An outsider can't open the population page");
        } finally {
            leave(helper, outsider);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    private static TownHallMenu.Person person(List<TownHallMenu.Person> people, Unit unit) {
        return people.stream().filter(person -> person.id().equals(unit.getUUID())).findFirst().orElse(null);
    }

    // A unit of the faction that belongs to the settlement, with the given occupation (null for none).
    private static Unit settler(GameTestHelper helper, Settlement settlement, Faction faction, BlockPos pos, @Nullable ResourceLocation occupation) {
        Unit unit = unit(helper, pos, faction, occupation, false);
        SettlementOps.setUnitSettlement(helper.getLevel(), unit, settlement);
        return unit;
    }

    // A player of the faction, standing next to the hall.
    private static RecordingPlayer playerAtHall(GameTestHelper helper, Faction faction) {
        RecordingPlayer player = recordingPlayer(helper);
        FactionData.get(helper.getLevel()).join(player.getUUID(), faction);
        Vec3 next = Vec3.atBottomCenterOf(helper.absolutePos(HALL.west()));
        player.teleportTo(next.x, next.y, next.z);
        return player;
    }

    private static void leave(GameTestHelper helper, RecordingPlayer player) {
        FactionData.get(helper.getLevel()).leave(player.getUUID());
        removePlayer(helper, player);
    }

    // Right-clicks the hall and returns the panel as the client built it.
    private static TownHallMenu rightClick(GameTestHelper helper, RecordingPlayer player) {
        player.sent.clear();
        BlockPos pos = helper.absolutePos(HALL);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return clientMenu(helper, player);
    }

    private static TownHallMenu clientMenu(GameTestHelper helper, RecordingPlayer player) {
        if (!(player.containerMenu instanceof TownHallMenu)) {
            throw new GameTestAssertException("The town hall panel isn't open; got " + player.containerMenu);
        }
        TestSupport.OpenedScreen opened = lastOpenedScreen(helper, player);
        return new TownHallMenu(opened.windowId(), player.getInventory(), opened.data());
    }

    // Presses a panel button, as the client does: the server checks the menu is still valid first.
    private static void press(RecordingPlayer player, int button) {
        if (!(player.containerMenu instanceof TownHallMenu menu) || !menu.stillValid(player)) {
            throw new GameTestAssertException("The town hall panel isn't open and valid");
        }
        player.sent.clear();
        menu.clickMenuButton(player, button);
    }

    // Switching panels must not close the client's screen first (that re-centres the mouse).
    private static void assertNoScreenClosed(GameTestHelper helper, RecordingPlayer player) {
        helper.assertFalse(player.sent.stream().anyMatch(packet -> packet instanceof ClientboundContainerClosePacket),
                "The client was told to close its screen, which re-centres the mouse");
    }
}
