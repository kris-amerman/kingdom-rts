package com.krisamerman.kingdomrts.unit;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// The unit info panel, opened by right-clicking a unit. (A "menu" is the server-side half of a
// screen; see TownHallMenu.) The panel shows a UnitInfo snapshot taken when it opens, plus the
// unit's health, which a ContainerData keeps live.
//
// For a member of the unit's faction the menu also has the unit's inventory slots, which vanilla
// keeps in sync like a chest's, and the player's own inventory below them, laid out like any
// container screen. For anyone else it has no slots at all, so the inventory is never sent to them.
// Owners can move items between their inventory and the unit's, by clicking or shift-clicking.
public class UnitMenu extends AbstractContainerMenu {
    // Slot positions (top-left of the item), relative to the panel. See UnitScreen for the rest.
    public static final int SLOTS_X = 8;
    public static final int UNIT_SLOTS_Y = 124;
    public static final int PLAYER_SLOTS_Y = 158;
    public static final int HOTBAR_Y = PLAYER_SLOTS_Y + 58;
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private final UnitInfo info;
    // The unit's entity id, so the client screen can find the unit to draw it.
    private final int unitEntityId;
    private final ContainerData health;
    // Server only: the unit shown. Null on the client.
    @Nullable
    private final Unit unit;

    // Server side.
    private UnitMenu(int containerId, Inventory playerInventory, Unit unit, UnitInfo info) {
        super(KingdomRts.UNIT_MENU.get(), containerId);
        this.unit = unit;
        this.unitEntityId = unit.getId();
        this.info = info;
        this.health = new ContainerData() {
            @Override
            public int get(int index) {
                return (int) Math.ceil(unit.getHealth());
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
        addDataSlots(health);
        if (info.isFull()) {
            addSlots(unit.getInventory(), playerInventory);
        }
    }

    // Client side: built from the extra data the server wrote in open(). It has the same slots as
    // the server copy (or none), backed by an empty container the server fills in.
    public UnitMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(KingdomRts.UNIT_MENU.get(), containerId);
        this.unit = null;
        this.unitEntityId = extraData.readVarInt();
        this.info = UnitInfo.read(extraData);
        this.health = new SimpleContainerData(1);
        addDataSlots(health);
        if (info.isFull()) {
            addSlots(new SimpleContainer(Unit.INVENTORY_SIZE), inventory);
        }
    }

    // The unit's inventory (one row), then the player's: three rows plus the hotbar, where every
    // container screen has them. Slot order: unit 0-8, player 9-35, hotbar 36-44.
    private void addSlots(Container unitInventory, Inventory playerInventory) {
        for (int i = 0; i < Unit.INVENTORY_SIZE; i++) {
            addSlot(new Slot(unitInventory, i, SLOTS_X + i * 18, UNIT_SLOTS_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + column, SLOTS_X + column * 18, PLAYER_SLOTS_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, SLOTS_X + column * 18, HOTBAR_Y));
        }
    }

    // Opens the panel for a player, with the full view only if they're in the unit's faction.
    public static void open(ServerPlayer player, Unit unit) {
        UnitInfo info = UnitInfo.of(player.serverLevel(), unit, accessFor(player, unit));
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new UnitMenu(id, inventory, unit, info), unit.getName()),
                buf -> {
                    buf.writeVarInt(unit.getId());
                    info.write(buf);
                });
    }

    // Server: how much of this unit the player may see (and, with the full view, change).
    private static UnitInfo.Access accessFor(Player player, Unit unit) {
        Faction viewerFaction = FactionData.get((ServerLevel) unit.level()).factionOf(player.getUUID());
        return UnitInfo.accessFor(viewerFaction != null ? viewerFaction.id() : null, unit.getFactionId());
    }

    public UnitInfo getInfo() {
        return info;
    }

    public int getUnitEntityId() {
        return unitEntityId;
    }

    public int getHealth() {
        return health.get(0);
    }

    // Closes the panel if the unit dies, the player walks away, or the player stops being allowed
    // the full view (they left the faction, or the unit changed faction, e.g. on capture), so items
    // can't be moved by someone who no longer has access. Checked by the server every tick.
    @Override
    public boolean stillValid(Player player) {
        if (unit == null) {
            return true; // client copy; the server decides
        }
        if (!unit.isAlive() || player.distanceToSqr(unit) > MAX_DISTANCE_SQR) {
            return false;
        }
        return !info.isFull() || accessFor(player, unit) == UnitInfo.Access.FULL;
    }

    // Shift-click: moves a stack between the unit's inventory and the player's.
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int unitEnd = Unit.INVENTORY_SIZE;
        boolean moved = index < unitEnd
                ? moveItemStackTo(stack, unitEnd, slots.size(), true) // unit -> player (hotbar first, like a chest)
                : moveItemStackTo(stack, 0, unitEnd, false);          // player -> unit
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
