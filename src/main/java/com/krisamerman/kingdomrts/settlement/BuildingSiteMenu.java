package com.krisamerman.kingdomrts.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.building.BuildingRules;
import com.krisamerman.kingdomrts.building.BuildingType;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// The construction site panel, opened by right-clicking a construction site block (owners only,
// like its stock): what's being built, how far along it is, what's in the way, and the materials
// still needed (with how many are already in the site's stock). Buttons open the full materials
// list and the stock. When it notices the building is finished, it completes it (see
// BuildingOps#completeIfDone). While the builders clear out the site's stock it says so; once the
// stock is empty the site block goes, and the panel with it.
//
// Like the hut panel, the server builds a snapshot (SiteInfo) and sends it when the panel opens,
// checks once a second whether it's out of date (blocks get placed, the stock changes) and, if so,
// opens it again with a fresh one, in place.
public class BuildingSiteMenu extends AbstractContainerMenu {
    public static final int STOCK_BUTTON = 0;
    public static final int SHOW_MATERIALS_BUTTON = 1;
    public static final int SHOW_OVERVIEW_BUTTON = 2;
    public static final int PREVIOUS_PAGE_BUTTON = 3;
    public static final int NEXT_PAGE_BUTTON = 4;
    public static final int SHOW_BUILDERS_BUTTON = 5;
    // Assigning available builder i is button ASSIGN_BUTTON + i; unassigning assigned builder i, UNASSIGN_BUTTON + i.
    public static final int ASSIGN_BUTTON = 200;
    public static final int UNASSIGN_BUTTON = 400;
    // The builders page lists this many rows (section titles included) per page.
    public static final int ROWS_PER_PAGE = 6;
    // The overview shows this many materials; the materials page shows this many a page.
    public static final int OVERVIEW_MATERIALS = 3;
    public static final int MATERIALS_PER_PAGE = 6;
    public static final int REFRESH_CHECK_TICKS = 20;

    public enum View { OVERVIEW, MATERIALS, BUILDERS }

    // A row of the builders page: a section title, an assigned or available builder (by index), or
    // an "empty" line under a section with nobody in it.
    public record Row(Kind kind, int index) {
        public enum Kind { ASSIGNED_TITLE, ASSIGNED, NO_ASSIGNED, AVAILABLE_TITLE, AVAILABLE, NO_AVAILABLE }
    }

    // A material still needed: how many the missing blocks take, and how many are in the site's stock.
    public record Material(Item item, int needed, int onSite) {
        void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(BuiltInRegistries.ITEM.getKey(item));
            buf.writeVarInt(needed);
            buf.writeVarInt(onSite);
        }

        static Material read(FriendlyByteBuf buf) {
            return new Material(BuiltInRegistries.ITEM.get(buf.readResourceLocation()), buf.readVarInt(), buf.readVarInt());
        }
    }

    // What the panel shows. loaded is false when the site's chunks aren't loaded (then only the names
    // mean anything). built is true once the building is finished and its stock is being cleared out;
    // demolishing, while builders take it apart. total counts the building's blocks (not the air it needs clear); placed, how
    // many of them are in place. Materials are most needed first. assigned are the builders on this
    // site (up to maxBuilders), available the settlement's builders not working on any site.
    public record SiteInfo(String buildingName, String settlementName, boolean loaded, boolean built, boolean demolishing, int total, int placed, int inTheWay,
            List<Material> materials, List<PanelEntry> assigned, List<PanelEntry> available, int maxBuilders, View view, int page) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(buildingName);
            buf.writeUtf(settlementName);
            buf.writeBoolean(loaded);
            buf.writeBoolean(built);
            buf.writeBoolean(demolishing);
            buf.writeVarInt(total);
            buf.writeVarInt(placed);
            buf.writeVarInt(inTheWay);
            buf.writeCollection(materials, (b, material) -> material.write(b));
            buf.writeCollection(assigned, (b, entry) -> entry.write(b));
            buf.writeCollection(available, (b, entry) -> entry.write(b));
            buf.writeVarInt(maxBuilders);
            buf.writeEnum(view);
            buf.writeVarInt(page);
        }

        static SiteInfo read(FriendlyByteBuf buf) {
            return new SiteInfo(buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readList(Material::read), buf.readList(PanelEntry::read), buf.readList(PanelEntry::read), buf.readVarInt(),
                    buf.readEnum(View.class), buf.readVarInt());
        }

        public boolean isFinished() {
            return loaded && placed == total && inTheWay == 0;
        }

        public boolean isFull() {
            return assigned.size() >= maxBuilders;
        }

        public int pages() {
            return view == View.BUILDERS ? pagesOf(builderRows(assigned.size(), available.size()).size(), ROWS_PER_PAGE)
                    : pagesOf(materials.size(), MATERIALS_PER_PAGE);
        }

        // The rows on the current page of the builders page.
        public List<Row> pageRows() {
            List<Row> rows = builderRows(assigned.size(), available.size());
            int from = Math.min(rows.size(), page * ROWS_PER_PAGE);
            return rows.subList(from, Math.min(rows.size(), from + ROWS_PER_PAGE));
        }

        // The materials on the current page of the materials view.
        public List<Material> pageMaterials() {
            int from = Math.min(materials.size(), page * MATERIALS_PER_PAGE);
            return materials.subList(from, Math.min(materials.size(), from + MATERIALS_PER_PAGE));
        }
    }

    // The builders page: who works on this site, then the builders who could.
    static List<Row> builderRows(int assigned, int available) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Row.Kind.ASSIGNED_TITLE, 0));
        for (int i = 0; i < assigned; i++) {
            rows.add(new Row(Row.Kind.ASSIGNED, i));
        }
        if (assigned == 0) {
            rows.add(new Row(Row.Kind.NO_ASSIGNED, 0));
        }
        rows.add(new Row(Row.Kind.AVAILABLE_TITLE, 0));
        for (int i = 0; i < available; i++) {
            rows.add(new Row(Row.Kind.AVAILABLE, i));
        }
        if (available == 0) {
            rows.add(new Row(Row.Kind.NO_AVAILABLE, 0));
        }
        return rows;
    }

    private static int pagesOf(int items, int perPage) {
        return Math.max(1, (items + perPage - 1) / perPage);
    }

    private final SiteInfo info;
    // Server only: the construction site block and the player viewing it. Null on the client.
    @Nullable
    private final ConstructionSiteBlockEntity core;
    @Nullable
    private final ServerPlayer viewer;
    private int ticks;

    private BuildingSiteMenu(int containerId, ConstructionSiteBlockEntity core, ServerPlayer viewer, SiteInfo info) {
        super(KingdomRts.BUILDING_SITE_MENU.get(), containerId);
        this.core = core;
        this.viewer = viewer;
        this.info = info;
    }

    // Client side: built from the extra data the server wrote in open().
    public BuildingSiteMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(KingdomRts.BUILDING_SITE_MENU.get(), containerId);
        this.core = null;
        this.viewer = null;
        this.info = SiteInfo.read(extraData);
    }

    // Opens (or re-opens) the panel for a player, on the given view and page. A site found finished
    // is completed instead, and the player told.
    public static void open(ServerPlayer player, ConstructionSiteBlockEntity core, View view, int page) {
        if (completeIfDone(player, core)) {
            return;
        }
        SiteInfo info = snapshot((ServerLevel) core.getLevel(), core, view, page);
        if (info == null) {
            player.openMenu(core); // no longer a site: just its stock
            return;
        }
        player.openMenu(new SwitchingMenuProvider((id, inventory, p) -> new BuildingSiteMenu(id, core, player, info),
                core.getBlockState().getBlock().getName()), info::write);
    }

    // Completes the site if it's finished, telling the player once its site is closed (its stock
    // empty). Returns whether the site is closed now.
    private static boolean completeIfDone(ServerPlayer player, ConstructionSiteBlockEntity site) {
        Settlement settlement = site.getSettlement();
        Building building = settlement != null ? BuildingOps.siteAt(settlement, site.getBlockPos()) : null;
        if (building == null || !(site.getLevel() instanceof ServerLevel level) || !BuildingOps.completeIfDone(level, settlement, building)
                || BuildingOps.siteAt(settlement, site.getBlockPos()) != null) {
            return false;
        }
        String name = BuildingType.get(level, building.type()).map(BuildingType::name).orElse(building.type().toString());
        player.displayClientMessage(Component.literal("The " + name + " is finished"), true);
        return true;
    }

    // Server: what the panel shows for the site whose block this is, or null if it isn't one.
    @Nullable
    public static SiteInfo snapshot(ServerLevel level, ConstructionSiteBlockEntity core, View view, int page) {
        Settlement settlement = core.getSettlement();
        Building building = settlement != null ? BuildingOps.siteAt(settlement, core.getBlockPos()) : null;
        if (building == null) {
            return null;
        }
        String name = BuildingType.get(level, building.type()).map(BuildingType::name).orElse(building.type().toString());
        BuildingOps.RemainingResult result = BuildingOps.remaining(level, building);
        BuildingRules.Remaining remaining = result.remaining();
        List<PanelEntry> assigned = building.builders().entrySet().stream()
                .map(builder -> new PanelEntry(builder.getKey(), builder.getValue(), "")).toList();
        List<PanelEntry> available = BuilderOps.idleBuilders(level, settlement).stream()
                .map(builder -> new PanelEntry(builder.id(), builder.name(), "")).toList();
        int maxBuilders = BuildingType.get(level, building.type()).map(BuildingType::maxBuilders).orElse(BuildingType.DEFAULT_MAX_BUILDERS);
        if (remaining == null) {
            return new SiteInfo(name, settlement.name(), false, building.isBuilt(), building.demolishing(), 0, 0, 0, List.of(), assigned, available, maxBuilders, View.OVERVIEW, 0);
        }
        List<Material> materials = materials(remaining.materials(), core);
        int total = total(level, building);
        int pages = view == View.BUILDERS ? pagesOf(builderRows(assigned.size(), available.size()).size(), ROWS_PER_PAGE)
                : pagesOf(materials.size(), MATERIALS_PER_PAGE);
        return new SiteInfo(name, settlement.name(), true, building.isBuilt(), building.demolishing(), total, total - remaining.missing().size(), remaining.obstructions().size(),
                materials, assigned, available, maxBuilders, view, Math.max(0, Math.min(page, pages - 1)));
    }

    // The building's blocks, not counting the air it needs clear.
    private static int total(ServerLevel level, Building building) {
        var placed = BuildingOps.placed(level, building);
        return placed == null ? 0 : (int) placed.stream().filter(part -> !part.state().isAir()).count();
    }

    // The materials needed, most first, with how many of each are already in the site's stock.
    static List<Material> materials(Map<Item, Integer> needed, Container stock) {
        List<Material> materials = new ArrayList<>();
        needed.forEach((item, count) -> materials.add(new Material(item, count, stock.countItem(item))));
        materials.sort(Comparator.comparingInt(Material::needed).reversed()
                .thenComparing(material -> BuiltInRegistries.ITEM.getKey(material.item()).toString()));
        return materials;
    }

    public SiteInfo getInfo() {
        return info;
    }

    // Runs on the server every tick while the panel is open: once a second, re-opens it if what it
    // shows has changed (blocks placed or broken, materials added to the stock).
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (core == null || viewer == null || viewer.containerMenu != this || ++ticks % REFRESH_CHECK_TICKS != 0
                || !(core.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (completeIfDone(viewer, core)) {
            return; // the site block is gone, so stillValid closes the panel
        }
        SiteInfo now = snapshot(level, core, info.view(), info.page());
        if (now != null && !now.equals(info)) {
            open(viewer, core, info.view(), info.page());
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (core == null || viewer == null) {
            return false;
        }
        switch (id) {
            case STOCK_BUTTON -> BuildingSiteStockMenu.open(viewer, core);
            case SHOW_MATERIALS_BUTTON -> open(viewer, core, View.MATERIALS, 0);
            case SHOW_OVERVIEW_BUTTON -> open(viewer, core, View.OVERVIEW, 0);
            case SHOW_BUILDERS_BUTTON -> open(viewer, core, View.BUILDERS, 0);
            case PREVIOUS_PAGE_BUTTON, NEXT_PAGE_BUTTON -> open(viewer, core, info.view(), info.page() + (id == NEXT_PAGE_BUTTON ? 1 : -1));
            default -> {
                return assignOrUnassign(id);
            }
        }
        return true;
    }

    // The builders page's Assign and Unassign buttons.
    private boolean assignOrUnassign(int id) {
        Settlement settlement = core.getSettlement();
        Building building = settlement != null ? BuildingOps.siteAt(settlement, core.getBlockPos()) : null;
        if (building == null || !(core.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        if (id >= UNASSIGN_BUTTON && id - UNASSIGN_BUTTON < info.assigned().size()) {
            BuilderOps.unassign(level, settlement, building, info.assigned().get(id - UNASSIGN_BUTTON).id());
        } else if (id >= ASSIGN_BUTTON && id - ASSIGN_BUTTON < info.available().size()) {
            String problem = BuilderOps.assign(level, settlement, building, info.available().get(id - ASSIGN_BUTTON).id());
            if (problem != null) {
                viewer.displayClientMessage(Component.literal(problem), true);
                return false;
            }
        } else {
            return false;
        }
        open(viewer, core, info.view(), info.page()); // refresh, on the same page
        return true;
    }

    // Closes the panel if the site block is gone, the player walks away, or loses access.
    @Override
    public boolean stillValid(Player player) {
        return core == null || (Container.stillValidBlockEntity(core, player) && core.canAccess(player));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
