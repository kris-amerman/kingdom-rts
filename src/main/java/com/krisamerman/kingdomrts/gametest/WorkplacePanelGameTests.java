package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.current;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.lastOpenedScreen;
import static com.krisamerman.kingdomrts.gametest.TestSupport.lumberjackHut;
import static com.krisamerman.kingdomrts.gametest.TestSupport.recordingPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import java.util.List;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.gametest.TestSupport.RecordingPlayer;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.WorkArea;
import com.krisamerman.kingdomrts.settlement.Workplace;
import com.krisamerman.kingdomrts.settlement.WorkplaceMenu;
import com.krisamerman.kingdomrts.settlement.WorkplaceOps;
import com.krisamerman.kingdomrts.settlement.WorkplaceStockMenu;
import com.krisamerman.kingdomrts.settlement.WorkplaceType;
import com.krisamerman.kingdomrts.unit.Occupation;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;

// The hut panel, used the way a player uses it: right-click the hut, read the panel the client was
// sent, and press its buttons. Each test founds a settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class WorkplacePanelGameTests {
    private static final BlockPos HUT = new BlockPos(4, FLOOR_Y + 1, 4);

    @GameTest(template = TEMPLATE, batch = "hut_panel_info")
    public static void panelShowsTheHutAndWhoCouldWorkThere(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        Unit worker = settler(helper, settlement, blue, new BlockPos(12, FLOOR_Y + 1, 12));
        WorkplaceOps.hire(helper.getLevel(), worker, helper.absolutePos(HUT));
        Unit near = settler(helper, settlement, blue, HUT.east(2));
        Unit far = settler(helper, settlement, blue, HUT.east(9));
        Unit soldier = unit(helper, HUT.south(3), blue, false);
        SettlementOps.setUnitSettlement(helper.getLevel(), soldier, settlement);
        unit(helper, HUT.south(2), blue, null, false); // no settlement

        RecordingPlayer player = ownerAtHut(helper, blue);
        try {
            WorkplaceMenu.WorkplaceInfo info = rightClick(helper, player).getInfo();
            helper.assertTrue(info.settlementName().equals(settlement.name()) && info.occupation().equals(Occupation.LUMBERJACK.toString())
                    && info.capacity() == 1, "Unexpected details: " + info);
            helper.assertTrue(info.workers().size() == 1 && info.workers().get(0).name().equals(worker.getName().getString()),
                    "Expected the worker by name: " + info.workers());
            helper.assertTrue(info.candidates().stream().map(WorkplaceMenu.Person::id).toList().equals(List.of(near.getUUID(), far.getUUID())),
                    "Expected the two unemployed settlers, nearest first (not the worker, soldier or outsider): " + info.candidates());
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "hut_panel_hire_fire")
    public static void hireAndFireButtons(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        Unit settler = settler(helper, settlement, blue, HUT.east(2));
        RecordingPlayer player = ownerAtHut(helper, blue);
        try {
            helper.assertTrue(rightClick(helper, player).getInfo().view() == WorkplaceMenu.View.OVERVIEW, "The panel opens on its overview");
            press(player, WorkplaceMenu.SHOW_WORKERS_BUTTON);
            helper.assertTrue(clientMenu(helper, player).getInfo().view() == WorkplaceMenu.View.WORKERS, "The Workers button opens the workers page");
            press(player, WorkplaceMenu.HIRE_BUTTON); // the first candidate
            helper.assertTrue(Occupation.LUMBERJACK.equals(settler.getOccupationId()), "The Hire button should hire the settler");
            assertNoScreenClosed(helper, player);
            WorkplaceMenu refreshed = clientMenu(helper, player);
            helper.assertTrue(refreshed.getInfo().workers().size() == 1 && refreshed.getInfo().candidates().isEmpty(),
                    "The panel should refresh to show the new worker");
            helper.assertTrue(refreshed.getInfo().view() == WorkplaceMenu.View.WORKERS, "Hiring should keep the workers page open");

            press(player, WorkplaceMenu.FIRE_BUTTON);
            helper.assertTrue(settler.getOccupationId() == null, "The Fire button should fire the worker");
            WorkplaceMenu.WorkplaceInfo afterFiring = clientMenu(helper, player).getInfo();
            helper.assertTrue(afterFiring.workers().isEmpty(), "The panel should refresh to show nobody working");
            helper.assertFalse(afterFiring.autoHire(), "Firing from the panel should switch the hut to manual hiring");
            press(player, WorkplaceMenu.SHOW_OVERVIEW_BUTTON);
            helper.assertTrue(clientMenu(helper, player).getInfo().view() == WorkplaceMenu.View.OVERVIEW, "Back returns to the overview");
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "hut_panel_auto_hire")
    public static void automaticHiringToggle(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        RecordingPlayer player = ownerAtHut(helper, blue);
        try {
            helper.assertTrue(rightClick(helper, player).getInfo().autoHire(), "Huts hire automatically by default");
            press(player, WorkplaceMenu.AUTO_HIRE_BUTTON);
            helper.assertFalse(clientMenu(helper, player).getInfo().autoHire(), "The toggle should switch it off");
            helper.assertFalse(current(helper, settlement).workplaceAt(helper.absolutePos(HUT)).autoHire(), "...and the hut should remember");
            press(player, WorkplaceMenu.AUTO_HIRE_BUTTON);
            helper.assertTrue(clientMenu(helper, player).getInfo().autoHire(), "...and back on");
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    // The panel is left open while a settler takes the job by automatic hiring: the panel updates by
    // itself, without the viewer pressing anything, and without closing the screen first.
    @GameTest(template = TEMPLATE, batch = "hut_panel_live", timeoutTicks = Unit.JOB_SEEK_INTERVAL_TICKS + 100)
    public static void openPanelUpdatesWhenSomeoneIsHiredAutomatically(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        Unit settler = settler(helper, settlement, blue, HUT.east(3));
        RecordingPlayer player = ownerAtHut(helper, blue);
        helper.assertTrue(rightClick(helper, player).getInfo().workers().isEmpty(), "Nobody works here yet");
        player.sent.clear();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(settler.getOccupationId() != null, "Waiting for automatic hiring"))
                .thenWaitUntil(() -> helper.assertTrue(clientMenu(helper, player).getInfo().workers().size() == 1,
                        "Waiting for the open panel to show the new worker"))
                .thenExecute(() -> {
                    assertNoScreenClosed(helper, player);
                    leave(helper, player);
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    // Settlers moving about (so their nearest-first order changes) don't make an open panel re-open
    // itself when nothing it shows has changed. The nearest and farthest settlers swap places midway.
    @GameTest(template = TEMPLATE, batch = "hut_panel_quiet", timeoutTicks = 200)
    public static void openPanelStaysPutWhenNothingChanges(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        WorkplaceOps.setAutoHire(helper.getLevel(), current(helper, settlement).workplaceAt(helper.absolutePos(HUT)), false);
        Unit nearest = settler(helper, settlement, blue, HUT.east(2));
        settler(helper, settlement, blue, HUT.east(5));
        Unit farthest = settler(helper, settlement, blue, HUT.east(8));
        RecordingPlayer player = ownerAtHut(helper, blue);
        rightClick(helper, player);
        player.sent.clear();
        helper.startSequence()
                .thenIdle(2 * WorkplaceMenu.REFRESH_CHECK_TICKS)
                .thenExecute(() -> {
                    Vec3 a = nearest.position();
                    Vec3 b = farthest.position();
                    nearest.teleportTo(b.x, b.y, b.z);
                    farthest.teleportTo(a.x, a.y, a.z);
                })
                .thenIdle(3 * WorkplaceMenu.REFRESH_CHECK_TICKS)
                .thenExecute(() -> {
                    helper.assertFalse(player.sent.stream().anyMatch(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                            && custom.payload() instanceof AdvancedOpenScreenPayload), "The panel re-opened although nothing it shows changed");
                    leave(helper, player);
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "hut_panel_work_area")
    public static void workAreaButtons(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        RecordingPlayer player = ownerAtHut(helper, blue);
        try {
            WorkplaceMenu.WorkplaceInfo info = rightClick(helper, player).getInfo();
            helper.assertTrue(info.workRadius() == WorkplaceType.DEFAULT_WORK_RADIUS, "Expected the starting work area from data");
            press(player, WorkplaceMenu.LARGER_AREA_BUTTON);
            helper.assertTrue(clientMenu(helper, player).getInfo().workRadius() == info.workRadius() + WorkArea.RADIUS_STEP, "+ should grow it");
            for (int i = 0; i < 20; i++) {
                press(player, WorkplaceMenu.SMALLER_AREA_BUTTON);
            }
            helper.assertTrue(clientMenu(helper, player).getInfo().workRadius() == WorkArea.MIN_RADIUS, "- should stop at the minimum");
            helper.assertTrue(current(helper, settlement).workplaceAt(helper.absolutePos(HUT)).workRadius() == WorkArea.MIN_RADIUS,
                    "The hut should remember its work area");
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "hut_panel_job_settings")
    public static void jobSettingsButtons(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        RecordingPlayer player = ownerAtHut(helper, blue);
        ResourceLocation oak = BuiltInRegistries.BLOCK.getKey(Blocks.OAK_LOG);
        try {
            helper.assertTrue(rightClick(helper, player).getInfo().jobSettings(), "A lumberjack hut should offer job settings");
            press(player, WorkplaceMenu.SHOW_SETTINGS_BUTTON);
            assertNoScreenClosed(helper, player);
            WorkplaceMenu.WorkplaceInfo info = clientMenu(helper, player).getInfo();
            helper.assertTrue(info.view() == WorkplaceMenu.View.SETTINGS && info.replant(), "Expected the settings page, replanting on");
            List<ResourceLocation> woods = info.woods().stream().map(WorkplaceMenu.Wood::id).toList();
            helper.assertTrue(woods.contains(oak) && woods.contains(BuiltInRegistries.BLOCK.getKey(Blocks.CHERRY_LOG))
                    && info.woods().stream().allMatch(WorkplaceMenu.Wood::felled), "Expected every known wood, all felled: " + info.woods());

            press(player, WorkplaceMenu.REPLANT_BUTTON);
            press(player, WorkplaceMenu.WOOD_BUTTON + woods.indexOf(oak));
            info = clientMenu(helper, player).getInfo();
            helper.assertTrue(info.view() == WorkplaceMenu.View.SETTINGS && !info.replant(), "Replanting should be off, on the same page");
            helper.assertFalse(info.woods().get(woods.indexOf(oak)).felled(), "Oak should be left standing");
            Workplace workplace = current(helper, settlement).workplaceAt(helper.absolutePos(HUT));
            helper.assertTrue(!workplace.replant() && !workplace.fells(oak) && workplace.fells(BuiltInRegistries.BLOCK.getKey(Blocks.BIRCH_LOG)),
                    "The hut should remember its settings (and only oak is skipped)");

            press(player, WorkplaceMenu.WOOD_BUTTON + woods.indexOf(oak));
            helper.assertTrue(current(helper, settlement).workplaceAt(helper.absolutePos(HUT)).fells(oak), "Pressing again fells oak again");
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "hut_panel_full")
    public static void hireButtonRespectsCapacity(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        WorkplaceOps.hire(helper.getLevel(), settler(helper, settlement, blue, HUT.east(5)), helper.absolutePos(HUT));
        Unit second = settler(helper, settlement, blue, HUT.east(2));
        RecordingPlayer player = ownerAtHut(helper, blue);
        try {
            helper.assertTrue(rightClick(helper, player).getInfo().isFull(), "The panel should know the hut is full");
            press(player, WorkplaceMenu.HIRE_BUTTON);
            helper.assertTrue(second.getOccupationId() == null, "A full hut can't hire");
            helper.assertTrue(player.actionBar.stream().anyMatch(line -> line.contains("full")), "Expected a message saying it's full: " + player.actionBar);
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "hut_panel_unloaded")
    public static void firingAnUnloadedWorker(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        Unit worker = settler(helper, settlement, blue, HUT.east(2));
        WorkplaceOps.hire(helper.getLevel(), worker, helper.absolutePos(HUT));
        CompoundTag saved = worker.saveWithoutId(new CompoundTag());
        worker.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); // its chunk unloaded
        RecordingPlayer player = ownerAtHut(helper, blue);
        try {
            helper.assertTrue(rightClick(helper, player).getInfo().workers().size() == 1, "An unloaded worker is still listed, by name");
            press(player, WorkplaceMenu.FIRE_BUTTON);
            helper.assertTrue(current(helper, settlement).workplaceAt(helper.absolutePos(HUT)).workers().isEmpty(), "Expected it fired");
        } finally {
            leave(helper, player);
        }
        Unit back = KingdomRts.UNIT.get().create(helper.getLevel());
        back.load(saved);
        helper.getLevel().addFreshEntity(back);
        helper.assertTrue(back.getOccupationId() == null && back.getWorkplace() == null, "It should lose its job when it loads");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "hut_panel_paging")
    public static void candidatesComeAPageAtATime(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        for (int i = 0; i < 6; i++) {
            settler(helper, settlement, blue, new BlockPos(10 + i % 3, FLOOR_Y + 1, 10 + i / 3));
        }
        RecordingPlayer player = ownerAtHut(helper, blue);
        try {
            rightClick(helper, player);
            press(player, WorkplaceMenu.SHOW_WORKERS_BUTTON);
            WorkplaceMenu.WorkplaceInfo first = clientMenu(helper, player).getInfo();
            // 2 section titles + "nobody yet" + 6 settlers = 9 rows: two pages.
            helper.assertTrue(first.candidates().size() == 6 && first.pages() == 2 && first.page() == 0
                    && first.pageRows().size() == WorkplaceMenu.ROWS_PER_PAGE, "Page 1: " + first);
            press(player, WorkplaceMenu.NEXT_PAGE_BUTTON);
            assertNoScreenClosed(helper, player);
            WorkplaceMenu.WorkplaceInfo second = clientMenu(helper, player).getInfo();
            helper.assertTrue(second.page() == 1 && second.pageRows().size() == 3 && second.view() == WorkplaceMenu.View.WORKERS,
                    "Page 2, still on the workers page: " + second);
            press(player, WorkplaceMenu.NEXT_PAGE_BUTTON);
            helper.assertTrue(clientMenu(helper, player).getInfo().page() == 1, "There's no page 3");
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "hut_panel_stock")
    public static void stockButtonOpensTheStock(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        found(helper, HALL, blue);
        lumberjackHut(helper, HUT);
        RecordingPlayer player = ownerAtHut(helper, blue);
        try {
            rightClick(helper, player);
            press(player, WorkplaceMenu.STOCK_BUTTON);
            helper.assertTrue(player.containerMenu instanceof WorkplaceStockMenu, "Expected the stock's chest screen, got " + player.containerMenu);
            assertNoScreenClosed(helper, player);

            player.sent.clear();
            WorkplaceStockMenu stock = (WorkplaceStockMenu) player.containerMenu;
            helper.assertTrue(stock.stillValid(player), "The stock should be open for its owner");
            stock.clickMenuButton(player, WorkplaceStockMenu.BACK_BUTTON);
            helper.assertTrue(player.containerMenu instanceof WorkplaceMenu, "Back should return to the panel, got " + player.containerMenu);
            assertNoScreenClosed(helper, player);
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "hut_panel_outsider")
    public static void outsidersCantOpenThePanel(GameTestHelper helper) {
        found(helper, HALL, faction(helper, "Blue"));
        lumberjackHut(helper, HUT);
        RecordingPlayer player = ownerAtHut(helper, faction(helper, "Red"));
        try {
            use(helper, player);
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "Another faction shouldn't get the panel");
            helper.assertTrue(player.actionBar.stream().anyMatch(line -> line.contains("belongs to")), "Expected a 'belongs to' message");
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    // A player of the faction, standing next to the hut.
    private static RecordingPlayer ownerAtHut(GameTestHelper helper, Faction faction) {
        RecordingPlayer player = recordingPlayer(helper);
        FactionData.get(helper.getLevel()).join(player.getUUID(), faction);
        Vec3 next = Vec3.atBottomCenterOf(helper.absolutePos(HUT.east()));
        player.teleportTo(next.x, next.y, next.z);
        return player;
    }

    private static void leave(GameTestHelper helper, RecordingPlayer player) {
        FactionData.get(helper.getLevel()).leave(player.getUUID());
        removePlayer(helper, player);
    }

    // Right-clicks the hut with an empty hand.
    private static void use(GameTestHelper helper, RecordingPlayer player) {
        BlockPos pos = helper.absolutePos(HUT);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    // Right-clicks the hut and returns the panel as the client built it.
    private static WorkplaceMenu rightClick(GameTestHelper helper, RecordingPlayer player) {
        player.sent.clear();
        use(helper, player);
        return clientMenu(helper, player);
    }

    private static WorkplaceMenu clientMenu(GameTestHelper helper, RecordingPlayer player) {
        if (!(player.containerMenu instanceof WorkplaceMenu)) {
            throw new GameTestAssertException("The hut panel isn't open; got " + player.containerMenu);
        }
        TestSupport.OpenedScreen opened = lastOpenedScreen(helper, player);
        return new WorkplaceMenu(opened.windowId(), player.getInventory(), opened.data());
    }

    // Switching panels must not close the client's screen first: closing a screen makes Minecraft
    // move the mouse to the middle of the window (the cursor jump the owner saw when paging).
    private static void assertNoScreenClosed(GameTestHelper helper, RecordingPlayer player) {
        helper.assertFalse(player.sent.stream().anyMatch(packet -> packet instanceof ClientboundContainerClosePacket),
                "The client was told to close its screen, which re-centres the mouse");
    }

    // Presses a panel button, as the client does: the server checks the menu is still valid, then
    // runs the button.
    private static void press(RecordingPlayer player, int button) {
        if (!(player.containerMenu instanceof WorkplaceMenu menu) || !menu.stillValid(player)) {
            throw new GameTestAssertException("The hut panel isn't open and valid");
        }
        player.sent.clear();
        menu.clickMenuButton(player, button);
    }

    // An unemployed unit of the faction that belongs to the settlement.
    private static Unit settler(GameTestHelper helper, Settlement settlement, Faction faction, BlockPos pos) {
        Unit unit = unit(helper, pos, faction, null, false);
        SettlementOps.setUnitSettlement(helper.getLevel(), unit, settlement);
        return unit;
    }
}
