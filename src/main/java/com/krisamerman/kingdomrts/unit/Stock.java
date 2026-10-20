package com.krisamerman.kingdomrts.unit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// Moving items into and out of a container (a workplace's or a site's stock): topping up matching
// stacks first, then filling empty slots, like a hopper would; and taking a set of items out, all or
// nothing. Works on any Container, so it can be unit tested.
public final class Stock {
    private Stock() {
    }

    // Puts as much of stack into the container as fits. Returns what didn't fit (empty if it all did).
    public static ItemStack insert(Container container, ItemStack stack) {
        ItemStack left = stack.copy();
        for (int i = 0; i < container.getContainerSize() && !left.isEmpty(); i++) {
            ItemStack existing = container.getItem(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, left)) {
                int room = Math.min(existing.getMaxStackSize(), container.getMaxStackSize()) - existing.getCount();
                int moved = Math.min(room, left.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    left.shrink(moved);
                }
            }
        }
        for (int i = 0; i < container.getContainerSize() && !left.isEmpty(); i++) {
            if (container.getItem(i).isEmpty() && container.canPlaceItem(i, left)) {
                int moved = Math.min(left.getCount(), Math.min(left.getMaxStackSize(), container.getMaxStackSize()));
                container.setItem(i, left.split(moved));
            }
        }
        if (left.getCount() != stack.getCount()) {
            container.setChanged();
        }
        return left;
    }

    // Whether the container holds all of these (counting stacks of the same item together).
    public static boolean has(Container container, List<ItemStack> stacks) {
        Map<Item, Integer> needed = new HashMap<>();
        stacks.forEach(stack -> needed.merge(stack.getItem(), stack.getCount(), Integer::sum));
        return needed.entrySet().stream().allMatch(entry -> container.countItem(entry.getKey()) >= entry.getValue());
    }

    // Takes these out of the container, all or nothing. Returns false (taking nothing) if it doesn't
    // hold them all.
    public static boolean take(Container container, List<ItemStack> stacks) {
        if (!has(container, stacks)) {
            return false;
        }
        for (ItemStack stack : stacks) {
            int left = stack.getCount();
            for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
                ItemStack existing = container.getItem(i);
                if (existing.is(stack.getItem())) {
                    int taken = Math.min(left, existing.getCount());
                    existing.shrink(taken);
                    left -= taken;
                }
            }
        }
        container.setChanged();
        return true;
    }
}
