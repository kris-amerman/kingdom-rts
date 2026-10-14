package com.krisamerman.kingdomrts.building;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

// A building's layout, read from a structure template: every block it's made of, at its position
// relative to the template's corner, and the template's size. Air blocks are kept: they're spots
// that must be clear. Structure voids (the "don't care" block) aren't stored in templates at all.
//
// Read from a template's saved NBT (vanilla's structure file format: "size", "palette" and "blocks"),
// because StructureTemplate keeps its block lists private.
public record Blueprint(Vec3i size, List<Part> parts) {
    // A template with more blocks than this isn't a building (and would make every check slow).
    public static final int MAX_BLOCKS = 32768;

    // One block of the layout.
    public record Part(BlockPos pos, BlockState state) {
    }

    public Blueprint {
        parts = List.copyOf(parts);
    }

    // Reads a template's NBT. Throws IllegalArgumentException if it's malformed or too big.
    public static Blueprint read(CompoundTag tag, HolderGetter<Block> blockLookup) {
        ListTag size = tag.getList("size", Tag.TAG_INT);
        if (size.size() != 3) {
            throw new IllegalArgumentException("No size");
        }
        // A template has one palette, or several variants ("palettes"); buildings use the first.
        ListTag paletteTag = tag.contains("palettes", Tag.TAG_LIST)
                ? tag.getList("palettes", Tag.TAG_LIST).getList(0)
                : tag.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(blockLookup, paletteTag.getCompound(i)));
        }
        ListTag blockList = tag.getList("blocks", Tag.TAG_COMPOUND);
        if (blockList.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException(blockList.size() + " blocks is more than the " + MAX_BLOCKS + " a building may have");
        }
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag block = blockList.getCompound(i);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            int state = block.getInt("state");
            if (pos.size() != 3 || state < 0 || state >= palette.size()) {
                throw new IllegalArgumentException("Malformed block " + block);
            }
            parts.add(new Part(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), palette.get(state)));
        }
        return new Blueprint(new Vec3i(size.getInt(0), size.getInt(1), size.getInt(2)), parts);
    }
}
