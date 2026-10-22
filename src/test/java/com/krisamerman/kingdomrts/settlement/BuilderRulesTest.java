package com.krisamerman.kingdomrts.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class BuilderRulesTest {
    private static final UUID HOME = UUID.randomUUID();

    @Test
    void anIdleBuilderCanBeAssignedToASiteWithRoom() {
        assertNull(BuilderRules.assignProblem("Ada", true, "storage shed", false, null, 1, 2));
    }

    @Test
    void assigningIsRefusedForTheRightReasons() {
        assertEquals("Ada isn't one of the settlement's builders", BuilderRules.assignProblem("Ada", false, "storage shed", false, null, 0, 2));
        assertEquals("The storage shed is already built", BuilderRules.assignProblem("Ada", true, "storage shed", true, null, 0, 2));
        assertEquals("Ada is already working on the barn", BuilderRules.assignProblem("Ada", true, "storage shed", false, "barn", 0, 2));
        assertEquals("The storage shed already has 2 builders", BuilderRules.assignProblem("Ada", true, "storage shed", false, null, 2, 2));
    }
}
