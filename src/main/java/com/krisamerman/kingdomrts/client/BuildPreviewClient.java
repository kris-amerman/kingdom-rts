package com.krisamerman.kingdomrts.client;

import java.util.List;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.building.BuildingPreview;
import com.krisamerman.kingdomrts.nation.NationPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

// Client only. Previewing a building picked from the Build catalog: a see-through ghost of it where
// the player is looking, which they can nudge, raise, lower and turn, until they cancel it.
// Commissioning from the preview comes in S-034.
//
// The ghost is drawn with vanilla's own block rendering (each block's model, as the world draws
// it), made see-through and evenly lit, plus an outline of the building's box.
@EventBusSubscriber(modid = KingdomRts.MODID, value = Dist.CLIENT)
public final class BuildPreviewClient {
    // How far away the ghost can be put: further than a player can reach, since a building is placed
    // by looking at the ground where it goes.
    private static final double LOOK_RANGE = 48.0;
    private static final float GHOST_ALPHA = 0.55f;
    // A see-through render type on the block texture atlas, which vanilla uses for translucent
    // blocks drawn outside the world's chunks.
    private static final RenderType GHOST_TYPE = Sheets.translucentCullBlockSheet();
    private static final int HINT_COLOR = 0xFFFFFFFF;

    private static final String CATEGORY = "key.categories.kingdomrts";
    public static final KeyMapping ROTATE_KEY = key("rotate_building", GLFW.GLFW_KEY_R);
    public static final KeyMapping FORWARD_KEY = key("move_building_forward", GLFW.GLFW_KEY_UP);
    public static final KeyMapping BACK_KEY = key("move_building_back", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping LEFT_KEY = key("move_building_left", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping RIGHT_KEY = key("move_building_right", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping RAISE_KEY = key("raise_building", GLFW.GLFW_KEY_PAGE_UP);
    public static final KeyMapping LOWER_KEY = key("lower_building", GLFW.GLFW_KEY_PAGE_DOWN);
    public static final KeyMapping CANCEL_KEY = key("cancel_building", GLFW.GLFW_KEY_BACKSPACE);
    private static final List<KeyMapping> KEYS = List.of(ROTATE_KEY, FORWARD_KEY, BACK_KEY, LEFT_KEY, RIGHT_KEY, RAISE_KEY, LOWER_KEY, CANCEL_KEY);

    // The building being previewed, or null when there's none.
    @Nullable
    private static BuildingPreview preview;
    // Where the player last looked at a block (the spot in front of it), or null if they haven't
    // since the preview started.
    @Nullable
    private static BlockPos anchor;

    private BuildPreviewClient() {
    }

    // Rebindable under Controls, in the Kingdom RTS section. IN_GAME: only while no screen is open.
    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.kingdomrts." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KEYS.forEach(event::register);
    }

    // The server sent the blueprint of a building the player picked: start previewing it, replacing
    // any building already being previewed.
    public static void start(NationPayloads.ShowBlueprint payload) {
        preview = new BuildingPreview(payload.building(), payload.name(), payload.blueprint());
        anchor = null;
    }

    public static void cancel() {
        preview = null;
        anchor = null;
    }

    @Nullable
    public static BuildingPreview current() {
        return preview;
    }

    // Once per client tick: the keys, and where the player is looking.
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (preview == null || player == null) {
            // Presses while there's nothing to preview are dropped, so they don't pile up.
            KEYS.forEach(key -> {
                while (key.consumeClick()) {
                    // nothing to do
                }
            });
            return;
        }
        while (ROTATE_KEY.consumeClick()) {
            preview.rotate();
        }
        nudge(FORWARD_KEY, BuildingPreview.Nudge.FORWARD, player);
        nudge(BACK_KEY, BuildingPreview.Nudge.BACK, player);
        nudge(LEFT_KEY, BuildingPreview.Nudge.LEFT, player);
        nudge(RIGHT_KEY, BuildingPreview.Nudge.RIGHT, player);
        nudge(RAISE_KEY, BuildingPreview.Nudge.UP, player);
        nudge(LOWER_KEY, BuildingPreview.Nudge.DOWN, player);
        if (CANCEL_KEY.consumeClick()) {
            cancel();
            return;
        }
        HitResult hit = player.pick(LOOK_RANGE, 1.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block) {
            anchor = block.getBlockPos().relative(block.getDirection());
        }
    }

    private static void nudge(KeyMapping key, BuildingPreview.Nudge nudge, LocalPlayer player) {
        while (key.consumeClick()) {
            preview.nudge(nudge, player.getDirection());
        }
    }

    // Leaving the world or server ends the preview.
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        cancel();
    }

    // Draws the ghost after the world's particles, the stage NeoForge suggests for see-through
    // things. The pose stack here is relative to the camera, so everything is moved by -camera.
    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || preview == null || anchor == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        BlockPos origin = preview.origin(anchor);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        MultiBufferSource ghost = type -> new GhostConsumer(buffers.getBuffer(type), GHOST_ALPHA);
        BlockRenderDispatcher blocks = minecraft.getBlockRenderer();

        pose.pushPose();
        pose.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
        for (Blueprint.Part part : preview.turnedParts()) {
            pose.pushPose();
            pose.translate(part.pos().getX(), part.pos().getY(), part.pos().getZ());
            blocks.renderSingleBlock(part.state(), pose, ghost, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, GHOST_TYPE);
            pose.popPose();
        }
        pose.popPose();
        buffers.endBatch(GHOST_TYPE);

        AABB box = AABB.of(preview.footprint(anchor)).move(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, 1.0f, 1.0f, 1.0f, 0.8f);
        // Also draws anything the ghost's few block-entity-style blocks (chests and the like) used.
        buffers.endBatch();
    }

    // What the player can do, at the top of the screen while previewing.
    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (preview == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        String keys = keyName(ROTATE_KEY) + ": turn   " + keyName(FORWARD_KEY) + keyName(LEFT_KEY) + keyName(BACK_KEY) + keyName(RIGHT_KEY)
                + ": move   " + keyName(RAISE_KEY) + "/" + keyName(LOWER_KEY) + ": up/down   " + keyName(CANCEL_KEY) + ": cancel";
        String title = anchor == null ? preview.name() + ": look at the ground where it goes" : preview.name();
        int middle = graphics.guiWidth() / 2;
        graphics.drawCenteredString(minecraft.font, title, middle, 6, HINT_COLOR);
        graphics.drawCenteredString(minecraft.font, keys, middle, 18, HINT_COLOR);
    }

    private static String keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }

    // Passes vertices through to the real buffer, with their alpha scaled down so the ghost is
    // see-through. (A VertexConsumer is what block and entity rendering writes each vertex's
    // position, colour, texture coordinates, light and normal to.)
    private record GhostConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, Math.round(alpha * this.alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }
    }
}
