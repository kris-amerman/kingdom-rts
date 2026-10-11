#!/usr/bin/env python3
"""Writes the GameTest platform structure: src/main/resources/data/kingdomrts/structure/platform.nbt

A flat stone floor (y = 0) with air above, used as the test area for every GameTest. Structure
files are gzipped NBT, so this writes the format directly instead of needing a structure block
in-game. Re-run it if the size changes: python3 scripts/gen_platform_nbt.py
"""
import gzip
import pathlib
import struct

SIZE_X, SIZE_Y, SIZE_Z = 17, 5, 17
DATA_VERSION = 3955  # Minecraft 1.21.1 (SharedConstants.WORLD_VERSION)
OUT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/data/kingdomrts/structure/platform.nbt"

# NBT tag ids
END, INT, STRING, LIST, COMPOUND = 0, 3, 8, 9, 10


def name(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def int_tag(key, value):
    return bytes([INT]) + name(key) + struct.pack(">i", value)


def string_tag(key, value):
    return bytes([STRING]) + name(key) + name(value)


def int_list(key, values):
    return bytes([LIST]) + name(key) + bytes([INT]) + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)


def compound_list(key, payloads):
    # Each payload is a compound's contents (its tags), without the closing END.
    element_type = COMPOUND if payloads else END
    body = b"".join(p + bytes([END]) for p in payloads)
    return bytes([LIST]) + name(key) + bytes([element_type]) + struct.pack(">i", len(payloads)) + body


def main():
    palette = [string_tag("Name", "minecraft:stone"), string_tag("Name", "minecraft:air")]
    blocks = []
    for y in range(SIZE_Y):
        for x in range(SIZE_X):
            for z in range(SIZE_Z):
                state = 0 if y == 0 else 1
                blocks.append(int_list("pos", [x, y, z]) + int_tag("state", state))
    root = (
        int_tag("DataVersion", DATA_VERSION)
        + int_list("size", [SIZE_X, SIZE_Y, SIZE_Z])
        + compound_list("palette", palette)
        + compound_list("blocks", blocks)
        + compound_list("entities", [])
    )
    data = bytes([COMPOUND]) + name("") + root + bytes([END])
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(gzip.compress(data))
    print(f"wrote {OUT} ({SIZE_X}x{SIZE_Y}x{SIZE_Z})")


if __name__ == "__main__":
    main()
