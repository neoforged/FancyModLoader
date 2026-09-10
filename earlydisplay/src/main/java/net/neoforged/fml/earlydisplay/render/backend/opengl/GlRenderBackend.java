/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend.opengl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.earlydisplay.render.ElementShader;
import net.neoforged.fml.earlydisplay.render.backend.ELSBuffer;
import net.neoforged.fml.earlydisplay.render.backend.ELSBufferSlice;
import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import net.neoforged.fml.earlydisplay.render.backend.ELSRenderPipeline;
import net.neoforged.fml.earlydisplay.render.backend.ELSTexture;
import net.neoforged.fml.earlydisplay.render.backend.TextureFormat;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

final class GlRenderBackend extends ELSRenderBackend {
    private final long windowHandle;
    private final int maxTextureSize;
    private final Map<ELSRenderPipeline, GlCompiledPipeline> pipelines = new IdentityHashMap<>();
    private final QuadAutoIndexBuffer quadAutoIndexBuffer = new QuadAutoIndexBuffer();
    final VaoCache vaoCache = new VaoCache();

    GlRenderBackend(long windowHandle) {
        this.windowHandle = windowHandle;
        this.maxTextureSize = GL33C.glGetInteger(GL33C.GL_MAX_TEXTURE_SIZE);
    }

    @Override
    public void preloadPipelines(@UnknownNullability Collection<ELSRenderPipeline> pipelines) {
        for (ELSRenderPipeline pipeline : pipelines) {
            ElementShader shader = pipeline.shader();
            try (var vertexShader = shader.loadVertexShader(); var fragmentShader = shader.loadFragmentShader()) {
                GlProgram program = GlProgram.create(shader.getName(), pipeline, vertexShader.buffer(), fragmentShader.buffer());
                this.pipelines.put(pipeline, new GlCompiledPipeline(pipeline, program));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read shaders for " + shader.getName(), e);
            }
        }
    }

    @Override
    public GlTexture createTexture(String debugName, int width, int height, TextureFormat format, boolean linearFilter) {
        int texId = GL33C.glGenTextures();
        GlState.bindTexture2D(texId);
        GlDebug.labelTexture(texId, debugName);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAG_FILTER, linearFilter ? GL33C.GL_LINEAR : GL33C.GL_NEAREST);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, linearFilter ? GL33C.GL_LINEAR : GL33C.GL_NEAREST);
        GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GlConst.toGlInternalId(format), width, height, 0, GlConst.toGlExternalId(format), GL33C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return new GlTexture(debugName, width, height, format, texId);
    }

    @Override
    public void writeToTexture(ELSTexture texture, ByteBuffer pixels) {
        TextureFormat format = texture.format();
        GlState.bindTexture2D(((GlTexture) texture).textureId);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_ROW_LENGTH, texture.width());
        GL33C.glPixelStorei(GL33C.GL_UNPACK_SKIP_PIXELS, 0);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_SKIP_ROWS, 0);
        GL33C.glPixelStorei(GL33C.GL_UNPACK_ALIGNMENT, format.getComponents());
        GL33C.glTexSubImage2D(GL33C.GL_TEXTURE_2D, 0, 0, 0, texture.width(), texture.height(), GlConst.toGlExternalId(format), GL33C.GL_UNSIGNED_BYTE, pixels);
    }

    @Override
    public GlBuffer createBuffer(String label, Set<ELSBuffer.Usage> usage, long size) {
        int bufferId = GL33C.glGenBuffers();
        int bindTarget = GlConst.getBufferBindTarget(usage);
        GL33C.glBindBuffer(bindTarget, bufferId);
        GlDebug.labelBuffer(bufferId, label);
        GL33C.glBufferData(bindTarget, size, GlConst.bufferUsageToGlEnum(usage));
        GL33C.glBindBuffer(bindTarget, 0);
        return new GlBuffer(bufferId, usage, size);
    }

    @Override
    public GlBuffer createBuffer(String label, Set<ELSBuffer.Usage> usage, ByteBuffer data) {
        int bufferId = GL33C.glGenBuffers();
        int size = data.remaining();
        int bindTarget = GlConst.getBufferBindTarget(usage);
        GL33C.glBindBuffer(bindTarget, bufferId);
        GlDebug.labelBuffer(bufferId, label);
        GL33C.glBufferData(bindTarget, data, GlConst.bufferUsageToGlEnum(usage));
        GL33C.glBindBuffer(bindTarget, 0);
        return new GlBuffer(bufferId, usage, size);
    }

    @Override
    public void writeToBuffer(ELSBufferSlice destination, ByteBuffer data) {
        GlBuffer buffer = (GlBuffer) destination.buffer();
        int bindTarget = GlConst.getBufferBindTarget(buffer.usage());
        GL33C.glBindBuffer(bindTarget, buffer.bufferId);
        GL33C.glBufferSubData(bindTarget, destination.offset(), data);
        GL33C.glBindBuffer(bindTarget, 0);
    }

    @Override
    public void copyBufferToBuffer(@UnknownNullability ELSBufferSlice source, ELSBufferSlice target) {
        GL33C.glBindBuffer(GL33C.GL_COPY_READ_BUFFER, ((GlBufferSlice) source).buffer().bufferId);
        GL33C.glBindBuffer(GL33C.GL_COPY_WRITE_BUFFER, ((GlBufferSlice) target).buffer().bufferId);
        GL33C.glCopyBufferSubData(GL33C.GL_COPY_READ_BUFFER, GL33C.GL_COPY_WRITE_BUFFER, 0, 0, source.buffer().size());
        GL33C.glBindBuffer(GL33C.GL_COPY_READ_BUFFER, 0);
        GL33C.glBindBuffer(GL33C.GL_COPY_WRITE_BUFFER, 0);
    }

    @Override
    public GlBuffer getQuadAutoIndexBuffer(int indexCount) {
        return this.quadAutoIndexBuffer.acquire(indexCount);
    }

    @Override
    public GlRenderPass createRenderPass(String label, ELSTexture target, ThemeColor clearColor) {
        GlDebug.pushGroup(label);
        GlState.bindFramebuffer(((GlTexture) target).fbo());
        GlState.clearColor(clearColor.r(), clearColor.b(), clearColor.g(), clearColor.a());
        GlState.scissorTest(false);
        GL33C.glColorMask(true, true, true, true);
        GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT | GL33C.GL_DEPTH_BUFFER_BIT);
        GlState.viewport(0, 0, target.width(), target.height());
        return new GlRenderPass(this);
    }

    @Override
    public boolean startFrame(FramebufferSizeListener listener) {
        int[] fbWidth = new int[1];
        int[] fbHeight = new int[1];
        GLFW.glfwGetFramebufferSize(windowHandle, fbWidth, fbHeight);
        listener.accept(fbWidth[0], fbHeight[0]);
        return true;
    }

    @Override
    public void presentTexture(ELSTexture texture, ThemeColor backgroundColor, int windowFBWidth, int windowFBHeight) {
        int width = texture.width();
        int height = texture.height();
        GlState.viewport(0, 0, width, height);

        float wscale = ((float) windowFBWidth / width);
        float hscale = ((float) windowFBHeight / height);
        float scale = Math.min(wscale, hscale) / 2f;
        int wleft = (int) (windowFBWidth * 0.5f - scale * width);
        int wtop = (int) (windowFBHeight * 0.5f - scale * height);
        int wright = (int) (windowFBWidth * 0.5f + scale * width);
        int wbottom = (int) (windowFBHeight * 0.5f + scale * height);

        GlState.bindDrawFramebuffer(0);
        GlState.bindReadFramebuffer(((GlTexture) texture).fbo());
        GlState.clearColor(backgroundColor.r(), backgroundColor.g(), backgroundColor.b(), 1f);
        GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT | GL33C.GL_DEPTH_BUFFER_BIT);
        GL33C.glBlitFramebuffer(
                0,
                0,
                width,
                height,
                Math.clamp(wleft, 0, windowFBWidth),
                Math.clamp(wtop, 0, windowFBHeight),
                Math.clamp(wright, 0, windowFBWidth),
                Math.clamp(wbottom, 0, windowFBHeight),
                GL33C.GL_COLOR_BUFFER_BIT,
                GL33C.GL_NEAREST);
        GlState.bindFramebuffer(0);

        GLFW.glfwSwapBuffers(this.windowHandle);
    }

    GlCompiledPipeline getCompiledPipeline(ELSRenderPipeline pipeline) {
        GlCompiledPipeline compiledPipeline = this.pipelines.get(pipeline);
        if (compiledPipeline == null) {
            throw new IllegalArgumentException("Unrecognized pipeline: " + pipeline);
        }
        return compiledPipeline;
    }

    @Override
    public int getMaxTextureSize() {
        return this.maxTextureSize;
    }

    @Override
    public long getWindowHandle() {
        return this.windowHandle;
    }

    @Override
    public void acquireContextOwnership(boolean createContext) {
        GLFW.glfwMakeContextCurrent(this.windowHandle);
        if (createContext) {
            GL.createCapabilities();
        }
    }

    @Override
    public void releaseContextOwnership() {
        GLFW.glfwMakeContextCurrent(0L);
    }

    @Override
    public void guardResourceCleanup(Runnable cleanupTask) {
        long previousContext = GLFW.glfwGetCurrentContext();
        GLCapabilities previousCaps;
        try {
            previousCaps = GL.getCapabilities();
        } catch (Throwable t) {
            previousCaps = null;
        }

        boolean needsToRestoreContext = previousContext != this.windowHandle;
        if (needsToRestoreContext) {
            GLFW.glfwMakeContextCurrent(this.windowHandle);
            GL.createCapabilities();
        }

        try {
            cleanupTask.run();

            // Clear out bound resources as the cleanup task most likely destroyed them
            GlState.bindElementArrayBuffer(0);
            GlState.bindFramebuffer(0);
            GlState.bindTexture2D(0);
            GlState.bindVertexArray(0);
        } finally {
            if (needsToRestoreContext) {
                GLFW.glfwMakeContextCurrent(previousContext);
                GL.setCapabilities(previousCaps);
            }
        }
    }

    @Override
    public void close() {
        this.quadAutoIndexBuffer.close();
    }

    @Override
    public String name() {
        return "OpenGL";
    }

    private final class QuadAutoIndexBuffer implements AutoCloseable {
        private static final Set<ELSBuffer.Usage> BUFFER_USAGE = Set.of(ELSBuffer.Usage.INDEX);
        private static final int QUAD_STRIDE = 4;
        private static final int INDEX_STRIDE = 6;

        @Nullable
        private GlBuffer buffer;
        private int indexCount;

        GlBuffer acquire(int indexCount) {
            if (this.buffer == null || this.indexCount < indexCount) {
                int bufferSize = indexCount * 4;
                ByteBuffer data = MemoryUtil.memAlloc(bufferSize);
                try {
                    for (int i = 0; i < indexCount; i += INDEX_STRIDE) {
                        int idx = i * QUAD_STRIDE / INDEX_STRIDE;
                        data.putInt(idx);
                        data.putInt(idx + 1);
                        data.putInt(idx + 2);
                        data.putInt(idx + 2);
                        data.putInt(idx + 3);
                        data.putInt(idx);
                    }
                    data.flip();
                    if (this.buffer != null) {
                        this.buffer.close();
                    }
                    this.buffer = GlRenderBackend.this.createBuffer("ELS quad auto index buffer", BUFFER_USAGE, data);
                    GlState.bindElementArrayBuffer(0);
                } finally {
                    MemoryUtil.memFree(data);
                }
                this.indexCount = indexCount;
            }
            return this.buffer;
        }

        @Override
        public void close() {
            if (this.buffer != null) {
                this.buffer.close();
                this.buffer = null;
            }
        }
    }
}
