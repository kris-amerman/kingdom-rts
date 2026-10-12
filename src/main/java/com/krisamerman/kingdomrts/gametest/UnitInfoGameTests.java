package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.HALL;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.found;
import static com.krisamerman.kingdomrts.gametest.TestSupport.recordingPlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removeHall;
import static com.krisamerman.kingdomrts.gametest.TestSupport.removePlayer;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import java.util.ArrayList;
import java.util.List;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.gametest.TestSupport.RecordingPlayer;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.unit.Unit;
import com.krisamerman.kingdomrts.unit.UnitMenu;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;

// The unit info panel, opened the way a player opens it (right-clicking the unit): the unit's own
// faction gets the details and the inventory; anyone else gets only the basics. The checks are made
// on what was actually sent to each player's client, not just on the server's menu.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class UnitInfoGameTests {
    private static final BlockPos SPOT = new BlockPos(4, FLOOR_Y + 1, 4);

    @GameTest(template = TEMPLATE, batch = "unit_info")
    public static void ownFactionSeesEverythingOthersSeeTheBasics(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Faction red = faction(helper, "Red");
        Settlement settlement = found(helper, HALL, blue);
        Unit unit = unit(helper, SPOT, blue, false);
        SettlementOps.setUnitSettlement(helper.getLevel(), unit, settlement);
        unit.setHome(helper.absolutePos(SPOT.east(2)));
        unit.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 5));

        RecordingPlayer member = recordingPlayer(helper);
        RecordingPlayer outsider = recordingPlayer(helper);
        RecordingPlayer factionless = recordingPlayer(helper);
        FactionData factions = FactionData.get(helper.getLevel());
        try {
            factions.join(member.getUUID(), blue);
            factions.join(outsider.getUUID(), red);

            UnitMenu full = rightClick(helper, member, unit);
            helper.assertTrue(full.getInfo().isFull(), "A member of the unit's faction should get the full view");
            helper.assertTrue(full.getInfo().details().settlementName().equals(settlement.name()), "Expected the settlement name");
            helper.assertTrue(unit.getHome().equals(full.getInfo().details().home()), "Expected the home");
            helper.assertTrue(full.slots.size() == Unit.INVENTORY_SIZE + 36, "Expected the unit's slots and the player's inventory");
            helper.assertTrue(sentItems(member).stream().anyMatch(stack -> stack.is(Items.OAK_LOG)), "Expected the logs to be sent");

            for (RecordingPlayer other : new RecordingPlayer[] {outsider, factionless}) {
                UnitMenu limited = rightClick(helper, other, unit);
                helper.assertFalse(limited.getInfo().isFull(), "Only the unit's faction should get the details");
                helper.assertTrue(limited.slots.isEmpty(), "Only the unit's faction should get inventory slots");
                helper.assertFalse(sentItems(other).stream().anyMatch(stack -> stack.is(Items.OAK_LOG)),
                        "The unit's items were sent to a player outside its faction");
                helper.assertTrue(limited.getInfo().factionName().equals(blue.name()) && limited.getInfo().maxHealth() == unit.getMaxHealth(),
                        "Everyone should get the basics");
            }
        } finally {
            factions.leave(member.getUUID());
            factions.leave(outsider.getUUID());
            removePlayer(helper, member);
            removePlayer(helper, outsider);
            removePlayer(helper, factionless);
        }
        removeHall(helper, HALL);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void nobodyGetsTheFullViewOfAFactionlessUnit(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Unit unit = unit(helper, SPOT, null, false);
        RecordingPlayer player = recordingPlayer(helper);
        FactionData factions = FactionData.get(helper.getLevel());
        try {
            factions.join(player.getUUID(), blue);
            UnitMenu menu = rightClick(helper, player, unit);
            helper.assertTrue(!menu.getInfo().isFull() && menu.slots.isEmpty(), "A factionless unit belongs to nobody");
        } finally {
            factions.leave(player.getUUID());
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    // The owner gives the unit an item and takes it back, with the same clicks a player makes; once
    // they leave the faction, the panel stops being valid so they can't keep moving items.
    @GameTest(template = TEMPLATE)
    public static void ownerCanGiveAndTakeItems(GameTestHelper helper) {
        Faction blue = faction(helper, "Blue");
        Unit unit = unit(helper, SPOT, blue, false);
        RecordingPlayer player = recordingPlayer(helper);
        FactionData factions = FactionData.get(helper.getLevel());
        try {
            factions.join(player.getUUID(), blue);
            player.teleportTo(unit.getX(), unit.getY(), unit.getZ());
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
            rightClick(helper, player, unit);
            UnitMenu menu = (UnitMenu) player.containerMenu;
            int hotbarSlot = Unit.INVENTORY_SIZE + 27; // menu slot of the player's first hotbar slot

            // Pick the diamonds up from the hotbar and put them in the unit's first slot.
            menu.clicked(hotbarSlot, 0, ClickType.PICKUP, player);
            menu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(unit.getInventory().getItem(0).is(Items.DIAMOND) && unit.getInventory().getItem(0).getCount() == 3,
                    "Expected the diamonds in the unit's inventory, got " + unit.getInventory().getItem(0));
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "Expected the diamonds to leave the player");

            // Shift-click them back.
            menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(unit.getInventory().isEmpty(), "Expected shift-click to take the diamonds back");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 3, "Expected the diamonds back with the player");

            // Shift-clicking from the player's inventory gives them to the unit. (They came back to
            // the far end of the hotbar, as with a chest, so find where they are.)
            int playerSlot = -1;
            for (int i = Unit.INVENTORY_SIZE; i < menu.slots.size(); i++) {
                if (menu.getSlot(i).getItem().is(Items.DIAMOND)) {
                    playerSlot = i;
                }
            }
            menu.clicked(playerSlot, 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(unit.getInventory().countItem(Items.DIAMOND) == 3, "Expected shift-click to give the diamonds");

            helper.assertTrue(menu.stillValid(player), "The panel should stay open for the owner");
            factions.leave(player.getUUID());
            helper.assertFalse(menu.stillValid(player), "The panel should close once the player leaves the unit's faction");
        } finally {
            factions.leave(player.getUUID());
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    // Right-clicks the unit with an empty hand, as a player would, and returns the panel as the
    // player's client would build it from the packet the server sent. The menu stays open.
    private static UnitMenu rightClick(GameTestHelper helper, RecordingPlayer player, Unit unit) {
        player.sent.clear();
        player.interactOn(unit, InteractionHand.MAIN_HAND);
        if (!(player.containerMenu instanceof UnitMenu)) {
            throw new GameTestAssertException("Right-clicking the unit didn't open its panel");
        }
        for (Packet<?> packet : player.sent) {
            if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof AdvancedOpenScreenPayload open) {
                // Read the extra data the way the client does (see NeoForge's ClientPayloadHandler).
                RegistryFriendlyByteBuf data = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(open.additionalData()),
                        helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
                return new UnitMenu(open.windowId(), player.getInventory(), data);
            }
        }
        throw new GameTestAssertException("No open-screen packet was sent");
    }

    // Every item stack sent to the player in menu contents or slot updates.
    private static List<ItemStack> sentItems(RecordingPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        for (Packet<?> packet : player.sent) {
            if (packet instanceof ClientboundContainerSetContentPacket content) {
                items.addAll(content.getItems());
            } else if (packet instanceof ClientboundContainerSetSlotPacket slot) {
                items.add(slot.getItem());
            }
        }
        return items;
    }
}
