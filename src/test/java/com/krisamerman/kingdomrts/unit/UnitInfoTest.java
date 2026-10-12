package com.krisamerman.kingdomrts.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.krisamerman.kingdomrts.unit.UnitInfo.Access;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

class UnitInfoTest {
    private static final UUID BLUE = UUID.randomUUID();
    private static final UUID RED = UUID.randomUUID();

    @Test
    void ownFactionGetsTheFullView() {
        assertEquals(Access.FULL, UnitInfo.accessFor(BLUE, BLUE));
    }

    @Test
    void otherFactionsGetTheLimitedView() {
        assertEquals(Access.LIMITED, UnitInfo.accessFor(RED, BLUE));
    }

    @Test
    void factionlessViewerGetsTheLimitedView() {
        assertEquals(Access.LIMITED, UnitInfo.accessFor(null, BLUE));
    }

    @Test
    void nobodyGetsTheFullViewOfAFactionlessUnit() {
        assertEquals(Access.LIMITED, UnitInfo.accessFor(BLUE, null));
        assertEquals(Access.LIMITED, UnitInfo.accessFor(null, null));
    }

    @Test
    void fullInfoSurvivesTheTripToTheClient() {
        UnitInfo info = new UnitInfo("Red soldier", "Red", "kingdomrts:soldier", 24, 4, 2.5, 0.3,
                new UnitInfo.Details("Millbrook", new BlockPos(1, 64, -3), null));
        assertEquals(info, roundTrip(info));
    }

    @Test
    void limitedInfoSurvivesTheTripToTheClient() {
        UnitInfo info = new UnitInfo("Unit", "None", "None", 20, 1, 0, 0.25, null);
        assertEquals(info, roundTrip(info));
    }

    private static UnitInfo roundTrip(UnitInfo info) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        info.write(buf);
        return UnitInfo.read(buf);
    }
}
