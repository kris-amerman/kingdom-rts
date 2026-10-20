package com.krisamerman.kingdomrts.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

// The town hall menu, as two pages:
//   - Overview: the settlement, owner, control meter and claim, with a Claim button for unclaimed
//     halls (claiming founds a settlement) and, for the owning faction's members, a Population
//     button.
//   - Population (members only): everyone who belongs to the settlement, a page at a time, with
//     their job title.
//
// A "menu" is the server-side half of a screen (like a chest's). Each open screen has one copy on
// the server and one on the client. Here the server copy reads the real town hall; the client copy
// only knows what the server sends: a snapshot (HallInfo) when the menu opens, and the control
// value, which a ContainerData keeps in sync automatically while the screen is open. Like the hut
// panel, the server re-opens the menu in place when the snapshot goes out of date.
public class TownHallMenu extends AbstractContainerMenu {
    public static final int CLAIM_BUTTON = 0;
    public static final int SHOW_POPULATION_BUTTON = 1;
    public static final int SHOW_OVERVIEW_BUTTON = 2;
    public static final int PREVIOUS_PAGE_BUTTON = 3;
    public static final int NEXT_PAGE_BUTTON = 4;
    public static final int PEOPLE_PER_PAGE = 9;
    public static final int REFRESH_CHECK_TICKS = 20;
    private static final Component TITLE = Component.translatable("container.kingdomrts.town_hall");

    public enum View { OVERVIEW, POPULATION }

    // Someone who belongs to the settlement, as the population page lists them. occupation is an
    // occupation id ("" for none).
    public record Person(UUID id, String name, String occupation) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name);
            buf.writeUtf(occupation);
        }

        static Person read(FriendlyByteBuf buf) {
            return new Person(buf.readUUID(), buf.readUtf(), buf.readUtf());
        }
    }

    // What the panel shows. Names are "" for an unclaimed hall. member is whether the viewer belongs
    // to the owning faction; only members get the population (people is only filled in on the
    // population page).
    public record HallInfo(String settlementName, String ownerName, int claimedChunks, boolean member, int population,
            List<Person> people, View view, int page) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(settlementName);
            buf.writeUtf(ownerName);
            buf.writeVarInt(claimedChunks);
            buf.writeBoolean(member);
            buf.writeVarInt(population);
            buf.writeCollection(people, (b, person) -> person.write(b));
            buf.writeEnum(view);
            buf.writeVarInt(page);
        }

        static HallInfo read(FriendlyByteBuf buf) {
            return new HallInfo(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                    buf.readList(Person::read), buf.readEnum(View.class), buf.readVarInt());
        }

        public boolean isUnclaimed() {
            return settlementName.isEmpty();
        }

        public int pages() {
            return Math.max(1, (people.size() + PEOPLE_PER_PAGE - 1) / PEOPLE_PER_PAGE);
        }

        // The people on the current page.
        public List<Person> pagePeople() {
            int from = Math.min(people.size(), page * PEOPLE_PER_PAGE);
            return people.subList(from, Math.min(people.size(), from + PEOPLE_PER_PAGE));
        }
    }

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
    private final HallInfo info;
    private int ticks;

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
        this.info = HallInfo.read(extraData);
        this.data = new SimpleContainerData(1);
        addDataSlots(data);
    }

    // Opens the menu on its overview.
    public static void open(ServerPlayer player, TownHallBlockEntity townHall) {
        open(player, townHall, View.OVERVIEW, 0);
    }

    // Opens (or re-opens, in place) the menu for a player, on the given view and page.
    public static void open(ServerPlayer player, TownHallBlockEntity townHall, View view, int page) {
        HallInfo info = snapshot(player, townHall, view, page);
        player.openMenu(new SwitchingMenuProvider((id, inventory, p) -> new TownHallMenu(id, inventory, townHall, info), TITLE), info::write);
    }

    // Server: what the panel shows this player.
    public static HallInfo snapshot(ServerPlayer player, TownHallBlockEntity townHall, View view, int page) {
        ServerLevel level = player.serverLevel();
        Settlement settlement = townHall.getSettlement();
        if (settlement == null) {
            return new HallInfo("", "", 0, false, 0, List.of(), View.OVERVIEW, 0);
        }
        Faction viewerFaction = FactionData.get(level).factionOf(player.getUUID());
        boolean member = viewerFaction != null && viewerFaction.id().equals(settlement.owner());
        View shown = member ? view : View.OVERVIEW;
        List<Person> people = member && shown == View.POPULATION ? people(level, settlement) : List.of();
        int pages = Math.max(1, (people.size() + PEOPLE_PER_PAGE - 1) / PEOPLE_PER_PAGE);
        return new HallInfo(settlement.name(), FactionData.get(level).nameOf(settlement.owner()), settlement.chunks().size(), member,
                member ? settlement.units().size() : 0, people, shown, Math.max(0, Math.min(page, pages - 1)));
    }

    // Everyone who belongs to the settlement, by name. Loaded units are described as they are now;
    // the others as the settlement last saw them (see Resident).
    static List<Person> people(ServerLevel anyLevel, Settlement settlement) {
        ServerLevel level = anyLevel.getServer().getLevel(settlement.dimension());
        List<Person> people = new ArrayList<>();
        settlement.residents().forEach((id, resident) -> {
            Resident shown = level != null && level.getEntity(id) instanceof Unit unit && unit.isAlive() ? Resident.of(unit) : resident;
            people.add(new Person(id, shown.name().isEmpty() ? "Unknown settler" : shown.name(),
                    shown.occupation() != null ? shown.occupation().toString() : ""));
        });
        people.sort(Comparator.comparing((Person person) -> person.name().toLowerCase(Locale.ROOT)).thenComparing(Person::id));
        return people;
    }

    public HallInfo getInfo() {
        return info;
    }

    public boolean isUnclaimed() {
        return info.isUnclaimed();
    }

    public int getControl() {
        return data.get(0);
    }

    // Runs on the server every tick while the menu is open, sending changed ContainerData values
    // (the control meter) to the client. If the owner changed (e.g. the hall was captured while
    // open), or once a second if the population changed, the menu is re-opened to show it.
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (townHall == null || viewer == null || viewer.containerMenu != this) {
            return;
        }
        if (!Objects.equals(townHall.getOwner(), openedOwner)) {
            open(viewer, townHall, info.view(), info.page());
            return;
        }
        if (++ticks % REFRESH_CHECK_TICKS == 0 && info.member() && !snapshot(viewer, townHall, info.view(), info.page()).equals(info)) {
            open(viewer, townHall, info.view(), info.page());
        }
    }

    // Runs on the server when the client reports a button click (vanilla only calls this after
    // checking stillValid, i.e. the player is still next to the hall).
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (townHall == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == CLAIM_BUTTON) {
            return claim(serverPlayer);
        }
        if (!info.member()) {
            return false;
        }
        switch (id) {
            case SHOW_POPULATION_BUTTON -> open(serverPlayer, townHall, View.POPULATION, 0);
            case SHOW_OVERVIEW_BUTTON -> open(serverPlayer, townHall, View.OVERVIEW, 0);
            case PREVIOUS_PAGE_BUTTON, NEXT_PAGE_BUTTON -> open(serverPlayer, townHall, info.view(), info.page() + (id == NEXT_PAGE_BUTTON ? 1 : -1));
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean claim(ServerPlayer player) {
        SettlementOps.FoundResult result = townHall.claim(player);
        if (result.settlement() == null) {
            player.sendSystemMessage(Component.literal("Can't claim: " + result.problem()));
            return false;
        }
        Settlement settlement = result.settlement();
        player.sendSystemMessage(Component.literal("You founded " + settlement.name() + " for "
                + FactionData.get(player.serverLevel()).nameOf(settlement.owner())));
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
