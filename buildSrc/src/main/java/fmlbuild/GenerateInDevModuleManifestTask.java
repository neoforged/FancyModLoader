package fmlbuild;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

abstract class GenerateInDevModuleManifestTask extends DefaultTask {
    @Input
    abstract Property<String> getModuleName();

    @InputDirectory
    abstract ConfigurableFileCollection getClassesFolders();

    @InputDirectory
    abstract DirectoryProperty getResourcesFolder();

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory();

    @Inject
    public GenerateInDevModuleManifestTask() {
    }

    @TaskAction
    public void writeManifest() throws IOException {
        var outputDirectory = getOutputDirectory().getAsFile().get();
        var outputFile = new File(outputDirectory, "META-INF/indevmodules/" + getModuleName().get() + ".properties");
        outputFile.getParentFile().mkdirs();

        try (var writer = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            for (File file : getClassesFolders().getFiles()) {
                writer.append(file.getAbsolutePath()).append("\n");
            }
            writer.append(getResourcesFolder().get().getAsFile().getAbsolutePath()).append("\n");
        }
    }
}
