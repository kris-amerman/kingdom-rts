package com.krisamerman.kingdomrts.client;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.building.BuildingPicker;
import com.krisamerman.kingdomrts.nation.NationPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Client only. The nation management interface's Demolish: the faction's buildings around the
// player are outlined, the one they look at is highlighted, and right-clicking it twice (the second
// time to confirm, within a few seconds) asks the server to demolish it. Builders then take it
// apart. Doing the same to one already coming down stops its demolition. Esc or the cancel key
// leaves; so does a successful order.
@EventBusSubscriber(modid = KingdomRts.MODID, value = Dist.CLIENT)
public final class DemolishClient {
    // How far away a building can be picked, as for placing one.
    private static final double LOOK_RANGE = 48.0;
    // How long the confirming second right-click may take.
    private static final int CONFIRM_TICKS = 60;
    // How often the buildings are asked for again while picking, as they change.
    private static final int REFRESH_TICKS = 100;
    private static final int HINT_COLOR = 0xFFFFFFFF;
    private static final int WARNING_COLOR = 0xFFFF6060;
    private static final int HINT_MARGIN = 6;
    private static final int HINT_LINE = 11;

    private static boolean active;
    private static List<NationPayloads.BuildingView> buildings = List.of();
    private static List<AABB> boxes = List.of();
    // The building looked at (an index into buildings), or -1.
    private static int looked = -1;
    // The building a first right-click picked, waiting for the second, and until when (client ticks).
    @Nullable
    private static BlockPos confirming;
    private static int confirmTicksLeft;
    private static int ticksSinceRefresh;
    // A demolish or stop request is waiting for its answer.
    private static boolean ordering;
    // The use button is still held since the last right-click (holding it repeats "use").
    private static boolean waitingForRelease;

    private DemolishClient() {
    }

    // Demolish was pressed: start picking, ending any building preview.
    public static void begin() {
        BuildPreviewClient.cancel();
        active = true;
        buildings = List.of();
        boxes = List.of();
        looked = -1;
        confirming = null;
        ordering = false;
        requestBuildings();
    }

    public static void cancel() {
        active = false;
        buildings = List.of();
        boxes = List.of();
        looked = -1;
        confirming = null;
        ordering = false;
    }

    public static boolean isActive() {
        return active;
    }

    private static void requestBuildings() {
        ticksSinceRefresh = 0;
        PacketDistributor.sendToServer(new NationPayloads.RequestBuildings());
    }

    // The server sent the faction's buildings around the player.
    public static void showBuildings(List<NationPayloads.BuildingView> sent) {
        if (!active) {
            return;
        }
        buildings = sent;
        boxes = sent.stream().map(view -> new AABB(view.min().getX(), view.min().getY(), view.min().getZ(), view.max().getX() + 1, view.max().getY() + 1,
                view.max().getZ() + 1)).toList();
        looked = -1;
    }

    // The server answered a demolish or stop request (the player is told how it went on the action bar).
    public static void demolishAnswered(boolean done) {
        ordering = false;
        confirming = null;
        if (done) {
            cancel();
        } else {
            requestBuildings();
        }
    }

    @Nullable
    private static NationPayloads.BuildingView lookedAt() {
        return looked >= 0 && looked < buildings.size() ? buildings.get(looked) : null;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (waitingForRelease && !minecraft.options.keyUse.isDown()) {
            waitingForRelease = false;
        }
        LocalPlayer player = minecraft.player;
        if (!active || player == null) {
            return;
        }
        if (BuildPreviewClient.CANCEL_KEY.consumeClick()) {
            cancel();
            return;
        }
        looked = BuildingPicker.pick(player.getEyePosition(), player.getViewVector(1.0f), LOOK_RANGE, boxes);
        NationPayloads.BuildingView target = lookedAt();
        if (confirming != null && (--confirmTicksLeft <= 0 || target == null || !target.origin().equals(confirming))) {
            confirming = null; // took too long, or looked away
        }
        if (++ticksSinceRefresh >= REFRESH_TICKS) {
            requestBuildings();
        }
    }

    // Right-click while picking: the first picks the building looked at, the second (within
    // CONFIRM_TICKS) asks the server to demolish it, or to stop demolishing it if it's already coming
    // down. Every use event is cancelled, so nothing is
    // placed or used, and only the first main-hand one of a click counts.
    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active || !event.isUseItem()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.getHand() != InteractionHand.MAIN_HAND || waitingForRelease || ordering) {
            return;
        }
        waitingForRelease = true;
        NationPayloads.BuildingView target = lookedAt();
        if (target == null) {
            return;
        }
        if (target.origin().equals(confirming)) {
            ordering = true;
            PacketDistributor.sendToServer(target.demolishing() ? new NationPayloads.StopDemolition(target.origin())
                    : new NationPayloads.DemolishBuilding(target.origin()));
        } else {
            confirming = target.origin();
            confirmTicksLeft = CONFIRM_TICKS;
        }
    }

    // Esc leaves Demolish instead of pausing (only a key press: the window losing focus doesn't).
    @SubscribeEvent
    static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (active && event.getCurrentScreen() == null && event.getNewScreen() instanceof PauseScreen
                && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE)) {
            event.setCanceled(true);
            cancel();
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        cancel();
    }

    // The outlines: the building looked at in white (red once picked, waiting for the confirming
    // click; yellow when that click would stop its demolition), ones already coming down in orange,
    // the rest faint.
    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !active || boxes.isEmpty()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (int i = 0; i < boxes.size(); i++) {
            NationPayloads.BuildingView view = buildings.get(i);
            AABB box = boxes.get(i).move(-camera.x, -camera.y, -camera.z);
            float[] color;
            if (i == looked && view.origin().equals(confirming)) {
                color = view.demolishing() ? new float[] {1.0f, 0.9f, 0.2f, 1.0f} : new float[] {1.0f, 0.25f, 0.25f, 1.0f};
            } else if (view.demolishing()) {
                color = new float[] {1.0f, 0.6f, 0.1f, 0.8f};
            } else if (i == looked) {
                color = new float[] {1.0f, 1.0f, 1.0f, 1.0f};
            } else {
                color = new float[] {0.8f, 0.8f, 0.8f, 0.35f};
            }
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, color[0], color[1], color[2], color[3]);
        }
        buffers.endBatch(RenderType.lines());
    }

    // What the player is looking at and can do, at the top of the screen.
    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int middle = graphics.guiWidth() / 2;
        int y = HINT_MARGIN;
        graphics.drawCenteredString(minecraft.font, "Demolish", middle, y, HINT_COLOR);
        y += HINT_LINE;
        NationPayloads.BuildingView target = lookedAt();
        String status;
        int color = HINT_COLOR;
        if (target == null) {
            status = buildings.isEmpty() ? "None of your buildings are nearby" : "Look at one of your buildings";
        } else if (target.demolishing()) {
            status = target.origin().equals(confirming) ? "Right-click again to stop demolishing the " + target.name().toLowerCase(Locale.ROOT)
                    : target.name() + " (being demolished): right-click to stop";
        } else if (target.origin().equals(confirming)) {
            status = "Right-click again to demolish the " + target.name().toLowerCase(Locale.ROOT);
            color = WARNING_COLOR;
        } else {
            status = target.name() + (target.built() ? "" : " (being built)");
        }
        graphics.drawCenteredString(minecraft.font, status, middle, y, color);
        y += HINT_LINE + 2;
        graphics.drawCenteredString(minecraft.font, "Right-click: demolish    Esc/" + BuildPreviewClient.CANCEL_KEY.getTranslatedKeyMessage().getString()
                + ": cancel", middle, y, HINT_COLOR);
    }
}
