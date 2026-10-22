package com.krisamerman.kingdomrts.nation;

import java.util.List;

import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

// One building in the interface's Build catalog: its id, its name and the size of its box. The
// catalog comes from the server, because building data (BuildingType) only exists there.
public record CatalogEntry(ResourceLocation id, String name, Vec3i size) {
    public static final StreamCodec<FriendlyByteBuf, List<CatalogEntry>> LIST_CODEC = StreamCodec.of(
            (buf, entries) -> buf.writeCollection(entries, (b, entry) -> entry.write(b)),
            buf -> buf.readList(CatalogEntry::read));

    private void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeUtf(name);
        buf.writeVarInt(size.getX());
        buf.writeVarInt(size.getY());
        buf.writeVarInt(size.getZ());
    }

    private static CatalogEntry read(FriendlyByteBuf buf) {
        return new CatalogEntry(buf.readResourceLocation(), buf.readUtf(), new Vec3i(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
    }
}
