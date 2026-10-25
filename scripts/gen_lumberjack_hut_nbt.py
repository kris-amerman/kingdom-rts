#!/usr/bin/env python3
"""Writes the lumberjack hut's template: src/main/resources/data/kingdomrts/structure/buildings/lumberjack_hut.nbt

A small wooden hut, 5x5 and 4 high: a cobblestone floor, oak plank walls on log corners with a door
in the north wall and an open window in the east and west walls, a slab roof inside a plank rim, a
torch inside, and the hut block (its core) in the middle of the floor. It's no higher than this
because builders build from the ground (no scaffolding yet): a roof a layer higher is out of their
reach from outside, and the ceiling under it is in the way from inside. Structure files are gzipped
NBT, so this writes the format directly instead of needing a structure block in-game. To re-author
it in-game instead, build it, save it with a structure block named kingdomrts:buildings/lumberjack_hut
and copy the file from run/world/generated/kingdomrts/structure/.
Re-run it after a change: python3 scripts/gen_lumberjack_hut_nbt.py
"""
import gzip
import pathlib
import struct

SIZE_X, SIZE_Y, SIZE_Z = 5, 4, 5
DATA_VERSION = 3955  # Minecraft 1.21.1 (SharedConstants.WORLD_VERSION)
OUT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/data/kingdomrts/structure/buildings/lumberjack_hut.nbt"

# NBT tag ids
END, INT, STRING, LIST, COMPOUND = 0, 3, 8, 9, 10


def name(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def int_tag(key, value):
    return bytes([INT]) + name(key) + struct.pack(">i", value)


def string_tag(key, value):
    return bytes([STRING]) + name(key) + name(value)


def compound_tag(key, payload):
    return bytes([COMPOUND]) + name(key) + payload + bytes([END])


def int_list(key, values):
    return bytes([LIST]) + name(key) + bytes([INT]) + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)


def compound_list(key, payloads):
    # Each payload is a compound's contents (its tags), without the closing END.
    element_type = COMPOUND if payloads else END
    body = b"".join(p + bytes([END]) for p in payloads)
    return bytes([LIST]) + name(key) + bytes([element_type]) + struct.pack(">i", len(payloads)) + body


def block_state(block, **properties):
    payload = string_tag("Name", block)
    if properties:
        payload += compound_tag("Properties", b"".join(string_tag(k, v) for k, v in sorted(properties.items())))
    return payload


DOOR = dict(facing="north", hinge="left", open="false", powered="false")
STATES = {
    "air": block_state("minecraft:air"),
    "cobblestone": block_state("minecraft:cobblestone"),
    "log": block_state("minecraft:oak_log", axis="y"),
    "planks": block_state("minecraft:oak_planks"),
    "slab": block_state("minecraft:oak_slab", type="bottom", waterlogged="false"),
    "door_lower": block_state("minecraft:oak_door", half="lower", **DOOR),
    "door_upper": block_state("minecraft:oak_door", half="upper", **DOOR),
    "torch": block_state("minecraft:wall_torch", facing="north"),  # on the south wall's inside
    "core": block_state("kingdomrts:lumberjack_hut"),
}


def block_at(x, y, z):
    edge_x = x in (0, SIZE_X - 1)
    edge_z = z in (0, SIZE_Z - 1)
    if y == 0:
        return "cobblestone"
    if y in (1, 2):
        if edge_x and edge_z:
            return "log"
        if (x, z) == (2, 0):
            return "door_lower" if y == 1 else "door_upper"
        if y == 2 and z == 2 and edge_x:
            return "air"  # an open window
        if edge_x or edge_z:
            return "planks"
        if (x, y, z) == (2, 1, 2):
            return "core"
        if (x, y, z) == (2, 2, 3):
            return "torch"
        return "air"
    if edge_x and edge_z:
        return "log"
    return "planks" if edge_x or edge_z else "slab"


def main():
    names = list(STATES)
    blocks = []
    for y in range(SIZE_Y):
        for x in range(SIZE_X):
            for z in range(SIZE_Z):
                blocks.append(int_list("pos", [x, y, z]) + int_tag("state", names.index(block_at(x, y, z))))
    root = (
        int_tag("DataVersion", DATA_VERSION)
        + int_list("size", [SIZE_X, SIZE_Y, SIZE_Z])
        + compound_list("palette", [STATES[n] for n in names])
        + compound_list("blocks", blocks)
        + compound_list("entities", [])
    )
    data = bytes([COMPOUND]) + name("") + root + bytes([END])
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(gzip.compress(data))
    print(f"wrote {OUT} ({SIZE_X}x{SIZE_Y}x{SIZE_Z})")


if __name__ == "__main__":
    main()
