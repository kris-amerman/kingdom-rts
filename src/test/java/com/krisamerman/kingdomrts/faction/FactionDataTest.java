package com.krisamerman.kingdomrts.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FactionDataTest {
    private FactionData factions;
    private Faction red;
    private Faction blue;

    @BeforeEach
    void setUp() {
        factions = new FactionData();
        red = factions.create("Red", null);
        blue = factions.create("Blue", null);
    }

    @Test
    void notHostileByDefault() {
        assertFalse(factions.isHostile(red.id(), blue.id()));
    }

    @Test
    void warIsMutual() {
        assertTrue(factions.setHostile(red, blue, true));
        assertTrue(factions.isHostile(red.id(), blue.id()));
        assertTrue(factions.isHostile(blue.id(), red.id()));
    }

    @Test
    void neverHostileToNullOrItself() {
        factions.setHostile(red, blue, true);
        assertFalse(factions.isHostile(red.id(), null));
        assertFalse(factions.isHostile(null, red.id()));
        assertFalse(factions.isHostile(red.id(), red.id()));
    }

    @Test
    void setHostileReportsWhetherAnythingChanged() {
        assertTrue(factions.setHostile(red, blue, true));
        assertFalse(factions.setHostile(blue, red, true));
        assertTrue(factions.setHostile(red, blue, false));
        assertFalse(factions.setHostile(red, blue, false));
        assertFalse(factions.isHostile(red.id(), blue.id()));
    }

    @Test
    void joiningLeavesThePreviousFaction() {
        UUID player = UUID.randomUUID();
        factions.join(player, red);
        factions.join(player, blue);
        assertSame(blue, factions.factionOf(player));
        assertTrue(factions.members(red).isEmpty());
    }

    @Test
    void leaderLeavingClearsLeader() {
        UUID player = UUID.randomUUID();
        Faction own = factions.create("Own", player);
        factions.join(player, own);
        assertEquals(own, factions.leave(player));
        assertNull(own.leader());
        assertNull(factions.factionOf(player));
    }

    @Test
    void namesAreUniqueIgnoringCaseAndNoneIsReserved() {
        assertNotNull(factions.nameProblem("red"));
        assertNotNull(factions.nameProblem("None"));
        assertNull(factions.nameProblem("Green"));
        assertSame(red, factions.byName("RED"));
    }
}
