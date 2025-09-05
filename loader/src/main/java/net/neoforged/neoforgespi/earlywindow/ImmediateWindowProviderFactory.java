package net.neoforged.neoforgespi.earlywindow;

import net.neoforged.fml.loading.ProgramArgs;

public interface ImmediateWindowProviderFactory {
    /**
     * @return The name of this window provider type. Do NOT use fmlearlywindow.
     */
    String name();

    /**
     * This is called very early on to initialize ourselves. Use this to initialize the window and other GL core resources.
     * <p>
     * One thing we want to ensure is that we try and create the highest GL_PROFILE we can accomplish.
     * GLFW_CONTEXT_VERSION_MAJOR,GLFW_CONTEXT_VERSION_MINOR should be as high as possible on the created window,
     * and it should have all the typical profile settings.
     *
     * @param arguments The arguments provided to the Java process. This is the entire command line, so you can process
     *                  stuff from it.
     */
    ImmediateWindowProvider create(ProgramArgs arguments);
}
