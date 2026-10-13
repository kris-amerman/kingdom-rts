package com.krisamerman.kingdomrts.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
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
// hire and fire, page through the settlers, switch automatic hiring on or off, and open the stock.
//
// Like TownHallMenu, the server builds a snapshot (WorkplaceInfo) when the panel opens and sends it
// along; after a hire or a fire it simply opens the panel again with a fresh snapshot.
public class WorkplaceMenu extends AbstractContainerMenu {
    public static final int STOCK_BUTTON = 0;
    public static final int PREVIOUS_PAGE_BUTTON = 1;
    public static final int NEXT_PAGE_BUTTON = 2;
    public static final int AUTO_HIRE_BUTTON = 3;
    // Hire candidate i is button HIRE_BUTTON + i; fire worker i is FIRE_BUTTON + i.
    public static final int HIRE_BUTTON = 100;
    public static final int FIRE_BUTTON = 1000;
    public static final int CANDIDATES_PER_PAGE = 4;
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

    // What the panel shows. candidates are only the current page's.
    public record WorkplaceInfo(ResourceLocation type, String settlementName, String occupation, int capacity, boolean autoHire,
            List<Person> workers, List<Person> candidates, int page, int pages) {
        void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(type);
            buf.writeUtf(settlementName);
            buf.writeUtf(occupation);
            buf.writeVarInt(capacity);
            buf.writeBoolean(autoHire);
            buf.writeCollection(workers, (b, person) -> person.write(b));
            buf.writeCollection(candidates, (b, person) -> person.write(b));
            buf.writeVarInt(page);
            buf.writeVarInt(pages);
        }

        static WorkplaceInfo read(FriendlyByteBuf buf) {
            return new WorkplaceInfo(buf.readResourceLocation(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readBoolean(),
                    buf.readList(Person::read), buf.readList(Person::read), buf.readVarInt(), buf.readVarInt());
        }

        public boolean isFull() {
            return workers.size() >= capacity;
        }
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

    // Opens (or re-opens) the panel for a player, showing the given page of candidates.
    public static void open(ServerPlayer player, WorkplaceBlockEntity stock, int page) {
        ServerLevel level = (ServerLevel) stock.getLevel();
        WorkplaceInfo info = snapshot(level, stock.getBlockPos(), page);
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
    public static WorkplaceInfo snapshot(ServerLevel level, BlockPos pos, int page) {
        Workplace workplace = WorkplaceOps.workplaceAt(level, pos);
        Settlement settlement = workplace != null ? SettlementData.get(level).claimAt(level.dimension(), new ChunkPos(pos)) : null;
        if (workplace == null || settlement == null) {
            return null;
        }
        WorkplaceType type = WorkplaceType.get(level, workplace.type()).orElse(null);
        List<Person> workers = new ArrayList<>();
        workplace.workers().forEach((id, name) -> workers.add(new Person(id, name)));
        List<Person> all = candidates(level, settlement, pos);
        int pages = Math.max(1, (all.size() + CANDIDATES_PER_PAGE - 1) / CANDIDATES_PER_PAGE);
        int shownPage = Math.max(0, Math.min(page, pages - 1));
        List<Person> shown = all.subList(shownPage * CANDIDATES_PER_PAGE, Math.min(all.size(), (shownPage + 1) * CANDIDATES_PER_PAGE));
        return new WorkplaceInfo(workplace.type(), settlement.name(), type != null ? type.occupation().toString() : "",
                type != null ? type.workers() : 0, workplace.autoHire(), workers, List.copyOf(shown), shownPage, pages);
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
        if (!workers.equals(info.workers()) || workplace.autoHire() != info.autoHire()
                || !candidateIds(level, stock.getBlockPos()).equals(candidateIds)) {
            open(viewer, stock, info.page());
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
        if (id == PREVIOUS_PAGE_BUTTON || id == NEXT_PAGE_BUTTON) {
            open(viewer, stock, info.page() + (id == NEXT_PAGE_BUTTON ? 1 : -1));
            return true;
        }
        Workplace workplace = WorkplaceOps.workplaceAt(level, stock.getBlockPos());
        if (workplace == null) {
            return false;
        }
        if (id == AUTO_HIRE_BUTTON) {
            WorkplaceOps.setAutoHire(level, workplace, !workplace.autoHire());
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
        open(viewer, stock, info.page()); // refresh
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
