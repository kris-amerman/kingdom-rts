package com.krisamerman.kingdomrts.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class NameRulesTest {
    @Test
    void acceptsCommandWordCharacters() {
        assertNull(NameRules.formatProblem("Faction", "Red_Team-2.0+"));
    }

    @Test
    void rejectsSpacesSymbolsEmptyAndTooLong() {
        assertNotNull(NameRules.formatProblem("Faction", "Red Team"));
        assertNotNull(NameRules.formatProblem("Faction", "*Steve"));
        assertNotNull(NameRules.formatProblem("Faction", ""));
        assertNotNull(NameRules.formatProblem("Faction", "x".repeat(NameRules.MAX_LENGTH + 1)));
    }

    @Test
    void sanitizeReplacesDisallowedCharacters() {
        assertEquals("_Steve", NameRules.sanitize("*Steve", "Faction"));
        assertEquals("St_ve", NameRules.sanitize("St@ve", "Faction"));
    }

    @Test
    void sanitizeFallsBackWhenEmpty() {
        assertEquals("Faction", NameRules.sanitize("", "Faction"));
    }

    @Test
    void sanitizedNamePlusSuffixAlwaysPasses() {
        // This is what guarantees the "_2", "_3" suffix loops in FactionData and SettlementNames end.
        for (String input : new String[] {"*Steve", "", "a b c", "x".repeat(100), "!@#$%^&*()"}) {
            assertNull(NameRules.formatProblem("Faction", NameRules.sanitize(input, "Faction") + "_999999"), input);
        }
    }
}
