/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend;

import java.util.List;
import net.neoforged.fml.earlydisplay.render.ElementShader;
import org.jetbrains.annotations.Nullable;

public record ELSRenderPipeline(
        ElementShader shader,
        VertexFormat vertexFormat,
        VertexFormat.Mode vertexMode,
        @Nullable String texture,
        List<String> uniforms) {}
