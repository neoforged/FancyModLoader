/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.wayland;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.memAllocInt;

import net.neoforged.fml.earlydisplay.DisplayWindow;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Loading Window that is opened Immediately after Forge starts.
 * It is called from the ModDirTransformerDiscoverer, the soonest method that ModLauncher calls into Forge code.
 * In this way, we can be sure that this will not run before any transformer or injection.
 *
 * The window itself is spun off into a secondary thread, and is handed off to the main game by Forge.
 *
 * Because it is created so early, this thread will "absorb" the context from OpenGL.
 * Therefore, it is of utmost importance that the Context is made Current for the main thread before handoff,
 * otherwise OS X will crash out.
 *
 * Based on the prior ClientVisualization, with some personal touches.
 */
public class WaylandDisplayWindow extends DisplayWindow {
    private static final Logger LOGGER = LoggerFactory.getLogger("WAYLANDEARLYDISPLAY");
    private static final int GLFW_WAYLAND_APP_ID = 0x26001;

    @Override
    public String name() {
        return "fmlwaylandearlywindow";
    }

    @Override
    public void initWindow(@Nullable String mcVersion) {
        if (glfwPlatformSupported(GLFW_PLATFORM_WAYLAND)) {
            glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_WAYLAND);
        }

        super.initWindow(mcVersion);
    }

    @Override
    protected void glfwWindowHints(String mcVersion) {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_NATIVE_CONTEXT_API);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
        var major = memAllocInt(1);
        var minor = memAllocInt(1);
        var rev = memAllocInt(1);
        glfwGetVersion(major, minor, rev);
        if (major.get(0) >= 3 && minor.get(0) >= 4) {
            WaylandIconProvider.injectIcon(mcVersion);
            glfwWindowHintString(GLFW_WAYLAND_APP_ID, WaylandIconProvider.APP_ID);
        }
    }
}
