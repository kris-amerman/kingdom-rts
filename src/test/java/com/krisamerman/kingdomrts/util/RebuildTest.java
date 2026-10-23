package com.krisamerman.kingdomrts.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RebuildTest {
    // A preview's mesh is built for one building turned one way.
    private record Turned(Object building, int quarterTurns) {
    }

    @Test
    void aPreviewIsRebuiltOnlyWhenItTurnsOrChangesBuilding() {
        Object shed = new Object();
        Rebuild<Turned> mesh = new Rebuild<>();
        assertTrue(mesh.needed(new Turned(shed, 0)), "Nothing built yet");
        mesh.done(new Turned(shed, 0));
        assertFalse(mesh.needed(new Turned(shed, 0)), "Moving it doesn't change what's built");
        assertTrue(mesh.needed(new Turned(shed, 1)), "Turning it does");
        assertTrue(mesh.needed(new Turned(new Object(), 0)), "So does another building");
        mesh.reset();
        assertTrue(mesh.needed(new Turned(shed, 0)), "After a reset, it's built again");
    }

    @Test
    void aSiteIsRebuiltWhenTheBlocksItShowsChange() {
        Rebuild<List<String>> site = new Rebuild<>();
        site.done(List.of("planks at 1,64,1", "planks at 2,64,1"));
        assertFalse(site.needed(List.of("planks at 1,64,1", "planks at 2,64,1")), "The same blocks, in a new list");
        assertTrue(site.needed(List.of("planks at 2,64,1")), "A builder placed one");
    }
}
