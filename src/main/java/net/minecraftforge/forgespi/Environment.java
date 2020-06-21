package net.minecraftforge.forgespi;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.TypesafeMap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.forgespi.locating.IModDirectoryLocatorFactory;
import net.minecraftforge.forgespi.locating.IModLocator;
import net.minecraftforge.forgespi.locating.ModFileFactory;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Global environment variables - allows discoverability with other systems without full forge
 * dependency
 */
public class Environment {
    public static final class Keys {

        /**
         * The @{@link Dist} which is running.
         * Populated by forge during {@link ITransformationService#initialize(IEnvironment)}
         */
        public static final Supplier<TypesafeMap.Key<Dist>> DIST = IEnvironment.buildKey("FORGEDIST", Dist.class);

        /**
         * Use {@link #MODDIRECTORYFACTORY} instead.
         */
        @Deprecated
        public static final Supplier<TypesafeMap.Key<Function<Path,IModLocator>>> MODFOLDERFACTORY = IEnvironment.buildKey("MODFOLDERFACTORY", Function.class);
        /**
         * Build a custom modlocator based on a supplied directory, with custom name
         */
        public static final Supplier<TypesafeMap.Key<IModDirectoryLocatorFactory>> MODDIRECTORYFACTORY = IEnvironment.buildKey("MODDIRFACTORY", IModDirectoryLocatorFactory.class);

        /**
         * Factory for building {@link net.minecraftforge.forgespi.locating.IModFile} instances
         */
        public static final Supplier<TypesafeMap.Key<ModFileFactory>> MODFILEFACTORY = IEnvironment.buildKey("MODFILEFACTORY", ModFileFactory.class);
        /**
         * Provides a string consumer which can be used to push notification messages to the early startup GUI.
         */
        public static final Supplier<TypesafeMap.Key<Consumer<String>>> PROGRESSMESSAGE = IEnvironment.buildKey("PROGRESSMESSAGE", Consumer.class);
    }
    private static Environment INSTANCE;

    public static void build(IEnvironment environment) {
        if (INSTANCE != null) throw new RuntimeException("Environment is a singleton");
        INSTANCE = new Environment(environment);
    }

    public static Environment get() {
        return INSTANCE;
    }


    private final IEnvironment environment;

    private final Dist dist;
    private final ModFileFactory modFileFactory;

    private Environment(IEnvironment setup) {
        this.environment = setup;
        this.dist = setup.getProperty(Keys.DIST.get()).orElseThrow(()->new RuntimeException("Missing DIST in environment!"));
        this.modFileFactory = setup.getProperty(Keys.MODFILEFACTORY.get()).orElseThrow(()->new RuntimeException("Missing MODFILEFACTORY in environment!"));
    }

    public Dist getDist() {
        return this.dist;
    }
    public ModFileFactory getModFileFactory() {
        return this.modFileFactory;
    }
}
