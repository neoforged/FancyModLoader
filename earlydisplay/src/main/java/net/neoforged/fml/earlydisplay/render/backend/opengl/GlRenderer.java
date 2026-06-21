package net.neoforged.fml.earlydisplay.render.backend.opengl;

import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import net.neoforged.fml.loading.FMLConfig;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GlRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlRenderer.class);

    public static void configureWindowHints() {
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_NATIVE_CONTEXT_API);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GL11C.GL_TRUE);

        if (FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.DEBUG_OPENGL)) {
            LOGGER.info("Requesting the creation of an OpenGL debug context");
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, GL11C.GL_TRUE);
        }
    }

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
