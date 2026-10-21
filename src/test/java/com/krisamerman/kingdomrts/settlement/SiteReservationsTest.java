package com.krisamerman.kingdomrts.settlement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

class SiteReservationsTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void oneFetcherPerSite() {
        BlockPos site = new BlockPos(1000, 0, 0);
        assertTrue(SiteReservations.reserveFetch(site, A, 0));
        assertFalse(SiteReservations.reserveFetch(site, B, 10), "A is fetching");
        assertTrue(SiteReservations.reserveFetch(site, A, 20), "A renews it");
        assertTrue(SiteReservations.reserveFetch(new BlockPos(2000, 0, 0), B, 20), "Another site has its own fetcher");
        SiteReservations.releaseFetch(site, B);
        assertFalse(SiteReservations.reserveFetch(site, B, 30), "Only A can let it go");
        SiteReservations.releaseFetch(site, A);
        assertTrue(SiteReservations.reserveFetch(site, B, 40));
    }

    @Test
    void aFetchLeftBehindExpires() {
        BlockPos site = new BlockPos(3000, 0, 0);
        assertTrue(SiteReservations.reserveFetch(site, A, 0));
        assertFalse(SiteReservations.reserveFetch(site, B, SiteReservations.FETCH_EXPIRES_AFTER_TICKS - 1));
        assertTrue(SiteReservations.reserveFetch(site, B, SiteReservations.FETCH_EXPIRES_AFTER_TICKS + 1));
    }
}
