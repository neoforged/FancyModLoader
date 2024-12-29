/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.earlywindow;

public interface ImmediateWindowProviderFactory {
    /**
     * @return The name of this window provider type. Do NOT use fmlearlywindow.
     */
    String name();

    /**
     * This is called very early on to initialize ourselves. Use this to initialize the window and other GL core resources.
     *
     * One thing we want to ensure is that we try and create the highest GL_PROFILE we can accomplish.
     * GLFW_CONTEXT_VERSION_MAJOR,GLFW_CONTEXT_VERSION_MINOR should be as high as possible on the created window,
     * and it should have all the typical profile settings.
     *
     * @param arguments The arguments provided to the Java process. This is the entire command line, so you can process
     *                  stuff from it.
     */
    ImmediateWindowProvider create(String[] arguments);
}