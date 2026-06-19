package net.neoforged.fml.earlydisplay.render.backend.opengl;

import net.neoforged.fml.earlydisplay.render.ElementShader;
import net.neoforged.fml.earlydisplay.render.backend.ELSBuffer;
import net.neoforged.fml.earlydisplay.render.backend.ELSBufferSlice;
import net.neoforged.fml.earlydisplay.render.backend.ELSRenderPass;
import net.neoforged.fml.earlydisplay.render.backend.ELSTexture;
import net.neoforged.fml.earlydisplay.render.backend.VertexFormat;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL33C;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class GlRenderPass implements ELSRenderPass {
    private final GlRenderBackend backend;
    private final Map<String, @Nullable GlTexture> textures = new HashMap<>();
    private final Map<String, GlBuffer> uniforms = new HashMap<>();
    @Nullable
    private GlProgram program;
    @Nullable
    private GlBufferSlice vertexBuffer;
    @Nullable
    private GlBuffer indexBuffer;

    GlRenderPass(GlRenderBackend backend) {
        this.backend = backend;
    }

    @Override
    public void setViewport(int x, int y, int width, int height) {
        GlState.viewport(x, y, width, height);
    }

    @Override
    public void enableScissor(int x, int y, int width, int height) {
        GlState.scissorTest(true);
        GlState.scissorBox(x, y, width, height);
    }

    @Override
    public void disableScissor() {
        GlState.scissorTest(false);
    }

    @Override
    public void bindShader(ElementShader shader) {
        this.program = this.backend.getCompiledShader(shader);
    }

    @Override
    public void bindTexture(String name, @Nullable ELSTexture texture) {
        if (texture != null) {
            this.textures.put(name, (GlTexture) texture);
        } else {
            this.textures.remove(name);
        }
    }

    @Override
    public void bindUniform(String name, ELSBuffer buffer) {
        this.uniforms.put(name, (GlBuffer) buffer);
    }

    @Override
    public void bindVertexBuffer(ELSBufferSlice buffer) {
        this.vertexBuffer = (GlBufferSlice) buffer;
    }

    @Override
    public void bindIndexBuffer(@Nullable ELSBuffer buffer) {
        this.indexBuffer = (GlBuffer) buffer;
    }

    @Override
    public void draw(int vertexCount) {
        setupPipelineState(false);
        GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, vertexCount);
    }

    @Override
    public void drawIndexed(int indexCount) {
        setupPipelineState(true);
        GL33C.glDrawElements(GL33C.GL_TRIANGLES, indexCount, GL33C.GL_UNSIGNED_INT, 0);
    }

    private void setupPipelineState(boolean indexed) {
        Objects.requireNonNull(this.program, "No shader set");
        Objects.requireNonNull(this.vertexBuffer, "No vertex buffer set");
        if (indexed) {
            Objects.requireNonNull(this.indexBuffer, "No index buffer set");
        }

        GlState.useProgram(this.program.program);

        GlState.bindSampler(0);
        for (Map.Entry<String, @Nullable GlTexture> entry : this.textures.entrySet()) {
            this.program.setSampler(entry.getKey(), 0);
            GlTexture texture = entry.getValue();
            GlState.bindTexture2D(texture != null ? texture.textureId : 0);
        }

        for (Map.Entry<String, GlBuffer> entry : this.uniforms.entrySet()) {
            this.program.setUniform(entry.getKey(), entry.getValue());
        }

        this.backend.vaoCache.bindVertexBuffer(VertexFormat.POS_TEX_COLOR, this.vertexBuffer);
        GlState.bindElementArrayBuffer(indexed ? indexBuffer.bufferId : 0);

        GlState.enableBlend(true);
        GlState.blendFuncSeparate(GL33C.GL_SRC_ALPHA, GL33C.GL_ONE_MINUS_SRC_ALPHA, GL33C.GL_ZERO, GL33C.GL_ONE);
    }

    @Override
    public void close() {
        GlState.bindFramebuffer(0);
        GlDebug.popGroup();
    }
}
