/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Set;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;

public abstract class ELSRenderBackend implements AutoCloseable {
    final ELSBuffer screenSizeUbo;

    protected ELSRenderBackend() {
        this.screenSizeUbo = this.createBuffer("ScreenSize UBO", Set.of(ELSBuffer.Usage.UNIFORM, ELSBuffer.Usage.COPY_DST), 2 * 4);
    }

    public abstract void preloadPipelines(Collection<ELSRenderPipeline> pipelines);

    public abstract ELSTexture createTexture(String debugName, int width, int height, TextureFormat format, boolean linearFilter);

    public abstract void writeToTexture(ELSTexture texture, ByteBuffer pixels);

    public abstract ELSBuffer createBuffer(String label, Set<ELSBuffer.Usage> usage, long size);

    public abstract ELSBuffer createBuffer(String label, Set<ELSBuffer.Usage> usage, ByteBuffer data);

    public abstract void writeToBuffer(ELSBufferSlice buffer, ByteBuffer data);

    public abstract void copyBufferToBuffer(ELSBufferSlice source, ELSBufferSlice destination);

    public abstract ELSBuffer getQuadAutoIndexBuffer(int indexCount);

    public abstract ELSRenderPass createRenderPass(String label, ELSTexture target, ThemeColor clearColor);

    public abstract boolean startFrame(FramebufferSizeListener listener);

    public abstract void presentTexture(ELSTexture texture, ThemeColor backgroundColor, int windowFBWidth, int windowFBHeight);

    public abstract int getMaxTextureSize();

    public abstract long getWindowHandle();

    public abstract void acquireContextOwnership(boolean createContext);

    public abstract void releaseContextOwnership();

    public abstract void guardResourceCleanup(Runnable cleanupTask);

    @Override
    public abstract void close();

    public abstract String name();

    public interface FramebufferSizeListener {
        void accept(int width, int height);
    }
}
