package com.krisamerman.kingdomrts.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

class WorkplaceSettingsTest {
    private static final ResourceLocation HUT = ResourceLocation.parse("kingdomrts:lumberjack_hut");
    private static final ResourceLocation OAK = ResourceLocation.parse("minecraft:oak_log");
    private static final ResourceLocation BIRCH = ResourceLocation.parse("minecraft:birch_log");

    @Test
    void newWorkplaceReplantsAndFellsEverything() {
        Workplace workplace = new Workplace(BlockPos.ZERO, HUT, 16);
        assertTrue(workplace.replant());
        assertTrue(workplace.fells(OAK) && workplace.fells(BIRCH));
    }

    @Test
    void settingsSurviveSaving() {
        Workplace workplace = new Workplace(new BlockPos(1, 2, 3), HUT, 16);
        workplace.setReplant(false);
        assertTrue(workplace.setFells(OAK, false));
        assertFalse(workplace.setFells(OAK, false), "Already skipped: no change");
        Workplace loaded = Workplace.load(workplace.save());
        assertFalse(loaded.replant());
        assertFalse(loaded.fells(OAK));
        assertTrue(loaded.fells(BIRCH));
        assertEquals(Set.of(OAK), loaded.skippedWood());
    }

    @Test
    void workplaceSavedBeforeSettingsExistedReplantsAndFellsEverything() {
        CompoundTag old = new Workplace(BlockPos.ZERO, HUT, 16).save();
        old.remove("replant");
        old.remove("skipped_wood");
        Workplace loaded = Workplace.load(old);
        assertTrue(loaded.replant());
        assertTrue(loaded.skippedWood().isEmpty());
    }

    @Test
    void settingsPageListsReplantingThenEachWood() {
        List<WorkplaceMenu.Row> rows = WorkplaceMenu.settingsRows(2);
        assertEquals(List.of(new WorkplaceMenu.Row(WorkplaceMenu.Row.Kind.REPLANT, 0), new WorkplaceMenu.Row(WorkplaceMenu.Row.Kind.WOODS_TITLE, 0),
                new WorkplaceMenu.Row(WorkplaceMenu.Row.Kind.WOOD, 0), new WorkplaceMenu.Row(WorkplaceMenu.Row.Kind.WOOD, 1)), rows);
    }
}
