package com.krisamerman.kingdomrts.settlement;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;

// A unit as a panel lists it (the town hall's builders page, a site's builders page): its id, its
// name, and a short note shown beside it ("idle", what it's working on; "" for none).
public record PanelEntry(UUID id, String name, String note) {
    void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name);
        buf.writeUtf(note);
    }

    static PanelEntry read(FriendlyByteBuf buf) {
        return new PanelEntry(buf.readUUID(), buf.readUtf(), buf.readUtf());
    }
}
