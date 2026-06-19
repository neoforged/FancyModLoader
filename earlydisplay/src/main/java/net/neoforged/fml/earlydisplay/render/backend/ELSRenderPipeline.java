package net.neoforged.fml.earlydisplay.render.backend;

import net.neoforged.fml.earlydisplay.render.ElementShader;

public record ELSRenderPipeline(ElementShader shader, VertexFormat vertexFormat, VertexFormat.Mode vertexMode) {}
