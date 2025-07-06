package fmlbuild;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipFile;

/**
 * Downloads and installs a production NeoForge server.
 */
public abstract class InstallProductionServerTask extends DefaultTask {
    private final ExecOperations execOperations;

    /**
     * The NeoForge installer jar is expected to be the only file in this file collection.
     */
    @InputFiles
    public abstract ConfigurableFileCollection getInstaller();

    /**
     * The NeoForge version that is being installed. This is required to then look up the server argument
     * file after the installer has done its thing.
     */
    @Input
    public abstract Property<String> getNeoForgeVersion();

    /**
     * Where the server should be installed.
     */
    @OutputDirectory
    public abstract DirectoryProperty getInstallDir();

    /**
     * Write an argument-file for the JVM containing the required JVM args for startup.
     * Any module or classpath arguments have been stripped.
     */
    @OutputFile
    public abstract RegularFileProperty getNeoForgeJvmArgFile();

    /**
     * Write an argument-file for DevLauncher here that contains the program arguments to launch the server.
     */
    @OutputFile
    public abstract RegularFileProperty getNeoForgeProgramArgFile();

    /**
     * Write an argument-file for DevLauncher here that contains the original main class name used
     * to launch the server.
     */
    @OutputFile
    public abstract RegularFileProperty getNeoForgeMainClassArgFile();

    @Inject
    public InstallProductionServerTask(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @TaskAction
    public void install() throws Exception {
        var installDir = getInstallDir().getAsFile().get().toPath().toAbsolutePath();
        Files.createDirectories(installDir);

        execOperations.javaexec(spec -> {
            spec.workingDir(installDir);
            spec.classpath(getInstaller().getSingleFile());
            spec.args("--install-server", installDir.toString());
            try {
                spec.setStandardOutput(new BufferedOutputStream(Files.newOutputStream(installDir.resolve("install.log"))));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // We need to know the name of the main class to split the arg-file into JVM and program arguments
        var mainClass = getMainClass();
        // The difference here is only really in path separators...
        var argFileName = File.pathSeparatorChar == ':' ? "unix_args.txt" : "win_args.txt";
        var argFilePath = installDir.resolve("libraries/net/neoforged/neoforge/" + getNeoForgeVersion().get() + "/" + argFileName);
        var argFileContent = Files.readString(
                argFilePath,
                StandardCharsets.UTF_8
        );
        var startOfSplit = argFileContent.indexOf(mainClass);
        if (startOfSplit == -1) {
            throw new GradleException("Argfile " + argFilePath + " does not contain the main class name " + mainClass);
        }
        if (argFileContent.indexOf(mainClass, startOfSplit + 1) != -1) {
            throw new GradleException("Argfile " + argFilePath + " contains the main class name " + mainClass + " more than once.");
        }

        var jvmArgs = argFileContent.substring(0, startOfSplit);
        var programArgs = argFileContent.substring(startOfSplit + mainClass.length() + 1);

        // We need to sanitize all JVM args by removing modular args
        var jvmArgParams = RunUtils.splitJvmArgs(jvmArgs);
        RunUtils.cleanJvmArgs(jvmArgParams);

        // This is read by the JVM using the native platform encoding
        Files.write(getNeoForgeJvmArgFile().getAsFile().get().toPath(), jvmArgParams, Charset.forName(System.getProperty("native.encoding")));
        Files.writeString(getNeoForgeMainClassArgFile().getAsFile().get().toPath(), mainClass, Charset.forName(System.getProperty("native.encoding")));
        // This is read by our own code in UTF-8
        Files.writeString(getNeoForgeProgramArgFile().getAsFile().get().toPath(), programArgs, StandardCharsets.UTF_8);
    }

    private String getMainClass() throws IOException {
        String versionContent;
        try (var zf = new ZipFile(getInstaller().getSingleFile())) {
            var entry = zf.getEntry("version.json");
            if (entry == null) {
                throw new GradleException("The installer " + getInstaller().getSingleFile() + " contains no version.json");
            }
            try (var in = zf.getInputStream(entry)) {
                versionContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        var versionRoot = new Gson().fromJson(versionContent, JsonObject.class);
        return versionRoot.getAsJsonPrimitive("mainClass").getAsString();
    }
}
