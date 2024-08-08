package fmlbuild;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads and installs a production NeoForge client.
 */
public abstract class InstallProductionClientTask extends DefaultTask {

    private final ExecOperations execOperations;

    /**
     * This file collection should contain exactly one file:
     * The NeoForge Installer Jar-File.
     */
    @InputFiles
    public abstract ConfigurableFileCollection getInstaller();

    /**
     * This file collection should only contain the NeoForm Runtime
     * executable jar file.
     */
    @InputFiles
    public abstract ConfigurableFileCollection getNfrt();

    /**
     * The Minecraft version matching the NeoForge version to install.
     */
    @Input
    public abstract Property<String> getMinecraftVersion();

    /**
     * The NeoForge version, used for placeholders when launching the game.
     * It needs to match the installer used.
     */
    @Input
    public abstract Property<String> getNeoForgeVersion();

    /**
     * Where NeoForge should be installed.
     */
    @OutputDirectory
    public abstract DirectoryProperty getInstallDir();

    /**
     * Where the game assets will be downloaded to.
     */
    @OutputDirectory
    public abstract DirectoryProperty getAssetsDir();

    /**
     * Where the shared libraries directory will be placed.
     */
    @OutputDirectory
    public abstract DirectoryProperty getLibrariesDir();

    /**
     * Points to the original Vanilla client JAR-file, if applicable.
     */
    @OutputFile
    public abstract RegularFileProperty getObfuscatedClientJar();

    /**
     * Points to a JVM compatible Arg-File, which contains the JVM parameters needed to launch the game.
     */
    @OutputFile
    public abstract RegularFileProperty getVanillaJvmArgFile();

    /**
     * A JVM compatible Arg-File that only contains the name of the main-class used by Vanilla.
     */
    @OutputFile
    public abstract RegularFileProperty getVanillaMainClassArgFile();

    /**
     * A File that contains one line per program argument passed to Vanilla. Encoded as UTF-8, unlike
     * the normal JVM arg-files.
     */
    @OutputFile
    public abstract RegularFileProperty getVanillaProgramArgFile();

    /**
     * Points to a JVM compatible Arg-File, which contains the JVM parameters needed to launch NeoForge.
     * This is additive to {@link #getVanillaJvmArgFile()}.
     */
    @OutputFile
    public abstract RegularFileProperty getNeoForgeJvmArgFile();

    /**
     * A JVM compatible Arg-File that contains the class-name used by NeoForge for launching.
     * This is mutually exclusive with {@link #getVanillaMainClassArgFile()}.
     */
    @OutputFile
    public abstract RegularFileProperty getNeoForgeMainClassArgFile();

    /**
     * A File that contains one line per program argument passed to NeoForge for startup. Encoded as UTF-8, unlike
     * the normal JVM arg-files.
     * This is additive to {@link #getVanillaProgramArgFile()}.
     */
    @OutputFile
    public abstract RegularFileProperty getNeoForgeProgramArgFile();

    @Inject
    public InstallProductionClientTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @TaskAction
    public void install() throws Exception {
        var installDir = getInstallDir().getAsFile().get().toPath().toAbsolutePath();
        Files.createDirectories(installDir);

        // Installer looks for this file
        var profilesJsonPath = installDir.resolve("launcher_profiles.json");
        Files.writeString(profilesJsonPath, "{}");

        execOperations.javaexec(spec -> {
            spec.workingDir(installDir);
            spec.classpath(getInstaller().getSingleFile());
            spec.args("--install-client", installDir.toString());
            try {
                spec.setStandardOutput(new BufferedOutputStream(Files.newOutputStream(installDir.resolve("install.log"))));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        var minecraftVersion = getMinecraftVersion().get();
        var neoForgeVersion = getNeoForgeVersion().get();

        // Download Minecraft Assets and read the asset index id and root for the program arguments
        var assetPropertiesFile = new File(getTemporaryDir(), "asset.properties");
        execOperations.javaexec(spec -> {
            spec.classpath(getNfrt().getSingleFile());
            spec.args(
                    "download-assets",
                    "--minecraft-version",
                    minecraftVersion,
                    "--output-properties-to",
                    assetPropertiesFile.getAbsolutePath()
            );
        });

        var assetProperties = new Properties();
        try (var in = new FileInputStream(assetPropertiesFile)) {
            assetProperties.load(in);
        }

        var assetIndex = Objects.requireNonNull(assetProperties.getProperty("asset_index"), "asset_index");
        var assetsRoot = Objects.requireNonNull(assetProperties.getProperty("assets_root"), "assets_root");
        var nativesDir = installDir.resolve("natives");
        Files.createDirectories(nativesDir);

        // Set up the placeholders generally used by Vanilla profiles in their argument definitions.
        var placeholders = new HashMap<String, String>();
        placeholders.put("auth_player_name", "FMLDev");
        placeholders.put("version_name", minecraftVersion);
        placeholders.put("game_directory", getInstallDir().getAsFile().get().getAbsolutePath());
        placeholders.put("auth_uuid", "00000000-0000-4000-8000-000000000000");
        placeholders.put("auth_access_token", "0");
        placeholders.put("clientid", "0");
        placeholders.put("auth_xuid", "0");
        placeholders.put("user_type", "legacy");
        placeholders.put("version_type", "release");
        placeholders.put("assets_index_name", assetIndex);
        placeholders.put("assets_root", assetsRoot);
        placeholders.put("launcher_name", "NeoForgeProdInstallation");
        placeholders.put("launcher_version", "1.0");
        placeholders.put("natives_directory", nativesDir.toAbsolutePath().toString());
        // These are used by NF but provided by the launcher
        placeholders.put("library_directory", getLibrariesDir().get().getAsFile().getAbsolutePath());
        placeholders.put("classpath_separator", File.pathSeparator);

        writeArgFiles(installDir, minecraftVersion, placeholders, getVanillaJvmArgFile(), getVanillaMainClassArgFile(), getVanillaProgramArgFile());
        writeArgFiles(installDir, "neoforge-" + neoForgeVersion, placeholders, getNeoForgeJvmArgFile(), getNeoForgeMainClassArgFile(), getNeoForgeProgramArgFile());
    }

    private void writeArgFiles(Path installDir,
                               String profileName,
                               HashMap<String, String> placeholders,
                               RegularFileProperty jvmArgFileDestination,
                               RegularFileProperty mainClassArgFileDestination,
                               RegularFileProperty programArgFileDestination) throws IOException {
        // Read back the version manifest and get the startup arguments
        var manifestPath = installDir.resolve("versions").resolve(profileName).resolve(profileName + ".json");
        var manifest = readJson(manifestPath);

        var mainClass = manifest.getAsJsonPrimitive("mainClass").getAsString();

        // Vanilla Arguments
        var programArgs = getArguments(manifest, "game");
        expandPlaceholders(programArgs, placeholders);
        RunUtils.escapeJvmArgs(programArgs);
        var programArgsFile = programArgFileDestination.get().getAsFile().toPath();
        // Program args are generally read as UTF-8 by user-code (i.e. our argument expansion, DevLaunch, etc.)
        Files.write(programArgsFile, programArgs, StandardCharsets.UTF_8);

        // This file can be read by both the JVM itself, or DevLaunch, which makes the lowest common denominator character set ASCII
        var mainArgsFile = mainClassArgFileDestination.get().getAsFile().toPath();
        Files.writeString(mainArgsFile, mainClass, StandardCharsets.US_ASCII);

        var jvmArgs = getArguments(manifest, "jvm");
        expandPlaceholders(jvmArgs, placeholders);
        RunUtils.cleanJvmArgs(jvmArgs);
        RunUtils.escapeJvmArgs(jvmArgs);
        var jvmArgsFile = jvmArgFileDestination.get().getAsFile().toPath();
        // The JVM receives the args in native platform encoding, so we have to encode them as such
        Files.write(jvmArgsFile, jvmArgs, Charset.forName(System.getProperty("native.encoding")));
    }

    private static void expandPlaceholders(List<String> args, Map<String, String> variables) {
        var pattern = Pattern.compile("\\$\\{([^}]+)}");

        args.replaceAll(s -> {
            var matcher = pattern.matcher(s);
            return matcher.replaceAll(match -> {
                var variable = match.group(1);
                return Matcher.quoteReplacement(variables.getOrDefault(variable, matcher.group()));
            });
        });
    }

    private static List<String> getArguments(JsonObject manifest, String kind) {
        var result = new ArrayList<String>();

        var gameArgs = manifest.getAsJsonObject("arguments").getAsJsonArray(kind);
        for (var gameArg : gameArgs) {
            if (gameArg.isJsonObject()) {
                evaluateRule(gameArg.getAsJsonObject(), result);
            } else {
                result.add(gameArg.getAsString());
            }
        }

        return result;
    }

    /**
     * Given a "rule" object from a Vanilla launcher profile, evaluate it into the effective arguments.
     */
    private static void evaluateRule(JsonObject ruleObject, List<String> out) {
        for (var ruleEl : ruleObject.getAsJsonArray("rules")) {
            var rule = ruleEl.getAsJsonObject();
            boolean allow = "allow".equals(rule.getAsJsonPrimitive("action").getAsString());
            // We only care about "os" rules
            if (rule.has("os")) {
                var os = rule.getAsJsonObject("os");
                var name = os.getAsJsonPrimitive("name");
                var arch = os.getAsJsonPrimitive("arch");
                boolean ruleMatches = (name == null || isCurrentOsName(name.getAsString())) && (arch == null || isCurrentOsArch(arch.getAsString()));
                if (ruleMatches != allow) {
                    return;
                }
            } else {
                // We assume unknown rules do not apply
                return;
            }
        }

        var value = ruleObject.get("value");
        if (value.isJsonPrimitive()) {
            out.add(value.getAsString());
        } else {
            for (var valueEl : value.getAsJsonArray()) {
                out.add(valueEl.getAsString());
            }
        }
    }

    private static boolean isCurrentOsName(String os) {
        return switch (os) {
            case "windows" -> OperatingSystem.current() == OperatingSystem.WINDOWS;
            case "osx" -> OperatingSystem.current() == OperatingSystem.MACOS;
            case "linux" -> OperatingSystem.current() == OperatingSystem.LINUX;
            default -> false;
        };
    }

    private static boolean isCurrentOsArch(String arch) {
        return switch (arch) {
            case "x86" -> System.getProperty("os.arch").equals("x86");
            default -> false;
        };
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, JsonObject.class);
        }
    }

}

