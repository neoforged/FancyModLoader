/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import static org.lwjgl.opengl.GL32C.GL_ACTIVE_UNIFORMS;
import static org.lwjgl.opengl.GL32C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL32C.GL_FALSE;
import static org.lwjgl.opengl.GL32C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL32C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL32C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL32C.glAttachShader;
import static org.lwjgl.opengl.GL32C.glBindAttribLocation;
import static org.lwjgl.opengl.GL32C.glCompileShader;
import static org.lwjgl.opengl.GL32C.glCreateProgram;
import static org.lwjgl.opengl.GL32C.glCreateShader;
import static org.lwjgl.opengl.GL32C.glDeleteProgram;
import static org.lwjgl.opengl.GL32C.glDeleteShader;
import static org.lwjgl.opengl.GL32C.glDetachShader;
import static org.lwjgl.opengl.GL32C.glGetActiveUniformName;
import static org.lwjgl.opengl.GL32C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL32C.glGetProgrami;
import static org.lwjgl.opengl.GL32C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL32C.glGetShaderi;
import static org.lwjgl.opengl.GL32C.glLinkProgram;
import static org.lwjgl.opengl.GL32C.glShaderSource;
import static org.lwjgl.opengl.GL32C.glUniform1i;
import static org.lwjgl.opengl.GL32C.glUniform2f;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.earlydisplay.theme.ThemeResource;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElementShader implements AutoCloseable {
    public static final String UNIFORM_SCREEN_SIZE = "screenSize";
    public static final String UNIFORM_SAMPLER0 = "tex";

    private static final Logger LOGGER = LoggerFactory.getLogger(ElementShader.class);

    private final String name;
    private int program;
    private final Map<String, Integer> uniformLocations;
    private final Set<String> warnedAboutUniforms = new HashSet<>();

    private ElementShader(String name, int program, Map<String, Integer> uniformLocations) {
        this.name = name;
        this.program = program;
        this.uniformLocations = uniformLocations;
    }

    public static ElementShader create(String name, ThemeResource vertexShader, ThemeResource fragmentShader, @Nullable Path externalThemeDirectory) {
        try (var vertexShaderBuffer = vertexShader.toNativeBuffer(externalThemeDirectory);
                var fragmentShaderBuffer = fragmentShader.toNativeBuffer(externalThemeDirectory)) {
            return create(name, vertexShaderBuffer.buffer(), fragmentShaderBuffer.buffer());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shaders for " + name);
        }
    }

    public static ElementShader create(String name, ByteBuffer vertexShaderSource, ByteBuffer fragmentShaderSource) {
        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        GlDebug.labelShader(vertexShader, "FML " + name + ".vert");
        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        GlDebug.labelShader(fragmentShader, "FML " + name + ".frag");

        // Bind the source of our shaders to the ones created above
        var sourcePointers = PointerBuffer.allocateDirect(1);
        sourcePointers.put(0, fragmentShaderSource);
        glShaderSource(fragmentShader, sourcePointers, new int[] { fragmentShaderSource.remaining() });
        sourcePointers.put(0, vertexShaderSource);
        glShaderSource(vertexShader, sourcePointers, new int[] { vertexShaderSource.remaining() });

        // Compile the vertex and fragment elementShader so that we can use them
        glCompileShader(vertexShader);
        if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new IllegalStateException("VertexShader linkage failure. \n" + glGetShaderInfoLog(vertexShader));
        }
        glCompileShader(fragmentShader);
        if (glGetShaderi(fragmentShader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new IllegalStateException("FragmentShader linkage failure. \n" + glGetShaderInfoLog(fragmentShader));
        }

        var program = glCreateProgram();
        GlDebug.labelProgram(program, "EarlyDisplay program");
        glBindAttribLocation(program, 0, "position");
        glBindAttribLocation(program, 1, "uv");
        glBindAttribLocation(program, 2, "color");
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("ShaderProgram linkage failure. \n" + glGetProgramInfoLog(program));
        }

        glDetachShader(program, vertexShader);
        glDetachShader(program, fragmentShader);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        var uniformCount = glGetProgrami(program, GL_ACTIVE_UNIFORMS);
        Map<String, Integer> uniformLocations = new HashMap<>(uniformCount);
        for (var i = 0; i < uniformCount; i++) {
            String uniformName = glGetActiveUniformName(program, i);
            uniformLocations.put(uniformName, i);
        }

        return new ElementShader(name, program, uniformLocations);
    }

    public void activate() {
        GlState.useProgram(program);
    }

    public void clear() {
        GlState.useProgram(0);
    }

    public boolean hasUniform(String name) {
        return uniformLocations.containsKey(name);
    }

    public void setUniform1i(String name, int value) {
        var location = uniformLocations.get(name);
        if (location != null) {
            glUniform1i(location, value);
        } else {
            warnAboutMissingUniform(name);
        }
    }

    public void setUniform2f(String name, float x, float y) {
        var location = uniformLocations.get(name);
        if (location != null) {
            glUniform2f(location, x, y);
        } else {
            warnAboutMissingUniform(name);
        }
    }

    private void warnAboutMissingUniform(String name) {
        if (warnedAboutUniforms.add(name)) {
            LOGGER.error("Missing uniform '{}' in shader '{}'", name, this);
        }
    }

    @Override
    public void close() {
        if (program > 0) {
            glDeleteProgram(program);
            program = 0;
        }
    }

    public enum RenderType {
        FONT, TEXTURE, BAR;
    }

    public int program() {
        return program;
    }

    @Override
    public String toString() {
        return name;
    }
}
