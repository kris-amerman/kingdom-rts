package com.krisamerman.kingdomrts.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FactionColorsTest {
    @Test
    void aFactionAlwaysHasTheSameColour() {
        UUID blue = UUID.fromString("6f1c1c2e-8a3b-4c1d-9e2f-0a1b2c3d4e5f");
        assertEquals(FactionColors.of(blue), FactionColors.of(UUID.fromString(blue.toString())));
    }

    @Test
    void aSettlementWithNoOwnerIsGrey() {
        assertEquals(FactionColors.UNOWNED, FactionColors.of(null));
    }

    @Test
    void factionsMostlyGetDifferentColoursAndAllAreBright() {
        Set<Integer> colours = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            int colour = FactionColors.of(new UUID(i * 7919L, i * 104729L));
            colours.add(colour);
            int brightest = Math.max(colour >> 16 & 0xFF, Math.max(colour >> 8 & 0xFF, colour & 0xFF));
            assertEquals(255, brightest, "Bright: " + Integer.toHexString(colour));
        }
        assertTrue(colours.size() >= 18, "Expected mostly different colours, got " + colours.size() + " of 20");
    }

    @Test
    void huesGoRoundTheWheel() {
        assertEquals(0xFF0000, FactionColors.hsvToRgb(0.0f, 1.0f, 1.0f));
        assertEquals(0x00FF00, FactionColors.hsvToRgb(1.0f / 3.0f, 1.0f, 1.0f));
        assertEquals(0x0000FF, FactionColors.hsvToRgb(2.0f / 3.0f, 1.0f, 1.0f));
    }
}
