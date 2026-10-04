package com.krisamerman.kingdomrts.settlement;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

// The town hall menu: shows the owner and control meter, and has a Claim button for neutral halls.
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
    // The owning faction's name, or "" for a neutral hall. Fixed for as long as the menu is open.
    private final String ownerName;

    // Server side: opened by a player right-clicking the hall.
    private TownHallMenu(int containerId, Inventory inventory, TownHallBlockEntity townHall, String ownerName) {
        super(KingdomRts.TOWN_HALL_MENU.get(), containerId);
        this.townHall = townHall;
        this.openedOwner = townHall.getOwner();
        this.viewer = (ServerPlayer) inventory.player;
        this.access = ContainerLevelAccess.create(townHall.getLevel(), townHall.getBlockPos());
        this.ownerName = ownerName;
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
        this.ownerName = extraData.readUtf();
        this.data = new SimpleContainerData(1);
        addDataSlots(data);
    }

    // Opens (or re-opens) the menu for a player.
    public static void open(ServerPlayer player, TownHallBlockEntity townHall) {
        String ownerName = townHall.getOwner() == null ? "" : FactionData.get(player.serverLevel()).nameOf(townHall.getOwner());
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TownHallMenu(id, inventory, townHall, ownerName), TITLE),
                buf -> {
                    buf.writeBlockPos(townHall.getBlockPos());
                    buf.writeUtf(ownerName);
                });
    }

    public boolean isNeutral() {
        return ownerName.isEmpty();
    }

    public String getOwnerName() {
        return ownerName;
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
        Faction faction = townHall.claim(serverPlayer);
        if (faction == null) {
            player.sendSystemMessage(Component.literal("This town hall is already owned"));
            return false;
        }
        player.sendSystemMessage(Component.literal("You claimed this town hall for " + faction.name()));
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
