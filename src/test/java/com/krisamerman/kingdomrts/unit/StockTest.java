package com.krisamerman.kingdomrts.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

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

    @Test
    void takesAllOrNothing() {
        SimpleContainer stock = new SimpleContainer(3);
        stock.setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
        stock.setItem(2, new ItemStack(Items.OAK_PLANKS, 4));
        stock.setItem(1, new ItemStack(Items.TORCH, 1));
        assertTrue(Stock.has(stock, List.of(new ItemStack(Items.OAK_PLANKS, 6), new ItemStack(Items.TORCH))));
        assertTrue(Stock.take(stock, List.of(new ItemStack(Items.OAK_PLANKS, 6))), "Across two stacks");
        assertEquals(1, stock.countItem(Items.OAK_PLANKS));
        assertFalse(Stock.take(stock, List.of(new ItemStack(Items.OAK_PLANKS, 1), new ItemStack(Items.TORCH, 2))), "Not enough torches");
        assertEquals(1, stock.countItem(Items.OAK_PLANKS), "Nothing taken when it can't take everything");
        assertEquals(1, stock.countItem(Items.TORCH));
    }

    @Test
    void theBestToolIsTheFastestThatGetsTheDrops() {
        SimpleContainer stock = new SimpleContainer(4);
        stock.setItem(0, new ItemStack(Items.STICK, 10)); // not a tool
        stock.setItem(1, new ItemStack(Items.WOODEN_SWORD));
        assertEquals(1, Stock.bestToolFor(stock, Blocks.COBWEB.defaultBlockState()), "A sword cuts cobweb");
        assertEquals(-1, Stock.bestToolFor(stock, Blocks.DIRT.defaultBlockState()), "Nothing beats bare hands on dirt");
        assertEquals(-1, Stock.bestToolFor(new SimpleContainer(2), Blocks.COBWEB.defaultBlockState()), "An empty stock has no tools");
    }
}
