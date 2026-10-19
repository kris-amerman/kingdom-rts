package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.current;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.lastOpenedScreen;
import static com.krisamerman.kingdomrts.gametest.TestSupport.recordingPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.runCommand;

import java.util.List;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.gametest.TestSupport.RecordingPlayer;
import com.krisamerman.kingdomrts.settlement.Building;
import com.krisamerman.kingdomrts.settlement.ConstructionSiteBlockEntity;
import com.krisamerman.kingdomrts.settlement.BuildingOps;
import com.krisamerman.kingdomrts.settlement.BuildingSiteMenu;
import com.krisamerman.kingdomrts.settlement.BuildingSiteStockMenu;
import com.krisamerman.kingdomrts.settlement.Settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Construction sites: commissioning a building puts a construction site block down inside it
// straight away; it holds the site's stock, opens the site panel, breaking it cancels the site, and
// it's taken away when the building is finished. The panel is used the way a player uses it:
// right-click the site block, read what the client was sent, press its buttons. Each test founds a
// settlement, so each has its own batch.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class BuildingSiteGameTests {
    private static final ResourceLocation SHED = ResourceLocation.fromNamespaceAndPath(KingdomRts.MODID, "storage_shed");
    private static final BlockPos SITE = new BlockPos(1, FLOOR_Y + 1, 1);
    // Where the shed's construction site goes: the middle of its floor, inside (relative to its
    // template corner; the same whichever way it's turned, since it turns about its middle).
    private static final BlockPos SITE_SPOT = new BlockPos(2, 1, 2);
    // The shed's blocks, not counting the air inside it.
    private static final int SHED_TOTAL = 84;

    @GameTest(template = TEMPLATE, batch = "site_core")
    public static void commissioningPutsDownASite(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building building = commission(helper, blue, SITE, Rotation.NONE);
        helper.assertTrue(helper.absolutePos(SITE.offset(SITE_SPOT)).equals(building.site()), "Expected the site in the middle of the floor: " + building.site());
        assertSite(helper, settlement, building.site());
        Building turned = commission(helper, blue, SITE.south(5), Rotation.CLOCKWISE_90);
        helper.assertTrue(helper.absolutePos(SITE.south(5).offset(SITE_SPOT)).equals(turned.site()), "A turned shed's middle stays put: " + turned.site());
        assertSite(helper, settlement, turned.site());
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "site_finish")
    public static void finishingTakesTheSiteAway(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building building = commission(helper, blue, SITE, Rotation.NONE);
        stock(helper, building).setItem(0, new ItemStack(Items.OAK_PLANKS, 3)); // left over
        BuildingOps.finish(helper.getLevel(), current(helper, settlement), building);
        Building built = current(helper, settlement).buildingAt(building.origin());
        helper.assertTrue(built != null && built.isBuilt() && built.site() == null, "Expected the building marked built, with no site: " + built);
        helper.assertFalse(helper.getLevel().getBlockState(building.site()).is(KingdomRts.CONSTRUCTION_SITE.get()), "The site block should be gone");
        helper.assertFalse(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(building.site()).inflate(2),
                item -> item.getItem().is(Items.OAK_PLANKS)).isEmpty(), "What was left in its stock should drop");
        removeHall(helper, HALL);
        helper.succeed();
    }

    // Built by hand while someone has the panel open: the panel notices, completes the building and
    // closes (its site block is gone).
    @GameTest(template = TEMPLATE, batch = "site_finish_by_hand")
    public static void openPanelCompletesABuildingFinishedByHand(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building building = commission(helper, blue, SITE, Rotation.NONE);
        RecordingPlayer player = playerAt(helper, blue, building.site());
        rightClick(helper, player, building.site());
        for (var part : BuildingOps.placed(helper.getLevel(), building)) {
            if (!part.state().isAir()) {
                helper.getLevel().setBlock(part.pos(), part.state(), 2 | 16); // as a player would, block by block
            }
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(current(helper, settlement).buildingAt(building.origin()).isBuilt(),
                        "Waiting for the open panel to notice the building is finished"))
                .thenWaitUntil(() -> helper.assertFalse(player.containerMenu instanceof BuildingSiteMenu, "Waiting for the panel to close"))
                .thenExecute(() -> {
                    leave(helper, player);
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "site_break")
    public static void breakingTheSiteBlockCancelsTheSite(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Settlement settlement = found(helper, HALL, blue);
        Building building = commission(helper, blue, SITE, Rotation.NONE);
        RecordingPlayer outsider = playerAt(helper, red, building.site());
        RecordingPlayer owner = playerAt(helper, blue, building.site());
        try {
            use(helper, outsider, building.site());
            helper.assertFalse(outsider.containerMenu instanceof BuildingSiteMenu, "An outsider can't open the site");
            outsider.gameMode.destroyBlock(building.site());
            helper.assertTrue(current(helper, settlement).buildingAt(building.origin()) != null, "An outsider can't cancel the site");
            helper.assertTrue(helper.getLevel().getBlockState(building.site()).is(KingdomRts.CONSTRUCTION_SITE.get()), "The site should still stand");

            owner.gameMode.destroyBlock(building.site());
            helper.assertTrue(current(helper, settlement).buildings().isEmpty(), "Breaking the site block should cancel the site");
        } finally {
            leave(helper, outsider);
            leave(helper, owner);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "site_cancel")
    public static void cancellingRemovesTheSiteBlock(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building building = commission(helper, blue, SITE, Rotation.NONE);
        stock(helper, building).setItem(0, new ItemStack(Items.OAK_PLANKS, 10));
        BuildingOps.cancel(helper.getLevel(), current(helper, settlement), building);
        helper.assertFalse(helper.getLevel().getBlockState(building.site()).is(KingdomRts.CONSTRUCTION_SITE.get()), "The site block should be gone");
        helper.assertFalse(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(building.site()).inflate(2),
                item -> item.getItem().is(Items.OAK_PLANKS)).isEmpty(), "Its stock should spill out");
        helper.assertTrue(current(helper, settlement).buildings().isEmpty(), "The site should be gone");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "site_demolish_built")
    public static void demolishingABuiltBuildingRemovesIt(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building building = commission(helper, blue, SITE, Rotation.NONE);
        BuildingOps.finish(helper.getLevel(), current(helper, settlement), building);
        BlockPos extra = SITE.offset(1, 1, 1); // not part of the shed: something put inside it
        helper.setBlock(extra, Blocks.STONE);
        List<String> output = runCommand(helper, HALL, "kingdomrts building demolish " + coords(helper, SITE.offset(2, 2, 2)));
        helper.assertTrue(output.stream().anyMatch(line -> line.startsWith("Demolished the Storage shed (84 blocks removed)")),
                "Expected the shed demolished, got " + output);
        helper.assertBlockPresent(Blocks.AIR, SITE);                  // its floor
        helper.assertBlockPresent(Blocks.AIR, SITE.offset(2, 1, 0));  // its door
        helper.assertBlockPresent(Blocks.AIR, SITE.offset(4, 3, 4));  // its roof
        helper.assertBlockPresent(Blocks.STONE, extra);
        helper.assertTrue(current(helper, settlement).buildings().isEmpty(), "The settlement should forget it");
        helper.assertFalse(runCommand(helper, HALL, "kingdomrts building list").stream().anyMatch(line -> line.contains("Storage shed")),
                "It shouldn't be listed");
        helper.setBlock(extra, Blocks.AIR);
        helper.assertTrue(BuildingOps.commission(helper.getLevel(), blue, SHED, helper.absolutePos(SITE), Rotation.NONE).building() != null,
                "Its ground should be free for a new building");
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "site_demolish_site")
    public static void demolishingASiteDropsItsStock(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Settlement settlement = found(helper, HALL, blue);
        Building building = commission(helper, blue, SITE, Rotation.NONE);
        stock(helper, building).setItem(0, new ItemStack(Items.OAK_PLANKS, 10));
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(SITE.offset(x, 0, z), Blocks.COBBLESTONE); // the floor, by hand
            }
        }
        int removed = BuildingOps.demolish(helper.getLevel(), current(helper, settlement), building);
        helper.assertTrue(removed == 25, "Expected the 25 floor blocks removed, got " + removed);
        helper.assertBlockPresent(Blocks.AIR, SITE.offset(2, 0, 2));
        helper.assertFalse(helper.getLevel().getBlockState(building.site()).is(KingdomRts.CONSTRUCTION_SITE.get()), "The site block should be gone");
        helper.assertFalse(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(building.site()).inflate(2),
                item -> item.getItem().is(Items.OAK_PLANKS)).isEmpty(), "The site's stock should drop");
        helper.assertTrue(current(helper, settlement).buildings().isEmpty(), "The site should be forgotten");
        removeHall(helper, HALL);
        helper.succeed();
    }

    // The absolute coordinates of a test position, for a command.
    private static String coords(GameTestHelper helper, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    @GameTest(template = TEMPLATE, batch = "site_panel")
    public static void panelShowsProgressAndMaterials(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        found(helper, HALL, blue);
        Building building = commission(helper, blue, SITE, Rotation.NONE);
        RecordingPlayer player = playerAt(helper, blue, building.site());
        try {
            BuildingSiteMenu.SiteInfo info = rightClick(helper, player, building.site()).getInfo();
            helper.assertTrue(info.buildingName().equals("Storage shed") && info.loaded() && info.total() == SHED_TOTAL && info.placed() == 0
                    && info.inTheWay() == 0, "Expected the fresh shed, nothing placed and nothing in the way (the site block doesn't count): " + info);
            helper.assertTrue(info.materials().size() == 7 && info.materials().get(0).needed() == 25
                    && info.materials().stream().allMatch(material -> material.onSite() == 0), "Unexpected materials: " + info.materials());

            stock(helper, building).setItem(0, new ItemStack(Items.OAK_PLANKS, 10));
            info = rightClick(helper, player, building.site()).getInfo();
            BuildingSiteMenu.Material planks = info.materials().stream().filter(material -> material.item() == Items.OAK_PLANKS).findFirst().orElseThrow();
            helper.assertTrue(planks.needed() == 22 && planks.onSite() == 10, "Expected 10 of the 22 planks on site: " + planks);
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "site_panel_live")
    public static void openPanelUpdatesAsBlocksArePlaced(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        found(helper, HALL, blue);
        Building building = commission(helper, blue, SITE, Rotation.NONE);
        RecordingPlayer player = playerAt(helper, blue, building.site());
        rightClick(helper, player, building.site());
        player.sent.clear();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(SITE.offset(x, 0, z), Blocks.COBBLESTONE); // the floor, by hand
            }
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(clientMenu(helper, player).getInfo().placed() == 25,
                        "Waiting for the open panel to show the floor placed"))
                .thenExecute(() -> {
                    assertNoScreenClosed(helper, player);
                    leave(helper, player);
                    removeHall(helper, HALL);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "site_panel_buttons")
    public static void panelButtons(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        found(helper, HALL, blue);
        Building building = commission(helper, blue, SITE, Rotation.NONE);
        RecordingPlayer player = playerAt(helper, blue, building.site());
        try {
            rightClick(helper, player, building.site());
            press(player, BuildingSiteMenu.SHOW_MATERIALS_BUTTON);
            assertNoScreenClosed(helper, player);
            BuildingSiteMenu.SiteInfo info = clientMenu(helper, player).getInfo();
            helper.assertTrue(info.view() == BuildingSiteMenu.View.MATERIALS && info.pages() == 2 && info.pageMaterials().size() == 6,
                    "Expected the first of two pages of materials: " + info);
            press(player, BuildingSiteMenu.NEXT_PAGE_BUTTON);
            info = clientMenu(helper, player).getInfo();
            helper.assertTrue(info.page() == 1 && info.pageMaterials().size() == 1, "Expected the last material on page 2: " + info);
            press(player, BuildingSiteMenu.SHOW_OVERVIEW_BUTTON);
            helper.assertTrue(clientMenu(helper, player).getInfo().view() == BuildingSiteMenu.View.OVERVIEW, "Back should show the overview");

            press(player, BuildingSiteMenu.STOCK_BUTTON);
            helper.assertTrue(player.containerMenu instanceof BuildingSiteStockMenu, "Expected the site's stock, got " + player.containerMenu);
            assertNoScreenClosed(helper, player);
            player.sent.clear();
            player.containerMenu.clickMenuButton(player, BuildingSiteStockMenu.BACK_BUTTON);
            helper.assertTrue(player.containerMenu instanceof BuildingSiteMenu, "Back should return to the site panel, got " + player.containerMenu);
            assertNoScreenClosed(helper, player);
        } finally {
            leave(helper, player);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    private static Building commission(GameTestHelper helper, Faction faction, BlockPos relativePos, Rotation rotation) {
        BuildingOps.CommissionResult result = BuildingOps.commission(helper.getLevel(), faction, SHED, helper.absolutePos(relativePos), rotation);
        if (result.building() == null) {
            throw new GameTestAssertException("Couldn't commission the shed: " + result.problem());
        }
        return result.building();
    }

    private static void assertSite(GameTestHelper helper, Settlement settlement, BlockPos site) {
        helper.assertTrue(helper.getLevel().getBlockEntity(site) instanceof ConstructionSiteBlockEntity stock
                && stock.getSettlement() != null && stock.getSettlement().id().equals(settlement.id()),
                "Expected a construction site belonging to " + settlement.name() + " at " + site.toShortString());
    }

    private static ConstructionSiteBlockEntity stock(GameTestHelper helper, Building building) {
        return (ConstructionSiteBlockEntity) helper.getLevel().getBlockEntity(building.site());
    }

    // A player of the faction, standing next to the site block.
    private static RecordingPlayer playerAt(GameTestHelper helper, Faction faction, BlockPos site) {
        RecordingPlayer player = recordingPlayer(helper);
        FactionData.get(helper.getLevel()).join(player.getUUID(), faction);
        Vec3 next = Vec3.atBottomCenterOf(site.east(2));
        player.teleportTo(next.x, next.y, next.z);
        return player;
    }

    private static void leave(GameTestHelper helper, RecordingPlayer player) {
        FactionData.get(helper.getLevel()).leave(player.getUUID());
        removePlayer(helper, player);
    }

    private static void use(GameTestHelper helper, RecordingPlayer player, BlockPos pos) {
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    // Right-clicks the site block and returns the panel as the client built it.
    private static BuildingSiteMenu rightClick(GameTestHelper helper, RecordingPlayer player, BlockPos site) {
        player.sent.clear();
        use(helper, player, site);
        return clientMenu(helper, player);
    }

    private static BuildingSiteMenu clientMenu(GameTestHelper helper, RecordingPlayer player) {
        if (!(player.containerMenu instanceof BuildingSiteMenu)) {
            throw new GameTestAssertException("The site panel isn't open; got " + player.containerMenu);
        }
        TestSupport.OpenedScreen opened = lastOpenedScreen(helper, player);
        return new BuildingSiteMenu(opened.windowId(), player.getInventory(), opened.data());
    }

    // Presses a panel button, as the client does: the server checks the menu is still valid first.
    private static void press(RecordingPlayer player, int button) {
        if (!(player.containerMenu instanceof BuildingSiteMenu menu) || !menu.stillValid(player)) {
            throw new GameTestAssertException("The site panel isn't open and valid");
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
