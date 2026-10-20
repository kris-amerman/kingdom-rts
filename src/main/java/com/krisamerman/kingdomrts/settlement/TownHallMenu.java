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
//   - Builders (members only): the settlement's builders, each with what they're working on and a
//     Fire button, then its unemployed settlers, each with a Hire button (hiring a builder is done
//     here, standing in for a builder's guild; see BuilderOps).
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
    public static final int SHOW_BUILDERS_BUTTON = 5;
    // Hiring unemployed settler i is button HIRE_BUILDER_BUTTON + i; firing builder i, FIRE_BUILDER_BUTTON + i.
    public static final int HIRE_BUILDER_BUTTON = 200;
    public static final int FIRE_BUILDER_BUTTON = 400;
    public static final int PEOPLE_PER_PAGE = 9;
    // The builders page lists this many rows (section titles included) per page.
    public static final int ROWS_PER_PAGE = 6;
    public static final int REFRESH_CHECK_TICKS = 20;
    private static final Component TITLE = Component.translatable("container.kingdomrts.town_hall");

    public enum View { OVERVIEW, POPULATION, BUILDERS }

    // A row of the builders page: a section title, a builder or unemployed settler (by index), or an
    // "empty" line under a section with nobody in it.
    public record Row(Kind kind, int index) {
        public enum Kind { BUILDERS_TITLE, BUILDER, NO_BUILDERS, UNEMPLOYED_TITLE, UNEMPLOYED, NO_UNEMPLOYED }
    }

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
    // to the owning faction; only members get the population and builders. people is only filled in
    // on the population page, builders and unemployed on the builders page.
    public record HallInfo(String settlementName, String ownerName, int claimedChunks, boolean member, int population,
            int builderCount, List<Person> people, List<PanelEntry> builders, List<PanelEntry> unemployed, View view, int page) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(settlementName);
            buf.writeUtf(ownerName);
            buf.writeVarInt(claimedChunks);
            buf.writeBoolean(member);
            buf.writeVarInt(population);
            buf.writeVarInt(builderCount);
            buf.writeCollection(people, (b, person) -> person.write(b));
            buf.writeCollection(builders, (b, entry) -> entry.write(b));
            buf.writeCollection(unemployed, (b, entry) -> entry.write(b));
            buf.writeEnum(view);
            buf.writeVarInt(page);
        }

        static HallInfo read(FriendlyByteBuf buf) {
            return new HallInfo(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readList(Person::read), buf.readList(PanelEntry::read), buf.readList(PanelEntry::read), buf.readEnum(View.class),
                    buf.readVarInt());
        }

        public boolean isUnclaimed() {
            return settlementName.isEmpty();
        }

        public int pages() {
            return view == View.BUILDERS ? pagesOf(builderRows(builders.size(), unemployed.size()).size(), ROWS_PER_PAGE)
                    : pagesOf(people.size(), PEOPLE_PER_PAGE);
        }

        // The rows on the current page of the builders page.
        public List<Row> pageRows() {
            List<Row> rows = builderRows(builders.size(), unemployed.size());
            int from = Math.min(rows.size(), page * ROWS_PER_PAGE);
            return rows.subList(from, Math.min(rows.size(), from + ROWS_PER_PAGE));
        }

        // The people on the current page.
        public List<Person> pagePeople() {
            int from = Math.min(people.size(), page * PEOPLE_PER_PAGE);
            return people.subList(from, Math.min(people.size(), from + PEOPLE_PER_PAGE));
        }
    }

    // The builders page: the builders, then the settlers looking for work.
    static List<Row> builderRows(int builders, int unemployed) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Row.Kind.BUILDERS_TITLE, 0));
        for (int i = 0; i < builders; i++) {
            rows.add(new Row(Row.Kind.BUILDER, i));
        }
        if (builders == 0) {
            rows.add(new Row(Row.Kind.NO_BUILDERS, 0));
        }
        rows.add(new Row(Row.Kind.UNEMPLOYED_TITLE, 0));
        for (int i = 0; i < unemployed; i++) {
            rows.add(new Row(Row.Kind.UNEMPLOYED, i));
        }
        if (unemployed == 0) {
            rows.add(new Row(Row.Kind.NO_UNEMPLOYED, 0));
        }
        return rows;
    }

    private static int pagesOf(int items, int perPage) {
        return Math.max(1, (items + perPage - 1) / perPage);
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
            return new HallInfo("", "", 0, false, 0, 0, List.of(), List.of(), List.of(), View.OVERVIEW, 0);
        }
        Faction viewerFaction = FactionData.get(level).factionOf(player.getUUID());
        boolean member = viewerFaction != null && viewerFaction.id().equals(settlement.owner());
        View shown = member ? view : View.OVERVIEW;
        List<Person> people = member && shown == View.POPULATION ? people(level, settlement) : List.of();
        List<BuilderOps.Listed> pool = member ? BuilderOps.builders(level, settlement) : List.of();
        List<PanelEntry> builders = List.of();
        List<PanelEntry> unemployed = List.of();
        if (shown == View.BUILDERS) {
            builders = pool.stream().map(builder -> {
                Building site = BuilderOps.siteOf(settlement, builder.id());
                return new PanelEntry(builder.id(), builder.name(), site != null ? BuilderOps.typeName(level, site) : "idle");
            }).toList();
            unemployed = WorkplaceMenu.candidates(level, settlement, townHall.getBlockPos()).stream()
                    .map(person -> new PanelEntry(person.id(), person.name(), "")).toList();
        }
        int pages = shown == View.BUILDERS ? pagesOf(builderRows(builders.size(), unemployed.size()).size(), ROWS_PER_PAGE)
                : pagesOf(people.size(), PEOPLE_PER_PAGE);
        return new HallInfo(settlement.name(), FactionData.get(level).nameOf(settlement.owner()), settlement.chunks().size(), member,
                member ? settlement.units().size() : 0, pool.size(), people, builders, unemployed, shown, Math.max(0, Math.min(page, pages - 1)));
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
            case SHOW_BUILDERS_BUTTON -> open(serverPlayer, townHall, View.BUILDERS, 0);
            case PREVIOUS_PAGE_BUTTON, NEXT_PAGE_BUTTON -> open(serverPlayer, townHall, info.view(), info.page() + (id == NEXT_PAGE_BUTTON ? 1 : -1));
            default -> {
                return hireOrFire(serverPlayer, id);
            }
        }
        return true;
    }

    // The builders page's Hire and Fire buttons.
    private boolean hireOrFire(ServerPlayer player, int id) {
        Settlement settlement = townHall.getSettlement();
        ServerLevel level = player.serverLevel();
        if (settlement == null) {
            return false;
        }
        if (id >= FIRE_BUILDER_BUTTON && id - FIRE_BUILDER_BUTTON < info.builders().size()) {
            BuilderOps.fire(level, settlement, info.builders().get(id - FIRE_BUILDER_BUTTON).id());
        } else if (id >= HIRE_BUILDER_BUTTON && id - HIRE_BUILDER_BUTTON < info.unemployed().size()) {
            PanelEntry settler = info.unemployed().get(id - HIRE_BUILDER_BUTTON);
            String problem = level.getEntity(settler.id()) instanceof Unit unit ? BuilderOps.hire(level, settlement, unit)
                    : settler.name() + " is no longer nearby";
            if (problem != null) {
                player.displayClientMessage(Component.literal(problem), true);
                return false;
            }
        } else {
            return false;
        }
        open(player, townHall, info.view(), info.page()); // refresh, on the same page
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
