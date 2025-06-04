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
            throw new RuntimeException("Attempted to load class " + classType.getClassName() + " for invalid dist " + dist);
        }
        return EMPTY;
    }

    public synchronized void stripClasses(Collection<String> classes) {
        strippedClasses.addAll(classes);
    }

    public void setDistribution(@Nullable Dist dist) {
        if (dist != null) {
            this.dist = dist.name();
            LOGGER.debug(DISTXFORM, "Configuring for Dist {}", this.dist);
        } else {
            this.dist = null;
            LOGGER.debug(DISTXFORM, "Disabling runtime dist cleaner");
        }
    }
}
