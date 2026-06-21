package net.neoforged.fml.earlydisplay.render.backend;

import net.neoforged.fml.earlydisplay.render.ElementShader;
import net.neoforged.fml.earlydisplay.render.SimpleBufferBuilder;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class ELSDrawCollector {
    private final ELSRenderBackend backend;
    private final List<Draw> draws = new ArrayList<>();
    private int viewportX;
    private int viewportY;
    private int viewportWidth;
    private int viewportHeight;
    private boolean scissorEnabled;
    private int scissorX;
    private int scissorY;
    private int scissorWidth;
    private int scissorHeight;

    public ELSDrawCollector(ELSRenderBackend backend) {
        this.backend = backend;
    }

    public void setViewport(int x, int y, int width, int height) {
        this.viewportX = x;
        this.viewportY = y;
        this.viewportWidth = width;
        this.viewportHeight = height;
    }

    public void enableScissor(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Stencil area must have non-zero width/height");
        }

        this.scissorEnabled = true;
        this.scissorX = x;
        this.scissorY = y;
        this.scissorWidth = width;
        this.scissorHeight = height;
    }

    public void disableScissor() {
        this.scissorEnabled = false;
    }

    public void submitDraw(ELSRenderPipeline pipeline, @Nullable ELSTexture texture, @Nullable SimpleBufferBuilder.Result bufferResult) {
        if (bufferResult != null) {
            this.draws.add(new Draw(pipeline, texture, bufferResult, this.scissorEnabled, this.scissorX, this.scissorY, this.scissorWidth, this.scissorHeight));
        }
    }

    public void execute(String label, ELSTexture target, ThemeColor clearColor, ELSBuffer vertexBuffer, int screenWidth, int screenHeight) {
        int maxIndices = this.draws.stream()
                .filter(Draw::indexed)
                .mapToInt(Draw::indexCount)
                .max()
                .orElse(0);
        ELSBuffer indexBuffer = this.backend.getQuadAutoIndexBuffer(maxIndices);

        ByteBuffer uboData = MemoryUtil.memAlloc(2 * 4);
        try {
            uboData.putFloat(screenWidth);
            uboData.putFloat(screenHeight);
            uboData.rewind();
            this.backend.writeToBuffer(this.backend.screenSizeUbo.slice(), uboData);
        } finally {
            MemoryUtil.memFree(uboData);
        }

        try (ELSRenderPass renderPass = this.backend.createRenderPass(label, target, clearColor)) {
            renderPass.setViewport(this.viewportX, this.viewportY, this.viewportWidth, this.viewportHeight);
            renderPass.bindUniform(ElementShader.UNIFORM_SCREEN_SIZE, this.backend.screenSizeUbo);

            for (Draw draw : this.draws) {
                SimpleBufferBuilder.Result result = draw.bufferResult;

                renderPass.bindPipeline(draw.pipeline);
                renderPass.bindVertexBuffer(vertexBuffer.slice(result.vertexOffset(), result.vertexCount() * (long) result.format().stride));
                renderPass.bindIndexBuffer(result.indexed() ? indexBuffer : null);
                renderPass.bindTexture(ElementShader.UNIFORM_SAMPLER0, draw.texture);

                if (draw.scissorEnabled) {
                    renderPass.enableScissor(draw.scissorX, draw.scissorY, draw.scissorWidth, draw.scissorHeight);
                } else {
                    renderPass.disableScissor();
                }

                if (result.indexed()) {
                    renderPass.drawIndexed(result.indexCount());
                } else {
                    renderPass.draw(result.vertexCount());
                }
            }
        }
    }

    private record Draw(
            ELSRenderPipeline pipeline,
            @Nullable ELSTexture texture,
            SimpleBufferBuilder.Result bufferResult,
            boolean scissorEnabled,
            int scissorX,
            int scissorY,
            int scissorWidth,
            int scissorHeight
    ) {
        boolean indexed() {
            return this.bufferResult.indexed();
        }

        int indexCount() {
            return this.bufferResult.indexCount();
        }
    }
}
