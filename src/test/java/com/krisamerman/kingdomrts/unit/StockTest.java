package com.krisamerman.kingdomrts.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class StockTest {
    @Test
    void topsUpMatchingStacksBeforeUsingEmptySlots() {
        SimpleContainer stock = new SimpleContainer(3);
        stock.setItem(2, new ItemStack(Items.OAK_LOG, 60));
        ItemStack left = Stock.insert(stock, new ItemStack(Items.OAK_LOG, 10));
        assertTrue(left.isEmpty());
        assertEquals(64, stock.getItem(2).getCount());
        assertEquals(6, stock.getItem(0).getCount());
        assertTrue(stock.getItem(1).isEmpty());
    }

    @Test
    void returnsWhatDoesntFit() {
        SimpleContainer stock = new SimpleContainer(1);
        stock.setItem(0, new ItemStack(Items.OAK_LOG, 60));
        ItemStack left = Stock.insert(stock, new ItemStack(Items.OAK_LOG, 10));
        assertEquals(6, left.getCount());
        assertEquals(64, stock.getItem(0).getCount());
        assertEquals(10, Stock.insert(stock, new ItemStack(Items.STICK, 10)).getCount(), "No room at all for something else");
    }

    @Test
    void theOriginalStackIsLeftAlone() {
        ItemStack logs = new ItemStack(Items.OAK_LOG, 5);
        Stock.insert(new SimpleContainer(1), logs);
        assertEquals(5, logs.getCount());
    }
}
