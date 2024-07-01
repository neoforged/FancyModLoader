package net.neoforged.fml.loading.moddiscovery.locators;

import com.google.common.collect.Streams;
import cpw.mods.jarhandling.JarContentsBuilder;
import cpw.mods.jarhandling.SecureJar;
import net.neoforged.fml.loading.moddiscovery.ModJarMetadata;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.fml.util.ClasspathResourceUtils;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class GameLocator implements IModFileCandidateLocator {

    public static final String LIBRARIES_DIRECTORY_PROPERTY = "libraryDirectory";

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {

        // Three possible ways to find the game:
        // 1a) It's exploded on the classpath
        // 1b) It's on the classpath, but as a jar

        var ourCl = getClass().getClassLoader();

        var classesJar = ClasspathResourceUtils.findFileSystemRootOfFileOnClasspath(ourCl, "net/minecraft/client/Minecraft.class");
        var resourceJar = ClasspathResourceUtils.findFileSystemRootOfFileOnClasspath(ourCl, "assets/.mcassetsroot");
        if (classesJar != null && resourceJar != null) {
            // Determine if we're dealing with a split jar-file situation (moddev)
            if (Files.isRegularFile(classesJar) && Files.isRegularFile(resourceJar)) {
                context.addLocated(classesJar);
                context.addLocated(resourceJar);
                addDevelopmentModFiles(List.of(classesJar), resourceJar, pipeline);
                return;
            }

            // when the classesJar is a directory, we're assuming that we are in neo dev
            // in that case, we also need to find the resource directory
            if (Files.isRegularFile(classesJar) && Files.isRegularFile(resourceJar)) {
                addDevelopmentModFiles(List.of(classesJar), resourceJar, pipeline);
                return;
            }
        }

        // 2) It's neither, but a libraries directory and desired versions are given on the commandline
        var librariesDirectory = System.getProperty(LIBRARIES_DIRECTORY_PROPERTY);
        if (librariesDirectory != null) {

        }
    }

    private void addDevelopmentModFiles(List<Path> paths, Path minecraftResourcesRoot, IDiscoveryPipeline pipeline) {
        var packages = getNeoForgeSpecificPathPrefixes();

        var mcJarContents = new JarContentsBuilder()
                .paths(Streams.concat(paths.stream(), Stream.of(minecraftResourcesRoot)).toArray(Path[]::new))
                .pathFilter((entry, basePath) -> {
                    // We serve everything, except for things in the forge packages.
                    if (basePath.equals(minecraftResourcesRoot) || entry.endsWith("/")) {
                        return true;
                    }
                    // Any non-class file will be served from the client extra jar file mentioned above
                    if (!entry.endsWith(".class")) {
                        return false;
                    }
                    for (var pkg : packages) {
                        if (entry.startsWith(pkg)) {
                            return false;
                        }
                    }
                    return true;
                })
                .build();

        var mcJarMetadata = new ModJarMetadata(mcJarContents);
        var mcSecureJar = SecureJar.from(mcJarContents, mcJarMetadata);
        var minecraftModFile = IModFile.create(mcSecureJar, MinecraftModInfo::buildMinecraftModInfo);
        mcJarMetadata.setModFile(minecraftModFile);
        pipeline.addModFile(minecraftModFile);

        // We need to separate out our resources/code so that we can show up as a different data pack.
        var neoforgeJarContents = new JarContentsBuilder()
                .paths(paths.toArray(Path[]::new))
                .pathFilter((entry, basePath) -> {
                    if (!entry.endsWith(".class")) return true;
                    for (var pkg : packages)
                        if (entry.startsWith(pkg)) return true;
                    return false;
                })
                .build();
        pipeline.addModFile(JarModsDotTomlModFileReader.createModFile(neoforgeJarContents, ModFileDiscoveryAttributes.DEFAULT));
    }

    private static String[] getNeoForgeSpecificPathPrefixes() {
        return new String[]{"net/neoforged/neoforge/", "META-INF/services/", "META-INF/coremods.json", JarModsDotTomlModFileReader.MODS_TOML};
    }

    @Override
    public int getPriority() {
        return HIGHEST_SYSTEM_PRIORITY;
    }

    @Override
    public String toString() {
        return "game locator";
    }
}
