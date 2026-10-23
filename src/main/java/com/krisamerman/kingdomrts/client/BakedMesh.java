package com.krisamerman.kingdomrts.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

// Client only, render thread only. Blocks drawn once into vertex buffers on the graphics card (one
// per render type they use) and then drawn from there every frame, instead of working every block
// out again each frame: what makes a preview cost almost nothing per frame (docs/perf.md, S-046).
// Close it when done with it, to free the buffers.
final class BakedMesh implements AutoCloseable {
    // Room to start with per render type; the builder grows as needed.
    private static final int INITIAL_BYTES = 64 * 1024;

    private final Map<RenderType, VertexBuffer> buffers;

    private BakedMesh(Map<RenderType, VertexBuffer> buffers) {
        this.buffers = buffers;
    }

    // Bakes whatever drawer writes: it's handed a function giving the vertex consumer to use for
    // each render type, as a MultiBufferSource would.
    static BakedMesh bake(Consumer<Function<RenderType, VertexConsumer>> drawer) {
        Map<RenderType, ByteBufferBuilder> memory = new LinkedHashMap<>();
        Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        drawer.accept(type -> builders.computeIfAbsent(type, t -> {
            ByteBufferBuilder bytes = new ByteBufferBuilder(INITIAL_BYTES);
            memory.put(t, bytes);
            return new BufferBuilder(bytes, t.mode(), t.format());
        }));
        Map<RenderType, VertexBuffer> buffers = new LinkedHashMap<>();
        try {
            builders.forEach((type, builder) -> {
                MeshData mesh = builder.build(); // null if nothing was drawn
                if (mesh != null) {
                    VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    buffer.bind();
                    buffer.upload(mesh); // also frees the mesh data
                    VertexBuffer.unbind();
                    buffers.put(type, buffer);
                }
            });
        } finally {
            memory.values().forEach(ByteBufferBuilder::close);
        }
        return new BakedMesh(buffers);
    }

    // Draws the mesh with modelView placing it (relative to the camera), its colours multiplied by
    // the tint (red, green, blue, alpha), writing depth or not. See-through meshes are drawn without
    // writing depth: their faces aren't sorted back to front, so faces drawn first would otherwise
    // hide the ones behind them.
    void draw(Matrix4f modelView, float red, float green, float blue, float alpha, boolean writeDepth) {
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        for (Map.Entry<RenderType, VertexBuffer> entry : buffers.entrySet()) {
            RenderType type = entry.getKey();
            type.setupRenderState();
            if (!writeDepth) {
                RenderSystem.depthMask(false);
            }
            RenderSystem.setShaderColor(red, green, blue, alpha);
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                // Block layers' shaders move each chunk by an offset; these meshes are placed by
                // modelView instead.
                if (shader.CHUNK_OFFSET != null) {
                    shader.CHUNK_OFFSET.set(0.0f, 0.0f, 0.0f);
                }
                VertexBuffer buffer = entry.getValue();
                buffer.bind();
                buffer.drawWithShader(modelView, projection, shader);
                VertexBuffer.unbind();
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.depthMask(true);
            type.clearRenderState();
        }
    }

    boolean isEmpty() {
        return buffers.isEmpty();
    }

    @Override
    public void close() {
        buffers.values().forEach(VertexBuffer::close);
        buffers.clear();
    }
}
