package cpw.mods.modlauncher;

import net.neoforged.fml.loading.mixin.FMLMixinClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public class ClassTransformStatistics {
    private static final Logger LOGGER = LogManager.getLogger();
    
    private static final Map<ProcessorName, Integer> TRANSFORMS_BY_PROCESSOR = new HashMap<>();
    private static int LOADED_CLASS_COUNT = 0;
    private static int TRANSFORMED_CLASS_COUNT = 0;
    private static int MIXIN_PARSED_CLASS_COUNT = 0;

    @ApiStatus.Internal
    public static void incrementMixinParsedClasses() {
        MIXIN_PARSED_CLASS_COUNT++;
    }
    
    synchronized static void noteHandlingProcessors(List<ClassProcessor> processors) {
        for (var processor : processors) {
            if (!processor.name().equals(ClassProcessor.COMPUTING_FRAMES)) {
                TRANSFORMS_BY_PROCESSOR.compute(processor.name(), (k, v) -> v == null ? 1 : v + 1);
            }
        }
    }

    static void incrementLoadedClasses() {
        LOADED_CLASS_COUNT++;
    }

    static void incrementTransformedClasses() {
        TRANSFORMED_CLASS_COUNT++;
    }

    @ApiStatus.Internal
    public static String getTransformationSummary() {
        var loaded = LOADED_CLASS_COUNT;
        var transformed = TRANSFORMED_CLASS_COUNT;
        double ratio = loaded == 0 ? 0d : ((double) transformed) / loaded * 100;
        return String.format("%s/%s (%.2f%%)", transformed, loaded, ratio);
    }

    @ApiStatus.Internal
    public static String getMixinParsedClassesSummary() {
        return String.valueOf(MIXIN_PARSED_CLASS_COUNT);
    }

    @ApiStatus.Internal
    public static void logTransformationSummary() {
        LOGGER.debug("Transformed/total loaded classes: {} and {} parsed for mixin", getTransformationSummary(), getMixinParsedClassesSummary());
    }
    
    @ApiStatus.Internal
    public synchronized static void checkTransformationBehavior() {
        // Checks if any transformers are acting suspiciously like they're targeting everything; logs an error for any
        // that are.
        TRANSFORMS_BY_PROCESSOR.forEach((name, count) -> {
            var ratio = ((double) count) / LOADED_CLASS_COUNT;
            if (ratio > 0.25) {
                if (name.equals(FMLMixinClassProcessor.NAME)) {
                    // We special-case mixin in order to provide a more useful message, as the root issue here could be
                    // a bad coprocessor. 
                    LOGGER.error("Class processor {} transformed {}% of loaded class which is suspiciously high; this could be due to a mixin coprocessor attempting mass-ASM", name, String.format("%.2f", ratio * 100));
                } else {
                    LOGGER.error("Class processor {} transformed {}% of loaded class which is suspiciously high; it may be attempting mass-ASM. Please report this to the mod author.", name, String.format("%.2f", ratio * 100));
                }
            }
        });
    }
}
