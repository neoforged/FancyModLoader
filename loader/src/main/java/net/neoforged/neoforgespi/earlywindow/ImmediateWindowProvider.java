/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.earlywindow;

import java.nio.file.Path;
import java.util.List;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.EarlyLoadingScreenController;

/**
 * This is for allowing the plugging in of alternative early display implementations.
 *
 * They can be selected through the config value "earlyWindowProvider" which defaults to "fmlearlywindow".
 *
 * There are a few key things to keep in mind if following through on implementation. You cannot access the game state as it
 * literally DOES NOT EXIST at the time this object is constructed. You have to be very careful about managing the handoff
 * to mojang, be sure that if you're trying to tick your window in a background thread (a nice idea!) that you properly
 * transition to the main thread before handoff is complete. Do note that in general, you should construct your GL objects
 * on the MAIN thread before starting your ticker, to ensure MacOS compatibility.
 *
 * No doubt many more things can be said here.
 */
public interface ImmediateWindowProvider extends EarlyLoadingScreenController {
    /**
     * @return The name of this window provider. Do NOT use fmlearlywindow.
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
     * @return A runnable that will be periodically ticked by FML during startup ON THE MAIN THREAD. This is usually
     *         a good place to put glfwPollEvents() tests.
     */
    Runnable initialize(String[] arguments);

    /**
     * This is called during some very early startup routines to show a crash dialog
     * using e.g. tinyfd dialogs
     * 
     * @param message The message to display
     */
    void crash(String message);

    /**
     * This is called when a fatal loading error occurs to show a MC-independent loading error screen and
     * then terminate the game.
     *
     * @param issues          The loading issues that occurred
     * @param modsFolder      The path to the mods folder
     * @param logFile         The path to the latest.log file
     * @param crashReportFile The path to the crash report of the fatal error
     */
    void displayFatalErrorAndExit(List<ModLoadingIssue> issues, Path modsFolder, Path logFile, Path crashReportFile);
}
