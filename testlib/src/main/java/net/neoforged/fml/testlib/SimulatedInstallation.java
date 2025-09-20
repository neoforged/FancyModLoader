/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.testlib;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.gson.JsonObject;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
    public static final IdentifiableContent CLIENT_ASSETS = new IdentifiableContent("CLIENT_ASSETS", "assets/.mcassetsroot");
    public static final IdentifiableContent SHARED_ASSETS = new IdentifiableContent("SHARED_ASSETS", "data/.mcassetsroot");
    public static final IdentifiableContent RESOURCES_MANIFEST;

    public enum Type {
        PRODUCTION_CLIENT,
        PRODUCTION_SERVER,
        /**
         * Used by NeoGradle and ModDevGradle currently.
         * It puts two jars on the classpath:
         * - A jar with all Minecraft Classes, NeoForge Classes and Resources
         * - A second jar with the original non-class content of the Minecraft jar
         * The Minecraft classes and resources are merged from server+client distributions.
         *
         * The difference between FOLDERS and JAR relates to how the "installation appropriate" mod project
         * is put onto the classpath (as folders, or built as a jar file).
         */
        USERDEV_LEGACY_FOLDERS,
        USERDEV_LEGACY_JAR,
        /**
         * Not used by any tooling yet.
         * It puts two jars on the classpath:
         * - The merged, patched Minecraft jar, including classes and resources from both distributions
         * - The unmodified NeoForge universal jar
         *
         * The difference between FOLDERS and JAR relates to how the "installation appropriate" mod project
         * is put onto the classpath (as folders, or built as a jar file).
         */
        USERDEV_FOLDERS,
        USERDEV_JAR,
        ;

        public boolean isProduction() {
            return this == PRODUCTION_CLIENT || this == PRODUCTION_SERVER;
        }
    }

    static {
        try {
            RESOURCES_MANIFEST = createManifest("RESOURCES_MANIFEST", Map.of(
                    "Minecraft-Dists", "client server"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A class that is contained in both client and dedicated server distribution, renamed to official mappings.
     */
    public static final IdentifiableContent RENAMED_SHARED = generateClass("RENAMED_SHARED", "net/minecraft/DetectedVersion.class");
    /**
     * A class that is contained in both client and dedicated server distribution, renamed to official mappings,
     * and containing NeoForge patches.
     */
    public static final IdentifiableContent PATCHED_SHARED = generateClass("PATCHED_SHARED", "net/minecraft/DetectedVersion.class");
    /**
     * A class that is only in the client distribution, renamed to official mappings.
     */
    public static final IdentifiableContent RENAMED_CLIENT = generateClass("RENAMED_CLIENT", "net/minecraft/client/Minecraft.class");
    /**
     * A neoforge.mods.toml for the Minecraft jar.
     */
    public static final IdentifiableContent MINECRAFT_MODS_TOML = new IdentifiableContent("MINECRAFT_MODS_TOML", "META-INF/neoforge.mods.toml", writeMinecraftModsToml());

    /**
     * A class that is contained in both client and dedicated server distribution, renamed to official mappings,
     * and containing NeoForge patches.
     */
    public static final IdentifiableContent PATCHED_CLIENT = generateClass("PATCHED_CLIENT", "net/minecraft/client/Minecraft.class");
    public static final IdentifiableContent NEOFORGE_CLIENT_CLASSES = generateClass("NEOFORGE_CLIENT_CLASSES", "net/neoforged/neoforge/client/ClientNeoForgeMod.class");
    public static final IdentifiableContent NEOFORGE_CLASSES = generateClass("NEOFORGE_CLASSES", "net/neoforged/neoforge/common/NeoForgeMod.class");
    public static final IdentifiableContent NEOFORGE_MODS_TOML = new IdentifiableContent("NEOFORGE_MODS_TOML", "META-INF/neoforge.mods.toml", writeNeoForgeModsToml());
    public static final IdentifiableContent NEOFORGE_MANIFEST = new IdentifiableContent("NEOFORGE_MANIFEST", JarFile.MANIFEST_NAME, writeNeoForgeManifest());
    public static final IdentifiableContent NEOFORGE_ASSETS = new IdentifiableContent("NEOFORGE_ASSETS", "neoforged_logo.png");

    public static final String LIBRARIES_DIRECTORY_PROPERTY = "libraryDirectory";
    public static final String MOD_FOLDERS_PROPERTIES = "fml.modFolders";
    public static final String NEOFORGE_VERSION = "20.4.9999";
    public static final String MC_VERSION = "1.20.4";
    public static final String NEOFORM_VERSION = "202401020304";
    public static final IdentifiableContent MINECRAFT_VERSION_JSON = new IdentifiableContent("MC_VERSION_JSON", "version.json", buildVersionJson(MC_VERSION));

    public static final IdentifiableContent[] SERVER_EXTRA_JAR_CONTENT = { SHARED_ASSETS, MINECRAFT_VERSION_JSON };
    public static final IdentifiableContent[] CLIENT_EXTRA_JAR_CONTENT = { CLIENT_ASSETS, SHARED_ASSETS, RESOURCES_MANIFEST, MINECRAFT_VERSION_JSON };
    public static final IdentifiableContent[] NEOFORGE_UNIVERSAL_JAR_CONTENT = { NEOFORGE_ASSETS, NEOFORGE_CLIENT_CLASSES, NEOFORGE_CLASSES, NEOFORGE_MODS_TOML, NEOFORGE_MANIFEST };
    public static final IdentifiableContent[] USERDEV_CLIENT_JAR_CONTENT = { PATCHED_CLIENT, PATCHED_SHARED };

    private static final String GAV_PATCHED_CLIENT = "net.neoforged:minecraft-client-patched:" + NEOFORGE_VERSION;
    private static final String GAV_PATCHED_SERVER = "net.neoforged:minecraft-server-patched:" + NEOFORGE_VERSION;
    private static final String GAV_NEOFORGE_UNIVERSAL = "net.neoforged:neoforge:" + NEOFORGE_VERSION + ":universal";

    private static byte[] buildVersionJson(String mcVersion) {
        var obj = new JsonObject();
        obj.addProperty("id", mcVersion);
        return obj.toString().getBytes(StandardCharsets.UTF_8);
    }

    // Simulates the runtime directory passed to the game (present in every directory)
    private final Path gameDir;
    // Simulates the libraries directory found in production installations (both client & server)
    private final Path librariesDir;
    // Simulates the versions directory found in production installations (only on client)
    private final Path versionsDir;
    // Used for testing running out of a Gradle project. Is the simulated Gradle project root directory.
    private final Path projectRoot;

    private Type type;

    // Launch classpath
    private final List<Path> launchClasspath = new ArrayList<>();

    // As the installation is setup, we record where which components are
    private InstallationComponents componentRoots;

    // For a production client: Simulates the "libraries" directory found in the Vanilla Minecraft installation directory (".minecraft")
    // For a production server: The NF installer creates a "libraries" directory in the server root
    // In both cases, the location of this directory is passed via a System property "libraryDirectory"
    public SimulatedInstallation() throws IOException {
        gameDir = Files.createTempDirectory("gameDir");
        librariesDir = Files.createTempDirectory("librariesDir");
        versionsDir = Files.createTempDirectory("versionsDir");
        projectRoot = Files.createTempDirectory("projectRoot");
    }

    public void setup(Type type) throws IOException {
        switch (type) {
            case PRODUCTION_CLIENT -> {
                System.setProperty(LIBRARIES_DIRECTORY_PROPERTY, librariesDir.toString());

                var patchedClientJar = writeLibrary(GAV_PATCHED_CLIENT, PATCHED_CLIENT, RENAMED_SHARED, CLIENT_ASSETS, SHARED_ASSETS, MINECRAFT_MODS_TOML, MINECRAFT_VERSION_JSON);
                var universalJar = writeLibrary(GAV_NEOFORGE_UNIVERSAL, NEOFORGE_UNIVERSAL_JAR_CONTENT);

                // For the production client, the Vanilla launcher puts the original, obfuscated client jar on the classpath
                // Since this can influence our detection logic, let's make sure it's included for the tests.
                Path obfuscatedClientJar = versionsDir.resolve(MC_VERSION).resolve(MC_VERSION + ".jar");
                writeJarFile(
                        obfuscatedClientJar,
                        generateClass("CLIENT_MAIN", "net/minecraft/client/main/Main.class"),
                        generateClass("CLIENT_DATA_MAIN", "net/minecraft/client/data/Main.class"),
                        generateClass("SERVER_MAIN", "net/minecraft/server/Main.class"),
                        generateClass("SERVER_DATA_MAIN", "net/minecraft/data/Main.class"),
                        generateClass("GAMETEST_MAIN", "net/minecraft/gametest/Main.class"),
                        generateClass("MINECRAFT_SERVER", "net/minecraft/server/MinecraftServer.class"),
                        MINECRAFT_VERSION_JSON,
                        SHARED_ASSETS,
                        CLIENT_ASSETS);
                launchClasspath.add(obfuscatedClientJar);

                componentRoots = InstallationComponents.productionJars(patchedClientJar, universalJar);
            }
            case PRODUCTION_SERVER -> {
                System.setProperty(LIBRARIES_DIRECTORY_PROPERTY, librariesDir.toString());

                var patchedServerJar = writeLibrary(GAV_PATCHED_SERVER, PATCHED_SHARED, SHARED_ASSETS, MINECRAFT_MODS_TOML, MINECRAFT_VERSION_JSON);
                var universalJar = writeLibrary(GAV_NEOFORGE_UNIVERSAL, NEOFORGE_UNIVERSAL_JAR_CONTENT);

                componentRoots = InstallationComponents.productionJars(patchedServerJar, universalJar);
            }
            case USERDEV_LEGACY_FOLDERS, USERDEV_LEGACY_JAR -> {
                var neoforgeJar = projectRoot.resolve("neoforge-joined.jar");
                launchClasspath.add(neoforgeJar);
                writeJarFile(neoforgeJar, Stream.concat(Stream.of(USERDEV_CLIENT_JAR_CONTENT), Stream.of(NEOFORGE_UNIVERSAL_JAR_CONTENT)).toArray(IdentifiableContent[]::new));

                var clientExtraJar = projectRoot.resolve("client-extra.jar");
                launchClasspath.add(clientExtraJar);
                writeJarFile(clientExtraJar, CLIENT_EXTRA_JAR_CONTENT);

                componentRoots = new InstallationComponents(
                        neoforgeJar,
                        neoforgeJar,
                        clientExtraJar,
                        clientExtraJar,
                        neoforgeJar,
                        neoforgeJar,
                        neoforgeJar,
                        neoforgeJar);
            }
            case USERDEV_FOLDERS, USERDEV_JAR -> {
                var universalJar = writeLibrary("net.neoforged", "neoforge", NEOFORGE_VERSION, "universal", NEOFORGE_UNIVERSAL_JAR_CONTENT);
                launchClasspath.add(universalJar);

                var minecraftJar = projectRoot.resolve("minecraft-patched-client-" + NEOFORGE_VERSION + ".jar");
                launchClasspath.add(minecraftJar);
                writeJarFile(minecraftJar, PATCHED_CLIENT, PATCHED_SHARED, CLIENT_ASSETS, SHARED_ASSETS, MINECRAFT_MODS_TOML, RESOURCES_MANIFEST, MINECRAFT_VERSION_JSON);

                componentRoots = InstallationComponents.productionJars(minecraftJar, universalJar);
            }
            default -> throw new UnsupportedOperationException();
        }
        this.type = type;
    }

    public Path getModsFolder() throws IOException {
        var modsFolder = gameDir.resolve("mods");
        Files.createDirectories(modsFolder);
        return modsFolder;
    }

    @Override
    public void close() throws Exception {
        tryDeleteDirectory(gameDir);
        tryDeleteDirectory(librariesDir);
        tryDeleteDirectory(versionsDir);
        tryDeleteDirectory(projectRoot);
        System.clearProperty(LIBRARIES_DIRECTORY_PROPERTY);
        System.clearProperty(MOD_FOLDERS_PROPERTIES);
    }

    private void tryDeleteDirectory(Path gameDir) throws IOException, InterruptedException {
        for (var i = 0; i < 5; i++) {
            try {
                MoreFiles.deleteRecursively(gameDir, RecursiveDeleteOption.ALLOW_INSECURE);
                break;
            } catch (IOException e) {
                if (i + 1 >= 5) {
                    throw e;
                }
                Thread.sleep(100L);
            }
        }
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
        setup(Type.PRODUCTION_CLIENT);
    }

    public void setupProductionClientLegacy() throws IOException {
        System.setProperty(LIBRARIES_DIRECTORY_PROPERTY, librariesDir.toString());

        writeLibrary("net.minecraft", "client", MC_VERSION + "-" + NEOFORM_VERSION, "srg", RENAMED_CLIENT, RENAMED_SHARED);
        writeLibrary("net.minecraft", "client", MC_VERSION + "-" + NEOFORM_VERSION, "extra", CLIENT_ASSETS, SHARED_ASSETS, MINECRAFT_VERSION_JSON);
        writeLibrary("net.neoforged", "neoforge", NEOFORGE_VERSION, "client", PATCHED_CLIENT);
        writeLibrary("net.neoforged", "neoforge", NEOFORGE_VERSION, "universal", NEOFORGE_UNIVERSAL_JAR_CONTENT);
    }

    public void setupProductionServer() throws IOException {
        setup(Type.PRODUCTION_SERVER);
    }

    public void setupProductionServerLegacy() throws IOException {
        System.setProperty(LIBRARIES_DIRECTORY_PROPERTY, librariesDir.toString());

        writeLibrary("net.minecraft", "server", MC_VERSION + "-" + NEOFORM_VERSION, "srg", RENAMED_SHARED);
        writeLibrary("net.minecraft", "server", MC_VERSION + "-" + NEOFORM_VERSION, "extra", SERVER_EXTRA_JAR_CONTENT);
        writeLibrary("net.neoforged", "neoforge", NEOFORGE_VERSION, "server", PATCHED_SHARED);
        writeLibrary("net.neoforged", "neoforge", NEOFORGE_VERSION, "universal", NEOFORGE_UNIVERSAL_JAR_CONTENT);
    }

    // The classes directory in a NeoForge development environment will contain both the Minecraft
    // and the NeoForge classes. This is due to both calling each other and having to be compiled in
    // the same javac compilation as a result.
    public List<Path> setupNeoForgeDevProject() throws IOException {
        var folders = createNeoForgeDevFolders();

        var additionalClasspath = new ArrayList<Path>();

        // Emulate the layout of a NeoForge development environment
        // In dev, the NeoForge sources itself are joined, but the Minecraft sources are not
        additionalClasspath.add(folders.clientClassesDir);
        additionalClasspath.add(folders.commonClassesDir);
        additionalClasspath.add(folders.commonResourcesDir);
        additionalClasspath.add(folders.clientExtraJar);

        setModFoldersProperty(Map.of("minecraft", List.of(folders.clientClassesDir, folders.commonClassesDir, folders.commonResourcesDir, folders.clientExtraJar)));

        return additionalClasspath;
    }

    // Similar to setupNeoForgeDevProject, but if the client is launched from Gradle, it will put the built jar files from
    // the common sourceSet onto the classpath, while adding the client as directories.
    public List<Path> setupSplitNeoForgeDevProjectForClientLaunch() throws IOException {
        var folders = createNeoForgeDevFolders();

        Path commonJarFile = projectRoot.resolve("build/libs/neoforge-common.jar");
        createJarFileFromFolders(commonJarFile, folders.commonClassesDir, folders.commonResourcesDir);

        setModFoldersProperty(Map.of("minecraft", List.of(folders.clientClassesDir, folders.commonClassesDir, folders.commonResourcesDir, folders.clientExtraJar)));

        return List.of(folders.clientClassesDir, commonJarFile, folders.clientExtraJar);
    }

    protected record NeoForgeDevFolders(
            Path clientClassesDir,
            Path commonClassesDir,
            Path commonResourcesDir,
            Path clientExtraJar) {}

    // Emulate the layout of a NeoForge development environment
    // In dev, the NeoForge sources itself are joined, but the Minecraft sources are not
    private NeoForgeDevFolders createNeoForgeDevFolders() throws IOException {
        var clientClassesDir = projectRoot.resolve("projects/neoforge/build/classes/java/client");
        writeFiles(clientClassesDir, PATCHED_CLIENT, NEOFORGE_CLIENT_CLASSES);

        var commonClassesDir = projectRoot.resolve("projects/neoforge/build/classes/java/main");
        writeFiles(commonClassesDir, PATCHED_SHARED, NEOFORGE_CLASSES);

        var resourcesDir = projectRoot.resolve("projects/neoforge/build/resources/main");
        writeFiles(resourcesDir, NEOFORGE_ASSETS, NEOFORGE_MODS_TOML, NEOFORGE_MANIFEST);

        var clientExtraJar = projectRoot.resolve("client-extra.jar");
        writeJarFile(clientExtraJar, CLIENT_EXTRA_JAR_CONTENT);

        return new NeoForgeDevFolders(
                clientClassesDir,
                commonClassesDir,
                resourcesDir,
                clientExtraJar);
    }

    public List<Path> setupUserdevProject() throws IOException {
        setup(Type.USERDEV_LEGACY_FOLDERS);
        return launchClasspath;
    }

    public List<Path> setupUserdevProjectNew() throws IOException {
        setup(Type.USERDEV_FOLDERS);
        return launchClasspath;
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
     * {@returns path to the jar file containing Minecraft resources}
     */
    public InstallationComponents getComponentRoots() {
        if (componentRoots == null) {
            throw new IllegalStateException("Installation hasn't been setup yet");
        }
        return componentRoots;
    }

    /**
     * {@returns the type of installation that was setup}
     */
    public Type getType() {
        if (type == null) {
            throw new IllegalStateException("Installation hasn't been setup yet");
        }
        return type;
    }

    /**
     * Helper to add some files to a jar file. It also correct overwrites the MANIFEST if given.
     */
    public static void addFilesToJar(Path jarFile, IdentifiableContent... content) throws IOException {
        IdentifiableContent newManifest = null;
        for (var identifiableContent : content) {
            if (JarFile.MANIFEST_NAME.equals(identifiableContent.relativePath())) {
                newManifest = identifiableContent;
                break;
            }
        }

        Set<String> written = new HashSet<>();
        var newJarFile = jarFile.resolveSibling(jarFile.getFileName() + ".new");
        try (var jarIn = new JarFile(jarFile.toFile());
                var jarOut = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(newJarFile)))) {
            // Ensure the manifest is written first
            if (newManifest != null) {
                jarOut.putNextEntry(new ZipEntry(JarFile.MANIFEST_NAME));
                jarOut.write(newManifest.content());
                jarOut.closeEntry();
                written.add(JarFile.MANIFEST_NAME);
            }

            for (var c : content) {
                if (written.add(c.relativePath())) {
                    jarOut.putNextEntry(new ZipEntry(c.relativePath()));
                    jarOut.write(c.content());
                    jarOut.closeEntry();
                }
            }

            var entries = jarIn.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.isDirectory() && written.add(entry.getName())) {
                    jarOut.putNextEntry(entry);
                    try (var entryIn = jarIn.getInputStream(entry)) {
                        entryIn.transferTo(jarOut);
                    }
                    jarOut.closeEntry();
                }
            }
        }

        Files.move(newJarFile, jarFile, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Dynamically generates a class. This is not 100% correct, but should be sufficient for the
     * background scanner to read it.
     */
    public static IdentifiableContent generateClass(String id, String relativePath) {
        var className = relativePath.replace(".class", "");
        var classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classWriter.visitAnnotation("Lfake/ClassAnnotation;", true);
        classWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);
        var constructor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);  // Load 'this'
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false);  // Call super()
        constructor.visitInsn(Opcodes.RETURN);  // Return
        constructor.visitMaxs(0, 0); // Let COMPUTE_MAXS handle this
        constructor.visitEnd();
        classWriter.visitEnd();

        return new IdentifiableContent(id, relativePath, classWriter.toByteArray());
    }

    private static byte[] writeNeoForgeManifest() {
        return "Manifest-Version: 1.0\nFML-System-Mods: neoforge\n".getBytes();
    }

    private static byte[] writeNeoForgeModsToml() {
        return """
                license = "LICENSE"

                [[mods]]
                modId="neoforge"
                """.getBytes();
    }

    private static byte[] writeMinecraftModsToml() {
        return """
                loader = "minecraft"
                license = "See Minecraft EULA"

                [[mods]]
                modId="minecraft"
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

    public Path writeLibrary(String groupArtifactVersion, IdentifiableContent... content) throws IOException {
        String[] parts = groupArtifactVersion.split(":", 4);
        return writeLibrary(parts[0], parts[1], parts[2], parts.length > 3 ? parts[3] : null, content);
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

    private Path getLibrary(String groupArtifactVersion) {
        String[] parts = groupArtifactVersion.split(":", 4);
        var group = parts[0];
        var artifact = parts[1];
        var version = parts[2];
        var classifier = parts.length >= 4 ? parts[3] : null;
        var folder = librariesDir.resolve(group.replace('.', '/'))
                .resolve(artifact)
                .resolve(version);

        var filename = artifact + "-" + version;
        if (classifier != null) {
            filename += "-" + classifier;
        }
        filename += ".jar";

        return folder.resolve(filename);
    }

    public Path writeModJar(String filename, IdentifiableContent... content) throws IOException {
        var path = getModsFolder().resolve(filename);
        writeJarFile(path, content);
        return path;
    }

    /**
     * This method of building a mod jar will use the current installation type to decide whether an exploded mod jar
     * is suitable, or a jar file in the mods/ folder.
     */
    public void buildInstallationAppropriateModProject(@Nullable String gradleProjectName, String jarFilename, ModFileBuilder.ModJarCustomizer customizer) throws IOException {
        buildInstallationAppropriateProject(gradleProjectName, jarFilename, customizer, true);
    }

    /**
     * Like buildInstallationAppropriateModProject, but doesn't add the created folders as mods to the run (like you'd do in one of our Gradle plugins).
     */
    public void buildInstallationAppropriateNonModProject(@Nullable String gradleProjectName, String jarFilename, ModFileBuilder.ModJarCustomizer customizer) throws IOException {
        buildInstallationAppropriateProject(gradleProjectName, jarFilename, customizer, false);
    }

    private void buildInstallationAppropriateProject(@Nullable String gradleProjectName, String jarFilename, ModFileBuilder.ModJarCustomizer customizer, boolean addModFolders) throws IOException {
        if (type == null) {
            throw new IllegalStateException("Installation hasn't been setup yet.");
        }

        if (type.isProduction()) {
            var builder = buildModJar(jarFilename);
            customizer.customize(builder);
            builder.build();
        } else {
            var buildJar = type == Type.USERDEV_JAR || type == Type.USERDEV_LEGACY_JAR;

            if (buildJar) {
                var builder = buildModJar(jarFilename);
                customizer.customize(builder);
                launchClasspath.add(builder.build());
            } else {
                var builder = buildGradleModProject(gradleProjectName);
                customizer.customize(builder);
                List<Path> pathItems = builder.build();
                launchClasspath.addAll(pathItems);
                var projectId = gradleProjectName == null ? "root" : gradleProjectName;
                if (addModFolders) {
                    setModFoldersProperty(Map.of(projectId, pathItems));
                }
            }
        }
    }

    public ModFileBuilder.ModJarBuilder buildModJar(String filename) throws IOException {
        var path = getModsFolder().resolve(filename);
        return ModFileBuilder.toJar(path);
    }

    public ModFileBuilder.ModFoldersBuilder buildGradleModProject() throws IOException {
        return buildGradleModProject(null);
    }

    /**
     * @param projectSubfolder Can be null to place output into the root project, but can also be a path relative
     *                         to the root project referring to the submodule.
     */
    public ModFileBuilder.ModFoldersBuilder buildGradleModProject(@Nullable String projectSubfolder) throws IOException {
        Path moduleRoot = projectRoot;
        if (projectSubfolder != null) {
            moduleRoot = moduleRoot.resolve(projectSubfolder);
        }

        // Build typical single-module gradle output directories
        var classesDir = moduleRoot.resolve("build/classes/java/main");
        Files.createDirectories(classesDir);
        var resourcesDir = moduleRoot.resolve("build/resources/main");
        Files.createDirectories(resourcesDir);

        return ModFileBuilder.toGradleOutputFolders(classesDir, resourcesDir);
    }

    public void writeConfig(String... lines) throws IOException {
        var file = getGameDir().resolve("config/fml.toml");

        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", lines));
    }

    public static void writeJarFile(Path file, IdentifiableContent... content) throws IOException {
        Files.createDirectories(file.getParent());
        try (var fout = Files.newOutputStream(file)) {
            writeJarFile(fout, content);
        }
    }

    public static IdentifiableContent createJarFile(String name, String relativePath, IdentifiableContent... content) throws IOException {
        var bout = new ByteArrayOutputStream();
        writeJarFile(bout, content);
        return new IdentifiableContent(name, relativePath, bout.toByteArray());
    }

    public static void createJarFileFromFolders(Path jarFile, Path... folders) throws IOException {
        // Look for manifests
        byte[] manifest = null;
        for (Path folder : folders) {
            Path manifestPath = folder.resolve(JarFile.MANIFEST_NAME);
            if (Files.isRegularFile(manifestPath)) {
                manifest = Files.readAllBytes(manifestPath);
            }
        }

        if (jarFile.getParent() != null) {
            Files.createDirectories(jarFile.getParent());
        }

        try (var jout = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(jarFile)))) {
            if (manifest != null) {
                jout.putNextEntry(new ZipEntry(JarFile.MANIFEST_NAME));
                jout.write(manifest);
                jout.closeEntry();
            }

            for (Path folder : folders) {
                try (var files = Files.walk(folder)) {
                    files.filter(Files::isRegularFile).forEach(path -> {
                        var relativePath = folder.relativize(path).toString().replace('\\', '/');
                        if (relativePath.startsWith("/")) {
                            relativePath = relativePath.substring(1);
                        }
                        if (JarFile.MANIFEST_NAME.equals(relativePath)) {
                            return;
                        }
                        var entry = new JarEntry(relativePath);
                        try {
                            jout.putNextEntry(entry);
                            Files.copy(path, jout);
                            jout.closeEntry();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                }
            }
        }
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

    public List<Path> getLaunchClasspath() {
        return launchClasspath;
    }
}
