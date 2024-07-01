/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.neoforged.fmlstartup.api.DiscoveredFile;
import net.neoforged.fmlstartup.api.StartupArgs;
import org.jetbrains.annotations.Nullable;

public class Startup {
    public static void main(String[] args) throws IOException {
        StartupLog.info("JVM Uptime: {}ms", ManagementFactory.getRuntimeMXBean().getUptime());

        try {
            run(args);
        } catch (FatalStartupException e) {
            FatalErrorReporting.reportFatalError(e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        var gameDir = getGameDir(args);
        StartupLog.info("Game Directory: {}", gameDir);
        var launchTarget = getLaunchTarget(args);
        StartupLog.info("Launch Target: {}", launchTarget);

        var cacheDir = new File(gameDir, ".neoforgecache");
        if (!cacheDir.exists() && !cacheDir.mkdir()) {
            StartupLog.error("Failed to create cache directory: {}", cacheDir);
        }

        var files = new ArrayList<DiscoveredFile>(200);
        var directories = new ArrayList<File>();

        searchClassPath(files, directories);

        // Discovery content in mod folders
        for (var modFolder : getModFolders(gameDir)) {
            searchFolder(modFolder, files);
        }

        // Load metadata cache
        var start = System.nanoTime();
        var cache = new MetadataCache(cacheDir);
        StartupLog.info("Read metadata cache in {}", elapsedMillis(start));

        // Build the startup layer
        List<Path> bootModulePaths = new ArrayList<>();
        List<String> bootModules = new ArrayList<>();
        start = System.nanoTime();
        var metaRead = 0;
        var claimedFiles = new HashSet<File>(files.size());
        for (var file : files) {
            var metadata = cache.get(file.cacheKey());
            if (metadata == null) {
                metaRead++;
                metadata = readMetadata(file.file());
            }

            // This also marks it as used
            cache.set(file.cacheKey(), metadata);
            if (isIncompatibleArchitecture(metadata.nativeArchitectures())) {
                StartupLog.info("Skipping {} due to incompatible architecture", file.file());
                claimedFiles.add(file.file());
            } else if (BootModules.isBootModule(metadata.moduleName())) {
                bootModulePaths.add(file.file().toPath());
                bootModules.add(metadata.moduleName());
                claimedFiles.add(file.file());
            }
        }
        StartupLog.info("Metadata for {} files read in {}. Used cache for {}.", metaRead, elapsedMillis(start), files.size() - metaRead);

        cache.save();

        var moduleFinder = ModuleFinder.of(bootModulePaths.toArray(Path[]::new));

        var missingRequiredModules = BootModules.getMissingRequiredModules(bootModules);
        if (!missingRequiredModules.isEmpty()) {
            // In development: Try to recover from not finding the modules as files on the classpath by
            // assembling them from directories
            bootModules.addAll(missingRequiredModules);

            var recoveredModuleFinder = BootModules.recoverRequiredModulesFromDirectories(missingRequiredModules, directories, claimedFiles);
            moduleFinder = ModuleFinder.compose(moduleFinder, recoveredModuleFinder);
        }

        start = System.nanoTime();

        Configuration startupConfiguration = Configuration.resolveAndBind(
                ModuleFinder.of(),
                List.of(ModuleLayer.boot().configuration()),
                moduleFinder,
                bootModules);

        StartupLog.info("Built startup module layer configuration in {}", elapsedMillis(start));

        start = System.nanoTime();
        var systemClassLoader = ClassLoader.getSystemClassLoader();
        ModuleLayer.defineModules(
                startupConfiguration,
                List.of(ModuleLayer.boot()),
                s -> systemClassLoader);
        StartupLog.info("Built startup module layer in {}", elapsedMillis(start));

        var instrumentation = obtainInstrumentation();

        // Use "loadModule" hacks to get the system CL to fully recognize our modules as if they were on the module path
        loadModules(instrumentation, systemClassLoader, startupConfiguration);

        // Disabling JMX for JUnit improves startup time
        if (System.getProperty("log4j2.disable.jmx") == null) {
            System.setProperty("log4j2.disable.jmx", "true");
        }

        // Launch FML
        var previousClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            var fmlLoader = Class.forName("net.neoforged.fml.loading.FMLLoader");

            var startupArgs = new StartupArgs(
                    gameDir,
                    launchTarget,
                    args,
                    claimedFiles);

            var startupMethod = fmlLoader.getMethod("startup", Instrumentation.class, StartupArgs.class);
            startupMethod.invoke(null, instrumentation, startupArgs);
        } catch (InvocationTargetException e) {
            e.getCause().printStackTrace();
            throw new FatalStartupException("Failed to load FML: " + e.getCause());
        } catch (Exception e) {
            throw new FatalStartupException("Failed to load FML." + e);
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }

        StartupLog.info("After FMLLoader.startup");

//        instrumentation.addTransformer(new ClassFileTransformer() {
//            @Override
//            public byte[] transform(Module module, ClassLoader loader, String className, Class<?>
//                    classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws
//                    IllegalClassFormatException {
//                return null;
//            }
//        });
    }

    private static boolean isIncompatibleArchitecture(List<NativeArchitecture> nativeArchitectures) {
        if (nativeArchitectures.isEmpty()) {
            return false;
        }

        for (var nativeArchitecture : nativeArchitectures) {
            if (nativeArchitecture.os() != NativeArchitectureOS.current()) {
                continue;
            }
            if (nativeArchitecture.cpu() != null && nativeArchitecture.cpu() != NativeArchitectureCPU.current()) {
                continue;
            }
            return false;
        }

        return true;
    }

    private static void loadModules(Instrumentation instrumentation, ClassLoader systemCl, Configuration cf) {
        long start = System.nanoTime();
        instrumentation.redefineModule(
                systemCl.getClass().getModule(),
                Set.of(),
                Map.of(),
                Map.of(
                        systemCl.getClass().getPackageName(), Set.of(Startup.class.getModule())),
                Set.of(),
                Map.of());

        Method loadModule;
        try {
            loadModule = systemCl.getClass().getMethod("loadModule", ModuleReference.class);
        } catch (NoSuchMethodException e) {
            throw new FatalStartupException("Failed to find method 'loadModule(ModuleReference)' in the system classloader (" + systemCl.getClass() + "). This may occur with an incompatible version of Java.");
        }
        try {
            for (var module : cf.modules()) {
                loadModule.invoke(systemCl, module.reference());
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new FatalStartupException("Failed to invoke method 'loadModule(ModuleReference)' in the system classloader (" + systemCl.getClass() + "). This may occur with an incompatible version of Java: " + e);
        }
        StartupLog.debug("Added modules to system classloader in {}", elapsedMillis(start));
    }

    private static String getPathDisplayName(File gameDir, File path) {
        var gameDirPathStr = gameDir.getAbsolutePath();
        var pathStr = path.getAbsolutePath();
        if (pathStr.startsWith(gameDirPathStr)) {
            return pathStr.substring(gameDirPathStr.length());
        }
        return pathStr;
    }

    private static void searchClassPath(List<DiscoveredFile> files, List<File> directories) {
        var start = System.nanoTime();

        // Find entries on the classpath
        var discovered = 0;
        for (var classPathEntry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            var file = new File(classPathEntry);
            if (file.isFile()) {
                files.add(DiscoveredFile.of(file));
            } else {
                directories.add(file);
            }
            discovered++;
        }

        StartupLog.info("Discovered {} classpath entries in {}.", discovered, elapsedMillis(start));
    }

    private static void searchFolder(File folder, List<DiscoveredFile> files) {
        var start = System.nanoTime();

        // Find entries on the classpath
        var discovered = 0;
        var folderContent = folder.list();
        if (folderContent == null) {
            throw new RuntimeException("Failed to list folder contents of " + folder);
        }
        for (String filename : folderContent) {
            if (caseInsensitiveEndsWith(filename, ".jar")) {
                var file = new File(folder, filename);
                if (file.isFile()) {
                    files.add(DiscoveredFile.of(file));
                    discovered++;
                }
            }
        }

        StartupLog.info("Discovered {} items in {} in {}.", discovered, folder, elapsedMillis(start));
    }

    // This is the fastest case-insensitive .endsWith we can get
    private static boolean caseInsensitiveEndsWith(String filename, String suffix) {
        return filename.regionMatches(true, filename.length() - suffix.length(), suffix, 0, suffix.length());
    }

    private static String elapsedMillis(long start) {
        var elapsed = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start) / 1000.0f;
        return String.format(Locale.ROOT, "%.2fms", elapsed);
    }

    private static CachedMetadata readMetadata(File file) throws IOException {
        var nativeArchitectures = new ArrayList<NativeArchitecture>(1);
        Manifest manifest;
        ModuleDescriptor moduleDescriptor = null;
        try (var jarFile = new JarFile(file, false, JarFile.OPEN_READ, JarFile.runtimeVersion())) {
            manifest = jarFile.getManifest();
            var moduleInfoEntry = jarFile.getEntry("module-info.class");
            if (moduleInfoEntry != null) {
                try (var moduleInfoIn = jarFile.getInputStream(moduleInfoEntry)) {
                    moduleDescriptor = ModuleDescriptor.read(moduleInfoIn);
                }
            }
            if (moduleDescriptor == null) {
                try {
                    moduleDescriptor = getAutomaticModuleDescriptor(file, jarFile);
                } catch (Exception e) {
                    StartupLog.info("Failed to generate automatic module descriptor for {}: {}", file, e);
                }
            }
        }

        // This is an early pass to find libraries that are not to be loaded on the game layer
        boolean forceBootLayer = false;
        if (manifest != null) {
            var mainAttributes = manifest.getMainAttributes();
            var modType = mainAttributes.getValue("FMLModType");
            if ("BOOTLIBRARY".equalsIgnoreCase(modType) || "LIBRARY".equalsIgnoreCase(modType)) {
                forceBootLayer = true;
            }

            // Handle Minecraft putting all LWJGL natives for an OS onto the CP, while LWJGL does not support that
            // when in modular mode. We have to filter out the non-applicable natives.
            // Example Manifest entry: LWJGL-Platform: windows/x64
            var lwjglPlatform = mainAttributes.getValue("LWJGL-Platform");
            if (lwjglPlatform != null) {
                var arch = parseLwjglPlatform(file, lwjglPlatform);
                if (arch != null) {
                    nativeArchitectures.add(arch);
                }
            }
        }

        var moduleName = moduleDescriptor != null ? moduleDescriptor.name() : null;

        return new CachedMetadata(moduleName, nativeArchitectures, forceBootLayer);
    }

    private static @Nullable NativeArchitecture parseLwjglPlatform(File file, String lwjglPlatform) {
        var parts = lwjglPlatform.split("/", 2);
        var os = switch (parts[0]) {
            case "windows" -> NativeArchitectureOS.WINDOWS;
            case "macosx" -> NativeArchitectureOS.MACOSX;
            case "linux" -> NativeArchitectureOS.LINUX;
            default -> null;
        };
        NativeArchitectureCPU cpu = null;
        boolean invalidCpu = false;
        if (parts.length > 1) {
            cpu = switch (parts[1]) {
                case "x64" -> NativeArchitectureCPU.X64;
                case "x86" -> NativeArchitectureCPU.X86;
                case "arm64" -> NativeArchitectureCPU.ARM64;
                default -> {
                    invalidCpu = true;
                    yield null;
                }
            };
        }
        NativeArchitecture arch;
        if (os == null || invalidCpu) {
            StartupLog.error("{} reference invalid LWJGL-Platform: {}", file, lwjglPlatform);
            arch = null;
        } else {
            arch = new NativeArchitecture(os, cpu);
        }
        return arch;
    }

    private static final Attributes.Name AUTOMATIC_MODULE_NAME = new Attributes.Name("Automatic-Module-Name");

    /**
     * Sadly this logic is not exposed. For testing purposes, one can use ModuleFinder to get the same info.
     * The implementation as of Java 21 was here: jdk.internal.module.ModulePath#deriveModuleDescriptor(java.util.jar.JarFile)
     */
    private static ModuleDescriptor getAutomaticModuleDescriptor(File file, JarFile jarFile) throws IOException {
        // Read Automatic-Module-Name attribute if present
        Manifest man = jarFile.getManifest();
        String moduleName = null;
        if (man != null && man.getMainAttributes() != null) {
            moduleName = man.getMainAttributes().getValue(AUTOMATIC_MODULE_NAME);
        }

        // Derive the version, and the module name if needed, from JAR file name
        String filename = file.getName();
        String basename = filename.substring(0, filename.length() - 4);
        String vs = null;

        // find first occurrence of -${NUMBER}. or -${NUMBER}$
        Matcher matcher = Patterns.DASH_VERSION.matcher(basename);
        if (matcher.find()) {
            int start = matcher.start();

            // attempt to parse the tail as a version string
            try {
                String tail = basename.substring(start + 1);
                ModuleDescriptor.Version.parse(tail);
                vs = tail;
            } catch (IllegalArgumentException ignore) {}

            basename = basename.substring(0, start);
        }

        // Create builder, using the name derived from file name when
        // Automatic-Module-Name not present
        ModuleDescriptor.Builder builder;
        if (moduleName != null) {
            try {
                builder = ModuleDescriptor.newAutomaticModule(moduleName);
            } catch (IllegalArgumentException e) {
                throw new FindException(AUTOMATIC_MODULE_NAME + ": " + e.getMessage());
            }
        } else {
            builder = ModuleDescriptor.newAutomaticModule(cleanModuleName(basename));
        }

        // module version if present
        if (vs != null)
            builder.version(vs);

        return builder.build();
    }

    /**
     * These are from jdk.internal.module.ModulePath.Patterns
     * Interesting little trick here with the inner class, avoiding class-loading and compiling
     * until its needed.
     */
    private static class Patterns {
        static final Pattern DASH_VERSION = Pattern.compile("-(\\d+(\\.|$))");
        static final Pattern NON_ALPHANUM = Pattern.compile("[^A-Za-z0-9]");
        static final Pattern REPEATING_DOTS = Pattern.compile("(\\.)(\\1)+");
        static final Pattern LEADING_DOTS = Pattern.compile("^\\.");
        static final Pattern TRAILING_DOTS = Pattern.compile("\\.$");
    }

    /**
     * Clean up candidate module name derived from a JAR file name.
     */
    private static String cleanModuleName(String mn) {
        // replace non-alphanumeric
        mn = Patterns.NON_ALPHANUM.matcher(mn).replaceAll(".");

        // collapse repeating dots
        mn = Patterns.REPEATING_DOTS.matcher(mn).replaceAll(".");

        // drop leading dots
        if (!mn.isEmpty() && mn.charAt(0) == '.')
            mn = Patterns.LEADING_DOTS.matcher(mn).replaceAll("");

        // drop trailing dots
        int len = mn.length();
        if (len > 0 && mn.charAt(len - 1) == '.')
            mn = Patterns.TRAILING_DOTS.matcher(mn).replaceAll("");

        return mn;
    }

    private static List<File> getModFolders(File gameDir) {
        var result = new ArrayList<File>();

        // When the folders are overriden, they must exist, unless prefixed with a dash
        var overriddenModFolders = System.getProperty("fml.modFolders", "-mods");

        var paths = overriddenModFolders.split(File.pathSeparator);
        for (String path : paths) {
            // Allow specifying folders as: -Dfml.modFolders=-servermods;mods
            // to allow optional entries that do not have to exist.
            boolean optional = false;
            if (path.startsWith("-")) {
                path = path.substring(1);
                optional = true;
            }

            var folder = new File(path);
            if (!folder.isAbsolute()) {
                folder = new File(gameDir, path);
            }
            if (!folder.isDirectory()) {
                if (!optional) {
                    throw new RuntimeException("Mod folder passed in system property fml.modFolders is not a directory: " + path);
                }
            } else {
                result.add(folder);
            }
        }

        return result;
    }

    private static File getGameDir(String[] args) {
        var gameDir = new File(getArg(args, "gameDir", "")).getAbsoluteFile();
        if (!gameDir.isDirectory()) {
            throw new RuntimeException("The game directory passed on the command-line is not a directory: " + gameDir);
        }
        return gameDir;
    }

    private static String getLaunchTarget(String[] args) {
        return getArg(args, "launchTarget", "forgeclient");
    }

    private static String getArg(String[] args, String name, String defaultValue) {
        var argName = "--" + name;
        for (int i = 0; i + 1 < args.length; i++) {
            if (argName.equals(args[i])) {
                return args[i + 1];
            }
        }

        return defaultValue;
    }

    private static Instrumentation obtainInstrumentation() {
        var storedExceptions = new ArrayList<Exception>();

        // Obtain instrumentation as early as possible. We use reflection here since we want to make sure that even if
        // we are loaded through other means, we get the agent class from the system CL.
        try {
            var devAgent = Class.forName("net.neoforged.fmlstartup.DevAgent", true, ClassLoader.getSystemClassLoader());
            var instrumentation = (Instrumentation) devAgent.getMethod("getInstrumentation").invoke(null);
            StartupLog.info("Using our own agent");
            return instrumentation;
        } catch (Exception e) {
            storedExceptions.add(e);
        }

        try {
            var byteBuddyAgent = Class.forName("net.bytebuddy.agent.ByteBuddyAgent", true, ClassLoader.getSystemClassLoader());
            var instrumentation = (Instrumentation) byteBuddyAgent.getMethod("install").invoke(null);
            StartupLog.info("Using byte-buddy fallback");
            return instrumentation;
        } catch (Exception e) {
            storedExceptions.add(e);
        }

        var e = new IllegalStateException("Failed to obtain instrumentation.");
        storedExceptions.forEach(e::addSuppressed);
        throw e;
    }
}
