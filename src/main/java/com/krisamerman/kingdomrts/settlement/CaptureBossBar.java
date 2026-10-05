package com.krisamerman.kingdomrts.settlement;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;

// The capture meter shown as a boss bar (the bar along the top of the screen, as for the Ender
// Dragon) while a town hall is under attack. Server only: vanilla syncs boss bars to clients itself,
// so this just decides who sees the bar and what it says.
public class CaptureBossBar {
    // Players within this many blocks of the hall see the bar. Independent of the capture radius.
    public static final double VIEW_DISTANCE = 48.0;
    // How long the bar stays up after the last attacker leaves or dies (100 ticks = 5 seconds), so
    // units stepping in and out of the capture radius don't make it flicker.
    public static final int HIDE_DELAY_TICKS = 100;
    // At most this many attacking faction names in the title; the rest show as "+N".
    private static final int MAX_NAMED_ATTACKERS = 2;
    // PLACEHOLDER until settlements exist and have names.
    private static final String SETTLEMENT_NAME_PLACEHOLDER = "Town Hall";

    // Which way control is moving this check, shown as the bar's color.
    public enum Trend {
        DRAINING(BossEvent.BossBarColor.RED),
        HOLDING(BossEvent.BossBarColor.YELLOW),
        RISING(BossEvent.BossBarColor.GREEN);

        final BossEvent.BossBarColor color;

        Trend(BossEvent.BossBarColor color) {
            this.color = color;
        }
    }

    // NOTCHED_20: 20 segments, so each change of 5 control moves exactly one notch.
    private final ServerBossEvent event = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.NOTCHED_20);
    // Game time of the last check with attackers present. Starts far enough back that a fresh hall
    // shows no bar.
    private long lastAttackTick = -HIDE_DELAY_TICKS - 1;

    // Called once per capture check for an owned hall. attackersStrongestFirst = names of the hostile
    // factions with units in the capture radius, strongest first (empty when nobody is attacking).
    public void update(ServerLevel level, BlockPos pos, String ownerName, int control, Trend trend,
            List<String> attackersStrongestFirst) {
        long now = level.getGameTime();
        if (!attackersStrongestFirst.isEmpty()) {
            lastAttackTick = now;
        }
        if (now - lastAttackTick > HIDE_DELAY_TICKS) {
            hide(); // no active attack: no bar in peacetime
            return;
        }

        event.setName(title(ownerName, attackersStrongestFirst));
        event.setColor(trend.color);
        event.setProgress(control / (float) TownHallBlockEntity.MAX_CONTROL);
        updateViewers(level, pos);
    }

    // Removes the bar from everyone's screen.
    public void hide() {
        event.removeAllPlayers();
    }

    // One centered line, e.g. "[Dev] Town Hall  ⚔  [Red] [Green] +1" (no ⚔ part when nobody's
    // attacking). Two spaces on each side of the ⚔ separate the owner's half from the attackers'.
    private static Component title(String ownerName, List<String> attackers) {
        StringBuilder title = new StringBuilder("[").append(ownerName).append("] ").append(SETTLEMENT_NAME_PLACEHOLDER);
        if (!attackers.isEmpty()) {
            title.append("  ⚔ ");
            for (String name : attackers.subList(0, Math.min(MAX_NAMED_ATTACKERS, attackers.size()))) {
                title.append(" [").append(name).append(']');
            }
            if (attackers.size() > MAX_NAMED_ATTACKERS) {
                title.append(" +").append(attackers.size() - MAX_NAMED_ATTACKERS);
            }
        }
        return Component.literal(title.toString());
    }

    // Shows the bar to players within VIEW_DISTANCE of the hall and removes it for everyone else.
    // Removals go first: a player who respawns or changes dimension is a new ServerPlayer object on
    // the same connection, and removing the old one after adding the new one would hide the bar.
    private void updateViewers(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        double maxDistanceSqr = VIEW_DISTANCE * VIEW_DISTANCE;
        List<ServerPlayer> nearby = level.getPlayers(p -> p.distanceToSqr(center) <= maxDistanceSqr);
        for (ServerPlayer player : new ArrayList<>(event.getPlayers())) {
            if (!nearby.contains(player)) {
                event.removePlayer(player);
            }
        }
        nearby.forEach(event::addPlayer); // no-op for players who already see it
    }
}
