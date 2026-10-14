package com.krisamerman.kingdomrts.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

class LumberjackRulesTest {
    @Test
    void depositsOnlyWhenNearlyFull() {
        SimpleContainer inventory = new SimpleContainer(Unit.INVENTORY_SIZE);
        assertFalse(LumberjackJob.shouldDeposit(inventory), "Empty: keep working");
        for (int i = 0; i < Unit.INVENTORY_SIZE - 2; i++) {
            inventory.setItem(i, new ItemStack(Items.OAK_LOG, 64));
        }
        assertFalse(LumberjackJob.shouldDeposit(inventory), "Two slots free: room for another tree");
        inventory.setItem(Unit.INVENTORY_SIZE - 2, new ItemStack(Items.STICK, 3));
        assertTrue(LumberjackJob.shouldDeposit(inventory), "One slot free: time to go back");
    }

    @Test
    void onlyNaturalLeavesWithNoLogNearbyAreOrphaned() {
        var leaves = Blocks.OAK_LEAVES.defaultBlockState();
        assertTrue(LumberjackJob.isOrphanedLeaf(leaves.setValue(LeavesBlock.DISTANCE, 7).setValue(LeavesBlock.PERSISTENT, false)));
        assertFalse(LumberjackJob.isOrphanedLeaf(leaves.setValue(LeavesBlock.DISTANCE, 6).setValue(LeavesBlock.PERSISTENT, false)),
                "Still within 6 of a log (another tree's): it stays");
        assertFalse(LumberjackJob.isOrphanedLeaf(leaves.setValue(LeavesBlock.DISTANCE, 7).setValue(LeavesBlock.PERSISTENT, true)),
                "Placed by a player: never touched");
        assertFalse(LumberjackJob.isOrphanedLeaf(Blocks.OAK_LOG.defaultBlockState()));
    }

    @Test
    void theCanopyBoxIsTheTreeGrownByTheLeafRange() {
        BoundingBox box = TreeRules.canopyBox(List.of(new BlockPos(0, 64, 0), new BlockPos(0, 67, 0), new BlockPos(1, 68, 0)), 6);
        assertEquals(new BoundingBox(-6, 58, -6, 7, 74, 6), box);
    }

    @Test
    void plantsFromTheSlotHoldingTheSapling() {
        SimpleContainer inventory = new SimpleContainer(Unit.INVENTORY_SIZE);
        assertEquals(-1, LumberjackJob.saplingSlot(inventory, Items.OAK_SAPLING), "No sapling: nothing to plant");
        inventory.setItem(0, new ItemStack(Items.BIRCH_SAPLING));
        inventory.setItem(3, new ItemStack(Items.OAK_SAPLING, 2));
        assertEquals(3, LumberjackJob.saplingSlot(inventory, Items.OAK_SAPLING));
        assertEquals(-1, LumberjackJob.saplingSlot(inventory, Items.SPRUCE_SAPLING), "Only a sapling of the tree's own wood");
    }
}
