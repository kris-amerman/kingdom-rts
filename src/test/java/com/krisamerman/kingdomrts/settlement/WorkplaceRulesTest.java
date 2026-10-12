package com.krisamerman.kingdomrts.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

class WorkplaceRulesTest {
    private static final UUID MILLBROOK = UUID.randomUUID();

    private static String hire(UUID unitSettlement, boolean hasOccupation, int workers, int capacity) {
        return WorkplaceRules.hireProblem("Ann", unitSettlement, MILLBROOK, "Millbrook", hasOccupation, workers, capacity, "lumberjack hut");
    }

    @Test
    void anUnemployedSettlerCanBeHiredWhileThereIsRoom() {
        assertNull(hire(MILLBROOK, false, 0, 1));
        assertNull(hire(MILLBROOK, false, 2, 3));
    }

    @Test
    void onlyTheWorkplacesOwnSettlersCanBeHired() {
        assertEquals("Ann doesn't belong to Millbrook", hire(UUID.randomUUID(), false, 0, 1));
        assertEquals("Ann doesn't belong to Millbrook", hire(null, false, 0, 1));
    }

    @Test
    void unitsWithAnOccupationCantBeHired() {
        assertEquals("Ann already has an occupation", hire(MILLBROOK, true, 0, 1)); // a worker elsewhere, or a soldier
    }

    @Test
    void aFullWorkplaceTakesNoOne() {
        assertEquals("The lumberjack hut is full (1/1 workers)", hire(MILLBROOK, false, 1, 1));
    }

    @Test
    void workplaceEntryDecodesWithOneWorkerByDefault() {
        WorkplaceType type = WorkplaceType.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "occupation": "kingdomrts:lumberjack" }""")).getOrThrow();
        assertEquals(new WorkplaceType(ResourceLocation.fromNamespaceAndPath("kingdomrts", "lumberjack"), 1), type);
        assertTrue(WorkplaceType.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                { "occupation": "kingdomrts:lumberjack", "workers": 0 }""")).isError());
    }
}
