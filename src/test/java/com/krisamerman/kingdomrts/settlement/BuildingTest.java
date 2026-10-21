package com.krisamerman.kingdomrts.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

class BuildingTest {
    private static final Building SHED = new Building(ResourceLocation.parse("kingdomrts:storage_shed"), new BlockPos(10, 64, -20),
            Rotation.CLOCKWISE_180, new BoundingBox(10, 64, -20, 14, 67, -16), new BlockPos(12, 65, -18), 1234L, Building.NOT_BUILT);

    @Test
    void aSiteSurvivesSaving() {
        assertEquals(SHED, Building.load(SHED.save()));
    }

    @Test
    void aFinishedBuildingKeepsItsSiteAndBuildersUntilItsStockIsClearedOut() {
        UUID builder = UUID.randomUUID();
        Building built = SHED.withBuilder(builder, "Ada Lovelace").finishedAt(5678L);
        assertTrue(built.isBuilt());
        assertTrue(built.isBeingClearedOut());
        assertEquals(SHED.site(), built.site());
        assertEquals(Map.of(builder, "Ada Lovelace"), built.builders());
        assertEquals(5678L, built.built());
        assertEquals(built, Building.load(built.save()));
    }

    @Test
    void closingTheSiteFreesItsBuildersAndSurvivesSaving() {
        Building closed = SHED.withBuilder(UUID.randomUUID(), "Ada Lovelace").finishedAt(5678L).withSiteClosed();
        assertTrue(closed.isBuilt());
        assertFalse(closed.isBeingClearedOut());
        assertNull(closed.site());
        assertTrue(closed.builders().isEmpty());
        assertEquals(closed, Building.load(closed.save()));
    }

    @Test
    void aSiteSavedBeforeBuildingsCouldBeFinishedIsUnbuilt() {
        CompoundTag old = SHED.save();
        old.remove("built");
        assertFalse(Building.load(old).isBuilt());
    }

    @Test
    void sitePanelListsMaterialsMostNeededFirstWithWhatsOnSite() {
        SimpleContainer stock = new SimpleContainer(3);
        stock.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
        List<BuildingSiteMenu.Material> materials = BuildingSiteMenu.materials(Map.of(Items.OAK_PLANKS, 12, Items.COBBLESTONE, 25, Items.TORCH, 1), stock);
        assertEquals(List.of(new BuildingSiteMenu.Material(Items.COBBLESTONE, 25, 0), new BuildingSiteMenu.Material(Items.OAK_PLANKS, 12, 4),
                new BuildingSiteMenu.Material(Items.TORCH, 1, 0)), materials);
    }

    @Test
    void assignedBuildersSurviveSavingAndAreFreedWhenTheSiteCloses() {
        UUID ada = UUID.randomUUID();
        UUID ben = UUID.randomUUID();
        Building assigned = SHED.withBuilder(ada, "Ada Thorne").withBuilder(ben, "Ben Ash");
        assertEquals(List.of(ada, ben), List.copyOf(assigned.builders().keySet()), "In assignment order");
        assertEquals(assigned, Building.load(assigned.save()));
        assertEquals(Map.of(ben, "Ben Ash"), assigned.withoutBuilder(ada).builders());
        assertTrue(assigned.finishedAt(99L).withSiteClosed().builders().isEmpty());
    }

    @Test
    void aBuildingBeingDemolishedTakesBuildersAndSurvivesSaving() {
        Building closed = SHED.finishedAt(5678L).withSiteClosed();
        assertFalse(closed.takesBuilders(), "Built, with its site closed");
        Building demolishing = closed.withDemolishing(true);
        assertTrue(demolishing.takesBuilders());
        assertFalse(demolishing.isBeingClearedOut());
        assertEquals(demolishing, Building.load(demolishing.save()));
        assertFalse(Building.load(SHED.save()).demolishing(), "Not unless ordered");
    }
}
