package net.neoforged.fml.earlydisplay.render.backend.opengl;

import net.neoforged.fml.earlydisplay.render.ElementShader;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL33C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class GlProgram implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlProgram.class);

    private final String name;
    final int program;
    private final Map<String, Integer> uniformLocations;
    private final Set<String> warnedAboutUniforms = new HashSet<>();

    private GlProgram(String name, int program, Map<String, Integer> uniformLocations) {
        this.name = name;
        this.program = program;
        this.uniformLocations = uniformLocations;
    }

    public void setSampler(String name, int value) {
        Integer location = this.uniformLocations.get(name);
        if (location != null) {
            GL33C.glUniform1i(location, value);
        } else {
            warnAboutMissingUniform(name);
        }
    }

    public void setUniform(String name, GlBuffer ubo) {
        Integer location = this.uniformLocations.get(name);
        if (location != null) {
            GL33C.glBindBufferRange(GL33C.GL_UNIFORM_BUFFER, location, ubo.bufferId, 0, ubo.size());
        } else {
            warnAboutMissingUniform(name);
        }
    }

    private void warnAboutMissingUniform(String name) {
        if (this.warnedAboutUniforms.add(name)) {
            LOGGER.error("Missing uniform '{}' in shader '{}'", name, this);
        }
    }

    @Override
    public void close() {
        GL33C.glDeleteProgram(this.program);
    }

    @Override
    public String toString() {
        return "GlProgram{" + this.name + "@" + this.program + "}";
    }

    static GlProgram create(String name, ByteBuffer vertexShaderSource, ByteBuffer fragmentShaderSource) {
        int vertexShader = GL33C.glCreateShader(GL33C.GL_VERTEX_SHADER);
        GlDebug.labelShader(vertexShader, "FML " + name + ".vert");
        int fragmentShader = GL33C.glCreateShader(GL33C.GL_FRAGMENT_SHADER);
        GlDebug.labelShader(fragmentShader, "FML " + name + ".frag");

        // Bind the source of our shaders to the ones created above
        PointerBuffer sourcePointers = PointerBuffer.allocateDirect(1);
        sourcePointers.put(0, fragmentShaderSource);
        GL33C.glShaderSource(fragmentShader, sourcePointers, new int[] { fragmentShaderSource.remaining() });
        sourcePointers.put(0, vertexShaderSource);
        GL33C.glShaderSource(vertexShader, sourcePointers, new int[] { vertexShaderSource.remaining() });

        // Compile the vertex and fragment elementShader so that we can use them
        GL33C.glCompileShader(vertexShader);
        if (GL33C.glGetShaderi(vertexShader, GL33C.GL_COMPILE_STATUS) == GL33C.GL_FALSE) {
            throw new IllegalStateException("VertexShader linkage failure. \n" + GL33C.glGetShaderInfoLog(vertexShader));
        }
        GL33C.glCompileShader(fragmentShader);
        if (GL33C.glGetShaderi(fragmentShader, GL33C.GL_COMPILE_STATUS) == GL33C.GL_FALSE) {
            throw new IllegalStateException("FragmentShader linkage failure. \n" + GL33C.glGetShaderInfoLog(fragmentShader));
        }

        int program = GL33C.glCreateProgram();
        GlDebug.labelProgram(program, "EarlyDisplay program");
        GL33C.glBindAttribLocation(program, 0, "position");
        GL33C.glBindAttribLocation(program, 1, "uv");
        GL33C.glBindAttribLocation(program, 2, "color");
        GL33C.glAttachShader(program, vertexShader);
        GL33C.glAttachShader(program, fragmentShader);
        GL33C.glLinkProgram(program);
        if (GL33C.glGetProgrami(program, GL33C.GL_LINK_STATUS) == GL33C.GL_FALSE) {
            throw new RuntimeException("ShaderProgram linkage failure. \n" + GL33C.glGetProgramInfoLog(program));
        }

        GL33C.glDetachShader(program, vertexShader);
        GL33C.glDetachShader(program, fragmentShader);
        GL33C.glDeleteShader(vertexShader);
        GL33C.glDeleteShader(fragmentShader);

        int uniformCount = GL33C.glGetProgrami(program, GL33C.GL_ACTIVE_UNIFORMS);
        Map<String, Integer> uniformLocations = new HashMap<>(uniformCount);
        //for (var i = 0; i < uniformCount; i++) {
        //    String uniformName = GL33C.glGetActiveUniformName(program, i);
        //    uniformLocations.put(uniformName, i);
        //}
        int sampler0 = GL33C.glGetUniformLocation(program, ElementShader.UNIFORM_SAMPLER0);
        if (sampler0 != -1) {
            uniformLocations.put(ElementShader.UNIFORM_SAMPLER0, sampler0);
        }
        int screenSize = GL33C.glGetUniformBlockIndex(program, ElementShader.UNIFORM_SCREEN_SIZE);
        if (screenSize != -1) {
            int uboBinding = 0;
            GL33C.glUniformBlockBinding(program, screenSize, uboBinding);
            uniformLocations.put(ElementShader.UNIFORM_SCREEN_SIZE, uboBinding);
        }

        return new GlProgram(name, program, uniformLocations);
    }
}
