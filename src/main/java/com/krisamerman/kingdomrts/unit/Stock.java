package com.krisamerman.kingdomrts.unit;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

// Moving items into a container (a workplace's stock, say): topping up matching stacks first, then
// filling empty slots, like a hopper would. Works on any Container, so it can be unit tested.
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
}
