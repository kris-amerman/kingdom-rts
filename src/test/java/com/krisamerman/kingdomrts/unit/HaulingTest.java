package com.krisamerman.kingdomrts.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class HaulingTest {
    @Test
    void whatsStillWantedLeavesOutWhatsOnSiteAndBeingCarried() {
        SimpleContainer site = new SimpleContainer(3);
        site.setItem(0, new ItemStack(Items.OAK_PLANKS, 5));
        SimpleContainer carried = new SimpleContainer(3);
        carried.setItem(0, new ItemStack(Items.COBBLESTONE, 2));
        List<ItemStack> costs = List.of(new ItemStack(Items.COBBLESTONE), new ItemStack(Items.OAK_PLANKS, 4),
                new ItemStack(Items.COBBLESTONE, 3), new ItemStack(Items.TORCH));
        Map<Item, Integer> wanted = Hauling.stillWanted(costs, site, carried);
        assertEquals(List.of(Items.COBBLESTONE, Items.TORCH), List.copyOf(wanted.keySet()), "In the costs' order; the planks are all on site");
        assertEquals(2, wanted.get(Items.COBBLESTONE));
        assertEquals(1, wanted.get(Items.TORCH));
    }

    @Test
    void movesOnlyWhatsWantedAndWhatFits() {
        SimpleContainer storage = new SimpleContainer(3);
        storage.setItem(0, new ItemStack(Items.DIRT, 10));
        storage.setItem(1, new ItemStack(Items.COBBLESTONE, 40));
        storage.setItem(2, new ItemStack(Items.OAK_PLANKS, 40));
        SimpleContainer inventory = new SimpleContainer(1);
        Map<Item, Integer> wanted = new LinkedHashMap<>(Map.of(Items.COBBLESTONE, 30));
        wanted.put(Items.OAK_PLANKS, 5);
        assertEquals(30, Hauling.moveWanted(storage, inventory, wanted), "Cobblestone first; then no room for planks");
        assertEquals(30, inventory.countItem(Items.COBBLESTONE));
        assertEquals(10, storage.countItem(Items.COBBLESTONE));
        assertEquals(40, storage.countItem(Items.OAK_PLANKS));
        assertEquals(10, storage.countItem(Items.DIRT), "Dirt wasn't wanted");
        assertEquals(Map.of(Items.OAK_PLANKS, 5), wanted, "What's left to fetch");
    }

    @Test
    void movingEverythingKeepsWhatDoesntFit() {
        SimpleContainer from = new SimpleContainer(2);
        from.setItem(0, new ItemStack(Items.OAK_PLANKS, 20));
        from.setItem(1, new ItemStack(Items.TORCH, 3));
        SimpleContainer to = new SimpleContainer(1);
        to.setItem(0, new ItemStack(Items.OAK_PLANKS, 60));
        assertTrue(Hauling.hasRoomForAny(from, to));
        assertEquals(4, Hauling.moveAll(from, to));
        assertEquals(16, from.countItem(Items.OAK_PLANKS), "What didn't fit stays");
        assertEquals(3, from.countItem(Items.TORCH));
        assertFalse(Hauling.hasRoomForAny(from, to), "Now it's full");
    }

    @Test
    void aLoadFitsOnlyIfAllOfItDoes() {
        SimpleContainer inventory = new SimpleContainer(2);
        inventory.setItem(0, new ItemStack(Items.COBBLESTONE, 60));
        assertTrue(Hauling.hasEmptySlot(inventory));
        assertTrue(Hauling.fitsAll(inventory, List.of(new ItemStack(Items.COBBLESTONE, 4), new ItemStack(Items.OAK_PLANKS, 64))));
        assertFalse(Hauling.fitsAll(inventory, List.of(new ItemStack(Items.COBBLESTONE, 5), new ItemStack(Items.OAK_PLANKS, 64))),
                "The fifth cobblestone would need the slot the planks take");
        assertEquals(60, inventory.countItem(Items.COBBLESTONE), "Checking doesn't change it");
        inventory.setItem(1, new ItemStack(Items.DIRT));
        assertFalse(Hauling.hasEmptySlot(inventory));
    }
}
