package net.neoforged.fml.earlydisplay.render.backend.opengl;

import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GlRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlRenderer.class);

    public static ELSRenderBackend setupBackend(long windowHandle) {
        GLFW.glfwMakeContextCurrent(windowHandle);
        GLFW.glfwSwapInterval(1);
        GLCapabilities capabilities = GL.createCapabilities();
        GlDebug.setCapabilities(capabilities);
        LOGGER.info("GL info: {} GL version {}, {}", GL33C.glGetString(GL33C.GL_RENDERER), GL33C.glGetString(GL33C.GL_VERSION), GL33C.glGetString(GL33C.GL_VENDOR));
        GlState.readFromOpenGL();
        return new GlRenderBackend(windowHandle);
    }

    private GlRenderer() { }
}
