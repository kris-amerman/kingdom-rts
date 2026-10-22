package com.krisamerman.kingdomrts.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.krisamerman.kingdomrts.KingdomRts;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The "/kingdomrts perf ..." commands, for recording the mod's performance in a live world:
//   start / stop: record until stopped, then write a report (Markdown and JSON, the stress test's
//     format) to <game dir>/kingdomrts-perf/, with a summary in chat. The client of the player who
//     started it measures its frames meanwhile, and its numbers are added to the report.
//   log on [seconds] / log off: a one-line summary in the game log every so many seconds (counted
//     in server ticks, so a paused singleplayer game logs later); log alone says whether it's on.
// Both use Perf windows, so they can run together, and with neither running nothing is recorded.
// Server thread only.
@EventBusSubscriber(modid = KingdomRts.MODID)
public final class PerfCommands {
    public static final String REPORT_DIR = "kingdomrts-perf";
    public static final int DEFAULT_LOG_SECONDS = 10;
    // How long after stop a report waits for the client's numbers before it's left as it is.
    private static final int CLIENT_REPORT_WAIT_TICKS = 100;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    // The recording /kingdomrts perf start began, or null.
    @Nullable
    private static Session session;
    // A report written at stop that's waiting for the client's numbers, or null.
    @Nullable
    private static PendingReport pending;
    // The log's window, how often it logs, and how long until the next line; null when off.
    @Nullable
    private static Perf.Window logWindow;
    private static int logSeconds;
    private static int ticksToNextLog;
    @Nullable
    private static String lastLogLine;

    private record Session(Perf.Window window, @Nullable UUID owner, String ownerName, int unitsAtStart) {
    }

    private record PendingReport(UUID owner, String ownerName, String name, String markdown, List<PerfReport.Result> results, long expires) {
    }

    private PerfCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("perf")
                .then(Commands.literal("start").executes(ctx -> start(ctx.getSource())))
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("log")
                        .executes(ctx -> logStatus(ctx.getSource()))
                        .then(Commands.literal("on")
                                .executes(ctx -> logOn(ctx.getSource(), DEFAULT_LOG_SECONDS))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ctx -> logOn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
                        .then(Commands.literal("off").executes(ctx -> logOff(ctx.getSource()))));
    }

    private static int start(CommandSourceStack source) {
        if (session != null) {
            source.sendFailure(Component.literal("Already recording: /kingdomrts perf stop writes the report"));
            return 0;
        }
        if (Perf.isStressRecording()) {
            source.sendFailure(Component.literal("The stress test is recording"));
            return 0;
        }
        ServerPlayer player = source.getPlayer(); // null from the server console
        session = new Session(Perf.openWindow(), player != null ? player.getUUID() : null, player != null ? player.getGameProfile().getName() : "",
                loadedUnits(source.getServer()));
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new PerfPayloads.ClientRecording(true));
        }
        source.sendSuccess(() -> Component.literal("Recording Kingdom RTS performance. /kingdomrts perf stop writes the report."), true);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        if (session == null) {
            source.sendFailure(Component.literal("Not recording: /kingdomrts perf start first"));
            return 0;
        }
        Session finished = session;
        session = null;
        MinecraftServer server = source.getServer();
        Perf.Snapshot snapshot = Perf.closeWindow(finished.window());
        int unitsAtStop = loadedUnits(server);
        // Units come and go as chunks load, so the report uses the average of start and stop.
        PerfReport.Result result = new PerfReport.Result((finished.unitsAtStart() + unitsAtStop + 1) / 2, snapshot);
        List<PerfReport.Result> results = List.of(result);
        String name = unusedName("perf-" + LocalDateTime.now().format(FILE_TIME));
        String markdown = "# Kingdom RTS performance\n\n" + PerfReportFiles.header() + "\n\n"
                + String.format("Recorded in a live world over %d ticks (%.0f s), with %d units loaded at the start and %d at the end. Times are ms per tick.%n%n",
                        snapshot.ticks(), snapshot.ticks() / 20.0, finished.unitsAtStart(), unitsAtStop)
                + PerfReport.table(results, PerfReport.Budget.DEFAULT);
        boolean written = PerfReportFiles.write(reportDir(), name, markdown, PerfReportFiles.json(results, List.of(), null));
        if (!written) {
            source.sendFailure(Component.literal("Couldn't write the report (see the log)"));
            return 0;
        }
        ServerPlayer owner = finished.owner() != null ? server.getPlayerList().getPlayer(finished.owner()) : null;
        if (owner != null) {
            pending = new PendingReport(owner.getUUID(), finished.ownerName(), name, markdown, results, server.overworld().getGameTime() + CLIENT_REPORT_WAIT_TICKS);
            PacketDistributor.sendToPlayer(owner, new PerfPayloads.ClientRecording(false));
        }
        String summary = String.format("Our code took %.2f ms per tick (%d units, %.1f µs each); server ticks %.2f ms on average, worst %.1f ms.",
                result.ourMsPerTick(), result.units(), result.usPerUnit(), result.serverMsPerTick(), result.worstTickMs());
        source.sendSuccess(() -> Component.literal(summary + " Report: " + REPORT_DIR + "/" + name + ".md"), true);
        return 1;
    }

    private static int logOn(CommandSourceStack source, int seconds) {
        if (logWindow == null) {
            logWindow = Perf.openWindow();
        } else {
            logWindow.restart();
        }
        logSeconds = seconds;
        ticksToNextLog = seconds * 20;
        source.sendSuccess(() -> Component.literal("Logging a Kingdom RTS performance summary every " + seconds + " s to the game log"), true);
        return 1;
    }

    // Whether the log is on, and how to use it.
    private static int logStatus(CommandSourceStack source) {
        String status = logWindow != null
                ? "The Kingdom RTS performance log is on, every " + logSeconds + " s. /kingdomrts perf log off stops it."
                : "The Kingdom RTS performance log is off. /kingdomrts perf log on [seconds] logs a summary every " + DEFAULT_LOG_SECONDS
                        + " s (or as given).";
        source.sendSuccess(() -> Component.literal(status), false);
        return 1;
    }

    private static int logOff(CommandSourceStack source) {
        if (logWindow == null) {
            source.sendFailure(Component.literal("The performance log isn't on"));
            return 0;
        }
        Perf.closeWindow(logWindow);
        logWindow = null;
        source.sendSuccess(() -> Component.literal("Stopped logging Kingdom RTS performance"), true);
        return 1;
    }

    // A client sent what it measured: added to the waiting report if it's from the player who
    // started it, otherwise ignored.
    public static void clientReport(ServerPlayer player, PerfReport.ClientStats stats) {
        PendingReport report = pending;
        if (report == null || !report.owner().equals(player.getUUID())) {
            return;
        }
        pending = null;
        String markdown = report.markdown() + "\n" + PerfReport.clientSection(report.ownerName(), stats);
        JsonObject json = PerfReportFiles.json(report.results(), List.of(), stats);
        if (PerfReportFiles.write(reportDir(), report.name(), markdown, json)) {
            player.sendSystemMessage(Component.literal("Added your client's numbers to " + REPORT_DIR + "/" + report.name() + ".md"));
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (pending != null && event.getServer().overworld().getGameTime() > pending.expires()) {
            pending = null; // the report stays as written, without the client's numbers
        }
        if (logWindow != null && --ticksToNextLog <= 0) {
            ticksToNextLog = logSeconds * 20;
            lastLogLine = PerfReport.logLine(new PerfReport.Result(loadedUnits(event.getServer()), logWindow.read()), logSeconds);
            logWindow.restart();
            KingdomRts.LOGGER.info(lastLogLine);
        }
    }

    // A stopping server ends any recording, so a singleplayer world opened next in the same game
    // doesn't inherit one.
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        if (session != null) {
            Perf.closeWindow(session.window());
            session = null;
        }
        if (logWindow != null) {
            Perf.closeWindow(logWindow);
            logWindow = null;
        }
        pending = null;
    }

    // The last line the log wrote, or null (for tests).
    @Nullable
    public static String lastLogLine() {
        return lastLogLine;
    }

    // Whether a report is waiting for a client's numbers (for tests).
    public static boolean isWaitingForClient() {
        return pending != null;
    }

    // The name, or the name with -2, -3 and so on if a report already has it (two in one second).
    private static String unusedName(String name) {
        String unused = name;
        for (int i = 2; Files.exists(reportDir().resolve(unused + ".md")); i++) {
            unused = name + "-" + i;
        }
        return unused;
    }

    public static Path reportDir() {
        return FMLPaths.GAMEDIR.get().resolve(REPORT_DIR);
    }

    // Units loaded in every dimension.
    private static int loadedUnits(MinecraftServer server) {
        int units = 0;
        for (ServerLevel level : server.getAllLevels()) {
            units += level.getEntities(KingdomRts.UNIT.get(), unit -> true).size();
        }
        return units;
    }
}
