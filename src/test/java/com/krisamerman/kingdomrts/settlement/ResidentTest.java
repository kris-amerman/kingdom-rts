package com.krisamerman.kingdomrts.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

class ResidentTest {
    private static final UUID ADA = UUID.randomUUID();
    private static final UUID BEN = UUID.randomUUID();
    private static final Resident LUMBERJACK = new Resident("Ada Thorne", ResourceLocation.parse("kingdomrts:lumberjack"));
    private static final Resident IDLE = new Resident("Ben Ash", null);

    private static Settlement settlement() {
        return new Settlement(UUID.randomUUID(), "Mossvale", UUID.randomUUID(), Level.OVERWORLD, new BlockPos(0, 64, 0), Set.of(new ChunkPos(0, 0)));
    }

    @Test
    void residentsSurviveSaving() {
        Settlement settlement = settlement();
        settlement.addUnit(ADA, LUMBERJACK);
        settlement.addUnit(BEN, IDLE);
        Settlement loaded = Settlement.load(settlement.save());
        assertEquals(Map.of(ADA, LUMBERJACK, BEN, IDLE), loaded.residents());
        assertEquals(Set.of(ADA, BEN), loaded.units());
    }

    @Test
    void updatingOnlyChangesItsOwnUnits() {
        Settlement settlement = settlement();
        settlement.addUnit(BEN, IDLE);
        Resident hired = new Resident("Ben Ash", ResourceLocation.parse("kingdomrts:lumberjack"));
        assertTrue(settlement.updateResident(BEN, hired));
        assertFalse(settlement.updateResident(BEN, hired), "Nothing changed the second time");
        assertFalse(settlement.updateResident(ADA, LUMBERJACK), "Not one of its units");
        assertEquals(Set.of(BEN), settlement.units());
        assertEquals(hired, settlement.residents().get(BEN));
    }

    @Test
    void unitsSavedBeforeResidentsAreRememberedAsUnknown() {
        CompoundTag tag = settlement().save();
        ListTag old = new ListTag();
        old.add(NbtUtils.createUUID(ADA)); // the old format: just the unit's UUID
        tag.put("units", old);
        assertEquals(Map.of(ADA, Resident.UNKNOWN), Settlement.load(tag).residents());
    }
}
