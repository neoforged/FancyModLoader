/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend;

import org.jetbrains.annotations.Nullable;

public interface ELSRenderPass extends AutoCloseable {
    void setViewport(int x, int y, int width, int height);

    void enableScissor(int x, int y, int width, int height);

    void disableScissor();

    void bindPipeline(ELSRenderPipeline pipeline);

    void bindTexture(String name, @Nullable ELSTexture texture);

    void bindUniform(String name, ELSBuffer buffer);

    void bindVertexBuffer(ELSBufferSlice buffer);

    void bindIndexBuffer(@Nullable ELSBuffer buffer);

    void draw(int vertexCount);

    void drawIndexed(int indexCount);

    @Override
    void close();
}
