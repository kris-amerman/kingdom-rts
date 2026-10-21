package com.krisamerman.kingdomrts.settlement;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;

// Which block each builder on a site is working on, so builders sharing a site never go for the
// same block, and which builder is fetching for it. Kept in memory only (server thread): a
// reservation expires after a while, so one left by a builder that unloaded or died doesn't block
// its block (or its site's fetching) for long.
public final class SiteReservations {
    // How long a reservation lasts without being renewed.
    public static final long EXPIRES_AFTER_TICKS = 20 * 15;

    private record Reservation(UUID builder, long until) {
    }

    // Site (its building's origin) to block to reservation.
    private static final Map<BlockPos, Map<BlockPos, Reservation>> SITES = new HashMap<>();

    private SiteReservations() {
    }

    // Reserves a block of a site for a builder, unless another builder holds it. Returns whether the
    // builder holds it now.
    public static boolean reserve(BlockPos site, BlockPos block, UUID builder, long now) {
        Map<BlockPos, Reservation> blocks = SITES.computeIfAbsent(site.immutable(), key -> new HashMap<>());
        Reservation held = blocks.get(block);
        if (held != null && !held.builder().equals(builder) && held.until() > now) {
            return false;
        }
        blocks.put(block.immutable(), new Reservation(builder, now + EXPIRES_AFTER_TICKS));
        return true;
    }

    // Whether another builder holds a block of a site.
    public static boolean heldByOther(BlockPos site, BlockPos block, UUID builder, long now) {
        Map<BlockPos, Reservation> blocks = SITES.get(site);
        Reservation held = blocks != null ? blocks.get(block) : null;
        return held != null && !held.builder().equals(builder) && held.until() > now;
    }

    // Each site's fetcher: at most one builder per site fetches materials at a time. A fetch takes
    // longer than placing a block (a walk there and back), so it lasts longer, and is renewed as
    // the trip goes on.
    public static final long FETCH_EXPIRES_AFTER_TICKS = 20 * 90;
    private static final Map<BlockPos, Reservation> FETCHERS = new HashMap<>();

    // Makes the builder its site's fetcher (or renews it), unless another builder is fetching.
    // Returns whether it's the fetcher now.
    public static boolean reserveFetch(BlockPos site, UUID builder, long now) {
        Reservation held = FETCHERS.get(site);
        if (held != null && !held.builder().equals(builder) && held.until() > now) {
            return false;
        }
        FETCHERS.put(site.immutable(), new Reservation(builder, now + FETCH_EXPIRES_AFTER_TICKS));
        return true;
    }

    public static void releaseFetch(BlockPos site, UUID builder) {
        if (FETCHERS.get(site) instanceof Reservation held && held.builder().equals(builder)) {
            FETCHERS.remove(site);
        }
    }

    public static void release(BlockPos site, BlockPos block, UUID builder) {
        Map<BlockPos, Reservation> blocks = SITES.get(site);
        if (blocks != null && blocks.get(block) instanceof Reservation held && held.builder().equals(builder)) {
            blocks.remove(block);
            if (blocks.isEmpty()) {
                SITES.remove(site);
            }
        }
    }
}
