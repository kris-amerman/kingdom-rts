package com.krisamerman.kingdomrts.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.nation.BorderLines;
import com.krisamerman.kingdomrts.nation.NationPayloads;
import com.krisamerman.kingdomrts.util.Perf;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Client only. The nation management interface's borders view: while it's on, every claim around
// the player is outlined on the ground in its owner's colour, with a low see-through wall rising
// from the line and a faint curtain below it, down to the bottom of the world, which shows wherever
// a cave or tunnel crosses the border (rock hides it, as it hides blocks). A key in the corner says
// whose is whose. The server sends the claims
// (BorderViewers); here they're laid on the ground (BorderLines), baked into a mesh per
// settlement, and drawn.
//
// Cost (docs/perf.md): each frame draws the baked meshes of the settlements near enough to see,
// nothing more. Each tick checks the ground under a fixed number of border columns, going round
// the borders near the player, so a block placed or broken on a border shows within a tick or two
// without ever checking everything at once. A settlement's mesh is baked again only when the
// ground under one of its columns changed.
@EventBusSubscriber(modid = KingdomRts.MODID, value = Dist.CLIENT)
public final class BordersClient {
    // How many border columns each tick checks the ground under.
    private static final int COLUMNS_PER_TICK = 256;
    // Borders are drawn up to this far away (along the ground, to the nearest point of the claim),
    // fading out over the last stretch. Never further than the render distance: past it there's no
    // ground to lay them on.
    private static final double DRAW_DISTANCE = 128.0;
    private static final double FADE_DISTANCE = 32.0;
    // How high the wall rises above the line, and how see-through it is at the bottom (it fades out
    // to nothing at the top).
    private static final float WALL_HEIGHT = 1.25f;
    private static final float WALL_ALPHA = 0.35f;
    // How see-through the curtain below the ground is.
    private static final float CURTAIN_ALPHA = 0.15f;
    // The line sits this far above the ground, so the ground doesn't hide it.
    private static final float LINE_LIFT = 0.03f;
    // A column whose ground isn't known (its chunk isn't loaded): its piece isn't drawn.
    private static final int UNKNOWN = Integer.MIN_VALUE;
    private static final int LEGEND_MARGIN = 4;
    private static final int LEGEND_LINE = 10;
    private static final int LEGEND_MAX = 6;
    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private static boolean on;
    private static List<NationPayloads.BorderView> borders = List.of();
    private static List<Shown> shown = List.of();
    // Where the round of ground checks has got to: a settlement, and a piece of its border.
    private static int checkSettlement;
    private static int checkPiece;

    private BordersClient() {
    }

    public static boolean isOn() {
        return on;
    }

    // The Borders toggle: switches the view on or off.
    public static void toggle() {
        if (on) {
            turnOff();
            PacketDistributor.sendToServer(new NationPayloads.ToggleBorders(false));
        } else {
            on = true;
            PacketDistributor.sendToServer(new NationPayloads.ToggleBorders(true));
        }
    }

    private static void turnOff() {
        on = false;
        borders = List.of();
        closeAll();
    }

    private static void closeAll() {
        shown.forEach(Shown::close);
        shown = List.of();
        checkSettlement = 0;
        checkPiece = 0;
    }

    // The server sent the claims around the player. They're laid on the ground at once, where it's
    // loaded, and only in the dimension they were sent for.
    public static void show(NationPayloads.ShowBorders sent) {
        if (!on) {
            return;
        }
        closeAll();
        borders = sent.borders();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(sent.dimension())) {
            return;
        }
        List<Shown> laid = new ArrayList<>();
        for (NationPayloads.BorderView view : borders) {
            Shown settlement = new Shown(view, level.getMinBuildHeight());
            for (int i = 0; i < settlement.pieces.length; i++) {
                settlement.check(level, i);
            }
            laid.add(settlement);
        }
        shown = laid;
        KingdomRts.LOGGER.debug("Borders view: sent {} settlements ({} pieces of border) in {}", laid.size(),
                laid.stream().mapToInt(settlement -> settlement.pieces.length).sum(), sent.dimension());
    }

    // How far borders are drawn: DRAW_DISTANCE, or the render distance if that's shorter.
    private static double drawDistance(Minecraft minecraft) {
        return Math.min(DRAW_DISTANCE, minecraft.options.getEffectiveRenderDistance() * 16.0);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (!on || level == null || shown.isEmpty() || minecraft.player == null) {
            return;
        }
        ProfilerFiller profiler = minecraft.getProfiler();
        Perf.push(profiler, Perf.ClientSection.BORDERS_TICK);
        long start = System.nanoTime();
        // Round the settlements near enough to be drawn (a little further, so they're ready as
        // they come into sight), a fixed number of columns at a time.
        Vec3 at = minecraft.player.position();
        double reach = drawDistance(minecraft) + FADE_DISTANCE;
        int budget = COLUMNS_PER_TICK;
        for (int passed = 0; budget > 0 && passed < shown.size(); ) {
            if (checkSettlement >= shown.size()) {
                checkSettlement = 0;
            }
            Shown settlement = shown.get(checkSettlement);
            if (checkPiece >= settlement.pieces.length || settlement.distanceTo(at.x, at.z) > reach) {
                checkSettlement++;
                checkPiece = 0;
                passed++; // a whole round with nothing near enough to check ends the tick's work
                continue;
            }
            passed = 0;
            int end = Math.min(settlement.pieces.length, checkPiece + budget);
            for (int i = checkPiece; i < end; i++) {
                settlement.check(level, i);
            }
            budget -= end - checkPiece;
            checkPiece = end;
        }
        for (Shown settlement : shown) {
            if (settlement.changed) {
                settlement.bake();
            }
        }
        if (ClientPerf.isRecording()) {
            ClientPerf.bordersTick(System.nanoTime() - start);
        }
        profiler.pop();
    }

    // The first free block above the ground at a column, under any leaves, or UNKNOWN if its chunk
    // isn't loaded.
    private static int groundAt(ClientLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return UNKNOWN;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(BorderLines.GROUND, x, z), z);
        for (int i = 0; i < BorderLines.MAX_LEAVES && pos.getY() > level.getMinBuildHeight(); i++) {
            pos.move(0, -1, 0);
            BlockState below = level.getBlockState(pos);
            if (!below.is(BlockTags.LEAVES) && (below.blocksMotion() || !below.getFluidState().isEmpty())) {
                return pos.getY() + 1;
            }
        }
        return pos.getY() + 1;
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        turnOff();
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !on || shown.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ProfilerFiller profiler = minecraft.getProfiler();
        Perf.push(profiler, Perf.ClientSection.BORDERS_RENDER);
        long start = System.nanoTime();
        Vec3 camera = event.getCamera().getPosition();
        double distance = drawDistance(minecraft);
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(event.getPoseStack().last().pose());
        int drawn = 0;
        for (Shown settlement : shown) {
            double away = settlement.distanceTo(camera.x, camera.z);
            if (away >= distance || settlement.mesh == null) {
                continue;
            }
            // Fully there until the last stretch, then fading out to nothing at the edge.
            float alpha = (float) Math.min(1.0, (distance - away) / FADE_DISTANCE);
            BlockPos anchor = settlement.anchor;
            Matrix4f modelView = new Matrix4f(view).translate((float) (anchor.getX() - camera.x), (float) (anchor.getY() - camera.y),
                    (float) (anchor.getZ() - camera.z));
            settlement.mesh.draw(modelView, 1.0f, 1.0f, 1.0f, alpha, false);
            drawn += settlement.drawnPieces;
        }
        if (ClientPerf.isRecording()) {
            ClientPerf.bordersFrame(System.nanoTime() - start, drawn);
        }
        profiler.pop();
    }

    // The key, top left: each settlement shown, in its owner's colour, nearest first. Out of the way
    // of the F3 screen, which uses the same corner.
    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!on || minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int y = LEGEND_MARGIN;
        graphics.drawString(minecraft.font, "Borders", LEGEND_MARGIN, y, TITLE_COLOR);
        for (int i = 0; i < Math.min(borders.size(), LEGEND_MAX); i++) {
            NationPayloads.BorderView view = borders.get(i);
            y += LEGEND_LINE;
            int color = 0xFF000000 | view.color();
            graphics.fill(LEGEND_MARGIN, y + 1, LEGEND_MARGIN + 7, y + 8, color);
            String owner = view.own() ? "yours" : view.faction().isEmpty() ? "unclaimed" : view.faction();
            graphics.drawString(minecraft.font, view.settlement() + " (" + owner + ")", LEGEND_MARGIN + 10, y, color);
        }
        if (borders.size() > LEGEND_MAX) {
            graphics.drawString(minecraft.font, "and " + (borders.size() - LEGEND_MAX) + " more", LEGEND_MARGIN + 10, y + LEGEND_LINE, TITLE_COLOR);
        }
    }

    // One settlement's border as shown: its pieces, each at the ground under it as last checked,
    // and the mesh baked from them.
    private static final class Shown {
        final BorderLines.Piece[] pieces;
        final float red;
        final float green;
        final float blue;
        // The claim's box along the ground, in blocks: how far away it is is worked out from it.
        final int minX;
        final int minZ;
        final int maxX;
        final int maxZ;
        // The mesh's vertices are relative to this, so their numbers stay small.
        final BlockPos anchor;
        // The bottom of the world, where the curtain below the ground starts.
        final int bottom;
        @Nullable
        BakedMesh mesh;
        int drawnPieces;
        // The ground under a piece changed since the mesh was baked.
        boolean changed;

        Shown(NationPayloads.BorderView view, int bottom) {
            this.bottom = bottom;
            Set<Long> chunks = new HashSet<>();
            int lowX = Integer.MAX_VALUE;
            int lowZ = Integer.MAX_VALUE;
            int highX = Integer.MIN_VALUE;
            int highZ = Integer.MIN_VALUE;
            for (long chunk : view.chunks()) {
                chunks.add(chunk);
                lowX = Math.min(lowX, ChunkPos.getX(chunk));
                lowZ = Math.min(lowZ, ChunkPos.getZ(chunk));
                highX = Math.max(highX, ChunkPos.getX(chunk));
                highZ = Math.max(highZ, ChunkPos.getZ(chunk));
            }
            pieces = BorderLines.pieces(chunks, (x, z) -> UNKNOWN).toArray(BorderLines.Piece[]::new);
            red = (view.color() >> 16 & 0xFF) / 255.0f;
            green = (view.color() >> 8 & 0xFF) / 255.0f;
            blue = (view.color() & 0xFF) / 255.0f;
            minX = lowX * 16;
            minZ = lowZ * 16;
            maxX = highX * 16 + 16;
            maxZ = highZ * 16 + 16;
            anchor = new BlockPos(minX, 0, minZ);
        }

        // Checks the ground under one piece, noting if it changed.
        void check(ClientLevel level, int index) {
            BorderLines.Piece piece = pieces[index];
            int ground = groundAt(level, piece.columnX(), piece.columnZ());
            if (ground != piece.y()) {
                pieces[index] = piece.atHeight(ground);
                changed = true;
            }
        }

        // How far a point is from the claim, along the ground (0 inside it).
        double distanceTo(double x, double z) {
            double dx = Math.max(0.0, Math.max(minX - x, x - maxX));
            double dz = Math.max(0.0, Math.max(minZ - z, z - maxZ));
            return Math.sqrt(dx * dx + dz * dz);
        }

        // Bakes the pieces whose ground is known: for each, a line on the ground, a wall above it
        // fading upwards, and a faint curtain below it down to the bottom of the world. Where the
        // ground steps up or down between two pieces, a short upright line joins them.
        void bake() {
            close();
            changed = false;
            int count = 0;
            for (BorderLines.Piece piece : pieces) {
                if (piece.y() != UNKNOWN) {
                    count++;
                }
            }
            drawnPieces = count;
            if (count == 0) {
                return;
            }
            mesh = BakedMesh.bake(buffers -> {
                VertexConsumer lines = buffers.apply(RenderType.lines());
                VertexConsumer walls = buffers.apply(RenderType.debugQuads());
                BorderLines.Piece previous = null;
                for (BorderLines.Piece piece : pieces) {
                    if (piece.y() == UNKNOWN) {
                        previous = null;
                        continue;
                    }
                    float x1 = (float) (piece.x1() - anchor.getX());
                    float z1 = (float) (piece.z1() - anchor.getZ());
                    float x2 = (float) (piece.x2() - anchor.getX());
                    float z2 = (float) (piece.z2() - anchor.getZ());
                    float y = piece.y() + LINE_LIFT;
                    line(lines, x1, y, z1, x2, y, z2);
                    walls.addVertex(x1, y, z1).setColor(red, green, blue, WALL_ALPHA);
                    walls.addVertex(x2, y, z2).setColor(red, green, blue, WALL_ALPHA);
                    walls.addVertex(x2, y + WALL_HEIGHT, z2).setColor(red, green, blue, 0.0f);
                    walls.addVertex(x1, y + WALL_HEIGHT, z1).setColor(red, green, blue, 0.0f);
                    walls.addVertex(x1, bottom, z1).setColor(red, green, blue, CURTAIN_ALPHA);
                    walls.addVertex(x2, bottom, z2).setColor(red, green, blue, CURTAIN_ALPHA);
                    walls.addVertex(x2, y, z2).setColor(red, green, blue, CURTAIN_ALPHA);
                    walls.addVertex(x1, y, z1).setColor(red, green, blue, CURTAIN_ALPHA);
                    if (previous != null && previous.x2() == piece.x1() && previous.z2() == piece.z1() && previous.y() != piece.y()) {
                        float lower = Math.min(previous.y(), piece.y()) + LINE_LIFT;
                        float upper = Math.max(previous.y(), piece.y()) + LINE_LIFT;
                        line(lines, x1, lower, z1, x1, upper, z1);
                    }
                    previous = piece;
                }
            });
        }

        // A line from one point to another. Lines carry their direction as their normal: the shader
        // uses it to give them their width on screen.
        private void line(VertexConsumer lines, float x1, float y1, float z1, float x2, float y2, float z2) {
            float dx = x2 - x1;
            float dy = y2 - y1;
            float dz = z2 - z1;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= length;
            dy /= length;
            dz /= length;
            lines.addVertex(x1, y1, z1).setColor(red, green, blue, 1.0f).setNormal(dx, dy, dz);
            lines.addVertex(x2, y2, z2).setColor(red, green, blue, 1.0f).setNormal(dx, dy, dz);
        }

        void close() {
            if (mesh != null) {
                mesh.close();
                mesh = null;
            }
        }
    }
}
