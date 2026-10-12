package com.krisamerman.kingdomrts.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

class TerritoryMessagesTest {
    private static final UUID DEV = UUID.randomUUID();
    private static final Function<UUID, String> NAMES = id -> id.equals(DEV) ? "Dev" : "Red";
    private static final Settlement OAKHAVEN = settlement("Oakhaven", DEV);
    private static final Settlement ASHFORD = settlement("Ashford", UUID.randomUUID());

    @Test
    void enteringAClaimNamesItsOwner() {
        assertEquals("Entering Oakhaven (Dev)", TerritoryMessages.message(null, OAKHAVEN, NAMES));
    }

    @Test
    void leavingAClaim() {
        assertEquals("Leaving Oakhaven", TerritoryMessages.message(OAKHAVEN, null, NAMES));
    }

    @Test
    void crossingStraightIntoAnotherClaimSaysEntering() {
        assertEquals("Entering Ashford (Red)", TerritoryMessages.message(OAKHAVEN, ASHFORD, NAMES));
    }

    @Test
    void nothingWhenStayingInTheSameClaimOrWilderness() {
        assertNull(TerritoryMessages.message(OAKHAVEN, OAKHAVEN, NAMES));
        assertNull(TerritoryMessages.message(null, null, NAMES));
    }

    private static Settlement settlement(String name, UUID owner) {
        return new Settlement(UUID.randomUUID(), name, owner, Level.OVERWORLD, BlockPos.ZERO, Set.of());
    }
}
