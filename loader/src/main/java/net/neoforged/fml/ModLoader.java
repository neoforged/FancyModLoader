/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import static net.neoforged.fml.Logging.CORE;
import static net.neoforged.fml.Logging.LOADING;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
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
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageProvider;
import net.neoforged.neoforgespi.locating.ForgeFeature;
import net.neoforged.neoforgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Contains the logic to load mods, i.e. turn the {@link LoadingModList} into the {@link ModList},
 * as well as initialization tasks for mods and methods to dispatch mod bus events.
 *
 * <p>For the mod initialization flow, see {@code CommonModLoader} in NeoForge.
 *
 * <p>For mod bus event dispatch, see {@link #postEvent(Event)} and related methods.
 */
public final class ModLoader {
    private ModLoader() {}

    private static final Logger LOGGER = LogManager.getLogger();

    private static final List<ModLoadingIssue> loadingIssues = new ArrayList<>();
    private static ModList modList;

    static {
        CrashReportCallables.registerCrashCallable("ModLauncher", FMLLoader::getLauncherInfo);
        CrashReportCallables.registerCrashCallable("ModLauncher launch target", FMLLoader::launcherHandlerName);
        CrashReportCallables.registerCrashCallable("ModLauncher services", ModLoader::computeModLauncherServiceList);
        CrashReportCallables.registerCrashCallable("FML Language Providers", ModLoader::computeLanguageList);
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
        var loadingModList = FMLLoader.getLoadingModList();
        loadingIssues.clear();
        loadingIssues.addAll(loadingModList.getModLoadingIssues());

        ForgeFeature.registerFeature("javaVersion", ForgeFeature.VersionFeatureTest.forVersionString(IModInfo.DependencySide.BOTH, System.getProperty("java.version")));
        ForgeFeature.registerFeature("openGLVersion", ForgeFeature.VersionFeatureTest.forVersionString(IModInfo.DependencySide.CLIENT, ImmediateWindowHandler.getGLVersion()));
        FMLLoader.backgroundScanHandler.waitForScanToComplete(periodicTask);
        final ModList modList = ModList.of(loadingModList.getModFiles().stream().map(ModFileInfo::getFile).toList(),
                loadingModList.getMods());

        if (hasErrors()) {
            var loadingErrors = getLoadingErrors();
            for (var loadingError : loadingErrors) {
                LOGGER.fatal(CORE, "Error during pre-loading phase: {}", loadingError, loadingError.cause());
            }
            cancelLoading(modList);
            throw new ModLoadingException(loadingIssues);
        }
        List<? extends ForgeFeature.Bound> failedBounds = loadingModList.getMods().stream()
                .map(ModInfo::getForgeFeatures)
                .flatMap(Collection::stream)
                .filter(bound -> !ForgeFeature.testFeature(FMLEnvironment.dist, bound))
                .toList();

        if (!failedBounds.isEmpty()) {
            LOGGER.fatal(CORE, "Failed to validate feature bounds for mods: {}", failedBounds);
            for (var fb : failedBounds) {
                loadingIssues.add(ModLoadingIssue.error("fml.modloading.feature.missing", null, fb, ForgeFeature.featureValue(fb)).withAffectedMod(fb.modInfo()));
            }
            cancelLoading(modList);
            throw new ModLoadingException(loadingIssues);
        }

        var modContainers = loadingModList.getModFiles().stream()
                .map(ModFileInfo::getFile)
                .map(ModLoader::buildMods)
                .<ModContainer>mapMulti(Iterable::forEach)
                .toList();
        if (hasErrors()) {
            for (var loadingError : getLoadingErrors()) {
                LOGGER.fatal(CORE, "Failed to initialize mod containers: {}", loadingError, loadingError.cause());
            }
            cancelLoading(modList);
            throw new ModLoadingException(loadingIssues);
        }
        modList.setLoadedMods(modContainers);
        ModLoader.modList = modList;

        constructMods(syncExecutor, parallelExecutor, periodicTask);
    }

    private static void cancelLoading(ModList modList) {
        StartupNotificationManager.modLoaderMessage("ERROR DURING MOD LOADING");
        modList.setLoadedMods(Collections.emptyList());
    }

    @Deprecated(forRemoval = true, since = "20.5")
    public static boolean isLoadingStateValid() {
        return !hasErrors();
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
     * Exception that is fired when a mod loading future cannot be executed because a dependent future failed.
     * It is only used for control flow and easy filtering out, but never logged or propagated further.
     */
    private static class DependentFutureFailedException extends RuntimeException {}

    /**
     * Dispatches a task across all mod containers in parallel, with progress displayed on the loading screen.
     */
    public static void dispatchParallelTask(String name, Executor parallelExecutor, Runnable periodicTask, Consumer<ModContainer> task) {
        var progress = StartupNotificationManager.addProgressBar(name, modList.size());
        try {
            periodicTask.run();
            Map<IModInfo, CompletableFuture<Void>> modFutures = new IdentityHashMap<>(modList.size());
            var futureList = modList.getSortedMods().stream()
                    .map(modContainer -> {
                        // Collect futures for all dependencies first
                        var depFutures = LoadingModList.get().getDependencies(modContainer.getModInfo()).stream()
                                .map(modInfo -> {
                                    var future = modFutures.get(modInfo);
                                    if (future == null) {
                                        throw new IllegalStateException("Dependency future for mod %s which is a dependency of %s not found!".formatted(
                                                modInfo.getModId(), modContainer.getModId()));
                                    }
                                    return future;
                                })
                                .toArray(CompletableFuture[]::new);

                        // Build the future for this container
                        var future = CompletableFuture.allOf(depFutures)
                                .<Void>handleAsync((void_, exception) -> {
                                    if (exception != null) {
                                        // If there was any exception, short circuit.
                                        // The exception will already be handled by `waitForFuture` since it comes from another mod.
                                        LOGGER.debug("Skipping {} task for mod {} because a dependency threw an exception.", name, modContainer.getModId());
                                        progress.increment();
                                        // Throw a marker exception to make sure that dependencies of *this* task don't get executed.
                                        throw new DependentFutureFailedException();
                                    }

                                    try {
                                        ModLoadingContext.get().setActiveContainer(modContainer);
                                        task.accept(modContainer);
                                    } finally {
                                        progress.increment();
                                        ModLoadingContext.get().setActiveContainer(null);
                                    }
                                    return null;
                                }, parallelExecutor);
                        modFutures.put(modContainer.getModInfo(), future);
                        return future;
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
                // Merge all potential modloading issues
                var errorCount = 0;
                for (var error : e.getCause().getSuppressed()) {
                    if (error instanceof DependentFutureFailedException) {
                        continue;
                    } else if (error instanceof ModLoadingException modLoadingException) {
                        loadingIssues.addAll(modLoadingException.getIssues());
                    } else {
                        loadingIssues.add(ModLoadingIssue.error("fml.modloading.uncaughterror", name).withCause(e));
                    }
                    errorCount++;
                }
                LOGGER.fatal(LOADING, "Failed to wait for future {}, {} errors found", name, errorCount);
                cancelLoading(modList);
                throw new ModLoadingException(loadingIssues);
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
                .toList();
        if (containers.size() != modInfoMap.size()) {
            var modIds = modInfoMap.values().stream().map(IModInfo::getModId).sorted().collect(Collectors.toList());
            var containerIds = containers.stream().map(c -> c != null ? c.getModId() : "(null)").sorted().collect(Collectors.toList());

            LOGGER.fatal(LOADING, "File {} constructed {} mods: {}, but had {} mods specified: {}",
                    modFile.getFilePath(),
                    containers.size(), containerIds,
                    modInfoMap.size(), modIds);

            var missingClasses = new ArrayList<>(modIds);
            missingClasses.removeAll(containerIds);
            LOGGER.fatal(LOADING, "The following classes are missing, but are reported in the {}: {}", JarModsDotTomlModFileReader.MODS_TOML, missingClasses);

            var missingMods = new ArrayList<>(containerIds);
            missingMods.removeAll(modIds);
            LOGGER.fatal(LOADING, "The following mods are missing, but have classes in the jar: {}", missingMods);

            loadingIssues.add(ModLoadingIssue.error("fml.modloading.missingclasses", modFile.getFilePath()).withAffectedModFile(modFile));
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
                    orElseThrow(() -> new ModLoadingException(ModLoadingIssue.error("fml.modloading.missingmetadata", modId)));
            return languageLoader.loadMod(info, modFile.getScanResult(), FMLLoader.getGameLayer());
        } catch (ModLoadingException mle) {
            // exceptions are caught and added to the error list for later handling
            loadingIssues.addAll(mle.getIssues());
            // return an errored container instance here, because we tried and failed building a container.
            return new ErroredModContainer();
        }
    }

    public static <T extends Event & IModBusEvent> void runEventGenerator(Function<ModContainer, T> generator) {
        if (hasErrors()) {
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
        if (hasErrors()) {
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
        if (hasErrors()) {
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

    /**
     * @return If the errors occurred during mod loading. Use if you interact with vanilla systems directly during loading
     *         and don't want to cause extraneous crashes due to trying to do things that aren't possible.
     *         If you are running in a Mixin before mod loading has actually started, check {@link LoadingModList#hasErrors()} instead.
     */
    public static boolean hasErrors() {
        return loadingIssues.stream().anyMatch(issue -> issue.severity() == ModLoadingIssue.Severity.ERROR);
    }

    @ApiStatus.Internal
    public static List<ModLoadingIssue> getLoadingErrors() {
        return loadingIssues.stream().filter(issue -> issue.severity() == ModLoadingIssue.Severity.ERROR).toList();
    }

    @ApiStatus.Internal
    public static List<ModLoadingIssue> getLoadingWarnings() {
        return loadingIssues.stream().filter(issue -> issue.severity() == ModLoadingIssue.Severity.WARNING).toList();
    }

    @ApiStatus.Internal
    public static List<ModLoadingIssue> getLoadingIssues() {
        return List.copyOf(loadingIssues);
    }

    @ApiStatus.Internal
    public static void addLoadingIssue(ModLoadingIssue issue) {
        loadingIssues.add(issue);
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
