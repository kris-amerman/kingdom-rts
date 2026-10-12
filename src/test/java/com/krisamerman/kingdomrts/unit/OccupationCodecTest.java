package com.krisamerman.kingdomrts.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

class OccupationCodecTest {
    @Test
    void militaryEntryDecodes() {
        Occupation occupation = decode("""
                { "type": "kingdomrts:military", "max_health": 24, "movement_speed": 0.3,
                  "attack_damage": 4, "attack_range": 2.5 }""").getOrThrow();
        assertEquals(new MilitaryOccupation(24, 0.3, 4, 2.5), occupation);
        assertEquals(OccupationType.MILITARY, occupation.type());
        assertTrue(occupation.isMilitary());
    }

    @Test
    void unknownTypeIsRejected() {
        assertTrue(decode("""
                { "type": "kingdomrts:wizard", "max_health": 24 }""").isError());
    }

    @Test
    void missingTypeIsRejected() {
        assertTrue(decode("""
                { "max_health": 24, "movement_speed": 0.3, "attack_damage": 4, "attack_range": 2.5 }""").isError());
    }

    @Test
    void missingStatIsRejected() {
        assertTrue(decode("""
                { "type": "kingdomrts:military", "max_health": 24, "movement_speed": 0.3, "attack_damage": 4 }""").isError());
    }

    @Test
    void encodesBackToTheSameJson() {
        MilitaryOccupation soldier = new MilitaryOccupation(24, 0.3, 4, 2.5);
        JsonElement json = OccupationType.OCCUPATION_CODEC.encodeStart(JsonOps.INSTANCE, soldier).getOrThrow();
        assertEquals("kingdomrts:military", json.getAsJsonObject().get("type").getAsString());
        assertEquals(soldier, OccupationType.OCCUPATION_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void lumberjackEntryDecodes() {
        Occupation occupation = decode("""
                { "type": "kingdomrts:lumberjack", "movement_speed": 0.3 }""").getOrThrow();
        assertEquals(new LumberjackOccupation(0.3), occupation);
        assertTrue(!occupation.isMilitary());
    }

    @Test
    void lumberjackNeedsAMovementSpeed() {
        assertTrue(decode("""
                { "type": "kingdomrts:lumberjack" }""").isError());
    }

    private static DataResult<Occupation> decode(String json) {
        return OccupationType.OCCUPATION_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }
}
