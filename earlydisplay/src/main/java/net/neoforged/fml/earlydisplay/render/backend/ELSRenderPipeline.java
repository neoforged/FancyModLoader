package net.neoforged.fml.earlydisplay.render.backend;

import net.neoforged.fml.earlydisplay.render.ElementShader;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ELSRenderPipeline(
        ElementShader shader,
        VertexFormat vertexFormat,
        VertexFormat.Mode vertexMode,
        @Nullable String sampler,
        List<String> uniforms) {}
