/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL32C.*;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import joptsimple.OptionParser;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
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
public class DisplayWindow implements ImmediateWindowProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("EARLYDISPLAY");
    private final AtomicBoolean animationTimerTrigger = new AtomicBoolean(true);
    private final ProgressMeter mainProgress;

    private ColourScheme colourScheme;
    private ElementShader elementShader;

    private RenderElement.DisplayContext context;
    private List<RenderElement> elements;
    private int framecount;
    private EarlyFramebuffer framebuffer;
    private ScheduledFuture<?> windowTick;
    private ScheduledFuture<?> initializationFuture;

    private PerformanceInfo performanceInfo;
    private ScheduledFuture<?> performanceTick;
    // The GL ID of the window. Used for all operations
    private long window;
    // The thread that contains and ticks the window while Forge is loading mods
    private ScheduledExecutorService renderScheduler;
    private int fbWidth;
    private int fbHeight;
    private int fbScale;
    private int winWidth;
    private int winHeight;
    private int winX;
    private int winY;

    private final Semaphore renderLock = new Semaphore(1);
    private boolean maximized;
    private SimpleFont font;
    private Runnable repaintTick = () -> {};

    public DisplayWindow() {
        mainProgress = StartupNotificationManager.addProgressBar("EARLY", 0);
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
        fbScale = FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_FBSCALE);
        if (System.getenv("FML_EARLY_WINDOW_DARK") != null) {
            this.colourScheme = ColourScheme.BLACK;
        } else {
            try {
                var optionLines = Files.readAllLines(FMLPaths.GAMEDIR.get().resolve(Paths.get("options.txt")));
                var options = optionLines.stream().map(l -> l.split(":")).filter(a -> a.length == 2).collect(Collectors.toMap(a -> a[0], a -> a[1]));
                var colourScheme = Boolean.parseBoolean(options.getOrDefault("darkMojangStudiosBackground", "false"));
                this.colourScheme = colourScheme ? ColourScheme.BLACK : ColourScheme.RED;
            } catch (IOException ioe) {
                // No options
                this.colourScheme = ColourScheme.RED; // default to red colourscheme
            }
        }
        this.maximized = parsed.has(maximizedopt) || FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_MAXIMIZED);

        var forgeVersion = parsed.valueOf(forgeversionopt);
        StartupNotificationManager.modLoaderConsumer().ifPresent(c -> c.accept("NeoForge loading " + forgeVersion));
        performanceInfo = new PerformanceInfo();
        return start(parsed.valueOf(mcversionopt), forgeVersion);
    }

    private static final long MINFRAMETIME = TimeUnit.MILLISECONDS.toNanos(10); // This is the FPS cap on the window - note animation is capped at 20FPS via the tickTimer
    private long nextFrameTime = 0;

    /**
     * The main render loop.
     * renderThread executes this.
     *
     * Performs initialization and then ticks the screen at 20 fps.
     * When the thread is killed, context is destroyed.
     */
    private void renderThreadFunc() {
        if (!renderLock.tryAcquire()) {
            return;
        }
        try {
            long nt;
            if ((nt = System.nanoTime()) < nextFrameTime) {
                return;
            }
            nextFrameTime = nt + MINFRAMETIME;
            glfwMakeContextCurrent(window);

            GlState.readFromOpenGL();
            var backup = GlState.createSnapshot();

            framebuffer.activate();
            GlState.viewport(0, 0, this.context.scaledWidth(), this.context.scaledHeight());
            this.context.elementShader().activate();
            this.context.elementShader().updateScreenSizeUniform(this.context.scaledWidth(), this.context.scaledHeight());
            GlState.clearColor(colourScheme.background().redf(), colourScheme.background().greenf(), colourScheme.background().bluef(), 1f);
            paintFramebuffer();
            this.context.elementShader().clear();
            framebuffer.deactivate();
            GlState.viewport(0, 0, fbWidth, fbHeight);
            framebuffer.draw(this.fbWidth, this.fbHeight);
            // Swap buffers; we're done
            glfwSwapBuffers(window);

            GlState.applySnapshot(backup);
        } catch (Throwable t) {
            LOGGER.error("BARF", t);
        } finally {
            if (this.windowTick != null) glfwMakeContextCurrent(0); // we release the gl context IF we're running off the main thread
            renderLock.release();
        }
    }

    /**
     * Render initialization methods called by the Render Thread.
     * It compiles the fragment and vertex shaders for rendering text with STB, and sets up basic render framework.
     *
     * Nothing fancy, we just want to draw and render text.
     */
    private void initRender(final @Nullable String mcVersion, final String forgeVersion) {
        // This thread owns the GL render context now. We should make a note of that.
        glfwMakeContextCurrent(window);
        // Wait for one frame to be complete before swapping; enable vsync in other words.
        glfwSwapInterval(1);
        var capabilities = createCapabilities();
        GlState.readFromOpenGL();
        GlDebug.setCapabilities(capabilities);
        LOGGER.info("GL info: {} GL version {}, {}", glGetString(GL_RENDERER), glGetString(GL_VERSION), glGetString(GL_VENDOR));

        elementShader = new ElementShader();
        try {
            elementShader.init();
        } catch (Throwable t) {
            LOGGER.error("Crash during shader initialization", t);
            crashElegantly("An error occurred initializing shaders.");
        }

        // Set the clear color based on the colour scheme
        GlState.clearColor(colourScheme.background().redf(), colourScheme.background().greenf(), colourScheme.background().bluef(), 1f);

        // we always render to an 854x480 texture and then fit that to the screen - with a scale factor
        this.context = new RenderElement.DisplayContext(854, 480, fbScale, elementShader, colourScheme, performanceInfo);
        framebuffer = new EarlyFramebuffer(this.context);
        try {
            this.font = new SimpleFont("Monocraft.ttf", fbScale, 200000);
        } catch (Throwable t) {
            LOGGER.error("Crash during font initialization", t);
            crashElegantly("An error occurred initializing a font for rendering. " + t.getMessage());
        }
        this.elements = new ArrayList<>(Arrays.asList(
                RenderElement.fox(font),
                RenderElement.logMessageOverlay(font),
                RenderElement.forgeVersionOverlay(font, mcVersion + "-" + forgeVersion.split("-")[0]),
                RenderElement.performanceBar(font),
                RenderElement.progressBars(font)));

        var date = Calendar.getInstance();
        if (FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_SQUIR) || (date.get(Calendar.MONTH) == Calendar.APRIL && date.get(Calendar.DAY_OF_MONTH) == 1))
            this.elements.add(0, RenderElement.squir());

        GlState.enableBlend(true);
        GlState.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glfwMakeContextCurrent(0);
        this.windowTick = renderScheduler.scheduleAtFixedRate(this::renderThreadFunc, 50, 50, TimeUnit.MILLISECONDS);
        this.performanceTick = renderScheduler.scheduleAtFixedRate(performanceInfo::update, 0, 500, TimeUnit.MILLISECONDS);
        // schedule a 50 ms ticker to try and smooth out the rendering
        renderScheduler.scheduleAtFixedRate(() -> animationTimerTrigger.set(true), 1, 50, TimeUnit.MILLISECONDS);
    }

    /**
     * Called every frame by the Render Thread to draw to the screen.
     */
    void paintFramebuffer() {
        // Clear the screen to our color
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GlState.enableBlend(true);
        GlState.blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        this.elements.removeIf(element -> !element.render(context, framecount));
        if (animationTimerTrigger.compareAndSet(true, false)) // we only increment the framecount on a periodic basis
            framecount++;
    }

    // Called from NeoForge
    public void renderToFramebuffer() {
        GlDebug.pushGroup("update EarlyDisplay framebuffer");
        GlState.readFromOpenGL();
        var backup = GlState.createSnapshot();

        GlState.viewport(0, 0, this.context.scaledWidth(), this.context.scaledHeight());
        framebuffer.activate();
        GlState.clearColor(colourScheme.background().redf(), colourScheme.background().greenf(), colourScheme.background().bluef(), 1f);
        elementShader.activate();
        elementShader.updateScreenSizeUniform(this.context.scaledWidth(), this.context.scaledHeight());
        paintFramebuffer();
        elementShader.clear();
        framebuffer.deactivate();

        GlState.applySnapshot(backup);
        GlDebug.popGroup();
    }

    /**
     * Start the window and Render Thread; we're ready to go.
     */
    public Runnable start(@Nullable String mcVersion, final String forgeVersion) {
        renderScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final var thread = Executors.defaultThreadFactory().newThread(r);
            thread.setDaemon(true);
            return thread;
        });
        initWindow(mcVersion);
        this.initializationFuture = renderScheduler.schedule(() -> initRender(mcVersion, forgeVersion), 1, TimeUnit.MILLISECONDS);
        return this::periodicTick;
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
     *
     * The act of calling glfwInit here creates a concurrency issue; GL doesn't know whether we're gonna call any
     * GL functions from the secondary thread and the main thread at the same time.
     *
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
        int[] channels = new int[1];
        try (var glfwImgBuffer = GLFWImage.malloc(1)) {
            final ByteBuffer imgBuffer;
            try (GLFWImage glfwImages = GLFWImage.malloc()) {
                imgBuffer = STBHelper.loadImageFromClasspath("neoforged_icon.png", 20000, x, y, channels);
                glfwImgBuffer.put(glfwImages.set(x[0], y[0], imgBuffer));
                glfwImgBuffer.flip();
                glfwSetWindowIcon(window, glfwImgBuffer);
                STBImage.stbi_image_free(imgBuffer);
            }
        } catch (NullPointerException e) {
            LOGGER.error("Failed to load NeoForged icon");
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
        // wait for the window to actually be initialized
        try {
            this.initializationFuture.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            Thread.dumpStack();
            crashElegantly("We seem to be having trouble initializing the window, waited for 30 seconds");
        }
        // we have to spin wait for the window ticker
        updateProgress("Initializing Game Graphics");
        while (!this.windowTick.isDone()) {
            this.windowTick.cancel(false);
        }
        try {
            if (!renderLock.tryAcquire(5, TimeUnit.SECONDS)) {
                crashElegantly("We seem to be having trouble handing off the window, tried for 5 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // we don't want the lock, just making sure it's back on the main thread
        renderLock.release();

        glfwMakeContextCurrent(window);
        // Set the title to what the game wants
        glfwSwapInterval(0);
        // Clean up our hooks
        glfwSetFramebufferSizeCallback(window, null).free();
        glfwSetWindowPosCallback(window, null).free();
        glfwSetWindowSizeCallback(window, null).free();
        this.repaintTick = this::renderThreadFunc; // the repaint will continue to be called until the overlay takes over
        this.windowTick = null; // this tells the render thread that the async ticker is done
        return window;
    }

    @Override
    public void updateModuleReads(final ModuleLayer layer) {}

    public int getFramebufferTextureId() {
        return framebuffer.getTexture();
    }

    public RenderElement.DisplayContext context() {
        return this.context;
    }

    @Override
    public void periodicTick() {
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

    public void addMojangTexture(final int textureId) {
        this.elements.add(0, RenderElement.mojang(textureId, framecount));
//        this.elements.get(0).retire(framecount + 1);
    }

    public void close() {
        // Close the Render Scheduler thread
        renderScheduler.shutdown();
        this.framebuffer.close();
        this.context.elementShader().close();
        SimpleBufferBuilder.destroy();
    }

    @Override
    public void crash(final String message) {
        crashElegantly(message);
    }
}
