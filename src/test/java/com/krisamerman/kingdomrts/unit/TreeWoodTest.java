package com.krisamerman.kingdomrts.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.world.level.block.Blocks;

class TreeWoodTest {
    @Test
    void entryDecodesInFullOrAsJustTheSapling() {
        assertEquals(new TreeWood(Blocks.OAK_SAPLING), decode("{ \"sapling\": \"minecraft:oak_sapling\" }").getOrThrow());
        assertEquals(new TreeWood(Blocks.MANGROVE_PROPAGULE), decode("\"minecraft:mangrove_propagule\"").getOrThrow());
    }

    @Test
    void unknownSaplingIsRejected() {
        assertTrue(decode("{ \"sapling\": \"minecraft:no_such_sapling\" }").isError());
    }

    private static DataResult<TreeWood> decode(String json) {
        return TreeWood.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }
}
