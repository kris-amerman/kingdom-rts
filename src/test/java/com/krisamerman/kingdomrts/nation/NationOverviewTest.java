package com.krisamerman.kingdomrts.nation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

class NationOverviewTest {
    private static NationOverview roundTrip(NationOverview overview) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        NationOverview.STREAM_CODEC.encode(buf, overview);
        return NationOverview.STREAM_CODEC.decode(buf);
    }

    @Test
    void anOverviewArrivesAsItWasSent() {
        NationOverview overview = new NationOverview("Blue", true, List.of(
                new NationOverview.SettlementLine("Ashford", "minecraft:overworld", new BlockPos(120, 64, -300), 9, 14),
                new NationOverview.SettlementLine("Brook", "minecraft:the_nether", new BlockPos(-5, 70, 8), 1, 0)));
        assertEquals(overview, roundTrip(overview));
    }

    @Test
    void aPlayerWithoutAFactionGetsTheJoinScreen() {
        assertFalse(NationOverview.NO_FACTION.hasFaction());
        assertEquals(NationOverview.NO_FACTION, roundTrip(NationOverview.NO_FACTION));
        assertTrue(new NationOverview("Blue", false, List.of()).hasFaction());
    }
}
