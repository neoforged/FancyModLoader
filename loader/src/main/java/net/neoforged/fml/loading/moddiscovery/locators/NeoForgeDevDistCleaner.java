package net.neoforged.fml.loading.moddiscovery.locators;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.neoforged.api.distmarker.Dist;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class NeoForgeDevDistCleaner implements ILaunchPluginService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Marker DISTXFORM = MarkerFactory.getMarker("DISTXFORM");
    private static final EnumSet<Phase> EMPTY = EnumSet.noneOf(Phase.class); 

    @Nullable
    private String dist;

    private final Set<String> strippedClasses = new HashSet<>();
    
    @Override
    public String name() {
        return "neoforgedevdistcleaner";
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        if (strippedClasses.contains(classType.getClassName())) {
            LOGGER.error(DISTXFORM, "Attempted to load class {} for invalid dist {}", classType.getClassName(), dist);
            // We must sneakily throw a (usually checked) ClassNotFoundException here. This is necessary so that java's
            // initialization logic produces a NoClassDefFoundError in dev, like it would in prod, when a class
            // referencing such a class is loaded. Though this should not often matter, as errors cannot generally be
            // caught in a recoverable fashion, they may still be caught for debugging purposes or the like so it is
            // best to be consistent here.
            throwUnchecked(new ClassNotFoundException("Attempted to load class " + classType.getClassName() + " for invalid dist " + dist));
        }
        return EMPTY;
    }

    @SuppressWarnings("unchecked")
    private static <T, X extends Throwable> void throwUnchecked(T throwable) throws X {
        throw (X) throwable;
    }

    public synchronized void stripClasses(Collection<String> classes) {
        strippedClasses.addAll(classes);
    }

    public void setDistribution(Dist dist) {
        this.dist = Objects.requireNonNull(dist.name());
    }
}
