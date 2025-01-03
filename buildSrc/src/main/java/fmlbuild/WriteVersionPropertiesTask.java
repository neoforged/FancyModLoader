package fmlbuild;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Writes version.properties for inclusion in the Jar to enable retrieval of the FML version in dev and prod.
 */
@DisableCachingByDefault(because = "not worth caching")
public abstract class WriteVersionPropertiesTask extends DefaultTask {
    @Input
    abstract Property<String> getProjectGroup();

    @Input
    abstract Property<String> getProjectArtifact();

    @Input
    abstract Property<String> getProjectVersion();

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory();

    @Inject
    public WriteVersionPropertiesTask() {
        var project = getProject();
        getProjectGroup().convention(project.provider(() -> project.getGroup().toString()));
        var baseExtension = project.getExtensions().getByType(BasePluginExtension.class);
        getProjectArtifact().convention(baseExtension.getArchivesName());
        getProjectVersion().set(project.provider(() -> project.getVersion().toString()));
    }

    @TaskAction
    public void write() throws IOException {
        var relativeFilePath = "META-INF/versions/" + getProjectGroup().get() + "." + getProjectArtifact().get();

        var propertiesPath = getOutputDirectory().file(relativeFilePath).get().getAsFile();
        propertiesPath.getParentFile().mkdirs();
        var properties = new Properties();
        properties.setProperty("projectVersion", getProjectVersion().get());
        try (var writer = new FileWriter(propertiesPath, StandardCharsets.UTF_8)) {
            properties.store(writer, "");
        }
    }
}
