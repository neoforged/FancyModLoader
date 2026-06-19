package net.neoforged.fml.earlydisplay.render.backend;

import net.neoforged.fml.earlydisplay.render.ElementShader;
import org.jetbrains.annotations.Nullable;

public interface ELSRenderPass extends AutoCloseable {
    void setViewport(int x, int y, int width, int height);

    void enableScissor(int x, int y, int width, int height);

    void disableScissor();

    void bindShader(ElementShader shader);

    void bindTexture(String name, @Nullable ELSTexture texture);

    void bindUniform(String name, ELSBuffer buffer);

    void bindVertexBuffer(ELSBufferSlice buffer);

    void bindIndexBuffer(@Nullable ELSBuffer buffer);

    void draw(int vertexCount);

    void drawIndexed(int indexCount);

    @Override
    void close();
}
