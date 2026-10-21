package com.krisamerman.kingdomrts.unit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// Rules for carrying things between containers (a unit's inventory, a site's stock, settlement
// storage), kept free of the world so they can be unit-tested. Nothing is ever dropped: what doesn't
// fit stays where it was.
public final class Hauling {
    private Hauling() {
    }

    // What still has to be fetched for these costs (in their order, e.g. a site's missing blocks in
    // build order): each item's total, less what's already in the stock and what's being carried.
    public static Map<Item, Integer> stillWanted(List<ItemStack> costs, Container stock, Container carried) {
        Map<Item, Integer> wanted = new LinkedHashMap<>();
        costs.forEach(cost -> wanted.merge(cost.getItem(), cost.getCount(), Integer::sum));
        wanted.replaceAll((item, count) -> count - stock.countItem(item) - carried.countItem(item));
        wanted.values().removeIf(count -> count <= 0);
        return wanted;
    }

    // Whether the container holds any of these items.
    public static boolean hasAny(Container container, Map<Item, Integer> items) {
        return items.keySet().stream().anyMatch(item -> container.countItem(item) > 0);
    }

    // Moves the wanted items (in the map's order, up to each count) from one container to another,
    // as much as fits. Returns how many items moved; the counts in wanted go down by what moved.
    public static int moveWanted(Container from, Container to, Map<Item, Integer> wanted) {
        int moved = 0;
        for (Map.Entry<Item, Integer> entry : wanted.entrySet()) {
            for (int i = 0; i < from.getContainerSize() && entry.getValue() > 0; i++) {
                ItemStack stack = from.getItem(i);
                if (!stack.is(entry.getKey())) {
                    continue;
                }
                ItemStack part = stack.copyWithCount(Math.min(stack.getCount(), entry.getValue()));
                int fitted = part.getCount() - Stock.insert(to, part).getCount();
                if (fitted == 0) {
                    break; // no room for any more of it
                }
                stack.shrink(fitted);
                entry.setValue(entry.getValue() - fitted);
                moved += fitted;
            }
        }
        wanted.values().removeIf(count -> count <= 0);
        if (moved > 0) {
            from.setChanged();
        }
        return moved;
    }

    // Moves everything from one container to another, as much as fits. Returns how many items moved.
    public static int moveAll(Container from, Container to) {
        int moved = 0;
        for (int i = 0; i < from.getContainerSize(); i++) {
            ItemStack stack = from.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack left = Stock.insert(to, stack);
            moved += stack.getCount() - left.getCount();
            from.setItem(i, left);
        }
        return moved;
    }

    // Whether any of the first container's items would fit, at least partly, in the second.
    public static boolean hasRoomForAny(Container items, Container to) {
        for (int i = 0; i < items.getContainerSize(); i++) {
            ItemStack stack = items.getItem(i);
            if (!stack.isEmpty() && hasRoomFor(to, stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRoomFor(Container to, ItemStack stack) {
        for (int i = 0; i < to.getContainerSize(); i++) {
            ItemStack existing = to.getItem(i);
            if (existing.isEmpty() || (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }
}
