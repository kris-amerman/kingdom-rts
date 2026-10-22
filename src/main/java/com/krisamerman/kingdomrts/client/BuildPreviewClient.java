package com.krisamerman.kingdomrts.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.building.Blueprint;
import com.krisamerman.kingdomrts.building.BuildingPreview;
import com.krisamerman.kingdomrts.building.BuildingRules;
import com.krisamerman.kingdomrts.nation.NationPayloads;
import com.krisamerman.kingdomrts.util.Perf;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;

// Client only. Previewing a building picked from the Build catalog: a see-through ghost of it where
// the player is looking, which they can nudge, raise, lower and turn. Right-clicking commissions
// it there, as right-clicking places a block (Shift-right-click keeps the preview, to build
// another); Esc or the cancel key drops it.
//
// As the ghost moves, the server is asked whether the building could go there, and the ghost turns
// red, with the reason shown, where it couldn't. The server still decides when it's commissioned.
//
// While previewing, the faction's unfinished buildings nearby are drawn finished (solid, lit like
// the world), so the player sees how the place will look when everything's built.
//
// The ghost is drawn with vanilla's own block rendering (each block's model, as the world draws
// it, hiding faces where its blocks touch), made see-through and evenly lit, plus an outline of
// the building's box.
@EventBusSubscriber(modid = KingdomRts.MODID, value = Dist.CLIENT)
public final class BuildPreviewClient {
    // How far away the ghost can be put: further than a player can reach, since a building is placed
    // by looking at the ground where it goes.
    private static final double LOOK_RANGE = 48.0;
    private static final float GHOST_ALPHA = 0.55f;
    // The ghost is drawn pulled towards the camera by this share of its distance, so where it
    // overlaps a block its faces sit just in front of that block's instead of flickering against
    // them (z-fighting). Scaling about the camera keeps it the same size on screen, since perspective
    // undoes it exactly, and the gap grows with distance as the depth buffer's precision shrinks:
    // the same trick vanilla uses to draw outlines over blocks.
    private static final float TOWARD_CAMERA = 0.999f;
    // A see-through render type on the block texture atlas, which vanilla uses for translucent
    // blocks drawn outside the world's chunks.
    private static final RenderType GHOST_TYPE = Sheets.translucentCullBlockSheet();
    // Blocks drawn like entities (chests, beds, shulker boxes, signs, banners) ask for these solid or
    // cut-out render types, which ignore the ghost's alpha; each is swapped for a see-through one
    // on the same texture.
    private static final Map<RenderType, RenderType> SEE_THROUGH = Map.of(
            Sheets.chestSheet(), RenderType.entityTranslucentCull(Sheets.CHEST_SHEET),
            Sheets.bedSheet(), RenderType.entityTranslucentCull(Sheets.BED_SHEET),
            Sheets.shulkerBoxSheet(), RenderType.entityTranslucentCull(Sheets.SHULKER_SHEET),
            Sheets.signSheet(), RenderType.entityTranslucentCull(Sheets.SIGN_SHEET),
            Sheets.bannerSheet(), RenderType.entityTranslucentCull(Sheets.BANNER_SHEET));
    private static final int HINT_COLOR = 0xFFFFFFFF;
    private static final int PROBLEM_COLOR = 0xFFFF6060;
    // The ghost's colour where the building couldn't go: its blocks keep their texture, in red.
    private static final float[] NO_TINT = {1.0f, 1.0f, 1.0f};
    private static final float[] PROBLEM_TINT = {1.0f, 0.35f, 0.35f};
    // How often the server is asked whether the building fits: when the ghost has moved and at least
    // this long since the last ask (more than NationRequests.CHECK_INTERVAL_TICKS, which the server
    // allows), and again every RECHECK_TICKS while it stays put, in case the world changed.
    private static final int CHECK_TICKS = 3;
    private static final int RECHECK_TICKS = 20;
    // How often the unfinished buildings nearby are asked for again while previewing, as builders
    // finish them and the player walks around.
    private static final int SITES_REFRESH_TICKS = 100;
    private static final int HINT_MARGIN = 6;
    private static final int HINT_LINE = 11;
    private static final String HINT_GAP = "    ";

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

    // Where and how the ghost was last checked with the server, and how long ago.
    @Nullable
    private static NationPayloads.CheckPlacement lastCheck;
    private static int ticksSinceCheck;
    // The server's answers for the spots the ghost has been checked at lately, so going back to one
    // shows its answer at once. Forgotten after a commission and whenever the unfinished buildings
    // are asked for again, since either may change which spots work.
    private static final int REMEMBERED_ANSWERS = 64;
    private static final Map<Spot, NationPayloads.PlacementAnswer> answers = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Spot, NationPayloads.PlacementAnswer> eldest) {
            return size() > REMEMBERED_ANSWERS;
        }
    };
    // The answer the ghost shows: the one for where it is when there is one, otherwise the last one
    // shown. Keeping the last one while the answer for a new spot is on its way stops the tint
    // flickering off and on as the ghost moves.
    @Nullable
    private static NationPayloads.PlacementAnswer shownAnswer;
    // A commission request is waiting for its answer (so a second click doesn't send another), and
    // whether the preview stays afterwards (Shift was held).
    private static boolean commissioning;
    private static boolean keepPlacing;
    // The use button (right-click) is still held since the last commission: holding it repeats
    // "use" every few ticks, which mustn't commission again.
    private static boolean waitingForRelease;

    // The faction's unfinished buildings nearby, each with its blocks where they go, and how long
    // since they were asked for.
    private static List<Site> sites = List.of();
    private static int ticksSinceSites;

    // The stand-in level for the current preview's blocks, rebuilt when the building turns.
    @Nullable
    private static GhostLevel ghostLevel;

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
        FRAMES.clear();
        forgetChecks();
        shownAnswer = null;
        commissioning = false;
        requestSites();
    }

    public static void cancel() {
        preview = null;
        anchor = null;
        ghostLevel = null;
        forgetChecks();
        shownAnswer = null;
        commissioning = false;
        sites = List.of();
    }

    private static void requestSites() {
        ticksSinceSites = 0;
        answers.clear();
        PacketDistributor.sendToServer(new NationPayloads.RequestSites());
    }

    // The server sent the unfinished buildings around the player.
    public static void showSites(NationPayloads.ShowSites payload) {
        ClientLevel world = Minecraft.getInstance().level;
        if (preview == null || world == null) {
            return;
        }
        List<Site> shown = new ArrayList<>();
        for (NationPayloads.SiteView view : payload.sites()) {
            Blueprint blueprint = payload.blueprints().get(view.building());
            if (blueprint != null) {
                shown.add(new Site(BuildingRules.place(blueprint, view.origin(), view.rotation()), world));
            }
        }
        sites = shown;
    }

    private static void forgetChecks() {
        lastCheck = null;
        answers.clear();
        ticksSinceCheck = 0;
    }

    // The server answered a commission request (the player is told how it went on the action bar).
    public static void commissionAnswered(NationPayloads.CommissionAnswer commissionAnswer) {
        commissioning = false;
        if (commissionAnswer.commissioned()) {
            requestSites(); // the new site is one of them now
        }
        if (commissionAnswer.commissioned() && !keepPlacing) {
            cancel();
        } else {
            // The spot has changed (now taken, or still refused): check it again straight away.
            forgetChecks();
            ticksSinceCheck = CHECK_TICKS;
        }
    }

    // The server answered a placement check.
    public static void placementAnswered(NationPayloads.PlacementAnswer placementAnswer) {
        if (preview != null && placementAnswer.building().equals(preview.type())) {
            answers.put(new Spot(placementAnswer.origin(), placementAnswer.rotation()), placementAnswer);
        }
    }

    // The answer the ghost shows (see shownAnswer), or null before the first one arrives.
    @Nullable
    private static NationPayloads.PlacementAnswer currentAnswer() {
        if (preview == null || anchor == null) {
            return null;
        }
        NationPayloads.PlacementAnswer here = answers.get(new Spot(preview.origin(anchor), preview.rotation()));
        if (here != null) {
            shownAnswer = here;
        }
        return shownAnswer;
    }

    // Where a building's preview stands: its template corner and how it's turned.
    private record Spot(BlockPos origin, Rotation rotation) {
    }

    // Right-click while previewing commissions the building where the ghost is, instead of using
    // whatever the player holds. The event comes once per hand (and again every few ticks while the
    // button is held); every one is cancelled so nothing is placed or used, and only the first
    // main-hand one of a click sends the request.
    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (preview == null || !event.isUseItem()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.getHand() != InteractionHand.MAIN_HAND || waitingForRelease || commissioning || anchor == null || player == null) {
            return;
        }
        waitingForRelease = true;
        commissioning = true;
        keepPlacing = player.isShiftKeyDown();
        PacketDistributor.sendToServer(new NationPayloads.CommissionBuilding(preview.type(), preview.origin(anchor), preview.rotation()));
    }

    @Nullable
    public static BuildingPreview current() {
        return preview;
    }

    // Once per client tick: the keys, and where the player is looking.
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (waitingForRelease && !minecraft.options.keyUse.isDown()) {
            waitingForRelease = false;
        }
        if (preview == null || player == null) {
            // Presses while there's nothing to preview are dropped, so they don't pile up.
            KEYS.forEach(key -> {
                while (key.consumeClick()) {
                    // nothing to do
                }
            });
            return;
        }
        ProfilerFiller profiler = minecraft.getProfiler();
        Perf.push(profiler, Perf.ClientSection.PREVIEW_TICK);
        try {
            previewTick(player);
        } finally {
            profiler.pop();
        }
    }

    private static void previewTick(LocalPlayer player) {
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
        checkPlacement();
        if (++ticksSinceSites >= SITES_REFRESH_TICKS) {
            requestSites();
        }
        sites.forEach(Site::update);
    }

    // Asks the server whether the building fits where the ghost is, when it's moved (and often
    // enough after the last ask) or every RECHECK_TICKS.
    private static void checkPlacement() {
        ticksSinceCheck++;
        if (anchor == null) {
            return;
        }
        NationPayloads.CheckPlacement check = new NationPayloads.CheckPlacement(preview.type(), preview.origin(anchor), preview.rotation());
        boolean moved = !check.equals(lastCheck);
        if ((moved && ticksSinceCheck >= CHECK_TICKS) || ticksSinceCheck >= RECHECK_TICKS) {
            PacketDistributor.sendToServer(check);
            lastCheck = check;
            ticksSinceCheck = 0;
        }
    }

    private static void nudge(KeyMapping key, BuildingPreview.Nudge nudge, LocalPlayer player) {
        while (key.consumeClick()) {
            preview.nudge(nudge, player.getDirection());
        }
    }

    // Esc cancels the preview instead of pausing, as it cancels placing things in strategy games;
    // with no preview, Esc pauses as usual. The pause screen also opens when the game window loses
    // focus, which shouldn't cancel anything, so only an Esc press counts (the key is still down
    // as the pause screen opens).
    @SubscribeEvent
    static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (preview != null && event.getCurrentScreen() == null && event.getNewScreen() instanceof PauseScreen
                && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE)) {
            event.setCanceled(true);
            cancel();
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
        NationPayloads.PlacementAnswer placement = currentAnswer();
        boolean refused = placement != null && !placement.fits();
        float[] tint = refused ? PROBLEM_TINT : NO_TINT;
        MultiBufferSource ghost = type -> new GhostConsumer(buffers.getBuffer(SEE_THROUGH.getOrDefault(type, type)), GHOST_ALPHA, tint);
        BlockRenderDispatcher blocks = minecraft.getBlockRenderer();
        long frameStart = System.nanoTime();
        ProfilerFiller profiler = minecraft.getProfiler();
        Perf.push(profiler, Perf.ClientSection.SITES_RENDER);
        renderSites(pose, camera, buffers, blocks);
        profiler.pop();
        Perf.push(profiler, Perf.ClientSection.PREVIEW_RENDER);

        // Drawn against a stand-in level made of the ghost's own blocks, so faces where two of its
        // blocks touch are left out, as the world leaves them out between real blocks.
        GhostLevel ghostLevel = ghostLevel(minecraft, origin);
        RandomSource random = RandomSource.create();
        pose.pushPose();
        pose.scale(TOWARD_CAMERA, TOWARD_CAMERA, TOWARD_CAMERA); // the pose stack is centred on the camera
        pose.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
        // Entity-style blocks (chests and the like) first, finished before the rest: see-through
        // faces still hide what's drawn behind them later, so a chest drawn after the walls would
        // vanish behind them instead of showing through.
        for (Blueprint.Part part : preview.turnedParts()) {
            if (part.state().getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
                pose.pushPose();
                pose.translate(part.pos().getX(), part.pos().getY(), part.pos().getZ());
                blocks.renderSingleBlock(part.state(), pose, ghost, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, GHOST_TYPE);
                pose.popPose();
            }
        }
        buffers.endBatch();
        for (Blueprint.Part part : preview.turnedParts()) {
            if (part.state().getRenderShape() == RenderShape.MODEL) {
                pose.pushPose();
                pose.translate(part.pos().getX(), part.pos().getY(), part.pos().getZ());
                blocks.renderBatched(part.state(), part.pos(), ghostLevel, pose, ghost.getBuffer(GHOST_TYPE), true, random, ModelData.EMPTY, null);
                pose.popPose();
            }
        }
        pose.popPose();
        buffers.endBatch(GHOST_TYPE);

        AABB box = AABB.of(preview.footprint(anchor)).move(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, 1.0f, refused ? 0.3f : 1.0f, refused ? 0.3f : 1.0f, 0.8f);
        buffers.endBatch(RenderType.lines());
        profiler.pop();
        FRAMES.add(System.nanoTime() - frameStart);
    }

    // The unfinished buildings, finished: solid, and lit and shaded like the world, drawn the way
    // vanilla draws falling blocks (each block's own render types, in their moving-block form).
    private static void renderSites(PoseStack pose, Vec3 camera, MultiBufferSource.BufferSource buffers, BlockRenderDispatcher blocks) {
        if (sites.isEmpty()) {
            return;
        }
        RandomSource random = RandomSource.create();
        for (Site site : sites) {
            for (Blueprint.Part part : site.shown) {
                BlockPos pos = part.pos();
                pose.pushPose();
                pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                if (part.state().getRenderShape() == RenderShape.MODEL) {
                    BakedModel model = blocks.getBlockModel(part.state());
                    for (RenderType type : model.getRenderTypes(part.state(), random, ModelData.EMPTY)) {
                        blocks.renderBatched(part.state(), pos, site, pose, buffers.getBuffer(RenderTypeHelper.getMovingBlockRenderType(type)), true,
                                random, ModelData.EMPTY, type);
                    }
                } else {
                    blocks.renderSingleBlock(part.state(), pose, buffers, LevelRenderer.getLightColor(site.world, pos), OverlayTexture.NO_OVERLAY,
                            ModelData.EMPTY, null);
                }
                pose.popPose();
            }
        }
        buffers.endBatch();
    }

    private static GhostLevel ghostLevel(Minecraft minecraft, BlockPos origin) {
        if (ghostLevel == null || ghostLevel.parts != preview.turnedParts() || ghostLevel.world != minecraft.level) {
            ghostLevel = new GhostLevel(preview.turnedParts(), minecraft.level);
        }
        ghostLevel.origin = origin;
        return ghostLevel;
    }

    // With the F3 screen open while previewing, one line on its left: how many blocks the preview
    // draws and what drawing them costs a frame (see docs/perf.md, Interface budget).
    @SubscribeEvent
    static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (preview == null) {
            return;
        }
        int siteBlocks = sites.stream().mapToInt(site -> site.shown.size()).sum();
        event.getLeft().add(String.format("Kingdom RTS preview: %d ghost + %d site blocks, %.2f ms/frame (max %.2f)",
                preview.turnedParts().size(), siteBlocks, FRAMES.averageMs(), FRAMES.maxMs()));
    }

    // What the player can do, at the top of the screen while previewing.
    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (preview == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        List<String> hints = List.of("Right-click: build", "Shift+right-click: build another", keyName(ROTATE_KEY) + ": turn", moveKeysName() + ": move",
                keyName(RAISE_KEY) + "/" + keyName(LOWER_KEY) + ": up/down", "Esc/" + keyName(CANCEL_KEY) + ": cancel");
        String title = anchor == null ? preview.name() + ": look at the ground where it goes" : preview.name();
        int middle = graphics.guiWidth() / 2;
        int room = graphics.guiWidth() - 2 * HINT_MARGIN;
        int y = HINT_MARGIN;
        graphics.drawCenteredString(minecraft.font, title, middle, y, HINT_COLOR);
        y += HINT_LINE;
        NationPayloads.PlacementAnswer placement = currentAnswer();
        if (placement != null && !placement.fits()) {
            for (FormattedCharSequence line : minecraft.font.split(Component.literal(placement.problem()), room)) {
                graphics.drawCenteredString(minecraft.font, line, middle, y, PROBLEM_COLOR);
                y += HINT_LINE;
            }
        }
        y += 2;
        // As many hints to a line as fit across the screen, so they wrap on narrow windows and
        // with long key names.
        StringBuilder line = new StringBuilder();
        for (String hint : hints) {
            String longer = line.isEmpty() ? hint : line + HINT_GAP + hint;
            if (!line.isEmpty() && minecraft.font.width(longer) > room) {
                graphics.drawCenteredString(minecraft.font, line.toString(), middle, y, HINT_COLOR);
                y += HINT_LINE;
                line = new StringBuilder(hint);
            } else {
                line = new StringBuilder(longer);
            }
        }
        graphics.drawCenteredString(minecraft.font, line.toString(), middle, y, HINT_COLOR);
    }

    // "Arrows" while the four move keys are the arrow keys, as they are unless rebound; otherwise
    // the four keys' names.
    private static String moveKeysName() {
        List<KeyMapping> moves = List.of(FORWARD_KEY, LEFT_KEY, BACK_KEY, RIGHT_KEY);
        List<Integer> arrows = List.of(GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT);
        boolean allArrows = true;
        for (int i = 0; i < moves.size(); i++) {
            KeyMapping key = moves.get(i);
            allArrows &= key.getKey().getType() == InputConstants.Type.KEYSYM && key.getKey().getValue() == arrows.get(i)
                    && key.getKeyModifier() == KeyModifier.NONE;
        }
        if (allArrows) {
            return "Arrows";
        }
        return String.join(" ", moves.stream().map(BuildPreviewClient::keyName).toList());
    }

    private static String keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }

    // A level that holds only the ghost's blocks (in the ghost's own coordinates, its turned corner
    // at 0,0,0), for block rendering to look at: which faces touch another of its blocks, and how
    // bright each spot is. Everything is fully lit, so the ghost reads clearly in the dark; shading
    // by face and biome colours (grass, leaves) come from the real world where the ghost stands.
    private static final class GhostLevel implements BlockAndTintGetter {
        private final List<Blueprint.Part> parts;
        private final ClientLevel world;
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private BlockPos origin = BlockPos.ZERO;

        GhostLevel(List<Blueprint.Part> parts, ClientLevel world) {
            this.parts = parts;
            this.world = world;
            parts.forEach(part -> blocks.put(part.pos(), part.state()));
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return 15;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int darkening) {
            return 15;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return world.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return world.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return world.getBlockTint(pos.offset(origin), colorResolver);
        }

        @Override
        public int getHeight() {
            return world.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return world.getMinBuildHeight();
        }
    }

    // An unfinished building shown finished, and the level its blocks are drawn against: the world,
    // with the building's planned blocks standing in it, so faces against its own blocks (planned or
    // already built) and against the ground are left out, as the world leaves them out.
    private static final class Site implements BlockAndTintGetter {
        private final List<Blueprint.Part> placed;
        private final ClientLevel world;
        private final Map<BlockPos, BlockState> planned = new HashMap<>();
        // The blocks to draw, worked out once a tick (BuildingRules.stillToShow).
        private List<Blueprint.Part> shown = List.of();

        Site(List<Blueprint.Part> placed, ClientLevel world) {
            this.placed = placed;
            this.world = world;
            placed.stream().filter(part -> !part.state().isAir()).forEach(part -> planned.put(part.pos(), part.state()));
            update();
        }

        void update() {
            shown = BuildingRules.stillToShow(placed, world::getBlockState);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState state = planned.get(pos);
            return state != null ? state : world.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return world.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return world.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return world.getBlockTint(pos, colorResolver);
        }

        @Override
        public int getHeight() {
            return world.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return world.getMinBuildHeight();
        }
    }

    // How long drawing the preview took over the last FRAME_WINDOW frames, for the F3 line.
    private static final int FRAME_WINDOW = 60;
    private static final FrameTimes FRAMES = new FrameTimes(FRAME_WINDOW);

    private static final class FrameTimes {
        private final long[] nanos;
        private int next;
        private int count;

        FrameTimes(int window) {
            nanos = new long[window];
        }

        void add(long took) {
            nanos[next] = took;
            next = (next + 1) % nanos.length;
            count = Math.min(count + 1, nanos.length);
        }

        void clear() {
            next = 0;
            count = 0;
        }

        double averageMs() {
            long total = 0;
            for (int i = 0; i < count; i++) {
                total += nanos[i];
            }
            return count == 0 ? 0 : total / 1_000_000.0 / count;
        }

        double maxMs() {
            long max = 0;
            for (int i = 0; i < count; i++) {
                max = Math.max(max, nanos[i]);
            }
            return max / 1_000_000.0;
        }
    }

    // Passes vertices through to the real buffer, with their alpha scaled down so the ghost is
    // see-through, and their colour tinted (red where the building can't go). (A VertexConsumer is what block and entity rendering writes each vertex's
    // position, colour, texture coordinates, light and normal to.)
    private record GhostConsumer(VertexConsumer delegate, float alpha, float[] tint) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(Math.round(red * tint[0]), Math.round(green * tint[1]), Math.round(blue * tint[2]), Math.round(alpha * this.alpha));
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
