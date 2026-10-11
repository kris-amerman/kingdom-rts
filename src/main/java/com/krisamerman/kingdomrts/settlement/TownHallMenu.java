package com.krisamerman.kingdomrts.settlement;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.FactionData;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

// The town hall menu: shows the settlement, owner, control meter and claim, and has a Claim button
// for unclaimed halls (claiming founds a settlement).
//
// A "menu" is the server-side half of a screen (like a chest's). Each open screen has one copy on
// the server and one on the client. Here the server copy reads the real town hall; the client copy
// only knows what the server sends: the owner's name when the menu opens, and the control value,
// which a ContainerData keeps in sync automatically while the screen is open.
public class TownHallMenu extends AbstractContainerMenu {
    public static final int CLAIM_BUTTON = 0;
    private static final Component TITLE = Component.translatable("container.kingdomrts.town_hall");

    // Server only: the hall this menu shows, its owner when the menu opened, and the player viewing
    // it. Null on the client.
    @Nullable
    private final TownHallBlockEntity townHall;
    @Nullable
    private final UUID openedOwner;
    @Nullable
    private final ServerPlayer viewer;
    // Used to check the player is still at the hall (and it still exists).
    private final ContainerLevelAccess access;
    private final ContainerData data;
    // What the panel shows besides control. Fixed for as long as the menu is open.
    private final HallInfo info;

    // The hall's settlement as the panel shows it: names are "" for an unclaimed hall.
    private record HallInfo(String settlementName, String ownerName, int claimedChunks) {
        static HallInfo of(ServerLevel level, TownHallBlockEntity townHall) {
            Settlement settlement = townHall.getSettlement();
            if (settlement == null) {
                return new HallInfo("", "", 0);
            }
            return new HallInfo(settlement.name(), FactionData.get(level).nameOf(settlement.owner()), settlement.chunks().size());
        }

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(settlementName);
            buf.writeUtf(ownerName);
            buf.writeVarInt(claimedChunks);
        }

        static HallInfo read(RegistryFriendlyByteBuf buf) {
            return new HallInfo(buf.readUtf(), buf.readUtf(), buf.readVarInt());
        }
    }

    // Server side: opened by a player right-clicking the hall.
    private TownHallMenu(int containerId, Inventory inventory, TownHallBlockEntity townHall, HallInfo info) {
        super(KingdomRts.TOWN_HALL_MENU.get(), containerId);
        this.townHall = townHall;
        this.openedOwner = townHall.getOwner();
        this.viewer = (ServerPlayer) inventory.player;
        this.access = ContainerLevelAccess.create(townHall.getLevel(), townHall.getBlockPos());
        this.info = info;
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return townHall.getControl();
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
        addDataSlots(data);
    }

    // Client side: built from the extra data the server wrote in open().
    public TownHallMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(KingdomRts.TOWN_HALL_MENU.get(), containerId);
        this.townHall = null;
        this.openedOwner = null;
        this.viewer = null;
        this.access = ContainerLevelAccess.NULL;
        extraData.readBlockPos();
        this.info = HallInfo.read(extraData);
        this.data = new SimpleContainerData(1);
        addDataSlots(data);
    }

    // Opens (or re-opens) the menu for a player.
    public static void open(ServerPlayer player, TownHallBlockEntity townHall) {
        HallInfo info = HallInfo.of(player.serverLevel(), townHall);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TownHallMenu(id, inventory, townHall, info), TITLE),
                buf -> {
                    buf.writeBlockPos(townHall.getBlockPos());
                    info.write(buf);
                });
    }

    public boolean isUnclaimed() {
        return info.settlementName().isEmpty();
    }

    public String getSettlementName() {
        return info.settlementName();
    }

    public String getOwnerName() {
        return info.ownerName();
    }

    public int getClaimedChunks() {
        return info.claimedChunks();
    }

    public int getControl() {
        return data.get(0);
    }

    // Runs on the server every tick while the menu is open, sending changed ContainerData values
    // (the control meter) to the client. The owner name isn't a ContainerData value, so if the
    // owner changed (e.g. the hall was captured while open), re-open the menu to show the new one.
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (townHall != null && viewer != null && viewer.containerMenu == this
                && !Objects.equals(townHall.getOwner(), openedOwner)) {
            open(viewer, townHall);
        }
    }

    // Runs on the server when the client reports a button click (vanilla only calls this after
    // checking stillValid, i.e. the player is still next to the hall).
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != CLAIM_BUTTON || townHall == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        SettlementOps.FoundResult result = townHall.claim(serverPlayer);
        if (result.settlement() == null) {
            player.sendSystemMessage(Component.literal("Can't claim: " + result.problem()));
            return false;
        }
        Settlement settlement = result.settlement();
        player.sendSystemMessage(Component.literal("You founded " + settlement.name() + " for "
                + FactionData.get(serverPlayer.serverLevel()).nameOf(settlement.owner())));
        // The owner changed, so broadcastChanges re-opens the menu to show it.
        return true;
    }

    // Closes the screen if the hall is broken or the player walks away (more than 8 blocks).
    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, KingdomRts.TOWN_HALL.get());
    }

    // Required by AbstractContainerMenu for shift-clicking items; this menu has no item slots.
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
