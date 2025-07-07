/*
 * ModLauncher - for launching Java programs with in-flight transformation ability.
 * Copyright (C) 2017-2019 cpw
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package cpw.mods.modlauncher;

import static cpw.mods.modlauncher.LogMarkers.MODLAUNCHER;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.api.TypesafeMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cpw.mods.modlauncher.serviceapi.ITransformerDiscoveryService;
import cpw.mods.modlauncher.util.ServiceLoaderUtils;
import net.neoforged.fml.loading.FMLServiceProvider;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Entry point for the ModLauncher.
 */
public class Launcher {
    private static final Logger LOGGER = LogManager.getLogger();
    
    public static Launcher INSTANCE;
    private final TypesafeMap blackboard;
    private final Environment environment;
    private final ArgumentHandler argumentHandler;
    private final LaunchServiceHandler launchService;
    private final ModuleLayerHandler moduleLayerHandler;
    private TransformingClassLoader classLoader;
    private FMLServiceProvider fmlServiceProvider;
    private TransformStore transformStore;

    private Launcher() {
        INSTANCE = this;
        LogManager.getLogger().info(MODLAUNCHER, "ModLauncher {} starting: java version {} by {}; OS {} arch {} version {}", () -> IEnvironment.class.getPackage().getImplementationVersion(), () -> System.getProperty("java.version"), () -> System.getProperty("java.vendor"), () -> System.getProperty("os.name"), () -> System.getProperty("os.arch"), () -> System.getProperty("os.version"));
        this.moduleLayerHandler = new ModuleLayerHandler();
        this.launchService = new LaunchServiceHandler(this.moduleLayerHandler);
        this.blackboard = new TypesafeMap();
        this.environment = new Environment(this);
        environment.computePropertyIfAbsent(IEnvironment.Keys.MLSPEC_VERSION.get(), s -> IEnvironment.class.getPackage().getSpecificationVersion());
        environment.computePropertyIfAbsent(IEnvironment.Keys.MLIMPL_VERSION.get(), s -> IEnvironment.class.getPackage().getImplementationVersion());
        environment.computePropertyIfAbsent(IEnvironment.Keys.MODLIST.get(), s -> new ArrayList<>());
        this.argumentHandler = new ArgumentHandler();
    }

    public static void main(String... args) {
        var props = System.getProperties();
        if (props.getProperty("java.vm.name").contains("OpenJ9")) {
            System.err.printf("""
                    WARNING: OpenJ9 is detected. This is definitely unsupported and you may encounter issues and significantly worse performance.
                    For support and performance reasons, we recommend installing a temurin JVM from https://adoptium.net/
                    JVM information: %s %s %s
                    """, props.getProperty("java.vm.vendor"), props.getProperty("java.vm.name"), props.getProperty("java.vm.version"));
        }
        LOGGER.info(MODLAUNCHER, "ModLauncher running: args {}", () -> LaunchServiceHandler.hideAccessToken(args));
        LOGGER.info(MODLAUNCHER, "JVM identified as {} {} {}", props.getProperty("java.vm.vendor"), props.getProperty("java.vm.name"), props.getProperty("java.vm.version"));
        new Launcher().run(args);
    }

    public final TypesafeMap blackboard() {
        return blackboard;
    }

    private void run(String... args) {
        final ArgumentHandler.DiscoveryData discoveryData = this.argumentHandler.setArgs(args);
        discoverServices(discoveryData, this.moduleLayerHandler);

        this.fmlServiceProvider = new FMLServiceProvider();
        this.fmlServiceProvider.onLoad(this.environment);
        this.argumentHandler.processArguments(
                this.environment,
                parser -> this.fmlServiceProvider.arguments((a, b) -> parser.accepts("fml." + a, b)),
                (options, resultHandler) -> fmlServiceProvider.argumentValues(resultHandler.apply("fml", options))
        );
        this.fmlServiceProvider.initialize(this.environment);

        final var scanResults = this.fmlServiceProvider.beginScanning(this.environment)
                .stream().collect(Collectors.groupingBy(FMLServiceProvider.Resource::target));
        scanResults.getOrDefault(IModuleLayerManager.Layer.PLUGIN, List.of())
                .stream()
                .<SecureJar>mapMulti((resource, action) -> resource.resources().forEach(action))
                .forEach(np -> this.moduleLayerHandler.addToLayer(IModuleLayerManager.Layer.PLUGIN, np));
        this.moduleLayerHandler.buildLayer(IModuleLayerManager.Layer.PLUGIN);
        final var gameResults = this.fmlServiceProvider.completeScan(this.moduleLayerHandler)
                .stream().collect(Collectors.groupingBy(FMLServiceProvider.Resource::target));
        final var gameContents = Stream.of(scanResults, gameResults)
                .flatMap(m -> m.getOrDefault(IModuleLayerManager.Layer.GAME, List.of()).stream())
                .<SecureJar>mapMulti((resource, action) -> resource.resources().forEach(action))
                .toList();
        gameContents.forEach(j -> this.moduleLayerHandler.addToLayer(IModuleLayerManager.Layer.GAME, j));
        
        this.launchService.validateLaunchTarget(this.argumentHandler);
        
        this.transformStore = new TransformStore(this.fmlServiceProvider.getLaunchContext());
        
        this.classLoader = buildTransformingClassLoader(this.transformStore, this.moduleLayerHandler);
        Thread.currentThread().setContextClassLoader(this.classLoader);
        this.launchService.launch(this.argumentHandler, this.moduleLayerHandler.getLayer(IModuleLayerManager.Layer.GAME).orElseThrow(), this.classLoader);
    }

    private TransformingClassLoader buildTransformingClassLoader(TransformStore transformStore, final ModuleLayerHandler layerHandler) {
        final var layerInfo = layerHandler.buildLayer(IModuleLayerManager.Layer.GAME, (cf, parents) -> new TransformingClassLoader(this.transformStore, this.environment, cf, parents));
        layerHandler.updateLayer(IModuleLayerManager.Layer.PLUGIN, li -> li.cl().setFallbackClassLoader(layerInfo.cl()));
        return (TransformingClassLoader) layerInfo.cl();
    }

    public Environment environment() {
        return this.environment;
    }

    Optional<ILaunchHandlerService> findLaunchHandler(final String name) {
        return launchService.findLaunchHandler(name);
    }
    
    Optional<ClassProcessor> findTransformer(final String name) {
        return this.transformStore.findTransformer(name);
    }

    public Optional<IModuleLayerManager> findLayerManager() {
        return Optional.ofNullable(this.moduleLayerHandler);
    }

    private void discoverServices(final ArgumentHandler.DiscoveryData discoveryData, ModuleLayerHandler layerHandler) {
        LOGGER.debug(MODLAUNCHER, "Discovering SERVICE layer services");
        var bootLayer = layerHandler.getLayer(IModuleLayerManager.Layer.BOOT).orElseThrow();
        var earlyDiscoveryServices = ServiceLoaderUtils.streamServiceLoader(() -> ServiceLoader.load(bootLayer, ITransformerDiscoveryService.class), sce -> LOGGER.fatal(MODLAUNCHER, "Encountered serious error loading transformation discoverer, expect problems", sce))
                .toList();
        var additionalPaths = earlyDiscoveryServices.stream()
                .map(s -> s.candidates(discoveryData.gameDir(), discoveryData.launchTarget()))
                .<NamedPath>mapMulti(Iterable::forEach)
                .toList();
        LOGGER.debug(MODLAUNCHER, "Found additional SERVICE layer services from discovery services: {}", () -> additionalPaths.stream().map(ap -> Arrays.toString(ap.paths())).collect(Collectors.joining()));
        additionalPaths.forEach(np -> layerHandler.addToLayer(IModuleLayerManager.Layer.SERVICE, np));
        layerHandler.buildLayer(IModuleLayerManager.Layer.SERVICE);
        earlyDiscoveryServices.forEach(s -> s.earlyInitialization(discoveryData.launchTarget(), discoveryData.arguments()));
    }
}
