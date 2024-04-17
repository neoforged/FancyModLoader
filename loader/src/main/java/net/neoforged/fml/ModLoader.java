/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import static net.neoforged.fml.Logging.CORE;
import static net.neoforged.fml.Logging.LOADING;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.event.lifecycle.ParallelDispatchEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.AbstractModProvider;
import net.neoforged.fml.loading.moddiscovery.InvalidModIdentifier;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageProvider;
import net.neoforged.neoforgespi.locating.ForgeFeature;
import net.neoforged.neoforgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * Contains the logic to load mods, i.e. turn the {@link LoadingModList} into the {@link ModList},
 * as well as initialization tasks for mods and methods to dispatch mod bus events.
 *
 * <p>For the mod initialization flow, see {@code CommonModLoader} in NeoForge.
 *
 * <p>For mod bus event dispatch, see {@link #postEvent(Event)} and related methods.
 */
public class ModLoader {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final List<ModLoadingException> loadingExceptions = new ArrayList<>();
    private static final List<ModLoadingWarning> loadingWarnings = new ArrayList<>();
    private static boolean loadingStateValid;
    private static ModList modList;

    static {
        CrashReportCallables.registerCrashCallable("ModLauncher", FMLLoader::getLauncherInfo);
        CrashReportCallables.registerCrashCallable("ModLauncher launch target", FMLLoader::launcherHandlerName);
        CrashReportCallables.registerCrashCallable("ModLauncher services", ModLoader::computeModLauncherServiceList);
        CrashReportCallables.registerCrashCallable("FML Language Providers", ModLoader::computeLanguageList);
    }

    private static void collectLoadingErrors() {
        FMLLoader.getLoadingModList().getErrors().stream()
                .flatMap(ModLoadingException::fromEarlyException)
                .forEach(loadingExceptions::add);
        FMLLoader.getLoadingModList().getBrokenFiles().stream()
                .map(file -> new ModLoadingWarning(null, InvalidModIdentifier.identifyJarProblem(file.getFilePath()).orElse("fml.modloading.brokenfile"), file.getFileName()))
                .forEach(loadingWarnings::add);

        FMLLoader.getLoadingModList().getWarnings().stream()
                .flatMap(ModLoadingWarning::fromEarlyException)
                .forEach(loadingWarnings::add);

        FMLLoader.getLoadingModList().getModFiles().stream()
                .filter(ModFileInfo::missingLicense)
                .filter(modFileInfo -> modFileInfo.getMods().stream().noneMatch(thisModInfo -> loadingExceptions.stream().map(ModLoadingException::getModInfo).anyMatch(otherInfo -> otherInfo == thisModInfo))) //Ignore files where any other mod already encountered an error
                .map(modFileInfo -> new ModLoadingException(null, "fml.modloading.missinglicense", null, modFileInfo.getFile()))
                .forEach(loadingExceptions::add);
    }

    private static String computeLanguageList() {
        return "\n" + FMLLoader.getLanguageLoadingProvider().applyForEach(lp -> lp.name() + "@" + lp.getClass().getPackage().getImplementationVersion()).collect(Collectors.joining("\n\t\t", "\t\t", ""));
    }

    private static String computeModLauncherServiceList() {
        final List<Map<String, String>> mods = FMLLoader.modLauncherModList();
        return "\n" + mods.stream().map(mod -> mod.getOrDefault("file", "nofile") +
                " " + mod.getOrDefault("name", "missing") +
                " " + mod.getOrDefault("type", "NOTYPE") +
                " " + mod.getOrDefault("description", "")).collect(Collectors.joining("\n\t\t", "\t\t", ""));
    }

    /**
     * Run on the primary starting thread by ClientModLoader and ServerModLoader
     * 
     * @param syncExecutor     An executor to run tasks on the main thread
     * @param parallelExecutor An executor to run tasks on a parallel loading thread pool
     * @param periodicTask     Optional periodic task to perform on the main thread while other activities run
     */
    public static void gatherAndInitializeMods(final Executor syncExecutor, final Executor parallelExecutor, final Runnable periodicTask) {
        collectLoadingErrors();

        ForgeFeature.registerFeature("javaVersion", ForgeFeature.VersionFeatureTest.forVersionString(IModInfo.DependencySide.BOTH, System.getProperty("java.version")));
        ForgeFeature.registerFeature("openGLVersion", ForgeFeature.VersionFeatureTest.forVersionString(IModInfo.DependencySide.CLIENT, ImmediateWindowHandler.getGLVersion()));
        loadingStateValid = true;
        FMLLoader.backgroundScanHandler.waitForScanToComplete(periodicTask);
        LoadingModList loadingModList = FMLLoader.getLoadingModList();
        final ModList modList = ModList.of(loadingModList.getModFiles().stream().map(ModFileInfo::getFile).toList(),
                loadingModList.getMods());
        if (!loadingExceptions.isEmpty()) {
            LOGGER.fatal(CORE, "Error during pre-loading phase", loadingExceptions.get(0));
            StartupNotificationManager.modLoaderMessage("ERROR DURING MOD LOADING");
            modList.setLoadedMods(Collections.emptyList());
            loadingStateValid = false;
            throw new LoadingFailedException(loadingExceptions);
        }
        List<? extends ForgeFeature.Bound> failedBounds = loadingModList.getMods().stream()
                .map(ModInfo::getForgeFeatures)
                .flatMap(Collection::stream)
                .filter(bound -> !ForgeFeature.testFeature(FMLEnvironment.dist, bound))
                .toList();

        if (!failedBounds.isEmpty()) {
            LOGGER.fatal(CORE, "Failed to validate feature bounds for mods: {}", failedBounds);
            StartupNotificationManager.modLoaderMessage("ERROR DURING MOD LOADING");
            modList.setLoadedMods(Collections.emptyList());
            loadingStateValid = false;
            throw new LoadingFailedException(failedBounds.stream()
                    .map(fb -> new ModLoadingException(fb.modInfo(), "fml.modloading.feature.missing", null, fb, ForgeFeature.featureValue(fb)))
                    .toList());
        }

        final List<ModContainer> modContainers = loadingModList.getModFiles().stream()
                .map(ModFileInfo::getFile)
                .map(ModLoader::buildMods)
                .<ModContainer>mapMulti(Iterable::forEach)
                .toList();
        if (!loadingExceptions.isEmpty()) {
            LOGGER.fatal(CORE, "Failed to initialize mod containers", loadingExceptions.get(0));
            StartupNotificationManager.modLoaderMessage("ERROR DURING MOD LOADING");
            modList.setLoadedMods(Collections.emptyList());
            loadingStateValid = false;
            throw new LoadingFailedException(loadingExceptions);
        }
        modList.setLoadedMods(modContainers);
        ModLoader.modList = modList;

        constructMods(syncExecutor, parallelExecutor, periodicTask);
    }

    private static void constructMods(Executor syncExecutor, Executor parallelExecutor, Runnable periodicTask) {
        var workQueue = new DeferredWorkQueue("Mod Construction");
        dispatchParallelTask("Mod Construction", parallelExecutor, periodicTask, modContainer -> {
            modContainer.constructMod();
            modContainer.acceptEvent(new FMLConstructModEvent(modContainer, workQueue));
        });
        waitForTask("Mod Construction: Deferred Queue", periodicTask, CompletableFuture.runAsync(workQueue::runTasks, syncExecutor));
    }

    /**
     * Runs a single task on the {@code syncExecutor}, while ticking the loading screen.
     */
    public static void runInitTask(String name, Executor syncExecutor, Runnable periodicTask, Runnable initTask) {
        waitForTask(name, periodicTask, CompletableFuture.runAsync(initTask, syncExecutor));
    }

    /**
     * Dispatches a parallel event across all mod containers, with progress displayed on the loading screen.
     */
    public static void dispatchParallelEvent(String name, Executor syncExecutor, Executor parallelExecutor, Runnable periodicTask, BiFunction<ModContainer, DeferredWorkQueue, ParallelDispatchEvent> eventConstructor) {
        var workQueue = new DeferredWorkQueue(name);
        dispatchParallelTask(name, parallelExecutor, periodicTask, modContainer -> {
            modContainer.acceptEvent(eventConstructor.apply(modContainer, workQueue));
        });
        runInitTask(name + ": Deferred Queue", syncExecutor, periodicTask, workQueue::runTasks);
    }

    /**
     * Waits for a task to complete, displaying the name of the task on the loading screen.
     */
    public static void waitForTask(String name, Runnable periodicTask, CompletableFuture<?> future) {
        var progress = StartupNotificationManager.addProgressBar(name, 0);
        try {
            waitForFuture(name, periodicTask, future);
        } finally {
            progress.complete();
        }
    }

    /**
     * Dispatches a task across all mod containers in parallel, with progress displayed on the loading screen.
     */
    public static void dispatchParallelTask(String name, Executor parallelExecutor, Runnable periodicTask, Consumer<ModContainer> task) {
        var progress = StartupNotificationManager.addProgressBar(name, modList.size());
        try {
            periodicTask.run();
            var futureList = modList.getSortedMods().stream()
                    .map(modContainer -> {
                        return CompletableFuture.runAsync(() -> {
                            ModLoadingContext.get().setActiveContainer(modContainer);
                            task.accept(modContainer);
                        }, parallelExecutor).whenComplete((result, exception) -> {
                            progress.increment();
                            ModLoadingContext.get().setActiveContainer(null);
                        });
                    })
                    .toList();
            var singleFuture = ModList.gather(futureList)
                    .thenCompose(ModList::completableFutureFromExceptionList);
            waitForFuture(name, periodicTask, singleFuture);
        } finally {
            progress.complete();
        }
    }

    private static void waitForFuture(String name, Runnable periodicTask, CompletableFuture<?> future) {
        while (true) {
            periodicTask.run();
            try {
                future.get(50, TimeUnit.MILLISECONDS);
                return;
            } catch (ExecutionException e) {
                loadingStateValid = false;
                Throwable t = e.getCause();
                final List<Throwable> notModLoading = Arrays.stream(t.getSuppressed())
                        .filter(obj -> !(obj instanceof ModLoadingException))
                        .collect(Collectors.toList());
                if (!notModLoading.isEmpty()) {
                    LOGGER.fatal("Encountered non-modloading exceptions!", e);
                    StartupNotificationManager.modLoaderMessage("ERROR DURING MOD LOADING");
                    throw new RuntimeException("Encountered non-modloading exception in future " + name, e);
                }

                final List<ModLoadingException> modLoadingExceptions = Arrays.stream(t.getSuppressed())
                        .filter(ModLoadingException.class::isInstance)
                        .map(ModLoadingException.class::cast)
                        .collect(Collectors.toList());
                LOGGER.fatal(LOADING, "Failed to wait for future {}, {} errors found", name, modLoadingExceptions.size());
                StartupNotificationManager.modLoaderMessage("ERROR DURING MOD LOADING");
                throw new LoadingFailedException(modLoadingExceptions);
            } catch (Exception ignored) {}
        }
    }

    private static List<ModContainer> buildMods(final IModFile modFile) {
        final Map<String, IModInfo> modInfoMap = modFile.getModFileInfo().getMods().stream().collect(Collectors.toMap(IModInfo::getModId, Function.identity()));

        LOGGER.trace(LOADING, "ModContainer is {}", ModContainer.class.getClassLoader());
        final List<ModContainer> containers = modFile.getScanResult().getTargets()
                .entrySet()
                .stream()
                .map(e -> buildModContainerFromTOML(modFile, modInfoMap, e))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (containers.size() != modInfoMap.size()) {
            var modIds = modInfoMap.values().stream().map(IModInfo::getModId).sorted().collect(Collectors.toList());
            var containerIds = containers.stream().map(c -> c != null ? c.getModId() : "(null)").sorted().collect(Collectors.toList());

            LOGGER.fatal(LOADING, "File {} constructed {} mods: {}, but had {} mods specified: {}",
                    modFile.getFilePath(),
                    containers.size(), containerIds,
                    modInfoMap.size(), modIds);

            var missingClasses = new ArrayList<>(modIds);
            missingClasses.removeAll(containerIds);
            LOGGER.fatal(LOADING, "The following classes are missing, but are reported in the {}: {}", AbstractModProvider.MODS_TOML, missingClasses);

            var missingMods = new ArrayList<>(containerIds);
            missingMods.removeAll(modIds);
            LOGGER.fatal(LOADING, "The following mods are missing, but have classes in the jar: {}", missingMods);

            loadingExceptions.add(new ModLoadingException(null, "fml.modloading.missingclasses", null, modFile.getFilePath()));
        }
        // remove errored mod containers
        return containers.stream().filter(mc -> !(mc instanceof ErroredModContainer)).collect(Collectors.toList());
    }

    private static ModContainer buildModContainerFromTOML(final IModFile modFile, final Map<String, IModInfo> modInfoMap, final Map.Entry<String, ? extends IModLanguageProvider.IModLanguageLoader> idToProviderEntry) {
        try {
            final String modId = idToProviderEntry.getKey();
            final IModLanguageProvider.IModLanguageLoader languageLoader = idToProviderEntry.getValue();
            IModInfo info = Optional.ofNullable(modInfoMap.get(modId)).
            // throw a missing metadata error if there is no matching modid in the modInfoMap from the mods.toml file
                    orElseThrow(() -> new ModLoadingException(null, "fml.modloading.missingmetadata", null, modId));
            return languageLoader.loadMod(info, modFile.getScanResult(), FMLLoader.getGameLayer());
        } catch (ModLoadingException mle) {
            // exceptions are caught and added to the error list for later handling
            loadingExceptions.add(mle);
            // return an errored container instance here, because we tried and failed building a container.
            return new ErroredModContainer();
        }
    }

    /**
     * @return If the current mod loading state is valid. Use if you interact with vanilla systems directly during loading
     *         and don't want to cause extraneous crashes due to trying to do things that aren't possible in a "broken load"
     */
    public static boolean isLoadingStateValid() {
        return loadingStateValid;
    }

    public static <T extends Event & IModBusEvent> void runEventGenerator(Function<ModContainer, T> generator) {
        if (!loadingStateValid) {
            LOGGER.error("Cowardly refusing to send event generator to a broken mod state");
            return;
        }

        // Construct events
        List<ModContainer> modContainers = ModList.get().getSortedMods();
        List<T> events = new ArrayList<>(modContainers.size());
        ModList.get().forEachModInOrder(mc -> events.add(generator.apply(mc)));

        // Post them
        for (EventPriority phase : EventPriority.values()) {
            for (int i = 0; i < modContainers.size(); i++) {
                modContainers.get(i).acceptEvent(phase, events.get(i));
            }
        }
    }

    public static <T extends Event & IModBusEvent> void postEvent(T e) {
        if (!loadingStateValid) {
            LOGGER.error("Cowardly refusing to send event {} to a broken mod state", e.getClass().getName());
            return;
        }
        for (EventPriority phase : EventPriority.values()) {
            ModList.get().forEachModInOrder(mc -> mc.acceptEvent(phase, e));
        }
    }

    public static <T extends Event & IModBusEvent> T postEventWithReturn(T e) {
        postEvent(e);
        return e;
    }

    public static <T extends Event & IModBusEvent> void postEventWrapContainerInModOrder(T event) {
        postEventWithWrapInModOrder(event, (mc, e) -> ModLoadingContext.get().setActiveContainer(mc), (mc, e) -> ModLoadingContext.get().setActiveContainer(null));
    }

    public static <T extends Event & IModBusEvent> void postEventWithWrapInModOrder(T e, BiConsumer<ModContainer, T> pre, BiConsumer<ModContainer, T> post) {
        if (!loadingStateValid) {
            LOGGER.error("Cowardly refusing to send event {} to a broken mod state", e.getClass().getName());
            return;
        }
        for (EventPriority phase : EventPriority.values()) {
            ModList.get().forEachModInOrder(mc -> {
                pre.accept(mc, e);
                mc.acceptEvent(phase, e);
                post.accept(mc, e);
            });
        }
    }

    public static List<ModLoadingWarning> getWarnings() {
        return ImmutableList.copyOf(loadingWarnings);
    }

    public static void addWarning(ModLoadingWarning warning) {
        loadingWarnings.add(warning);
    }

    private static boolean runningDataGen = false;

    public static boolean isDataGenRunning() {
        return runningDataGen;
    }

    private static class ErroredModContainer extends ModContainer {
        public ErroredModContainer() {
            super();
        }

        @Override
        public boolean matches(final Object mod) {
            return false;
        }

        @Override
        public Object getMod() {
            return null;
        }

        @Override
        public @Nullable IEventBus getEventBus() {
            return null;
        }
    }
}
