/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_CREATION_API;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_NATIVE_CONTEXT_API;
import static org.lwjgl.glfw.GLFW.GLFW_NO_ERROR;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_API;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_DEBUG_CONTEXT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.GLFW_X11_CLASS_NAME;
import static org.lwjgl.glfw.GLFW.GLFW_X11_INSTANCE_NAME;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwGetError;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetMonitorPos;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwGetWindowPos;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwMaximizeWindow;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetFramebufferSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowIcon;
import static org.lwjgl.glfw.GLFW.glfwSetWindowPos;
import static org.lwjgl.glfw.GLFW.glfwSetWindowPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowHintString;
import static org.lwjgl.opengl.GL32C.GL_TRUE;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Month;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import joptsimple.OptionParser;
import net.neoforged.fml.earlydisplay.render.LoadingScreenRenderer;
import net.neoforged.fml.earlydisplay.render.SimpleFont;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import net.neoforged.fml.earlydisplay.theme.ThemeIds;
import net.neoforged.fml.earlydisplay.theme.ThemeLoader;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Loading Window that is opened Immediately after Forge starts.
 * It is called from the ModDirTransformerDiscoverer, the soonest method that ModLauncher calls into Forge code.
 * In this way, we can be sure that this will not run before any transformer or injection.
 * <p>
 * The window itself is spun off into a secondary thread, and is handed off to the main game by Forge.
 * <p>
 * Because it is created so early, this thread will "absorb" the context from OpenGL.
 * Therefore, it is of utmost importance that the Context is made Current for the main thread before handoff,
 * otherwise OS X will crash out.
 * <p>
 * Based on the prior ClientVisualization, with some personal touches.
 */
public class DisplayWindow implements ImmediateWindowProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("EARLYDISPLAY");
    private static final ThreadGroup BACKGROUND_THREAD_GROUP = new ThreadGroup("fml-loadingscreen");
    private final ProgressMeter mainProgress;

    private boolean darkMode;
    private Theme theme;

    private int framecount;
    private ScheduledFuture<LoadingScreenRenderer> rendererFuture;

    // The GL ID of the window. Used for all operations
    private long window;
    // The thread that contains and ticks the window while Forge is loading mods
    private ScheduledExecutorService renderScheduler;
    private int fbWidth;
    private int fbHeight;
    private int winWidth;
    private int winHeight;
    private int winX;
    private int winY;

    private boolean maximized;
    private Map<String, SimpleFont> fonts;
    private Runnable repaintTick = () -> {};
    private ThemeColor background;

    public DisplayWindow() {
        mainProgress = StartupNotificationManager.addProgressBar("", 0);
    }

    @Override
    public String name() {
        return "fmlearlywindow";
    }

    @Override
    public Runnable initialize(String[] arguments) {
        final OptionParser parser = new OptionParser();
        var mcversionopt = parser.accepts("fml.mcVersion").withRequiredArg().ofType(String.class);
        var forgeversionopt = parser.accepts("fml.neoForgeVersion").withRequiredArg().ofType(String.class);
        var widthopt = parser.accepts("width")
                .withRequiredArg().ofType(Integer.class)
                .defaultsTo(FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH));
        var heightopt = parser.accepts("height")
                .withRequiredArg().ofType(Integer.class)
                .defaultsTo(FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT));
        var maximizedopt = parser.accepts("earlywindow.maximized");
        parser.allowsUnrecognizedOptions();
        var parsed = parser.parse(arguments);
        winWidth = parsed.valueOf(widthopt);
        winHeight = parsed.valueOf(heightopt);
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH, winWidth);
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT, winHeight);

        if (Boolean.getBoolean("fml.earlyWindowDarkMode")) {
            this.darkMode = true;
        } else {
            try {
                var optionLines = Files.readAllLines(FMLPaths.GAMEDIR.get().resolve(Paths.get("options.txt")));
                var options = optionLines.stream().map(l -> l.split(":")).filter(a -> a.length == 2).collect(Collectors.toMap(a -> a[0], a -> a[1]));
                this.darkMode = Boolean.parseBoolean(options.getOrDefault("darkMojangStudiosBackground", "false"));
            } catch (NoSuchFileException ignored) {
                // No options
            } catch (IOException e) {
                LOGGER.warn("Failed to read dark-mode settings from options.txt", e);
            }
        }
        this.theme = loadTheme(darkMode);
        this.maximized = parsed.has(maximizedopt) || FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_MAXIMIZED);

        var forgeVersion = parsed.valueOf(forgeversionopt);
        StartupNotificationManager.modLoaderConsumer().ifPresent(c -> c.accept("NeoForge loading " + forgeVersion));

        this.renderScheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().group(BACKGROUND_THREAD_GROUP)
                        .name("fml-loadingscreen")
                        .daemon()
                        .uncaughtExceptionHandler((t, e) -> {
                            System.err.println("Uncaught error on background rendering thread: " + e);
                            e.printStackTrace();
                        })
                        .factory());

        var mcVersion = parsed.valueOf(mcversionopt);
        initWindow(mcVersion);

        this.rendererFuture = renderScheduler.schedule(() -> new LoadingScreenRenderer(renderScheduler, window, theme, mcVersion, forgeVersion), 1, TimeUnit.MILLISECONDS);

        updateProgress("Initializing Game Graphics");
        StartupNotificationManager.addModMessage("BLAHFASEL");
        var pp = StartupNotificationManager.prependProgressBar("Minecraft Progress", 1000);
        pp.setAbsolute(250);

        return this::periodicTick;
    }

    private static Theme loadTheme(boolean darkMode) {
        Path themePath = getThemePath();
        var themeId = darkMode ? ThemeIds.DARK_MODE : ThemeIds.DEFAULT;

        // Specials
        var today = LocalDate.now();
        if (today.getMonth() == Month.APRIL && today.getDayOfMonth() == 1) {
            themeId = ThemeIds.APRIL_FOOLS;
        }

        Theme theme;
        try {
            theme = ThemeLoader.load(themePath, themeId);
        } catch (Exception e) {
            LOGGER.error("Failed to load theme {} from {}", themeId, themePath, e);
            theme = Theme.createDefaultTheme();
        }
        return theme;
    }

    private static Path getThemePath() {
        return FMLPaths.CONFIGDIR.get().resolve("fml");
    }

    // Called from NeoForge
    public void renderToFramebuffer() {
        if (rendererFuture.isDone()) {
            rendererFuture.resultNow().renderToFramebuffer();
        }
    }

    private static final String ERROR_URL = "https://links.neoforged.net/early-display-errors";

    private final ReentrantLock crashLock = new ReentrantLock();

    private void crashElegantly(String errorDetails) {
        crashLock.lock(); // Crash at most once!

        StringBuilder msgBuilder = new StringBuilder(2000);
        msgBuilder.append("Failed to initialize the mod loading system and display.\n");
        msgBuilder.append("\n\n");
        msgBuilder.append("Failure details:\n");
        msgBuilder.append(errorDetails);
        msgBuilder.append("\n\n");
        msgBuilder.append("If you click yes, we will try and open " + ERROR_URL + " in your default browser");
        LOGGER.error("ERROR DISPLAY\n{}", msgBuilder);
        // we show the display on a new dedicated thread
        var thread = new Thread(() -> {
            var res = TinyFileDialogs.tinyfd_messageBox("Minecraft: NeoForge", msgBuilder.toString(), "yesno", "error", false);
            if (res) {
                try {
                    Desktop.getDesktop().browse(URI.create(ERROR_URL));
                } catch (IOException ioe) {
                    TinyFileDialogs.tinyfd_messageBox("Minecraft: NeoForge", "Sadly, we couldn't open your browser.\nVisit " + ERROR_URL, "ok", "error", false);
                }
            }
        }, "crash-report");
        thread.setDaemon(true);
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException ignored) {}

        System.exit(1);
    }

    /**
     * Called to initialize the window when preparing for the Render Thread.
     * <p>
     * The act of calling glfwInit here creates a concurrency issue; GL doesn't know whether we're gonna call any
     * GL functions from the secondary thread and the main thread at the same time.
     * <p>
     * It's then our job to make sure this doesn't happen, only calling GL functions where the Context is Current.
     * As long as we can verify that, then GL (and things like OS X) have no complaints with doing this.
     *
     * @param mcVersion Minecraft Version
     * @return The selected GL profile as an integer pair
     */
    public void initWindow(@Nullable String mcVersion) {
        // Initialize GLFW with a time guard, in case something goes wrong
        long glfwInitBegin = System.nanoTime();
        if (!glfwInit()) {
            crashElegantly("We are unable to initialize the graphics system.\nglfwInit failed.\n");
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        long glfwInitEnd = System.nanoTime();

        if (glfwInitEnd - glfwInitBegin > 1e9) {
            LOGGER.error("WARNING : glfwInit took {} seconds to start.", (glfwInitEnd - glfwInitBegin) / 1.0e9);
        }

        // Clear the Last Exception (#7285 - Prevent Vanilla throwing an IllegalStateException due to invalid controller mappings)
        getLastGlfwError().ifPresent(error -> LOGGER.error("Suppressing Last GLFW error: {}", error));

        // Set window hints for the new window we're gonna create.
        // Start of flags copied from Vanilla Minecraft
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_NATIVE_CONTEXT_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
        // End of flags copied from Vanilla Minecraft
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        if (mcVersion != null) {
            // this emulates what we would get without early progress window
            // as vanilla never sets these, so GLFW uses the first window title
            // set them explicitly to avoid it using "FML early loading progress" as the class
            String vanillaWindowTitle = "Minecraft* " + mcVersion;
            glfwWindowHintString(GLFW_X11_CLASS_NAME, vanillaWindowTitle);
            glfwWindowHintString(GLFW_X11_INSTANCE_NAME, vanillaWindowTitle);
        }
        if (FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.DEBUG_OPENGL)) {
            LOGGER.info("Requesting the creation of an OpenGL debug context");
            glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GL_TRUE);
        }

        long primaryMonitor = glfwGetPrimaryMonitor();
        if (primaryMonitor == 0) {
            LOGGER.error("Failed to find a primary monitor - this means LWJGL isn't working properly");
            crashElegantly("Failed to locate a primary monitor.\nglfwGetPrimaryMonitor failed.\n");
            throw new IllegalStateException("Can't find a primary monitor");
        }
        GLFWVidMode vidmode = glfwGetVideoMode(primaryMonitor);

        if (vidmode == null) {
            LOGGER.error("Failed to get the current display video mode.");
            crashElegantly("Failed to get current display resolution.\nglfwGetVideoMode failed.\n");
            throw new IllegalStateException("Can't get a resolution");
        }

        var successfulWindow = new AtomicBoolean(false);
        var windowFailFuture = renderScheduler.schedule(() -> {
            if (!successfulWindow.get()) crashElegantly("Timed out trying to setup the Game Window.");
        }, 30, TimeUnit.SECONDS);

        this.window = glfwCreateWindow(winWidth, winHeight, "Minecraft: NeoForge Loading...", 0L, 0L);
        var creationError = getLastGlfwError().orElse("unknown error");
        if (this.window == 0L) {
            LOGGER.error("Failed to create window: {}", creationError);

            crashElegantly("Failed to create a window:\n" + creationError);
            throw new IllegalStateException("Failed to create a window");
        }

        // Cancel the watchdog
        successfulWindow.set(true);
        if (!windowFailFuture.cancel(true)) throw new IllegalStateException("We died but didn't somehow?");

        int[] x = new int[1];
        int[] y = new int[1];
        glfwGetMonitorPos(primaryMonitor, x, y);
        int monitorX = x[0];
        int monitorY = y[0];
//        glfwSetWindowSizeLimits(window, 854, 480, GLFW_DONT_CARE, GLFW_DONT_CARE);
        if (this.maximized) {
            glfwMaximizeWindow(window);
        }

        glfwGetWindowSize(window, x, y);
        this.winWidth = x[0];
        this.winHeight = y[0];

        glfwSetWindowPos(window, (vidmode.width() - this.winWidth) / 2 + monitorX, (vidmode.height() - this.winHeight) / 2 + monitorY);

        // Attempt setting the icon
        try (var glfwImgBuffer = GLFWImage.malloc(1);
                var glfwImages = GLFWImage.malloc();
                var icon = theme.windowIcon().loadAsImage()) {
            glfwImgBuffer.put(glfwImages.set(icon.width(), icon.height(), icon.imageData()));
            glfwImgBuffer.flip();
            glfwSetWindowIcon(window, glfwImgBuffer);
        } catch (Exception e) {
            LOGGER.error("Failed to load NeoForged icon", e);
        }
        getLastGlfwError().ifPresent(error -> LOGGER.warn("Failed to set window icon: {}", error));

        glfwSetFramebufferSizeCallback(window, this::fbResize);
        glfwSetWindowPosCallback(window, this::winMove);
        glfwSetWindowSizeCallback(window, this::winResize);

        // Show the window
        glfwShowWindow(window);
        glfwGetWindowPos(window, x, y);
        getLastGlfwError().ifPresent(error -> LOGGER.warn("Failed to show and position window: {}", error));
        this.winX = x[0];
        this.winY = y[0];
        glfwGetFramebufferSize(window, x, y);
        this.fbWidth = x[0];
        this.fbHeight = y[0];
        glfwPollEvents();
    }

    private void winResize(long window, int width, int height) {
        if (window == this.window && width != 0 && height != 0) {
            this.winWidth = width;
            this.winHeight = height;
        }
    }

    private void fbResize(long window, int width, int height) {
        if (window == this.window && width != 0 && height != 0) {
            this.fbWidth = width;
            this.fbHeight = height;
        }
    }

    private void winMove(long window, int x, int y) {
        if (window == this.window) {
            this.winX = x;
            this.winY = y;
        }
    }

    private static Optional<String> getLastGlfwError() {
        try (MemoryStack memorystack = MemoryStack.stackPush()) {
            PointerBuffer pointerbuffer = memorystack.mallocPointer(1);
            int error = glfwGetError(pointerbuffer);
            if (error != GLFW_NO_ERROR) {
                long pDescription = pointerbuffer.get();
                String description = pDescription == 0L ? null : MemoryUtil.memUTF8(pDescription);
                if (description != null) {
                    return Optional.of(String.format(Locale.ROOT, "[0x%X] %s", error, description));
                } else {
                    return Optional.of(String.format(Locale.ROOT, "[0x%X]", error));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Hand-off the window to the vanilla game.
     * Called on the main thread instead of the game's initialization.
     *
     * @return the Window we own.
     */
    public long takeOverGlfwWindow() {
        // While this should have happened already, wait for it now to continue
        LoadingScreenRenderer renderer;
        try {
            renderer = this.rendererFuture.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            dumpBackgroundThreadStack();
            crashElegantly("We seem to be having trouble initializing the window, waited for 30 seconds");
            return -1L; // crashElegantly will never return
        }

        updateProgress("Initializing Game Graphics");

        // Stop the automatic off-thread rendering to move the GL context back to the main thread (this thread)
        try {
            renderer.stopAutomaticRendering();
        } catch (TimeoutException e) {
            dumpBackgroundThreadStack();
            crashElegantly("Cannot hand over rendering to Minecraft! The background loading screen renderer seems stuck.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        completeProgress();

        glfwMakeContextCurrent(window);
        // Set the title to what the game wants
        glfwSwapInterval(0);
        // Clean up our hooks
        glfwSetFramebufferSizeCallback(window, null).close();
        glfwSetWindowPosCallback(window, null).close();
        glfwSetWindowSizeCallback(window, null).close();
        this.repaintTick = renderer::renderToScreen; // the repaint will continue to be called until the overlay takes over
        return window;
    }

    @Override
    public void updateModuleReads(final ModuleLayer layer) {}

    // Called from Neo
    public int getFramebufferTextureId() {
        if (!rendererFuture.isDone()) {
            throw new IllegalStateException("Initialization of the renderer has not completed yet.");
        }
        return rendererFuture.resultNow().getFramebufferTextureId();
    }

    @Override
    public void periodicTick() {
        if (rendererFuture.state() == Future.State.FAILED) {
            throw new RuntimeException("Initialization of the loading screen failed.", rendererFuture.exceptionNow());
        }
        glfwPollEvents();
        repaintTick.run();
    }

    @Override
    public void updateProgress(String label) {
        mainProgress.label(label);
    }

    @Override
    public void completeProgress() {
        mainProgress.complete();
    }

    public void addMojangTexture(final int textureId) {}

    public void close() {
        // Close the Render Scheduler thread
        renderScheduler.shutdown();
        try {
            rendererFuture.get().close();
        } catch (ExecutionException e) {
            LOGGER.error("Cannot close renderer since it failed to initialize", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Re-interrupt and continue closing
        }
    }

    @Override
    public void crash(final String message) {
        crashElegantly(message);
    }

    private static void dumpBackgroundThreadStack() {
        BACKGROUND_THREAD_GROUP.list();
    }
}
