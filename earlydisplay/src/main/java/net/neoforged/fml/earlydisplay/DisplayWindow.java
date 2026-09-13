/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import joptsimple.OptionParser;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.earlydisplay.error.ErrorDisplay;
import net.neoforged.fml.earlydisplay.render.LoadingScreenRenderer;
import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import net.neoforged.fml.earlydisplay.render.backend.opengl.GlRenderer;
import net.neoforged.fml.earlydisplay.theme.Theme;
import net.neoforged.fml.earlydisplay.theme.ThemeIds;
import net.neoforged.fml.earlydisplay.theme.ThemeLoader;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.ProgramArgs;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLMessageBox;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_DisplayMode;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_MessageBoxButtonData;
import org.lwjgl.sdl.SDL_MessageBoxData;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.sdl.SDL_Surface;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import org.lwjgl.system.linux.DynamicLinkLoader;
import org.lwjgl.system.windows.WinBase;
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
    private boolean borderless;
    private Theme theme;

    private Future<LoadingScreenRenderer> rendererFuture;

    // The GL ID of the window. Used for all operations
    private long window;
    // The thread that contains and ticks the window while Forge is loading mods
    private ScheduledExecutorService renderScheduler;
    private int winWidth;
    private int winHeight;
    private boolean iconified;
    @Nullable
    private String assetsDir;
    @Nullable
    private String assetIndex;

    private boolean maximized;
    private Runnable repaintTick = () -> {};
    private volatile boolean closed;
    private String neoForgeVersion;
    private String minecraftVersion;

    public DisplayWindow() {
        mainProgress = StartupNotificationManager.addProgressBar("", 0);
    }

    @Override
    public String name() {
        return "fmlearlywindow";
    }

    @Override
    public boolean isSupportedEnvironment() {
        try (MemoryStack _ = MemoryStack.stackPush()) {
            long handle = switch (Platform.get()) {
                case FREEBSD, MACOSX -> 0L; // RenderDoc does not support MacOS and FreeBSD
                case LINUX -> {
                    long linuxHandle = DynamicLinkLoader.dlopen("librenderdoc.so", DynamicLinkLoader.RTLD_NOW | DynamicLinkLoader.RTLD_NOLOAD);
                    if (linuxHandle != 0L) {
                        DynamicLinkLoader.dlclose(linuxHandle);
                    }
                    yield linuxHandle;
                }
                case WINDOWS -> WinBase.GetModuleHandle(null, "renderdoc.dll");
            };
            if (handle != 0L && !Boolean.getBoolean("fml.earlyWindowIgnoreRenderDoc")) {
                LOGGER.warn("Detected RenderDoc, disabling ELS to avoid potential segfault with multiple OpenGL contexts");
                return false;
            }
            return true;
        }
    }

    @Override
    public void initialize(ProgramArgs arguments) {
        OptionParser parser = new OptionParser();
        var widthopt = parser.accepts("width")
                .withRequiredArg().ofType(Integer.class)
                .defaultsTo(FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH));
        var heightopt = parser.accepts("height")
                .withRequiredArg().ofType(Integer.class)
                .defaultsTo(FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT));
        var maximizedopt = parser.accepts("earlywindow.maximized");
        var assetsDirOpt = parser.accepts("assetsDir").withRequiredArg().ofType(String.class);
        var assetIndexOpt = parser.accepts("assetIndex").withRequiredArg().ofType(String.class);
        parser.allowsUnrecognizedOptions();
        var parsed = parser.parse(arguments.getArguments());
        winWidth = parsed.valueOf(widthopt);
        winHeight = parsed.valueOf(heightopt);
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_WIDTH, winWidth);
        FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_HEIGHT, winHeight);

        if (parsed.has(assetsDirOpt) && parsed.has(assetIndexOpt)) {
            assetsDir = parsed.valueOf(assetsDirOpt);
            assetIndex = parsed.valueOf(assetIndexOpt);
        }

        boolean[] darkMode = new boolean[] { false };
        boolean[] borderless = new boolean[] { true };
        try (var lines = Files.lines(FMLPaths.GAMEDIR.get().resolve(Paths.get("options.txt")))) {
            lines.forEach(line -> {
                if (line.startsWith("darkMojangStudiosBackground:")) {
                    darkMode[0] = line.toLowerCase(Locale.ROOT).endsWith("true");
                } else if (line.startsWith("exclusiveFullscreen:")) {
                    borderless[0] = line.toLowerCase(Locale.ROOT).endsWith("false");
                }
            });
        } catch (NoSuchFileException ignored) {
            // No options
        } catch (IOException e) {
            LOGGER.warn("Failed to read options.txt", e);
        }
        this.darkMode = Boolean.getBoolean("fml.earlyWindowDarkMode") || darkMode[0];
        this.borderless = borderless[0];

        var forcedTheme = FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_LOADING_SCREEN_THEME);
        if (!forcedTheme.isEmpty()) {
            LOGGER.info("Trying to load configured early loading screen theme '{}'", forcedTheme);
            this.theme = loadTheme(forcedTheme);
        } else {
            this.theme = loadTheme(this.darkMode);
        }
        this.maximized = parsed.has(maximizedopt) || FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_MAXIMIZED);

        this.renderScheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().group(BACKGROUND_THREAD_GROUP)
                        .name("fml-loadingscreen")
                        .daemon()
                        .uncaughtExceptionHandler((t, e) -> {
                            System.err.println("Uncaught error on background rendering thread: " + e);
                            e.printStackTrace();
                        })
                        .factory());

        initWindow();

        this.rendererFuture = this.renderScheduler.schedule(() -> this.setupRenderer(() -> GlRenderer.setupBackend(this.window), true), 1, TimeUnit.MILLISECONDS);

        updateProgress("Initializing Game Graphics");
    }

    private LoadingScreenRenderer setupRenderer(Supplier<ELSRenderBackend> backend, boolean setupAutoRender) {
        return new LoadingScreenRenderer(
                this.renderScheduler,
                backend,
                this.theme,
                getThemePath(),
                () -> this.minecraftVersion,
                () -> this.neoForgeVersion,
                setupAutoRender);
    }

    @Override
    public void setMinecraftVersion(String version) {
        minecraftVersion = version;
    }

    @Override
    public void setNeoForgeVersion(String version) {
        if (!Objects.equals(neoForgeVersion, version)) {
            neoForgeVersion = version;
            StartupNotificationManager.modLoaderConsumer().ifPresent(c -> c.accept("Starting NeoForge " + version));
        }
    }

    private static Theme loadTheme(boolean darkMode) {
        return loadTheme(getThemeId(darkMode));
    }

    private static Theme loadTheme(String themeId) {
        var themePath = getThemePath();

        Theme theme;
        try {
            theme = ThemeLoader.load(themePath, themeId);
        } catch (Exception e) {
            LOGGER.error("Failed to load theme {} from {}", themeId, themePath, e);
            theme = Theme.createDefaultTheme();
        }
        return theme;
    }

    private static String getThemeId(boolean darkMode) {
        var themeId = darkMode ? ThemeIds.DARK_MODE : ThemeIds.DEFAULT;

        // Specials
        var today = LocalDate.now();
        if (today.getMonth() == Month.APRIL && today.getDayOfMonth() == 1) {
            themeId = darkMode ? ThemeIds.APRIL_FOOLS_DARK_MODE : ThemeIds.APRIL_FOOLS;
        }
        return themeId;
    }

    private static Path getThemePath() {
        return FMLPaths.CONFIGDIR.get().resolve("fml");
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
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer result = stack.callocInt(1);
                SDL_MessageBoxButtonData.Buffer buttons = SDL_MessageBoxButtonData.calloc(2, stack);
                buttons.get(0).buttonID(SDLMessageBox.SDL_MESSAGEBOX_BUTTON_RETURNKEY_DEFAULT).text(stack.UTF8("Yes"));
                buttons.get(1).buttonID(SDLMessageBox.SDL_MESSAGEBOX_BUTTON_ESCAPEKEY_DEFAULT).text(stack.UTF8("No"));
                SDL_MessageBoxData msgBox = SDL_MessageBoxData.calloc(stack)
                        .flags(SDLMessageBox.SDL_MESSAGEBOX_ERROR)
                        .title(stack.UTF8("Minecraft: NeoForge", true))
                        .message(stack.UTF8(msgBuilder.toString(), true))
                        .buttons(buttons);
                if (!SDLMessageBox.SDL_ShowMessageBox(msgBox, result)) {
                    LOGGER.error("Failed to show error message: {}", SDLError.SDL_GetError());
                } else if (result.get(0) == 1) {
                    try {
                        Desktop.getDesktop().browse(URI.create(ERROR_URL));
                    } catch (IOException ioe) {
                        String message = "Sadly, we couldn't open your browser.\nVisit " + ERROR_URL;
                        if (!SDLMessageBox.SDL_ShowSimpleMessageBox(SDLMessageBox.SDL_MESSAGEBOX_ERROR, "Minecraft: NeoForge", message, 0L)) {
                            LOGGER.error("Failed to show error message: {}", SDLError.SDL_GetError());
                        }
                    }
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
     */
    public void initWindow() {
        // Initialize SDL with a time guard, in case something goes wrong
        long sdlInitBegin = System.nanoTime();
        SDLInit.SDL_SetAppMetadataProperty(SDLInit.SDL_PROP_APP_METADATA_NAME_STRING, "Minecraft");
        SDLInit.SDL_SetAppMetadataProperty(SDLInit.SDL_PROP_APP_METADATA_IDENTIFIER_STRING, "com.mojang.minecraft");
        SDLInit.SDL_SetAppMetadataProperty(SDLInit.SDL_PROP_APP_METADATA_CREATOR_STRING, "Mojang Studios");
        SDLInit.SDL_SetAppMetadataProperty(SDLInit.SDL_PROP_APP_METADATA_COPYRIGHT_STRING, "Copyright Mojang AB.");
        SDLInit.SDL_SetAppMetadataProperty(SDLInit.SDL_PROP_APP_METADATA_URL_STRING, "https://www.minecraft.net");
        SDLInit.SDL_SetAppMetadataProperty(SDLInit.SDL_PROP_APP_METADATA_TYPE_STRING, "game");
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_NO_SIGNAL_HANDLERS, "1");
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_VIDEO_MINIMIZE_ON_FOCUS_LOSS, "0");
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_QUIT_ON_LAST_WINDOW_CLOSE, "0");
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_MOUSE_FOCUS_CLICKTHROUGH, "1");
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_ENABLE_SCREEN_KEYBOARD, "0");
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_IME_IMPLEMENTED_UI, "composition, candidates");
        if (!SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO)) {
            String error = SDLError.SDL_GetError();
            crashElegantly("We are unable to initialize the graphics system.\nSDL_Init failed.\n");
            throw new IllegalStateException("Unable to initialize SDL: " + error);
        }
        long sdlInitEnd = System.nanoTime();

        if (sdlInitEnd - sdlInitBegin > 1e9) {
            LOGGER.error("WARNING : SDL_Init took {} seconds to start.", (sdlInitEnd - sdlInitBegin) / 1.0e9);
        }

        // Set window hints for the new window we're gonna create.
        GlRenderer.configureWindowHints();
        int primaryMonitor = SDLVideo.SDL_GetPrimaryDisplay();
        if (primaryMonitor == 0) {
            LOGGER.error("Failed to find a primary monitor - this means LWJGL isn't working properly");
            crashElegantly("Failed to locate a primary monitor.\nSDL_GetPrimaryDisplay failed.\n");
            throw new IllegalStateException("Can't find a primary monitor");
        }

        SDL_DisplayMode displayMode = SDLVideo.SDL_GetDesktopDisplayMode(primaryMonitor);
        if (displayMode == null) {
            LOGGER.error("Failed to get the current display video mode.");
            crashElegantly("Failed to get current display resolution.\nSDL_GetCurrentDisplayMode failed.\n");
            throw new IllegalStateException("Can't get a resolution");
        }

        var successfulWindow = new AtomicBoolean(false);
        var windowFailFuture = renderScheduler.schedule(() -> {
            if (!successfulWindow.get()) crashElegantly("Timed out trying to setup the Game Window.");
        }, 30, TimeUnit.SECONDS);

        long flags = SDLVideo.SDL_WINDOW_OPENGL | SDLVideo.SDL_WINDOW_HIDDEN | SDLVideo.SDL_WINDOW_RESIZABLE | SDLVideo.SDL_WINDOW_HIGH_PIXEL_DENSITY;
        this.window = SDLVideo.SDL_CreateWindow("Minecraft: NeoForge Loading...", winWidth, winHeight, flags);
        if (this.window == 0L) {
            String creationError = SDLError.SDL_GetError();
            LOGGER.error("Failed to create window: {}", creationError);

            crashElegantly("Failed to create a window:\n" + creationError);
            throw new IllegalStateException("Failed to create a window");
        }
        SDLVideo.SDL_SetWindowMinimumSize(this.window, 320, 240);

        // Cancel the watchdog
        successfulWindow.set(true);
        if (!windowFailFuture.cancel(true)) throw new IllegalStateException("We died but didn't somehow?");

        int monitorX;
        int monitorY;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SDL_Rect bounds = SDL_Rect.malloc(stack);
            if (!SDLVideo.SDL_GetDisplayBounds(primaryMonitor, bounds)) {
                LOGGER.warn("Failed to query monitor bounds: {}", SDLError.SDL_GetError());
                monitorX = 0;
                monitorY = 0;
            } else {
                monitorX = bounds.x();
                monitorY = bounds.y();
            }
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            SDLVideo.SDL_GetWindowSize(this.window, w, h);
            this.winWidth = w.get(0);
            this.winHeight = h.get(0);
        }

        if (!SDLVideo.SDL_SetWindowPosition(this.window, monitorX + (displayMode.w() - this.winWidth) / 2, monitorY + (displayMode.h() - this.winHeight) / 2)) {
            LOGGER.warn("Failed to set window position: {}", SDLError.SDL_GetError());
        }

        setWindowIcon();

        // Show the window
        if (!SDLVideo.SDL_ShowWindow(this.window)) {
            LOGGER.warn("Failed to show window: {}", SDLError.SDL_GetError());
        }
        if (this.maximized) {
            SDLVideo.SDL_MaximizeWindow(this.window);
        }
        pollEvents();
    }

    private void setWindowIcon() {
        try (var icon = theme.windowIcon().loadAsImage(getThemePath())) {
            SDL_Surface surface = SDLSurface.SDL_CreateSurfaceFrom(icon.width(), icon.height(), 376840196, icon.imageData(), icon.width() * 4);
            if (surface == null) {
                LOGGER.warn("Failed to create SDL surface for {}x{} icon: {}", icon.width(), icon.height(), SDLError.SDL_GetError());
                return;
            }

            if (!SDLVideo.SDL_SetWindowIcon(this.window, surface)) {
                LOGGER.warn("Failed to set window icon: {}", SDLError.SDL_GetError());
            }
            SDLSurface.SDL_DestroySurface(surface);
        }
    }

    private void winResize(long window, int width, int height) {
        if (window == this.window && width != 0 && height != 0) {
            this.winWidth = width;
            this.winHeight = height;
        }
    }

    private void winIconify(long window, boolean iconified) {
        if (window == this.window) {
            this.iconified = iconified;
        }
    }

    @VisibleForTesting
    public long getWindowHandle() {
        return this.window;
    }

    @Override
    public WindowState handOverToMinecraft(Supplier<Object> backend) {
        return handOverToMinecraft(backend, true);
    }

    @VisibleForTesting
    public WindowState handOverToMinecraft(Supplier<Object> backend, boolean destroyWindow) {
        this.shutdownAutomaticRenderer(true);

        int windowX = 0;
        int windowY = 0;
        boolean posValid = false;
        String driver = SDLVideo.SDL_GetCurrentVideoDriver();
        if (driver != null && !driver.equals("wayland")) { // TODO: make sure this actually works (if it's even necessary)
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer x = stack.mallocInt(1);
                IntBuffer y = stack.mallocInt(1);
                SDLVideo.SDL_GetWindowPosition(this.window, x, y);
                windowX = x.get(0);
                windowY = y.get(0);
            }
            posValid = true;
        }

        boolean maximized = (SDLVideo.SDL_GetWindowFlags(this.window) & SDLVideo.SDL_WINDOW_MAXIMIZED) != 0;
        boolean fullscreen = (SDLVideo.SDL_GetWindowFlags(this.window) & SDLVideo.SDL_WINDOW_FULLSCREEN) != 0;
        WindowState windowState = new WindowState(windowX, windowY, winWidth, winHeight, posValid, this.iconified, maximized, fullscreen);

        if (destroyWindow) {
            SDLVideo.SDL_DestroyWindow(this.window);
            SDLInit.SDL_QuitSubSystem(SDLInit.SDL_INIT_VIDEO);
        }

        // Perform renderer re-init on the calling thread because the incoming B3D backend cannot move the context to another thread
        this.rendererFuture = CompletableFuture.completedFuture(this.setupRenderer(() -> (ELSRenderBackend) backend.get(), false));
        try {
            this.repaintTick = this.rendererFuture.get(30, TimeUnit.SECONDS)::renderToScreen;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException e) {
            dumpBackgroundThreadStack();
            crashElegantly("Cannot hand over rendering to Minecraft! The background loading screen renderer seems stuck.");
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        return windowState;
    }

    private ELSRenderBackend shutdownAutomaticRenderer(boolean destroyBackend) {
        // While this should have happened already, wait for it now to continue
        LoadingScreenRenderer renderer;
        try {
            renderer = this.rendererFuture.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            dumpBackgroundThreadStack();
            crashElegantly("We seem to be having trouble initializing the window, waited for 30 seconds");
            throw new AssertionError(); // crashElegantly will never return
        }

        updateProgress("Initializing Game Graphics");

        // Stop the automatic off-thread rendering to move the GL context back to the main thread (this thread)
        try {
            renderer.stopAutomaticRendering();
            renderer.close(destroyBackend);
        } catch (TimeoutException e) {
            dumpBackgroundThreadStack();
            crashElegantly("Cannot hand over rendering to Minecraft! The background loading screen renderer seems stuck.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        completeProgress();

        return renderer.getBackend();
    }

    private void pollEvents() {
        try (SDL_Event event = SDL_Event.malloc()) {
            while (SDLEvents.SDL_PollEvent(event)) {
                long window = SDLEvents.SDL_GetWindowFromEvent(event);
                switch (event.type()) {
                    case SDLEvents.SDL_EVENT_WINDOW_RESIZED -> winResize(window, event.window().data1(), event.window().data2());
                    case SDLEvents.SDL_EVENT_WINDOW_MINIMIZED -> winIconify(window, true);
                    case SDLEvents.SDL_EVENT_WINDOW_RESTORED, SDLEvents.SDL_EVENT_WINDOW_MAXIMIZED -> winIconify(window, false);
                    case SDLEvents.SDL_EVENT_WINDOW_CLOSE_REQUESTED -> close(); // TODO: check how this interacts with termination of mod loading
                }
            }
        }
    }

    @Override
    public void periodicTick() {
        Future.State rendererState = rendererFuture.state();
        if (rendererState == Future.State.FAILED) {
            throw new RuntimeException("Initialization of the loading screen failed.", rendererFuture.exceptionNow());
        }
        if (rendererState == Future.State.SUCCESS) {
            rendererFuture.resultNow().runWithBackgroundRenderingPaused(this::pollEvents);
        }
        // An event callback could have closed this display, in that case, we do not want to render again
        if (!closed) {
            repaintTick.run();
        }
    }

    @Override
    public void updateProgress(String label) {
        mainProgress.label(label);
    }

    @Override
    public void completeProgress() {
        mainProgress.complete();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            // Close the Render Scheduler thread
            renderScheduler.shutdown();
            try {
                rendererFuture.get().close(true);
            } catch (ExecutionException e) {
                LOGGER.error("Cannot close renderer since it failed to initialize", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Re-interrupt and continue closing
            }
        }
    }

    @Override
    public void crash(String message) {
        crashElegantly(message);
    }

    @Override
    public void displayFatalErrorAndExit(List<ModLoadingIssue> issues, @Nullable Path modsFolder, @Nullable Path logFile, @Nullable Path crashReportFile) {
        ELSRenderBackend backend = this.shutdownAutomaticRenderer(false);
        backend.acquireContextOwnership(true);
        this.close();
        ErrorDisplay.fatal(backend, assetsDir, assetIndex, issues, modsFolder, logFile, crashReportFile);
    }

    @VisibleForTesting
    public boolean isClosed() {
        return closed;
    }

    private static void dumpBackgroundThreadStack() {
        BACKGROUND_THREAD_GROUP.list();
    }
}
