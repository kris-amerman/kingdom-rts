package com.krisamerman.kingdomrts.settlement;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;

// Which block each builder on a site is working on, so builders sharing a site never go for the
// same block. Kept in memory only (server thread): a reservation expires after a while, so one left
// by a builder that unloaded or died doesn't block its block for long.
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
