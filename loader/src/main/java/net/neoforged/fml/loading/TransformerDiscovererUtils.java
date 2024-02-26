package net.neoforged.fml.loading;

import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;

import java.nio.file.Path;

public class TransformerDiscovererUtils {


    public static boolean shouldLoadInServiceLayer(Path... path) {
        JarMetadata metadata = JarMetadata.from(new JarContentsBuilder().paths(path).build());
        return metadata.providers().stream()
                .map(SecureJar.Provider::serviceName)
                .anyMatch(TransformerDiscovererConstants.SERVICES::contains);
    }
}
