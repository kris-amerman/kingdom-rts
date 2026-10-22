package com.krisamerman.kingdomrts.nation;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

// What the nation management interface's first screen shows a player: their faction and its
// settlements. factionName is "" for a player without a faction, who gets the "join or found a
// faction first" screen instead.
public record NationOverview(String factionName, boolean leader, List<SettlementLine> settlements) {
    public static final NationOverview NO_FACTION = new NationOverview("", false, List.of());

    // One of the faction's settlements. dimension is the dimension's id ("minecraft:overworld").
    public record SettlementLine(String name, String dimension, BlockPos townHall, int claimedChunks, int population) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(name);
            buf.writeUtf(dimension);
            buf.writeBlockPos(townHall);
            buf.writeVarInt(claimedChunks);
            buf.writeVarInt(population);
        }

        static SettlementLine read(FriendlyByteBuf buf) {
            return new SettlementLine(buf.readUtf(), buf.readUtf(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
        }
    }

    // How the overview is written to and read from a network packet. (A StreamCodec is NeoForge's
    // and vanilla's pairing of an encoder and a decoder, here built from the two methods below.)
    public static final StreamCodec<FriendlyByteBuf, NationOverview> STREAM_CODEC = StreamCodec.of((buf, overview) -> overview.write(buf),
            NationOverview::read);

    public boolean hasFaction() {
        return !factionName.isEmpty();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(factionName);
        buf.writeBoolean(leader);
        buf.writeCollection(settlements, (b, line) -> line.write(b));
    }

    private static NationOverview read(FriendlyByteBuf buf) {
        return new NationOverview(buf.readUtf(), buf.readBoolean(), buf.readList(SettlementLine::read));
    }
}
