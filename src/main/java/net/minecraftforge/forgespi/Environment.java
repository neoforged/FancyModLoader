package net.minecraftforge.forgespi;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.TypesafeMap;
import net.minecraftforge.api.distmarker.Dist;

import java.util.function.Supplier;

/**
 * Global environment variables - allows discoverability with other systems without full forge
 * dependency
 */
public class Environment {
    public static final class Keys {

        /**
         * The @{@link Dist} which is running.
         * Populated by forge during {@link cpw.mods.modlauncher.api.ITransformationService#initialize(IEnvironment)}
         */
        public static final Supplier<TypesafeMap.Key<Dist>> DIST = IEnvironment.buildKey("FORGEDIST", Dist.class);
    }

    private static Environment INSTANCE;
    public static Environment get() {
        return INSTANCE;
    }


    private final IEnvironment environment;
    private final Dist dist;

    private Environment(IEnvironment setup) {
        INSTANCE = this;
        this.environment = setup;
        this.dist = setup.getProperty(Keys.DIST.get()).orElseThrow(()->new RuntimeException("Missing DIST in environment!"));
    }
    public Dist getDist() {
        return this.dist;
    }
}
