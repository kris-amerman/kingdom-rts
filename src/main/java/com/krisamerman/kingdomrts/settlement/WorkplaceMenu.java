package com.krisamerman.kingdomrts.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.unit.LumberjackOccupation;
import com.krisamerman.kingdomrts.unit.Occupation;
import com.krisamerman.kingdomrts.unit.TreeWood;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

// The hut panel, opened by right-clicking a workplace (owners only, like its stock): what the
// workplace is, who works there, and the settlement's unemployed settlers who could. Its buttons
// hire and fire, page through the settlers, switch automatic hiring on or off, set the work area (how
// far from the hut its workers go), open the stock, and change the job's settings (for a lumberjack
// hut: replanting, and which woods to fell).
//
// Like TownHallMenu, the server builds a snapshot (WorkplaceInfo) when the panel opens and sends it
// along; after a hire or a fire it simply opens the panel again with a fresh snapshot.
public class WorkplaceMenu extends AbstractContainerMenu {
    public static final int STOCK_BUTTON = 0;
    public static final int PREVIOUS_PAGE_BUTTON = 1;
    public static final int NEXT_PAGE_BUTTON = 2;
    public static final int AUTO_HIRE_BUTTON = 3;
    public static final int SMALLER_AREA_BUTTON = 4;
    public static final int LARGER_AREA_BUTTON = 5;
    // Hire candidate i is button HIRE_BUTTON + i; fire worker i is FIRE_BUTTON + i.
    public static final int HIRE_BUTTON = 100;
    public static final int FIRE_BUTTON = 1000;
    public static final int SHOW_WORKERS_BUTTON = 6;
    public static final int SHOW_OVERVIEW_BUTTON = 7;
    public static final int SHOW_SETTINGS_BUTTON = 8;
    public static final int REPLANT_BUTTON = 9;
    // Toggling wood i (fell it or leave it standing) is button WOOD_BUTTON + i.
    public static final int WOOD_BUTTON = 2000;
    // The workers page lists this many rows (section titles included) per page.
    public static final int ROWS_PER_PAGE = 6;
    // At most this many settlers looking for work are listed.
    public static final int MAX_CANDIDATES = 64;
    // How often an open panel checks whether it's out of date (once a second).
    public static final int REFRESH_CHECK_TICKS = 20;

    // A worker or a candidate, as the panel lists them.
    public record Person(UUID id, String name) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name);
        }

        static Person read(FriendlyByteBuf buf) {
            return new Person(buf.readUUID(), buf.readUtf());
        }
    }

    // A wood type (a log block id, see TreeWood) and whether the workers fell it.
    public record Wood(ResourceLocation id, boolean felled) {
        void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(id);
            buf.writeBoolean(felled);
        }

        static Wood read(FriendlyByteBuf buf) {
            return new Wood(buf.readResourceLocation(), buf.readBoolean());
        }
    }

    // The panel's pages: the overview (details and settings), the workers page (hiring and firing)
    // and the job settings page. Which one is showing is part of what the server sends, so a refresh
    // after a click keeps the player on the same page.
    public enum View { OVERVIEW, WORKERS, SETTINGS }

    // A row of the workers or settings page: a section title, a worker, candidate or wood (by index),
    // an "empty" line under a section with nobody in it, or the replanting switch.
    public record Row(Kind kind, int index) {
        public enum Kind { WORKERS_TITLE, WORKER, NO_WORKERS, CANDIDATES_TITLE, CANDIDATE, NO_CANDIDATES, REPLANT, WOODS_TITLE, WOOD }
    }

    // What the panel shows: the workplace's details and settings, everyone working there, the
    // settlement's unemployed settlers (nearest first, up to MAX_CANDIDATES), and the job settings
    // (only for jobs that have them, i.e. lumberjacks: replanting and the known woods).
    public record WorkplaceInfo(ResourceLocation type, String settlementName, String occupation, int capacity, boolean autoHire,
            int workRadius, int maxWorkRadius, List<Person> workers, List<Person> candidates, boolean jobSettings, boolean replant,
            List<Wood> woods, View view, int page) {
        void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(type);
            buf.writeUtf(settlementName);
            buf.writeUtf(occupation);
            buf.writeVarInt(capacity);
            buf.writeBoolean(autoHire);
            buf.writeVarInt(workRadius);
            buf.writeVarInt(maxWorkRadius);
            buf.writeCollection(workers, (b, person) -> person.write(b));
            buf.writeCollection(candidates, (b, person) -> person.write(b));
            buf.writeBoolean(jobSettings);
            buf.writeBoolean(replant);
            buf.writeCollection(woods, (b, wood) -> wood.write(b));
            buf.writeEnum(view);
            buf.writeVarInt(page);
        }

        static WorkplaceInfo read(FriendlyByteBuf buf) {
            return new WorkplaceInfo(buf.readResourceLocation(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readList(Person::read), buf.readList(Person::read), buf.readBoolean(),
                    buf.readBoolean(), buf.readList(Wood::read), buf.readEnum(View.class), buf.readVarInt());
        }

        public boolean isFull() {
            return workers.size() >= capacity;
        }

        // Every row of the current page (workers or settings), in order.
        public List<Row> rows() {
            return view == View.SETTINGS ? settingsRows(woods.size()) : workerRows(workers.size(), candidates.size());
        }

        public int pages() {
            return WorkplaceMenu.pages(rows().size());
        }

        // The rows on the current page.
        public List<Row> pageRows() {
            List<Row> rows = rows();
            int from = Math.min(rows.size(), page * ROWS_PER_PAGE);
            return rows.subList(from, Math.min(rows.size(), from + ROWS_PER_PAGE));
        }
    }

    // The workers page: who works here, then who's looking for work.
    static List<Row> workerRows(int workers, int candidates) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Row.Kind.WORKERS_TITLE, 0));
        for (int i = 0; i < workers; i++) {
            rows.add(new Row(Row.Kind.WORKER, i));
        }
        if (workers == 0) {
            rows.add(new Row(Row.Kind.NO_WORKERS, 0));
        }
        rows.add(new Row(Row.Kind.CANDIDATES_TITLE, 0));
        for (int i = 0; i < candidates; i++) {
            rows.add(new Row(Row.Kind.CANDIDATE, i));
        }
        if (candidates == 0) {
            rows.add(new Row(Row.Kind.NO_CANDIDATES, 0));
        }
        return rows;
    }

    // The settings page: the replanting switch, then each wood with a fell/skip switch.
    static List<Row> settingsRows(int woods) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Row.Kind.REPLANT, 0));
        rows.add(new Row(Row.Kind.WOODS_TITLE, 0));
        for (int i = 0; i < woods; i++) {
            rows.add(new Row(Row.Kind.WOOD, i));
        }
        return rows;
    }

    private static int pages(int rows) {
        return Math.max(1, (rows + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    private final WorkplaceInfo info;
    // Server only: the workplace's stock (its block entity) and the player viewing it. Null on the client.
    @Nullable
    private final WorkplaceBlockEntity stock;
    @Nullable
    private final ServerPlayer viewer;
    // Server only: everyone looking for work when the snapshot was taken (all pages), to notice changes.
    private final Set<UUID> candidateIds;
    private int ticks;

    private WorkplaceMenu(int containerId, WorkplaceBlockEntity stock, ServerPlayer viewer, WorkplaceInfo info, Set<UUID> candidateIds) {
        super(KingdomRts.WORKPLACE_MENU.get(), containerId);
        this.stock = stock;
        this.viewer = viewer;
        this.info = info;
        this.candidateIds = candidateIds;
    }

    // Client side: built from the extra data the server wrote in open().
    public WorkplaceMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(KingdomRts.WORKPLACE_MENU.get(), containerId);
        this.stock = null;
        this.viewer = null;
        this.info = WorkplaceInfo.read(extraData);
        this.candidateIds = Set.of();
    }

    // Opens (or re-opens) the panel for a player, on the given view (and page of the workers view).
    public static void open(ServerPlayer player, WorkplaceBlockEntity stock, View view, int page) {
        ServerLevel level = (ServerLevel) stock.getLevel();
        WorkplaceInfo info = snapshot(level, stock.getBlockPos(), view, page);
        if (info == null) {
            player.openMenu(stock); // not a workplace of any settlement: just a chest
            return;
        }
        Set<UUID> candidateIds = candidateIds(level, stock.getBlockPos());
        // Switching in place, so refreshing the panel (or coming back from the stock) doesn't move the mouse.
        player.openMenu(new SwitchingMenuProvider((id, inventory, p) -> new WorkplaceMenu(id, stock, player, info, candidateIds),
                stock.getBlockState().getBlock().getName()), info::write);
    }

    // Server: what the panel shows for the workplace at pos, or null if it isn't a workplace.
    @Nullable
    public static WorkplaceInfo snapshot(ServerLevel level, BlockPos pos, View view, int page) {
        Workplace workplace = WorkplaceOps.workplaceAt(level, pos);
        Settlement settlement = workplace != null ? SettlementData.get(level).claimAt(level.dimension(), new ChunkPos(pos)) : null;
        if (workplace == null || settlement == null) {
            return null;
        }
        WorkplaceType type = WorkplaceType.get(level, workplace.type()).orElse(null);
        List<Person> workers = new ArrayList<>();
        workplace.workers().forEach((id, name) -> workers.add(new Person(id, name)));
        List<Person> all = candidates(level, settlement, pos);
        List<Person> candidates = List.copyOf(all.subList(0, Math.min(all.size(), MAX_CANDIDATES)));
        boolean jobSettings = type != null && Occupation.get(level, type.occupation()).orElse(null) instanceof LumberjackOccupation;
        List<Wood> woods = jobSettings ? woods(workplace) : List.of();
        View shownView = view == View.SETTINGS && !jobSettings ? View.OVERVIEW : view;
        int rows = (shownView == View.SETTINGS ? settingsRows(woods.size()) : workerRows(workers.size(), candidates.size())).size();
        int shownPage = Math.max(0, Math.min(page, pages(rows) - 1));
        return new WorkplaceInfo(workplace.type(), settlement.name(), type != null ? type.occupation().toString() : "",
                type != null ? type.workers() : 0, workplace.autoHire(), workplace.workRadius(),
                type != null ? type.maxWorkRadius() : WorkplaceType.DEFAULT_MAX_WORK_RADIUS, workers, candidates, jobSettings,
                workplace.replant(), woods, shownView, shownPage);
    }

    // Every known wood (see TreeWood), and whether the workplace's workers fell it.
    private static List<Wood> woods(Workplace workplace) {
        return TreeWood.woods().stream().map(id -> new Wood(id, workplace.fells(id))).toList();
    }

    // The settlement's loaded units with no occupation, nearest to the workplace first. (An unloaded
    // settler's occupation isn't known, so only loaded ones are offered.)
    static List<Person> candidates(ServerLevel level, Settlement settlement, BlockPos pos) {
        List<Unit> units = new ArrayList<>();
        for (UUID id : settlement.units()) {
            if (level.getEntity(id) instanceof Unit unit && unit.isAlive() && unit.getOccupationId() == null) {
                units.add(unit);
            }
        }
        units.sort(Comparator.comparingDouble(unit -> unit.distanceToSqr(pos.getCenter())));
        return units.stream().map(unit -> new Person(unit.getUUID(), unit.getName().getString())).toList();
    }

    private static Set<UUID> candidateIds(ServerLevel level, BlockPos pos) {
        Settlement settlement = SettlementData.get(level).claimAt(level.dimension(), new ChunkPos(pos));
        if (settlement == null) {
            return Set.of();
        }
        Set<UUID> ids = new HashSet<>();
        candidates(level, settlement, pos).forEach(person -> ids.add(person.id()));
        return ids;
    }

    public WorkplaceInfo getInfo() {
        return info;
    }

    // Runs on the server every tick while the panel is open. Things can change without the viewer
    // pressing anything (a settler takes a job by automatic hiring, a worker dies, a new settler
    // arrives), so once a second it checks whether the panel is out of date and, if so, re-opens it.
    // Settlers looking for work are compared as a set, not in order: they're listed nearest first,
    // and their order changes whenever they wander about.
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (stock == null || viewer == null || viewer.containerMenu != this || ++ticks % REFRESH_CHECK_TICKS != 0
                || !(stock.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Workplace workplace = WorkplaceOps.workplaceAt(level, stock.getBlockPos());
        if (workplace == null) {
            return; // the hut was removed; stillValid closes the panel
        }
        List<Person> workers = new ArrayList<>();
        workplace.workers().forEach((id, name) -> workers.add(new Person(id, name)));
        if (!workers.equals(info.workers()) || workplace.autoHire() != info.autoHire() || workplace.workRadius() != info.workRadius()
                || workplace.replant() != info.replant() || (info.jobSettings() && !woods(workplace).equals(info.woods()))
                || !candidateIds(level, stock.getBlockPos()).equals(candidateIds)) {
            open(viewer, stock, info.view(), info.page());
        }
    }

    // Runs on the server when the client reports a button click (vanilla checks stillValid first).
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (stock == null || viewer == null || !(stock.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        if (id == STOCK_BUTTON) {
            WorkplaceStockMenu.open(viewer, stock);
            return true;
        }
        if (id == SHOW_WORKERS_BUTTON || id == SHOW_OVERVIEW_BUTTON || id == SHOW_SETTINGS_BUTTON) {
            open(viewer, stock, id == SHOW_WORKERS_BUTTON ? View.WORKERS : id == SHOW_SETTINGS_BUTTON ? View.SETTINGS : View.OVERVIEW, 0);
            return true;
        }
        if (id == PREVIOUS_PAGE_BUTTON || id == NEXT_PAGE_BUTTON) {
            open(viewer, stock, info.view(), info.page() + (id == NEXT_PAGE_BUTTON ? 1 : -1));
            return true;
        }
        Workplace workplace = WorkplaceOps.workplaceAt(level, stock.getBlockPos());
        if (workplace == null) {
            return false;
        }
        if (id == AUTO_HIRE_BUTTON) {
            WorkplaceOps.setAutoHire(level, workplace, !workplace.autoHire());
        } else if (id == SMALLER_AREA_BUTTON || id == LARGER_AREA_BUTTON) {
            int step = id == LARGER_AREA_BUTTON ? WorkArea.RADIUS_STEP : -WorkArea.RADIUS_STEP;
            WorkplaceOps.setWorkRadius(level, workplace, workplace.workRadius() + step);
        } else if (id == REPLANT_BUTTON && info.jobSettings()) {
            WorkplaceOps.setReplant(level, workplace, !workplace.replant());
        } else if (id >= WOOD_BUTTON && id - WOOD_BUTTON < info.woods().size()) {
            ResourceLocation wood = info.woods().get(id - WOOD_BUTTON).id();
            WorkplaceOps.setFells(level, workplace, wood, !workplace.fells(wood));
        } else if (id >= FIRE_BUTTON && id - FIRE_BUTTON < info.workers().size()) {
            // Firing by hand switches the hut to manual hiring, so the unit isn't simply re-hired.
            WorkplaceOps.setAutoHire(level, workplace, false);
            WorkplaceOps.fire(level, workplace, info.workers().get(id - FIRE_BUTTON).id());
        } else if (id >= HIRE_BUTTON && id - HIRE_BUTTON < info.candidates().size()) {
            Person candidate = info.candidates().get(id - HIRE_BUTTON);
            String problem = level.getEntity(candidate.id()) instanceof Unit unit
                    ? WorkplaceOps.hire(level, unit, stock.getBlockPos())
                    : candidate.name() + " is no longer nearby";
            if (problem != null) {
                viewer.displayClientMessage(Component.literal(problem), true);
                return false;
            }
        } else {
            return false;
        }
        open(viewer, stock, info.view(), info.page()); // refresh, on the same view and page
        return true;
    }

    // Closes the panel if the hut is gone, the player walks away, or loses access (e.g. on capture).
    @Override
    public boolean stillValid(Player player) {
        return stock == null || (Container.stillValidBlockEntity(stock, player) && stock.canAccess(player));
    }

    // Required by AbstractContainerMenu for shift-clicking; this menu has no item slots.
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
