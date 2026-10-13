package com.krisamerman.kingdomrts.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.util.RandomSource;

class UnitNamesTest {
    private static final List<String> FIRST = List.of("Ada", "Hugo", "Nell");
    private static final List<String> LAST = List.of("Fletcher", "Moss");

    @Test
    void aNameIsAFirstAndALastNameFromThePools() {
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < 20; i++) {
            String[] parts = UnitNames.pick(FIRST, LAST, random).split(" ");
            assertEquals(2, parts.length);
            assertTrue(FIRST.contains(parts[0]) && LAST.contains(parts[1]));
        }
    }

    @Test
    void anEmptyPoolIsLeftOut() {
        assertEquals("Ada", UnitNames.pick(List.of("Ada"), List.of(), RandomSource.create(1)));
        assertEquals("Moss", UnitNames.pick(List.of(), List.of("Moss"), RandomSource.create(1)));
        assertEquals("Settler", UnitNames.pick(List.of(), List.of(), RandomSource.create(1)));
    }
}
