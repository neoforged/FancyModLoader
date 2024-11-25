/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import cpw.mods.jarhandling.SecureJar;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.neoforged.jarjar.metadata.ContainedJarMetadata;
import net.neoforged.jarjar.metadata.Metadata;
import net.neoforged.jarjar.metadata.MetadataIOHandler;
import net.neoforged.jarjar.selection.util.Constants;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * Simulates various installation types for NeoForge
 */
public class SimulatedInstallation implements AutoCloseable {
    private static final IdentifiableContent CLIENT_ASSETS = new IdentifiableContent("CLIENT_ASSETS", "assets/.mcassetsroot");
    private static final IdentifiableContent SHARED_ASSETS = new IdentifiableContent("SHARED_ASSETS", "data/.mcassetsroot");
    /**
     * A class that is contained in both client and dedicated server distribution, renamed to official mappings.
     */
    private static final IdentifiableContent RENAMED_SHARED = generateClass("RENAMED_SHARED", "net/minecraft/server/MinecraftServer.class");
    /**
     * A class that is contained in both client and dedicated server distribution, renamed to official mappings,
     * and containing NeoForge patches.
     */
    private static final IdentifiableContent PATCHED_SHARED = generateClass("PATCHED_SHARED", "net/minecraft/server/MinecraftServer.class");
    /**
     * A class that is only in the client distribution, renamed to official mappings.
     */
    private static final IdentifiableContent RENAMED_CLIENT = generateClass("RENAMED_CLIENT", "net/minecraft/client/Minecraft.class");
    /**
     * A class that is contained in both client and dedicated server distribution, renamed to official mappings,
     * and containing NeoForge patches.
     */
    private static final IdentifiableContent PATCHED_CLIENT = generateClass("PATCHED_CLIENT", "net/minecraft/client/Minecraft.class");
    private static final IdentifiableContent NEOFORGE_CLASSES = generateClass("NEOFORGE_CLASSES", "net/neoforged/neoforge/common/NeoForgeMod.class");
    private static final IdentifiableContent NEOFORGE_MODS_TOML = new IdentifiableContent("NEOFORGE_MODS_TOML", "META-INF/neoforge.mods.toml", writeNeoForgeModsToml());
    private static final IdentifiableContent NEOFORGE_MANIFEST = new IdentifiableContent("NEOFORGE_MANIFEST", JarFile.MANIFEST_NAME, writeNeoForgeManifest());
    private static final IdentifiableContent NEOFORGE_ASSETS = new IdentifiableContent("NEOFORGE_ASSETS", "neoforged_logo.png");

    public static final String LIBRARIES_DIRECTORY_PROPERTY = "libraryDirectory";
    public static final String MOD_FOLDERS_PROPERTIES = "fml.modFolders";
    public static final String NEOFORGE_VERSION = "20.4.9999";
    public static final String FML_VERSION = "3.0.9999";
    public static final String MC_VERSION = "1.20.4";
    public static final String NEOFORM_VERSION = "202401020304";
    // Simulates the runtime directory passed to the game (present in every directory)
    private final Path gameDir;
    // Simulates the libraries directory found in production installations (both client & server)
    private final Path librariesDir;
    // Used for testing running out of a Gradle project. Is the simulated Gradle project root directory.
    private final Path projectRoot;

    private static final IdentifiableContent[] SERVER_EXTRA_JAR_CONTENT = { SHARED_ASSETS };
    private static final IdentifiableContent[] CLIENT_EXTRA_JAR_CONTENT = { CLIENT_ASSETS, SHARED_ASSETS };
    private static final IdentifiableContent[] NEOFORGE_UNIVERSAL_JAR_CONTENT = { NEOFORGE_ASSETS, NEOFORGE_CLASSES, NEOFORGE_MODS_TOML, NEOFORGE_MANIFEST };
    private static final IdentifiableContent[] USERDEV_CLIENT_JAR_CONTENT = { PATCHED_CLIENT, PATCHED_SHARED };

    // For a production client: Simulates the "libraries" directory found in the Vanilla Minecraft installation directory (".minecraft")
    // For a production server: The NF installer creates a "libraries" directory in the server root
    // In both cases, the location of this directory is passed via a System property "libraryDirectory"
    public SimulatedInstallation() throws IOException {
        gameDir = Files.createTempDirectory("gameDir");
        librariesDir = Files.createTempDirectory("librariesDir");
        projectRoot = Files.createTempDirectory("projectRoot");
    }

    public Path getModsFolder() throws IOException {
        var modsFolder = gameDir.resolve("mods");
        Files.createDirectories(modsFolder);
        return modsFolder;
    }

    @Override
    public void close() throws Exception {
        MoreFiles.deleteRecursively(gameDir, RecursiveDeleteOption.ALLOW_INSECURE);
        MoreFiles.deleteRecursively(librariesDir, RecursiveDeleteOption.ALLOW_INSECURE);
        MoreFiles.deleteRecursively(projectRoot, RecursiveDeleteOption.ALLOW_INSECURE);
        System.clearProperty(LIBRARIES_DIRECTORY_PROPERTY);
        System.clearProperty(MOD_FOLDERS_PROPERTIES);
    }

    public void setupPlainJarInModsFolder(String filename) throws IOException {
        var modsDir = getModsFolder();
        writeJarFile(modsDir.resolve(filename), generateClass("test-class", "pkg/TestClass.class"));
    }

    public void setupModInModsFolder(String modId, String version) throws IOException {
        var modsDir = getModsFolder();
        var filename = modId + "-" + version + ".jar";
        var modEntrypointClass = generateClass(modId + "_ENTRYPOINT", modId + "/ModEntrypoint.class");
        var modsToml = createModsToml(modId, version);
        writeJarFile(modsDir.resolve(filename), modEntrypointClass, modsToml);
    }

    public void setup(String modId, String version) throws IOException {
        var modsDir = getModsFolder();
        var filename = modId + "-" + version + ".jar";
        var modEntrypointClass = generateClass(modId + "_ENTRYPOINT", modId + "/ModEntrypoint.class");
        var modsToml = createModsToml(modId, version);
        writeJarFile(modsDir.resolve(filename), modEntrypointClass, modsToml);
    }

    public void setupProductionClient() throws IOException {
        System.setProperty(LIBRARIES_DIRECTORY_PROPERTY, librariesDir.toString());

        writeLibrary("net.minecraft", "client", MC_VERSION + "-" + NEOFORM_VERSION, "srg", RENAMED_CLIENT, RENAMED_SHARED);
        writeLibrary("net.minecraft", "client", MC_VERSION + "-" + NEOFORM_VERSION, "extra", CLIENT_ASSETS, SHARED_ASSETS);
        writeLibrary("net.neoforged", "neoforge", NEOFORGE_VERSION, "client", PATCHED_CLIENT);
        writeLibrary("net.neoforged", "neoforge", NEOFORGE_VERSION, "universal", NEOFORGE_UNIVERSAL_JAR_CONTENT);
    }

    public void setupProductionServer() throws IOException {
        System.setProperty(LIBRARIES_DIRECTORY_PROPERTY, librariesDir.toString());

        writeLibrary("net.minecraft", "server", MC_VERSION + "-" + NEOFORM_VERSION, "srg", RENAMED_SHARED);
        writeLibrary("net.minecraft", "server", MC_VERSION + "-" + NEOFORM_VERSION, "extra", SERVER_EXTRA_JAR_CONTENT);
        writeLibrary("net.neoforged", "neoforge", NEOFORGE_VERSION, "server", PATCHED_SHARED);
        writeLibrary("net.neoforged", "neoforge", NEOFORGE_VERSION, "universal", NEOFORGE_UNIVERSAL_JAR_CONTENT);
    }

    // The classes directory in a NeoForge development environment will contain both the Minecraft
    // and the NeoForge classes. This is due to both calling each other and having to be compiled in
    // the same javac compilation as a result.
    public ArrayList<Path> setupNeoForgeDevProject() throws IOException {
        var additionalClasspath = new ArrayList<Path>();

        // Emulate the layout of a NeoForge development environment
        // In dev, we have a joined distribution containing both dedicated server and client
        var classesDir = projectRoot.resolve("projects/neoforge/build/classes/java/main");
        additionalClasspath.add(classesDir);
        writeFiles(classesDir, PATCHED_CLIENT, PATCHED_SHARED, NEOFORGE_CLASSES);

        var resourcesDir = projectRoot.resolve("projects/neoforge/build/resources/main");
        additionalClasspath.add(resourcesDir);
        writeFiles(resourcesDir, NEOFORGE_ASSETS, NEOFORGE_MODS_TOML, NEOFORGE_MANIFEST);

        var clientExtraJar = projectRoot.resolve("client-extra.jar");
        additionalClasspath.add(clientExtraJar);
        writeJarFile(clientExtraJar, CLIENT_EXTRA_JAR_CONTENT);

        setModFoldersProperty(Map.of("minecraft", List.of(classesDir, resourcesDir)));

        return additionalClasspath;
    }

    /**
     * In userdev, the Gradle tooling recompiles a joined Minecraft jar and injects the NeoForge classes and resources.
     * Original Minecraft assets are split off into a client-extra.jar similar to neodev.
     */
    public List<Path> setupUserdevProject() throws IOException {
        var additionalClasspath = new ArrayList<Path>();

        var neoforgeJar = projectRoot.resolve("neoforge-joined.jar");
        additionalClasspath.add(neoforgeJar);
        writeJarFile(neoforgeJar, Stream.concat(Stream.of(USERDEV_CLIENT_JAR_CONTENT), Stream.of(NEOFORGE_UNIVERSAL_JAR_CONTENT)).toArray(IdentifiableContent[]::new));

        var clientExtraJar = projectRoot.resolve("client-extra.jar");
        additionalClasspath.add(clientExtraJar);
        writeJarFile(clientExtraJar, CLIENT_EXTRA_JAR_CONTENT);

        return additionalClasspath;
    }

    public static void setModFoldersProperty(Map<String, List<Path>> modFolders) {
        var modFolderList = modFolders.entrySet()
                .stream()
                .flatMap(entry -> entry.getValue().stream().map(path -> entry.getKey() + "%%" + path))
                .collect(Collectors.joining(File.pathSeparator));

        System.setProperty(MOD_FOLDERS_PROPERTIES, modFolderList);
    }

    public Path getLibrariesDir() {
        return librariesDir;
    }

    public Path getGameDir() {
        return gameDir;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    /**
     * Dynamically generates a class. This is not 100% correct, but should be sufficient for the
     * background scanner to read it.
     */
    public static IdentifiableContent generateClass(String id, String relativePath) {
        var className = relativePath.replace(".class", "");
        var classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classWriter.visitAnnotation("Lfake/ClassAnnotation;", true);
        classWriter.visit(Opcodes.V21, 0, className, null, null, null);
        return new IdentifiableContent(id, relativePath, classWriter.toByteArray());
    }

    private static byte[] writeNeoForgeManifest() {
        return "FML-System-Mods: neoforge\n".getBytes();
    }

    private static byte[] writeNeoForgeModsToml() {
        return """
                modLoader = "javafml"
                loaderVersion = "[3,]"
                license = "LICENSE"

                [[mods]]
                modId="neoforge"
                """.getBytes();
    }

    public static IdentifiableContent createManifest(String name, Map<String, String> attributes) throws IOException {
        Manifest manifest = new Manifest();
        // If no manifest version is written, nothing is written.
        manifest.getMainAttributes().putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
        for (var entry : attributes.entrySet()) {
            manifest.getMainAttributes().putValue(entry.getKey(), entry.getValue());
        }
        var bout = new ByteArrayOutputStream();
        manifest.write(bout);
        var content = bout.toByteArray();
        return new IdentifiableContent(name, JarFile.MANIFEST_NAME, content);
    }

    public static IdentifiableContent createModsToml(String modId, String version) {
        var content = """
                modLoader = "javafml"
                loaderVersion = "[3,]"
                license = "LICENSE"

                [[mods]]
                modId="%s"
                version="%s"
                """.formatted(modId, version).getBytes();
        return new IdentifiableContent(modId + "_MODS_TOML", "META-INF/neoforge.mods.toml", content);
    }

    public static IdentifiableContent createMultiModsToml(String modId, String version, String secondaryModId, String secondaryModversion) {
        var content = """
                modLoader = "javafml"
                loaderVersion = "[3,]"
                license = "LICENSE"

                [[mods]]
                modId="%s"
                version="%s"

                [[mods]]
                modId="%s"
                version="%s"
                """.formatted(modId, version, secondaryModId, secondaryModversion).getBytes();
        return new IdentifiableContent(modId + "_MODS_TOML", "META-INF/neoforge.mods.toml", content);
    }

    public Path writeLibrary(String group, String artifact, String version, IdentifiableContent... content) throws IOException {
        return writeLibrary(group, artifact, version, null, content);
    }

    private Path writeLibrary(String group, String artifact, String version, @Nullable String classifier, IdentifiableContent... content) throws IOException {
        var folder = librariesDir.resolve(group.replace('.', '/'))
                .resolve(artifact)
                .resolve(version);
        Files.createDirectories(folder);

        var filename = artifact + "-" + version;
        if (classifier != null) {
            filename += "-" + classifier;
        }
        filename += ".jar";

        var file = folder.resolve(filename);
        writeJarFile(file, content);
        return file;
    }

    public Path writeModJar(String filename, IdentifiableContent... content) throws IOException {
        var path = getModsFolder().resolve(filename);
        writeJarFile(path, content);
        return path;
    }

    public ModFileBuilder buildModJar(String filename) throws IOException {
        var path = getModsFolder().resolve(filename);
        return new ModFileBuilder(path);
    }

    public void writeConfig(String... lines) throws IOException {
        var file = getGameDir().resolve("config/fml.toml");

        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", lines));
    }

    public static void writeJarFile(Path file, IdentifiableContent... content) throws IOException {
        try (var fout = Files.newOutputStream(file)) {
            writeJarFile(fout, content);
        }
    }

    public static IdentifiableContent createJarFile(String name, String relativePath, IdentifiableContent... content) throws IOException {
        var bout = new ByteArrayOutputStream();
        writeJarFile(bout, content);
        return new IdentifiableContent(name, relativePath, bout.toByteArray());
    }

    public static void writeJarFile(OutputStream out, IdentifiableContent... content) throws IOException {
        try (var jout = new JarOutputStream(out)) {
            // Make sure the Manifest is written first
            for (var identifiableContent : content) {
                if (JarFile.MANIFEST_NAME.equals(identifiableContent.relativePath())) {
                    var ze = new JarEntry(JarFile.MANIFEST_NAME);
                    jout.putNextEntry(ze);
                    jout.write(identifiableContent.content());
                    jout.closeEntry();
                }
            }

            for (var identifiableContent : content) {
                if (JarFile.MANIFEST_NAME.equals(identifiableContent.relativePath())) {
                    continue; // Written earlier
                }

                var ze = new JarEntry(identifiableContent.relativePath());
                jout.putNextEntry(ze);
                jout.write(identifiableContent.content());
                jout.closeEntry();
            }
        }
    }

    public void writeFiles(Path folder, IdentifiableContent... content) throws IOException {
        for (var identifiableContent : content) {
            var path = folder.resolve(identifiableContent.relativePath());
            Files.createDirectories(path.getParent());
            try (var out = Files.newOutputStream(path)) {
                out.write(identifiableContent.content());
            }
        }
    }

    public void assertMinecraftServerJar(LaunchResult launchResult) throws IOException {
        var expectedContent = new ArrayList<IdentifiableContent>();
        Collections.addAll(expectedContent, SERVER_EXTRA_JAR_CONTENT);
        expectedContent.add(PATCHED_SHARED);

        assertModContent(launchResult, "minecraft", expectedContent);
    }

    public void assertMinecraftClientJar(LaunchResult launchResult) throws IOException {
        var expectedContent = new ArrayList<IdentifiableContent>();
        Collections.addAll(expectedContent, CLIENT_EXTRA_JAR_CONTENT);
        expectedContent.add(PATCHED_CLIENT);
        expectedContent.add(PATCHED_SHARED);

        assertModContent(launchResult, "minecraft", expectedContent);
    }

    public void assertNeoForgeJar(LaunchResult launchResult) throws IOException {
        var expectedContent = List.of(
                NEOFORGE_ASSETS,
                NEOFORGE_CLASSES,
                NEOFORGE_MODS_TOML,
                NEOFORGE_MANIFEST);

        assertModContent(launchResult, "neoforge", expectedContent);
    }

    public void assertModContent(LaunchResult launchResult, String modId, Collection<IdentifiableContent> content) throws IOException {
        assertThat(launchResult.loadedMods()).containsKey(modId);

        var modFileInfo = launchResult.loadedMods().get(modId);
        assertNotNull(modFileInfo, "mod " + modId + " is missing");

        assertSecureJarContent(modFileInfo.getFile().getSecureJar(), content);
    }

    public void assertSecureJarContent(SecureJar jar, Collection<IdentifiableContent> content) throws IOException {
        var paths = listFilesRecursively(jar);

        assertThat(paths.keySet()).containsOnly(content.stream().map(IdentifiableContent::relativePath).toArray(String[]::new));

        for (var identifiableContent : content) {
            var expectedContent = identifiableContent.content();
            var actualContent = Files.readAllBytes(paths.get(identifiableContent.relativePath()));
            if (isPrintableAscii(expectedContent) && isPrintableAscii(actualContent)) {
                assertThat(new String(actualContent)).isEqualTo(new String(expectedContent));
            } else {
                assertThat(actualContent).isEqualTo(expectedContent);
            }
        }
    }

    private boolean isPrintableAscii(byte[] potentialText) {
        for (byte b : potentialText) {
            if (b < 0x20 || b == 0x7f) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Path> listFilesRecursively(SecureJar jar) throws IOException {
        Map<String, Path> paths;
        var rootPath = jar.getRootPath();
        try (var stream = Files.walk(rootPath)) {
            paths = stream
                    .filter(Files::isRegularFile)
                    .map(rootPath::relativize)
                    .collect(Collectors.toMap(
                            path -> path.toString().replace('\\', '/'),
                            Function.identity()));
        }
        return paths;
    }

    public static IdentifiableContent createJijMetadata(ContainedJarMetadata... containedJars) {
        Metadata metadata = new Metadata(Arrays.asList(containedJars));

        byte[] content;
        try (var in = MetadataIOHandler.toInputStream(metadata)) {
            content = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new IdentifiableContent("JIJ_METADATA", Constants.CONTAINED_JARS_METADATA_PATH, content);
    }

    public List<Path> setupGradleModule(IdentifiableContent... buildOutput) throws IOException {
        return setupGradleModule(null, buildOutput);
    }

    public List<Path> setupGradleModule(@Nullable String subfolder, IdentifiableContent... buildOutput) throws IOException {
        Path moduleRoot = projectRoot;
        if (subfolder != null) {
            moduleRoot = moduleRoot.resolve(subfolder);
        }

        // Build typical single-module gradle output directories
        var classesDir = moduleRoot.resolve("build/classes/java/main");
        Files.createDirectories(classesDir);
        var resourcesDir = moduleRoot.resolve("build/resources/main");
        Files.createDirectories(resourcesDir);
        for (IdentifiableContent identifiableContent : buildOutput) {
            if (identifiableContent.relativePath().endsWith(".class")) {
                writeFiles(classesDir, identifiableContent);
            } else {
                writeFiles(resourcesDir, identifiableContent);
            }
        }

        return List.of(classesDir, resourcesDir);
    }
}
