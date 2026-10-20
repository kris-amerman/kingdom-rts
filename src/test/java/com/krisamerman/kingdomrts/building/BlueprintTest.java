package com.krisamerman.kingdomrts.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

class BlueprintTest {
    private static final BlockState STAIRS = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);

    // Vanilla's structure file format: size, a palette of block states, and blocks pointing into it.
    private static CompoundTag template(int blocks) {
        CompoundTag tag = new CompoundTag();
        tag.put("size", ints(2, 1, 1));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));
        palette.add(NbtUtils.writeBlockState(STAIRS));
        tag.put("palette", palette);
        ListTag list = new ListTag();
        for (int i = 0; i < blocks; i++) {
            CompoundTag block = new CompoundTag();
            block.put("pos", ints(i % 2, 0, 0));
            block.putInt("state", i % 2);
            list.add(block);
        }
        tag.put("blocks", list);
        return tag;
    }

    private static ListTag ints(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }

    @Test
    void readsATemplate() {
        Blueprint blueprint = Blueprint.read(template(2), BuiltInRegistries.BLOCK.asLookup());
        assertEquals(new Vec3i(2, 1, 1), blueprint.size());
        assertEquals(new Blueprint.Part(BlockPos.ZERO, Blocks.STONE.defaultBlockState()), blueprint.parts().get(0));
        assertEquals(new Blueprint.Part(new BlockPos(1, 0, 0), STAIRS), blueprint.parts().get(1), "States keep their properties");
    }

    @Test
    void rejectsBrokenOrHugeTemplates() {
        CompoundTag broken = template(2);
        broken.getList("blocks", 10).getCompound(1).putInt("state", 7);
        assertThrows(IllegalArgumentException.class, () -> Blueprint.read(broken, BuiltInRegistries.BLOCK.asLookup()));
        assertThrows(IllegalArgumentException.class, () -> Blueprint.read(new CompoundTag(), BuiltInRegistries.BLOCK.asLookup()));
        assertThrows(IllegalArgumentException.class,
                () -> Blueprint.read(template(Blueprint.MAX_BLOCKS + 1), BuiltInRegistries.BLOCK.asLookup()));
    }

    @Test
    void buildingTypeDecodes() {
        BuildingType type = BuildingType.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{ \"structure\": \"kingdomrts:buildings/storage_shed\", \"name\": \"Storage shed\" }")).getOrThrow();
        assertEquals(new BuildingType(ResourceLocation.parse("kingdomrts:buildings/storage_shed"), "Storage shed"), type);
        assertEquals(BuildingType.DEFAULT_MAX_BUILDERS, type.maxBuilders(), "max_builders is optional");
        BuildingType bigger = BuildingType.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{ \"structure\": \"kingdomrts:buildings/barn\", \"name\": \"Barn\", \"max_builders\": 4 }")).getOrThrow();
        assertEquals(4, bigger.maxBuilders());
        assertTrue(BuildingType.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{ \"structure\": \"kingdomrts:buildings/barn\", \"name\": \"Barn\", \"max_builders\": 0 }")).isError(), "At least one");
    }
}
